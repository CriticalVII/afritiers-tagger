package za.co.afritiers.tagger.network;

import com.google.gson.JsonParser;
import za.co.afritiers.tagger.ranking.Rankings;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Official public current-name lookup; never accepts a server-supplied UUID. */
public final class HttpProfileLookup implements ProfileLookup {
    public static final URI ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;
    private final Duration timeout;
    private final Clock clock;
    public HttpProfileLookup() { this(ENDPOINT, Duration.ofSeconds(5), Clock.systemUTC()); }
    public HttpProfileLookup(URI endpoint, Duration timeout, Clock clock) { this.endpoint = endpoint; this.timeout = timeout; this.clock = clock; }

    @Override public Optional<Profile> lookup(String username) throws Exception {
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid Minecraft username");
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve(username)).timeout(timeout).header("Accept", "application/json").GET().build();
        var pending = client.sendAsync(request, info -> new HttpRankingSource.LimitedSubscriber(
                info.headers().firstValueAsLong("Content-Length").orElse(0) > 16_384 ? 0 : 16_384));
        try {
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 404 || response.statusCode() == 204) return Optional.empty();
            if (response.statusCode() != 200) {
                Instant retry = response.statusCode() == 429 || response.statusCode() == 503
                        ? HttpRankingSource.parseRetryAfter(response.headers().firstValue("Retry-After").orElse(""), clock.instant()) : null;
                throw new RankingSource.ApiException("Paid-name lookup returned HTTP " + response.statusCode(), retry);
            }
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).contains("application/json")) {
                throw new IllegalArgumentException("Paid-name lookup did not return JSON");
            }
            var json = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("id") || !json.get("id").isJsonPrimitive() || !json.getAsJsonPrimitive("id").isString()
                    || !json.has("name") || !json.get("name").isJsonPrimitive() || !json.getAsJsonPrimitive("name").isString()) {
                throw new IllegalArgumentException("Invalid paid-profile response");
            }
            String id = Rankings.canonicalProfileId(json.get("id").getAsString());
            String name = json.get("name").getAsString();
            if (id == null || !name.equalsIgnoreCase(username)) throw new IllegalArgumentException("Paid profile does not match the requested username");
            return Optional.of(new Profile(id, name));
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new RankingSource.ApiException("Paid-name lookup timed out", null);
        } catch (InterruptedException exception) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw exception;
        } catch (ExecutionException exception) {
            throw new RankingSource.ApiException("Paid-name lookup failed or response exceeded 16 KiB", null);
        }
    }
    @Override public void close() { client.shutdownNow(); }
}
