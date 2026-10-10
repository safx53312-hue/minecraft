package dev.enchantpoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.enchantpoints.core.PointsConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Loads, validates and atomically saves config/enchantpoints.json. The server is the only authority. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile PointsConfig config = PointsConfig.defaults();

    private ConfigManager() {}

    public static PointsConfig get() { return config; }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("enchantpoints.json"); }

    /** @return errors. If non-empty the previous (valid) config stays active and nothing is overwritten. */
    public static List<String> load() {
        Path p = path();
        if (!Files.exists(p)) {
            config = PointsConfig.defaults();
            try { save(); } catch (IOException e) { return List.of("Could not write default config: " + e.getMessage()); }
            return List.of();
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            PointsConfig loaded = GSON.fromJson(r, PointsConfig.class);
            if (loaded == null) return List.of("enchantpoints.json is empty");
            List<String> errors = loaded.validate();
            if (!errors.isEmpty()) return errors;
            config = loaded;
            return List.of();
        } catch (Exception e) {
            List<String> l = new ArrayList<>();
            l.add("enchantpoints.json could not be parsed: " + e.getMessage());
            return l;
        }
    }

    public static synchronized void save() throws IOException {
        Path p = path();
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling("enchantpoints.json.tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(config, w); }
        try { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING); }
    }
}
