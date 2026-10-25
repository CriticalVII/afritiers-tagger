package za.co.afritiers.tagger.network;

import za.co.afritiers.tagger.waitlist.Waitlists;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;

/** Fixed HTTPS endpoint with bounded bodies, a total deadline, no redirects and Retry-After support. */
public final class HttpQueueSource implements QueueSource {
    public static final URI ENDPOINT = URI.create("https://afritiers.co.za/api/queues");
    public static final int MAX_BYTES = 256 * 1024;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final Clock clock;

    public HttpQueueSource() { this(ENDPOINT, Duration.ofSeconds(10), Clock.systemUTC()); }
    public HttpQueueSource(URI endpoint, Duration timeout, Clock clock) {
        this.endpoint = endpoint; this.timeout = timeout; this.clock = clock;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public Waitlists fetch() throws Exception {
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Accept", "application/json")
                .header("User-Agent", "AfriTiers-Tagger (Minecraft 26.2)").GET().build();
        var pending = client.sendAsync(request, info -> new HttpRankingSource.LimitedSubscriber(
                info.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES ? 0 : MAX_BYTES));
        try {
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                Instant retry = response.statusCode() == 429 || response.statusCode() == 503
                        ? HttpRankingSource.parseRetryAfter(response.headers().firstValue("Retry-After").orElse(""), clock.instant()) : null;
                throw new RankingSource.ApiException("Queues returned HTTP " + response.statusCode(), retry);
            }
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).contains("application/json"))
                throw new RankingSource.ApiException("Queue API did not return JSON", null);
            return Waitlists.parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (TimeoutException exception) {
            pending.cancel(true); throw new RankingSource.ApiException("Queue request timed out", null);
        } catch (InterruptedException exception) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw exception;
        } catch (ExecutionException exception) {
            throw new RankingSource.ApiException("Queue connection failed or response exceeded 256 KiB", null);
        }
    }
    @Override public void close() { client.shutdownNow(); }
}
