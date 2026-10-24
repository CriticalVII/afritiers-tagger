package za.co.afritiers.tagger.network;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import za.co.afritiers.tagger.ranking.Rankings;

public final class HttpRankingSource implements RankingSource {
    public static final URI ENDPOINT = URI.create("https://afritiers.co.za/api/players");
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final Clock clock;

    public HttpRankingSource() { this(ENDPOINT, Duration.ofSeconds(10), Clock.systemUTC()); }

    public HttpRankingSource(URI endpoint, Duration timeout, Clock clock) {
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.clock = clock;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Rankings fetch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "AfriTiers-Tagger/1.0.0 (Minecraft 26.2)").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> {
            long length = info.headers().firstValueAsLong("Content-Length").orElse(0);
            return new LimitedSubscriber(length > MAX_BYTES ? 0 : MAX_BYTES);
        });
        try {
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                Instant retry = (response.statusCode() == 429 || response.statusCode() == 503)
                        ? parseRetryAfter(response.headers().firstValue("Retry-After").orElse(""), clock.instant()) : null;
                throw new ApiException("API returned HTTP " + response.statusCode(), retry);
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (!type.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
                throw new ApiException("API did not return JSON", null);
            }
            return Rankings.parse(new String(response.body(), StandardCharsets.UTF_8));
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new ApiException("API request timed out", null);
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            throw new ApiException("API connection failed or response exceeded 4 MiB", null);
        }
    }

    public static Instant parseRetryAfter(String value, Instant now) {
        try { return now.plusSeconds(Math.max(0, Long.parseLong(value.trim()))); }
        catch (RuntimeException ignored) {
            try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
            catch (RuntimeException invalidDate) { return null; }
        }
    }

    @Override public void close() { client.shutdownNow(); }

    /** Cancel the subscription before an unbounded body can be accumulated. */
    static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long limit;
        private long received;
        private Flow.Subscription subscription;
        private boolean done;

        LimitedSubscriber(long limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (done) return;
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > limit) {
                done = true;
                subscription.cancel();
                delegate.onError(new IllegalArgumentException("Response too large"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) {
            if (!done) { done = true; delegate.onError(error); }
        }
        @Override public void onComplete() {
            if (!done) { done = true; delegate.onComplete(); }
        }
    }
}
