package ao1roh.dev.nahnominimap.spigot.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PackFiles {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private PackFiles() {}

    public static boolean hasCraftEngineLangConflicts(Path resources, Path ownPack) throws IOException {
        if (!Files.isDirectory(resources)) return false;
        try (DirectoryStream<Path> packs = Files.newDirectoryStream(resources)) {
            for (Path pack : packs) {
                if (!Files.isDirectory(pack) || pack.equals(ownPack) || pack.getFileName().toString().startsWith(".")) continue;
                for (String locale : new String[]{"ru_ru", "en_us"}) {
                    for (String namespace : new String[]{"minecraft", "journeymap", "xaeroworldmap", "voxelmap"}) {
                        if (Files.isRegularFile(pack.resolve("resourcepack/assets").resolve(namespace).resolve("lang").resolve(locale + ".json"))) return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean sync(Path assets, String formatted, String plain) throws IOException {
        Map<Path, Map<String, String>> updates = new LinkedHashMap<>();
        for (String locale : new String[]{"ru_ru", "en_us"}) {
            entry(updates, assets.resolve("journeymap/lang/" + locale + ".json"), "jm.common.disabled_by_server", formatted);
            entry(updates, assets.resolve("xaeroworldmap/lang/" + locale + ".json"), "gui.xaero_no_world_map_item_message", formatted);
            entry(updates, assets.resolve("voxelmap/lang/" + locale + ".json"), "worldmap.disabled", plain);
            entry(updates, assets.resolve("minecraft/lang/" + locale + ".json"), "block.minecraft.test_instance_block", "\u00a70");
        }
        Map<Path, byte[]> changes = new LinkedHashMap<>();
        for (Map.Entry<Path, Map<String, String>> update : updates.entrySet()) {
            Path path = update.getKey();
            JsonObject object = new JsonObject();
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement parsed = new JsonParser().parse(reader);
                    if (!parsed.isJsonObject()) throw new IOException("Not a JSON object: " + path);
                    object = parsed.getAsJsonObject();
                } catch (RuntimeException e) { throw new IOException("Invalid JSON: " + path, e); }
            }
            boolean changed = false;
            for (Map.Entry<String, String> item : update.getValue().entrySet()) {
                JsonElement previous = object.get(item.getKey());
                if (previous == null || !previous.isJsonPrimitive() || !previous.getAsJsonPrimitive().isString() || !previous.getAsString().equals(item.getValue())) {
                    object.addProperty(item.getKey(), item.getValue());
                    changed = true;
                }
            }
            if (changed) changes.put(path, JSON.toJson(object).getBytes(StandardCharsets.UTF_8));
        }
        for (Map.Entry<Path, byte[]> change : changes.entrySet()) {
            Path path = change.getKey();
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), ".nahnominimap-", ".tmp");
            try {
                Files.write(temp, change.getValue());
                try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        }
        return !changes.isEmpty();
    }

    private static void entry(Map<Path, Map<String, String>> updates, Path path, String key, String value) {
        updates.computeIfAbsent(path, unused -> new LinkedHashMap<>()).put(key, value);
    }
}
