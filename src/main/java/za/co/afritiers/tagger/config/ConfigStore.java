package za.co.afritiers.tagger.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private String warning = "";

    public ConfigStore(Path file) { this.file = file; }
    public String warning() { return warning; }

    public TaggerConfig load() {
        if (!Files.exists(file)) return new TaggerConfig();
        try {
            if (Files.size(file) > 16_384) throw new IllegalArgumentException("Config is too large");
            TaggerConfig config = GSON.fromJson(Files.readString(file), TaggerConfig.class);
            if (config == null) throw new IllegalArgumentException("Empty config");
            config.validate();
            return config;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            warning = "Invalid config; defaults loaded. ";
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".invalid-" + Instant.now().toEpochMilli()));
                warning += "Original backed up.";
            } catch (IOException backupFailure) {
                warning += "Backup failed; save will not overwrite the original.";
            }
            return new TaggerConfig();
        }
    }

    public void save(TaggerConfig config) throws IOException {
        config.validate();
        if (warning.contains("Backup failed")) throw new IOException(warning);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "afritiers-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(config) + System.lineSeparator(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            warning = "";
        } finally { Files.deleteIfExists(temporary); }
    }
}
