package za.co.afritiers.tagger.network;

import za.co.afritiers.tagger.ranking.Tier;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Small session cache and throttled worker; render code only reads completed results. */
public final class PaidNameResolver implements AutoCloseable {
    private record Entry(ProfileLookup.Profile profile, boolean notFound, Instant expires) { }
    private final ProfileLookup source;
    private final Clock clock;
    private final ScheduledExecutorService worker;
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>();
    private final LinkedHashSet<String> queued = new LinkedHashSet<>();
    private String inFlight;
    private boolean active, closed;
    private long generation;
    private Instant retryAt = Instant.MIN;
    private String status = "Waiting for a world";

    public PaidNameResolver(ProfileLookup source) { this(source, Clock.systemUTC()); }
    public PaidNameResolver(ProfileLookup source, Clock clock) {
        this.source = source; this.clock = clock;
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("afritiers-paid-names").factory());
        worker.scheduleWithFixedDelay(this::processNext, 0, 1000, TimeUnit.MILLISECONDS);
    }
    public synchronized void setActive(boolean enabled) {
        if (active == enabled || closed) return;
        active = enabled; generation++;
        if (!active) { queued.clear(); status = "Paused"; }
        else status = "Ready";
    }
    /** A new server must not inherit cached name results or pending work from the previous one. */
    public synchronized void resetSession() {
        if (closed) return;
        generation++;
        queued.clear();
        cache.clear();
        status = active ? "Ready" : "Paused";
        // Preserve retryAt so changing servers cannot bypass the lookup service's cooldown.
    }
    public synchronized void request(String username) {
        if (!active || closed || username == null || !username.matches("[A-Za-z0-9_]{1,16}")) return;
        String key = Tier.normalizeUsername(username);
        if (validEntry(key) != null || key.equals(inFlight) || queued.size() >= 80) return;
        queued.add(key);
    }
    public synchronized Optional<String> profileId(String username) {
        Entry entry = validEntry(Tier.normalizeUsername(username));
        return entry == null || entry.profile == null ? Optional.empty() : Optional.of(entry.profile.id());
    }
    public synchronized boolean notFound(String username) {
        Entry entry = validEntry(Tier.normalizeUsername(username));
        return entry != null && entry.notFound;
    }
    public synchronized String status() { return status + (queued.isEmpty() ? "" : " | queued: " + queued.size()); }
    private Entry validEntry(String key) {
        Entry entry = cache.get(key);
        if (entry != null && !clock.instant().isBefore(entry.expires)) { cache.remove(key); return null; }
        return entry;
    }
    private void processNext() {
        String name; long token;
        synchronized (this) {
            if (!active || closed || queued.isEmpty() || clock.instant().isBefore(retryAt)) return;
            name = queued.iterator().next(); queued.remove(name); inFlight = name; token = generation;
            status = "Checking paid-account names";
        }
        ProfileLookup.Profile profile = null;
        boolean notFound = false;
        Instant expires = clock.instant().plusSeconds(60);
        Exception failure = null;
        try {
            profile = source.lookup(name).orElse(null);
            notFound = profile == null;
            expires = clock.instant().plusSeconds(300);
        } catch (Exception exception) { failure = exception; }
        synchronized (this) {
            inFlight = null;
            if (closed || !active || token != generation) return;
            if (failure != null) {
                Instant requested = failure instanceof RankingSource.ApiException api ? api.retryAfter() : null;
                retryAt = requested != null && requested.isAfter(clock.instant().plusSeconds(30)) ? requested : clock.instant().plusSeconds(30);
                status = "Paid-name lookup unavailable; retrying later";
            } else status = "Paid-account names checked";
            if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
            cache.put(name, new Entry(profile, notFound, expires));
        }
    }
    @Override public synchronized void close() {
        closed = true; active = false; generation++; queued.clear();
        worker.shutdownNow(); source.close(); cache.clear();
    }
}
