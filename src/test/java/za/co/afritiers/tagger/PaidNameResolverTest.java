package za.co.afritiers.tagger;

import org.junit.jupiter.api.Test;
import za.co.afritiers.tagger.network.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class PaidNameResolverTest {
    private static final String ID = "a48655695b2d4e068289883d615f94b6";
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Resolver did not complete");
    }
    @Test void deduplicatesNamesAndExpiresTheFiveMinuteCache() throws Exception {
        var clock = new TestClock(); var count = new AtomicInteger();
        try (var resolver = new PaidNameResolver(name -> { count.incrementAndGet(); return Optional.of(new ProfileLookup.Profile(ID, name)); }, clock)) {
            resolver.setActive(true);
            for (int i = 0; i < 20; i++) resolver.request(i % 2 == 0 ? "SadVerity67" : "SADVERITY67");
            await(() -> resolver.profileId("sadverity67").isPresent());
            resolver.request("SadVerity67"); assertEquals(1, count.get());
            clock.now = clock.now.plusSeconds(301);
            assertTrue(resolver.profileId("SadVerity67").isEmpty());
            resolver.request("SadVerity67"); await(() -> count.get() == 2);
        }
    }
    @Test void negativeResultsAreCachedAndInactiveResolversSendNoRequests() throws Exception {
        var count = new AtomicInteger();
        try (var resolver = new PaidNameResolver(name -> { count.incrementAndGet(); return Optional.empty(); })) {
            resolver.request("Cracked"); Thread.sleep(100); assertEquals(0, count.get());
            resolver.setActive(true); resolver.request("Cracked");
            await(() -> resolver.notFound("Cracked"));
            resolver.request("Cracked"); assertEquals(1, count.get());
        }
    }
    @Test void disconnectDropsAnInflightResult() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var resolver = new PaidNameResolver(name -> {
            started.countDown(); release.await(); return Optional.of(new ProfileLookup.Profile(ID, name));
        })) {
            resolver.setActive(true); resolver.request("Renamed");
            assertTrue(started.await(3, TimeUnit.SECONDS));
            resolver.setActive(false); release.countDown(); Thread.sleep(100);
            assertTrue(resolver.profileId("Renamed").isEmpty());
        } finally { release.countDown(); }
    }
    @Test void retryAfterSurvivesReconnects() throws Exception {
        var clock = new TestClock(); var count = new AtomicInteger();
        try (var resolver = new PaidNameResolver(name -> {
            if (count.incrementAndGet() == 1) throw new RankingSource.ApiException("Rate limited", clock.now.plusSeconds(120));
            return Optional.of(new ProfileLookup.Profile(ID, name));
        }, clock)) {
            resolver.setActive(true); resolver.request("First");
            await(() -> resolver.status().contains("unavailable"));
            resolver.setActive(false); resolver.setActive(true); resolver.request("Second");
            Thread.sleep(900); assertEquals(1, count.get());
            clock.now = clock.now.plusSeconds(121);
            await(() -> resolver.profileId("Second").isPresent());
        }
    }
    private static final class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-24T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
