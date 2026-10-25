package za.co.afritiers.tagger.network;

import za.co.afritiers.tagger.waitlist.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Polling runs off-thread; the client tick alone receives notification events. */
public final class QueueRepository implements AutoCloseable {
    public enum Phase { IDLE, LOADING, READY, STALE, ERROR }
    public record State(Phase phase, Waitlists snapshot, Instant lastSuccess, Instant nextRefresh, String message) { }
    private final QueueSource source;
    private final ExecutorService worker;
    private final Clock clock;
    private final WaitlistAlerts alerts = new WaitlistAlerts();
    private volatile State state = new State(Phase.IDLE, Waitlists.EMPTY, null, null, "Choose waitlists to watch");
    private boolean active, closed;
    private int failures;
    private long generation;
    private Future<?> pending;
    private Instant allowedAt = Instant.MIN;

    public QueueRepository(QueueSource source) {
        this(source, Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("afritiers-queues").factory()), Clock.systemUTC());
    }
    public QueueRepository(QueueSource source, ExecutorService worker, Clock clock) {
        this.source = source; this.worker = worker; this.clock = clock;
    }
    public State state() { return state; }

    public synchronized List<Waitlists.Entry> tick(boolean inGame, boolean browsing, Set<String> selected) {
        if (closed) return List.of();
        boolean needed = browsing || inGame && !selected.isEmpty();
        if (active != needed) {
            active = needed; generation++;
            if (pending != null) pending.cancel(true);
            state = new State(Phase.IDLE, Waitlists.EMPTY, state.lastSuccess(), active ? allowedAt : null,
                    active ? "Waiting to sync waitlists" : "Choose waitlists to watch");
        }
        if (!active) return List.of();
        if (state.phase() == Phase.READY && !fresh(state.snapshot()))
            state = new State(Phase.STALE, state.snapshot(), state.lastSuccess(), state.nextRefresh(), "Queue data is out of date");
        List<Waitlists.Entry> notifications = state.phase() == Phase.READY ? alerts.update(state.snapshot(), selected, inGame) : List.of();
        if (state.phase() != Phase.LOADING && !clock.instant().isBefore(allowedAt)) start();
        return notifications;
    }

    public synchronized String refresh() {
        if (!active || closed) return "Open Waitlists to refresh";
        if (state.phase() == Phase.LOADING) return "Waitlists are already updating";
        if (clock.instant().isBefore(allowedAt)) return "Refresh available in " + Math.max(1, Duration.between(clock.instant(), allowedAt).toSeconds()) + "s";
        start(); return "Updating waitlists";
    }

    private boolean fresh(Waitlists snapshot) {
        return snapshot.generatedAt() != null && !snapshot.generatedAt().isBefore(clock.instant().minusSeconds(300))
                && !snapshot.generatedAt().isAfter(clock.instant().plusSeconds(120));
    }

    private void start() {
        long token = generation;
        allowedAt = clock.instant().plusSeconds(30);
        state = new State(Phase.LOADING, state.snapshot(), state.lastSuccess(), allowedAt, "Updating waitlists");
        pending = worker.submit(() -> {
            try {
                Waitlists snapshot = source.fetch();
                synchronized (this) {
                    if (closed || !active || token != generation) return;
                    failures = 0;
                    state = new State(fresh(snapshot) ? Phase.READY : Phase.STALE, snapshot, clock.instant(), allowedAt,
                            fresh(snapshot) ? "Waitlists up to date" : "Queue data is out of date");
                }
            } catch (Exception exception) {
                synchronized (this) {
                    if (closed || !active || token != generation) return;
                    failures = Math.min(6, failures + 1);
                    allowedAt = clock.instant().plusSeconds(Math.min(900, 30L << (failures - 1)));
                    if (exception instanceof RankingSource.ApiException api && api.retryAfter() != null && api.retryAfter().isAfter(allowedAt))
                        allowedAt = api.retryAfter();
                    state = new State(Phase.ERROR, Waitlists.EMPTY, state.lastSuccess(), allowedAt,
                            exception instanceof RankingSource.ApiException ? exception.getMessage() : "Waitlists unavailable; retrying automatically");
                }
            }
        });
    }

    @Override public synchronized void close() {
        closed = true; active = false; generation++;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow(); source.close();
    }
}
