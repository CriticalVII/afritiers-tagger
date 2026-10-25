package za.co.afritiers.tagger.waitlist;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import za.co.afritiers.tagger.ranking.ModeIcon;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** Validated queue data; remote labels never become formatted chat or executable links. */
public record Waitlists(Instant generatedAt, Map<String, Entry> entries) {
    public static final Waitlists EMPTY = new Waitlists(null, Map.of());
    public Waitlists { entries = Map.copyOf(entries); }
    public record Entry(String mode, boolean open, String eventKey, URI joinUrl) { }

    public static String modeKey(String mode) {
        if (mode == null || !mode.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid waitlist mode");
        return ModeIcon.find(mode).map(ModeIcon::primaryMode).orElse(mode);
    }

    public static Waitlists parse(String json) {
        try {
            var reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT || !value.isJsonObject()) throw new IllegalArgumentException();
            var root = value.getAsJsonObject();
            if (!root.has("ok") || !root.get("ok").isJsonPrimitive() || !root.getAsJsonPrimitive("ok").isBoolean()
                    || !root.get("ok").getAsBoolean() || !root.has("version") || !root.get("version").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("version").isNumber() || !root.get("version").getAsString().equals("1")) throw new IllegalArgumentException();
            Instant generatedAt = Instant.parse(string(root.get("generatedAt"), 64));
            var queues = root.getAsJsonArray("queues");
            if (queues == null || queues.size() > 64) throw new IllegalArgumentException();
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (JsonElement element : queues) {
                var queue = element.getAsJsonObject();
                String mode = modeKey(string(queue.get("mode"), 32));
                var open = queue.getAsJsonPrimitive("open");
                if (open == null || !open.isBoolean()) throw new IllegalArgumentException();
                String event = "";
                if (queue.has("eventId") && !queue.get("eventId").isJsonNull()) {
                    var id = queue.getAsJsonPrimitive("eventId");
                    if (!id.isString() && !id.isNumber()) throw new IllegalArgumentException();
                    String token = id.getAsString();
                    if (!token.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
                    event = "id:" + token;
                }
                if (event.isEmpty() && queue.has("openedAt") && !queue.get("openedAt").isJsonNull())
                    event = "time:" + Instant.parse(string(queue.get("openedAt"), 64));
                URI link = safeJoinUrl(queue.get("joinUrl"));
                if (entries.putIfAbsent(mode, new Entry(mode, open.getAsBoolean(), event, link)) != null) throw new IllegalArgumentException();
            }
            return new Waitlists(generatedAt, entries);
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalArgumentException("Queue API data could not be read", exception);
        }
    }

    private static String string(JsonElement value, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        String text = value.getAsString();
        if (text.isEmpty() || text.length() > max) throw new IllegalArgumentException();
        return text;
    }

    private static URI safeJoinUrl(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        try {
            URI uri = URI.create(string(value, 256));
            return "https".equals(uri.getScheme()) && "discord.com".equals(uri.getHost()) && uri.getPort() == -1
                    && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPath().matches("/channels/[0-9]{1,24}/[0-9]{1,24}") ? uri : null;
        } catch (IllegalArgumentException exception) { return null; }
    }
}
