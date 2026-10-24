package za.co.afritiers.tagger.network;

import java.time.*;
import java.util.concurrent.*;
import java.util.function.IntSupplier;
import za.co.afritiers.tagger.ranking.Rankings;

/** All transitions are serialized; renderers read one immutable, volatile snapshot. */
public final class RankingRepository implements AutoCloseable {
    public enum Phase { DISCONNECTED, DISABLED, LOADING, READY, STALE, ERROR }
    public record State(Phase phase, Rankings rankings, Instant lastAttempt,
                        Instant lastSuccess, Instant nextRefresh, String message) { }
    private final RankingSource source;
    private final ExecutorService worker;
    private final Clock clock;
    private final IntSupplier jitter;
    private volatile State state = new State(Phase.DISCONNECTED, Rankings.EMPTY, null, null, null, "Join a world to fetch tiers");
    private boolean connected;
    private boolean enabled = true;
    private boolean closed;
    private long generation;
    private Future<?> request;
    private Instant manualAllowedAt = Instant.MIN;
    private Instant retryNotBefore = Instant.MIN;
    private int failures;

    public RankingRepository(RankingSource source) {
        this(source, Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("afritiers-api").factory()),
                Clock.systemUTC(), () -> ThreadLocalRandom.current().nextInt(31));
    }

    public RankingRepository(RankingSource source, ExecutorService worker, Clock clock, IntSupplier jitter) {
        this.source = source;
        this.worker = worker;
        this.clock = clock;
        this.jitter = jitter;
    }

    public State state() { return state; }

    public synchronized void connect() {
        if (closed) return;
        resetSession();
        connected = true;
        if (enabled) start(); else inactive();
    }

    public synchronized void disconnect() {
        connected = false;
        resetSession();
        inactive();
    }

    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled || closed) return;
        this.enabled = enabled;
        resetSession();
        if (enabled && connected) start(); else inactive();
    }

    public synchronized void tick() {
        if (closed || !enabled || !connected || state.phase() == Phase.LOADING) return;
        if (state.nextRefresh() != null && !clock.instant().isBefore(state.nextRefresh())) start();
    }

    public synchronized String refresh() {
        if (closed || !connected) return "Join a world before refreshing";
        if (!enabled) return "Enable AfriTiers in settings first";
        if (state.phase() == Phase.LOADING) return "A refresh is already running";
        Instant now = clock.instant();
        Instant allowed = manualAllowedAt.isAfter(retryNotBefore) ? manualAllowedAt : retryNotBefore;
        if (now.isBefore(allowed)) return "Refresh available in " + Math.max(1, Duration.between(now, allowed).toSeconds()) + "s";
        start();
        return "Refreshing AfriTiers";
    }

    private void start() {
        if (closed) return;
        Instant now = clock.instant();
        if (now.isBefore(retryNotBefore) || now.isBefore(manualAllowedAt)) {
            Instant next = manualAllowedAt.isAfter(retryNotBefore) ? manualAllowedAt : retryNotBefore;
            state = new State(Phase.ERROR, Rankings.EMPTY, state.lastAttempt(), state.lastSuccess(), next, "Waiting for request cooldown");
            return;
        }
        long token = generation;
        manualAllowedAt = now.plusSeconds(30);
        state = new State(Phase.LOADING, state.rankings(), now, state.lastSuccess(), null, "Fetching rankings");
        request = worker.submit(() -> {
            Rankings rankings;
            try { rankings = source.fetch(); }
            catch (Exception exception) {
                synchronized (this) {
                    if (token != generation || closed) return;
                    failures = Math.min(failures + 1, 6);
                    Instant next = clock.instant().plusSeconds(Math.min(900, 30L << (failures - 1)));
                    if (exception instanceof RankingSource.ApiException api && api.retryAfter() != null && api.retryAfter().isAfter(next)) {
                        next = api.retryAfter();
                    }
                    retryNotBefore = next;
                    String message = exception instanceof RankingSource.ApiException ? exception.getMessage()
                            : exception instanceof IllegalArgumentException ? "API data could not be read" : "API connection failed";
                    state = new State(Phase.ERROR, Rankings.EMPTY, state.lastAttempt(), state.lastSuccess(), next, message);
                }
                return;
            }
            synchronized (this) {
                if (token != generation || closed) return;
                failures = 0;
                retryNotBefore = Instant.MIN;
                Instant fetched = clock.instant();
                state = new State(rankings.stale() ? Phase.STALE : Phase.READY, rankings,
                        state.lastAttempt(), fetched, fetched.plusSeconds(300 + jitter.getAsInt()),
                        rankings.stale() ? "API reports stale data" : "Rankings up to date");
            }
        });
    }

    private void resetSession() {
        generation++;
        if (request != null) request.cancel(true);
        // Cooldowns survive reconnects and toggles to avoid flooding the API.
        state = new State(Phase.DISCONNECTED, Rankings.EMPTY, state.lastAttempt(), state.lastSuccess(), null, "Disconnected");
    }

    private void inactive() {
        state = new State(enabled ? Phase.DISCONNECTED : Phase.DISABLED, Rankings.EMPTY,
                state.lastAttempt(), state.lastSuccess(), null, enabled ? "Join a world to fetch tiers" : "AfriTiers is disabled");
    }

    @Override public synchronized void close() {
        closed = true;
        connected = false;
        resetSession();
        worker.shutdownNow();
        source.close();
        inactive();
    }
}
