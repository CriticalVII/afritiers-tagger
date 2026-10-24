package za.co.afritiers.tagger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.time.*;
import za.co.afritiers.tagger.network.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpRankingSourceTest {
    private HttpServer server;
    private HttpRankingSource source;
    @AfterEach void close() { if (source != null) source.close(); if (server != null) server.stop(0); }

    private HttpRankingSource serve(int status, String type, byte[] body, String retry, long delay, Duration timeout) throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/players", exchange -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                exchange.getResponseHeaders().add("Content-Type", type);
                if (retry != null) exchange.getResponseHeaders().add("Retry-After", retry);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        source = new HttpRankingSource(URI.create("http://localhost:" + server.getAddress().getPort() + "/players"), timeout, Clock.systemUTC());
        return source;
    }
    @Test void readsValidJson() throws Exception {
        assertTrue(serve(200, "application/json", "{\"players\":[],\"stale\":false}".getBytes(), null, 0, Duration.ofSeconds(2)).fetch().players().isEmpty());
    }
    @Test void htmlErrorPageCannotReplaceRankings() throws Exception {
        var source = serve(200, "text/html", "<html>Error</html>".getBytes(), null, 0, Duration.ofSeconds(2));
        assertThrows(RankingSource.ApiException.class, source::fetch);
    }
    @Test void rateLimitCarriesRetryDeadline() throws Exception {
        var source = serve(429, "application/json", "{}".getBytes(), "120", 0, Duration.ofSeconds(2));
        var error = assertThrows(RankingSource.ApiException.class, source::fetch);
        assertTrue(error.retryAfter().isAfter(Instant.now().plusSeconds(110)));
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        var source = serve(200, "application/json", new byte[HttpRankingSource.MAX_BYTES + 1], null, 0, Duration.ofSeconds(2));
        assertThrows(RankingSource.ApiException.class, source::fetch);
    }
    @Test void requestsHaveTotalDeadline() throws Exception {
        var source = serve(200, "application/json", "{}".getBytes(), null, 350, Duration.ofMillis(80));
        assertThrows(RankingSource.ApiException.class, source::fetch);
    }
    @Test void serverErrorsAreReported() throws Exception {
        var source = serve(500, "application/json", "{}".getBytes(), null, 0, Duration.ofSeconds(2));
        assertEquals("API returned HTTP 500", assertThrows(RankingSource.ApiException.class, source::fetch).getMessage());
    }
    @Test void chunkedResponseCannotBypassBodyLimit() throws Exception {
        source = serve(200, "application/json", "{}".getBytes(), null, 0, Duration.ofSeconds(2));
        server.createContext("/chunked", exchange -> {
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                byte[] chunk = new byte[65_536];
                for (int i = 0; i < 65; i++) exchange.getResponseBody().write(chunk);
            } finally { exchange.close(); }
        });
        try (var chunked = new HttpRankingSource(URI.create("http://localhost:" + server.getAddress().getPort() + "/chunked"), Duration.ofSeconds(2), Clock.systemUTC())) {
            assertThrows(RankingSource.ApiException.class, chunked::fetch);
        }
    }
    @Test void deadlineIncludesSlowResponseBody() throws Exception {
        source = serve(200, "application/json", "{}".getBytes(), null, 0, Duration.ofSeconds(2));
        server.createContext("/slowbody", exchange -> {
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                Thread.sleep(350);
                exchange.getResponseBody().write('}');
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try (var slow = new HttpRankingSource(URI.create("http://localhost:" + server.getAddress().getPort() + "/slowbody"), Duration.ofMillis(80), Clock.systemUTC())) {
            assertThrows(RankingSource.ApiException.class, slow::fetch);
        }
    }
    @Test void retryAfterSupportsDatesAndRejectsInvalidValues() {
        var now = Instant.parse("2026-10-23T12:00:00Z");
        assertEquals(now.plusSeconds(120), HttpRankingSource.parseRetryAfter("Fri, 23 Oct 2026 12:02:00 GMT", now));
        assertEquals(now, HttpRankingSource.parseRetryAfter("-10", now));
        assertNull(HttpRankingSource.parseRetryAfter("garbage", now));
    }
}
