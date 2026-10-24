package za.co.afritiers.tagger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import za.co.afritiers.tagger.network.*;
import java.net.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HttpProfileLookupTest {
    private HttpServer server;
    private HttpProfileLookup source;
    @AfterEach void close() { if (source != null) source.close(); if (server != null) server.stop(0); }
    private HttpProfileLookup serve(int status, String type, String body, String retry) throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/profiles/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", type);
            if (retry != null) exchange.getResponseHeaders().add("Retry-After", retry);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        source = new HttpProfileLookup(URI.create("http://localhost:" + server.getAddress().getPort() + "/profiles/"), Duration.ofSeconds(2), Clock.systemUTC());
        return source;
    }
    @Test void validatesTheCurrentNameAndCanonicalProfileId() throws Exception {
        var profile = serve(200, "application/json", "{\"id\":\"a4865569-5b2d-4e06-8289-883d615f94b6\",\"name\":\"SadVerity67\"}", null)
                .lookup("sadverity67").orElseThrow();
        assertEquals("a48655695b2d4e068289883d615f94b6", profile.id());
    }
    @Test void rejectsAResponseForAnotherUsername() throws Exception {
        serve(200, "application/json", "{\"id\":\"a48655695b2d4e068289883d615f94b6\",\"name\":\"OtherName\"}", null);
        assertThrows(IllegalArgumentException.class, () -> source.lookup("SadVerity67"));
    }
    @Test void missingProfileDoesNotInventAnIdentity() throws Exception {
        assertTrue(serve(404, "application/json", "{}", null).lookup("NoProfile").isEmpty());
    }
    @Test void rateLimitsCarryTheRetryDeadline() throws Exception {
        serve(429, "application/json", "{}", "120");
        var failure = assertThrows(RankingSource.ApiException.class, () -> source.lookup("SadVerity67"));
        assertTrue(failure.retryAfter().isAfter(Instant.now().plusSeconds(110)));
    }
    @Test void rejectsHtmlAndMalformedProfiles() throws Exception {
        serve(200, "text/html", "<html>failure</html>", null);
        assertThrows(IllegalArgumentException.class, () -> source.lookup("SadVerity67"));
        assertThrows(IllegalArgumentException.class, () -> source.lookup("../unsafe"));
    }
    @Test void limitsOfficialProfileResponseSize() throws Exception {
        serve(200, "application/json", " ".repeat(20_000), null);
        assertThrows(RankingSource.ApiException.class, () -> source.lookup("SadVerity67"));
    }
}
