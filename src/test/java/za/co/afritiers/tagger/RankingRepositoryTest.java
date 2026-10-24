package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import za.co.afritiers.tagger.network.*;
import za.co.afritiers.tagger.ranking.Rankings;
import static org.junit.jupiter.api.Assertions.*;

class RankingRepositoryTest {
    private static final Rankings DATA = Rankings.parse("""
        {"players":[{"name":"Tester","tiers":[{"mode":"mace","level":"HT5","retired":false}]}],"stale":false}
        """);

    @Test void fetchesOncePerIntervalAndManualRefreshHasCooldown() {
        var clock = new MutableClock();
        var executor = new ManualExecutor();
        var calls = new AtomicInteger();
        try (var repository = new RankingRepository(() -> { calls.incrementAndGet(); return DATA; }, executor, clock, () -> 0)) {
            repository.connect();
            assertEquals("A refresh is already running", repository.refresh());
            executor.runAll();
            assertEquals(RankingRepository.Phase.READY, repository.state().phase());
            assertTrue(repository.refresh().contains("30s"));
            clock.advance(299);
            repository.tick();
            executor.runAll();
            assertEquals(1, calls.get());
            clock.advance(1);
            repository.tick();
            executor.runAll();
            assertEquals(2, calls.get());
        }
    }
    @Test void failedRefreshClearsLabelsAndHonoursRetryAfter() {
        var clock = new MutableClock();
        var executor = new ManualExecutor();
        var calls = new AtomicInteger();
        try (var repository = new RankingRepository(() -> {
            if (calls.incrementAndGet() == 1) return DATA;
            throw new RankingSource.ApiException("HTTP 429", clock.instant().plusSeconds(120));
        }, executor, clock, () -> 0)) {
            repository.connect(); executor.runAll();
            clock.advance(30);
            repository.refresh(); executor.runAll();
            assertEquals(RankingRepository.Phase.ERROR, repository.state().phase());
            assertTrue(repository.state().rankings().players().isEmpty());
            assertTrue(repository.refresh().contains("120s"));
            repository.disconnect(); repository.connect(); executor.runAll();
            assertEquals(2, calls.get());
            clock.advance(120); repository.tick(); executor.runAll();
            assertEquals(3, calls.get());
        }
    }
    @Test void disconnectCancelsQueuedResultsAndDisablingStopsRequests() {
        var clock = new MutableClock();
        var executor = new ManualExecutor();
        try (var repository = new RankingRepository(() -> DATA, executor, clock, () -> 0)) {
            repository.connect(); repository.disconnect(); executor.runAll();
            assertEquals(RankingRepository.Phase.DISCONNECTED, repository.state().phase());
            assertTrue(repository.state().rankings().players().isEmpty());
            repository.setEnabled(false); clock.advance(31); repository.connect(); executor.runAll();
            assertEquals(RankingRepository.Phase.DISABLED, repository.state().phase());
            repository.setEnabled(true); executor.runAll();
            assertEquals(RankingRepository.Phase.READY, repository.state().phase());
        }
    }
    @Test void lateResponseFromOldSessionCannotPopulateReconnectedSession() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var repository = new RankingRepository(() -> {
            entered.countDown();
            boolean waiting = true;
            while (waiting) { try { release.await(); waiting = false; } catch (InterruptedException ignored) { } }
            finished.countDown();
            return DATA;
        }, executor, new MutableClock(), () -> 0)) {
            repository.connect();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            repository.disconnect(); repository.connect(); release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            executor.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertTrue(repository.state().rankings().players().isEmpty());
        } finally { release.countDown(); }
    }
    @Test void upstreamStaleFlagIsExposed() {
        var executor = new ManualExecutor();
        try (var repository = new RankingRepository(() -> Rankings.parse("{\"players\":[],\"stale\":true}"), executor, new MutableClock(), () -> 0)) {
            repository.connect(); executor.runAll();
            assertEquals(RankingRepository.Phase.STALE, repository.state().phase());
        }
    }
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static final class ManualExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
        public void execute(Runnable task) { tasks.add(task); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return List.copyOf(tasks); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
