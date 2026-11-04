package ao1roh.dev.nahnominimap.spigot;

import ao1roh.dev.nahnominimap.Main;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class Language {
    private final Main plugin;
    private JsonObject values;
    private JsonObject comments;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    Language(Main plugin) { this.plugin = plugin; }

    void load(String name, String oldMessage) throws IOException {
        if (!name.matches("[a-zA-Z0-9_-]{1,32}")) throw new IOException("Invalid language name: " + name);
        Path dir = plugin.getDataFolder().toPath().resolve("lang");
        Files.createDirectories(dir);
        for (String lang : new String[]{"ru", "en"}) {
            Path path = dir.resolve(lang + ".json");
            boolean created = !Files.exists(path);
            if (!Files.exists(path)) {
                try (InputStream in = plugin.getResource("lang/" + lang + ".json")) {
                    if (in == null) throw new IOException("Missing bundled language " + lang);
                    Files.copy(in, path);
                }
            }
            JsonObject stored = read(path);
            boolean changed = false;
            if (created && lang.equals("ru") && oldMessage != null && !oldMessage.isEmpty()) {
                stored.addProperty("blocked", oldMessage);
                changed = true;
            }
            try (InputStream input = plugin.getResource("lang/" + lang + ".json")) {
                if (input == null) throw new IOException("Missing bundled language " + lang);
                try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonObject defaults = new JsonParser().parse(reader).getAsJsonObject();
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : defaults.entrySet()) {
                        if (!stored.has(entry.getKey())) { stored.add(entry.getKey(), entry.getValue()); changed = true; }
                    }
                }
            } catch (RuntimeException e) { throw new IOException("Invalid bundled language " + lang, e); }
            List<String> storedKeys = new ArrayList<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : stored.entrySet()) storedKeys.add(entry.getKey());
            for (String key : storedKeys) {
                if (key.startsWith("comment-")) { stored.remove(key); changed = true; }
            }
            if (changed) writeJson(path, stored);
        }
        JsonObject defaults;
        try (Reader in = new InputStreamReader(plugin.getResource("lang/" + (name.equals("ru") ? "ru" : "en") + ".json"), StandardCharsets.UTF_8)) {
            defaults = new JsonParser().parse(in).getAsJsonObject();
        }
        Path selected = dir.resolve(name + ".json");
        JsonObject custom = Files.exists(selected) ? read(selected) : new JsonObject();
        if (!Files.exists(selected)) plugin.getLogger().warning("Language file not found: " + selected + "; using English defaults.");
        for (Map.Entry<String, com.google.gson.JsonElement> item : custom.entrySet()) defaults.add(item.getKey(), item.getValue());
        for (String key : new String[]{"blocked", "reload", "usage", "detected", "evidence-packet", "evidence-register", "no-permission", "reload-error"}) {
            if (!defaults.has(key) || !defaults.get(key).isJsonPrimitive() || !defaults.get(key).getAsJsonPrimitive().isString()) {
                throw new IOException("Invalid language entry: " + key);
            }
            try { parse(defaults.get(key).getAsString()); }
            catch (RuntimeException e) { throw new IOException("Invalid language entry: " + key, e); }
        }
        values = defaults;
        try (Reader in = new InputStreamReader(plugin.getResource("config-lang/" + (name.equals("ru") ? "ru" : "en") + ".json"), StandardCharsets.UTF_8)) {
            comments = new JsonParser().parse(in).getAsJsonObject();
        }
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(in).getAsJsonObject();
        } catch (RuntimeException e) { throw new IOException("Invalid language file: " + path, e); }
    }

    private static void writeJson(Path path, JsonObject object) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".nahnominimap-lang-", ".tmp");
        try {
            Files.write(temp, GSON.toJson(object).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    String raw(String key) { return values.get(key).getAsString(); }
    String legacy(String key) { return LegacyComponentSerializer.legacySection().serialize(parse(raw(key))); }
    String plain(String key) { return org.bukkit.ChatColor.stripColor(legacy(key)); }
    private net.kyori.adventure.text.Component parse(String value) {
        String[] colors = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        for (int i = 0; i < colors.length; i++) value = value.replace("<&" + Integer.toHexString(i) + ">", "<" + colors[i] + ">");
        return MiniMessage.miniMessage().deserialize(value);
    }
    public void send(org.bukkit.command.CommandSender sender, String key) { plugin.adventure().sender(sender).sendMessage(parse(raw(key))); }
    public void sendReload(org.bukkit.command.CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player) plugin.adventure().player((org.bukkit.entity.Player) sender).sendActionBar(parse(raw("reload")));
        else send(sender, "reload");
    }
    void console(String message) { plugin.adventure().console().sendMessage(parse(message)); }

    void updateConfigComments(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        String section = "";
        boolean hasLanguage = false;
        boolean hasRebuild = false;
        org.bukkit.configuration.file.YamlConfiguration parsed = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());
        boolean hasWorldsEnabled = parsed.contains("worlds.enabled");
        boolean hasCustomExample = lines.stream().anyMatch(line -> line.contains("custom_better_world:"));
        Set<String> configuredWorlds = new HashSet<>();
        org.bukkit.configuration.ConfigurationSection worldSection = parsed.getConfigurationSection("worlds");
        if (worldSection != null) configuredWorlds.addAll(worldSection.getKeys(false));
        int reservedIndent = -1;
        int voxelIndent = -1;
        int sharedIndent = -1;
        int dioramaIndent = -1;
        boolean inCustomExample = false;
        boolean skipLegacyPlaceholder = false;
        boolean skipWmapSetting = false;
        boolean hasAlertMaps = parsed.isConfigurationSection("alerts.maps");
        Set<String> generated = new HashSet<>();
        for (String lang : new String[]{"ru", "en"}) {
            try (Reader in = new InputStreamReader(plugin.getResource("config-lang/" + lang + ".json"), StandardCharsets.UTF_8)) {
                JsonObject obj = new JsonParser().parse(in).getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) generated.add("# " + entry.getValue().getAsString());
            }
        }
        Collections.addAll(generated, "# Оформление и переименование сообщений о запрете использавания миникарт (см. wiki)", "# Customize and rename minimap restriction messages (see wiki)", "# Язык плагина: ru или en. Файлы находятся в папке lang.", "# Plugin language: ru or en. Files are in the lang folder.", "# true — блокировать, false — разрешить.", "# true blocks the map, false allows it.", "# Оформление Xaero и восстановление названия тестового блока.", "# Xaero screen and restoration of the test block name.", "# none: автоопределение ItemsAdder; itemsadder: вручную.", "# none: auto-detect ItemsAdder; itemsadder: select manually.", "# Настройки отдельных миров. Если карта не указана, действует global.", "# Per-world settings. Missing entries inherit global.", "# Включить настройки отдельных миров.", "# Enable per-world settings.", "# Пример: добавь свой мир под его настоящим именем.", "# Example: add a custom world using its actual name.");
        generated.add("# Частичное отключение, в будущих версиях доработаем с разработчиком");
        generated.add("# Partial restriction; in future versions we will improve it with the developer");
        generated.add("# Блокировка Conflux Map; полная поддержка с версии 0.1.15");
        generated.add("# Block Conflux Map; full support since version 0.1.15");
        for (String line : lines) if (line.matches("^language:.*")) hasLanguage = true;
        for (String line : lines) if (line.matches("^  rebuild-on-reload:.*")) hasRebuild = true;
        for (String line : lines) {
            if (section.equals("alerts") && line.matches("^    diorama:\\s*(true|false)\\s*")) continue;
            if (section.equals("global") && line.matches("^  (wmap|world-map):\\s*")) { skipWmapSetting = true; continue; }
            if (skipWmapSetting && line.matches("^    blocked:.*")) { skipWmapSetting = false; continue; }
            skipWmapSetting = false;
            if (section.equals("alerts") && line.matches("^  (wmap|conflux-map):.*")) continue;
            if (section.equals("global") && line.matches("^  # (conflux-map|world-map|wmap|swiftmap|atlas-minimap|simply-world-map|immersive-minimaps|world-notes|off-client):\\s*")) { skipLegacyPlaceholder = true; continue; }
            if (skipLegacyPlaceholder && line.matches("^ +# blocked:.*")) { skipLegacyPlaceholder = false; continue; }
            skipLegacyPlaceholder = false;
            if (line.matches("^ {2,4}(# )?world-map:\\s*")) line = line.replace("world-map:", "wmap:");
            if (line.matches("^  # custom_better_world:\\s*")) {
                out.add("  # " + comments.get("comment-custom-world").getAsString());
                out.add(line);
                inCustomExample = true;
                continue;
            }
            if (inCustomExample && line.matches("^ {4,}#.*")) { out.add(line); continue; }
            inCustomExample = false;
            if ((section.equals("global") || section.equals("worlds")) && line.matches("^ {2,4}xaero-minimap-and-diorama:\\s*")) {
                sharedIndent = line.indexOf('x');
                line = line.replace("xaero-minimap-and-diorama:", "xaero-minimap:");
            } else if (sharedIndent >= 0 && line.matches("^ +blocked:\\s*(true|false)\\s*")) {
                int indent = line.length() - line.stripLeading().length();
                if (indent <= sharedIndent) throw new IOException("Invalid shared minimap setting");
                out.add(line);
                out.add(" ".repeat(sharedIndent) + "diorama:");
                out.add(line);
                sharedIndent = -1;
                continue;
            } else if (sharedIndent >= 0 && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
                throw new IOException("Cannot migrate shared minimap setting without blocked value");
            }
            if ((section.equals("global") || section.equals("worlds")) && line.matches("^ {2,4}# diorama:\\s*")) {
                dioramaIndent = line.indexOf('#');
                line = line.substring(0, dioramaIndent) + line.substring(dioramaIndent + 2);
            } else if (dioramaIndent >= 0 && line.matches("^ +# blocked:\\s*(true|false)\\s*")) {
                int marker = line.indexOf('#');
                line = line.substring(0, marker) + line.substring(marker + 2);
                dioramaIndent = -1;
            } else if (!line.trim().isEmpty()) dioramaIndent = -1;
            if ((section.equals("global") || section.equals("worlds")) && line.matches("^ {2,4}# voxelmap:\\s*")) {
                voxelIndent = line.indexOf('#');
                line = line.substring(0, voxelIndent) + line.substring(voxelIndent + 2);
            } else if (voxelIndent >= 0 && line.matches("^ +# blocked:\\s*(true|false)\\s*")) {
                int marker = line.indexOf('#');
                line = line.substring(0, marker) + line.substring(marker + 2);
                voxelIndent = -1;
            } else if (!line.trim().isEmpty()) voxelIndent = -1;
            if (section.equals("alerts") && line.matches("^    surveyor:.*")) {
                if (parsed.contains("alerts.maps.surveyor-framework")) continue;
                line = line.replaceFirst("surveyor:", "surveyor-framework:");
            }
            String trimmed = line.trim();
            if (generated.contains(trimmed)) continue;
            if (trimmed.startsWith("# ") && (trimmed.contains("НЕ МЕНЯТЬ") || trimmed.contains("true — блокировать") || trimmed.contains("Настройка зарезервирована") || trimmed.equals("# Ресурспак") || trimmed.contains("Оформление Xaero") || trimmed.contains("none, itemsadder, nexo, craftengine") || trimmed.contains("При none текст") || trimmed.equals("# Уведомления администрации") || trimmed.contains("Настройки миров действуют") || trimmed.contains("World settings apply when the map is allowed globally") || trimmed.contains("Если настройка мира отсутствует"))) continue;
            if (!line.startsWith(" ") && trimmed.endsWith(":")) section = trimmed.substring(0, trimmed.length() - 1);
            if (section.equals("resource-pack") && line.matches("^  message:.*")) continue;
            int indent = line.length() - line.stripLeading().length();
            if (reservedIndent >= 0 && !trimmed.isEmpty() && indent <= reservedIndent) reservedIndent = -1;
            if ((section.equals("global") || section.equals("worlds")) && line.matches("^ {2,4}(swiftmap|atlas-minimap|simply-world-map|immersive-minimaps|world-notes|off-client):\\s*")) reservedIndent = indent;
            if (reservedIndent >= 0 && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                out.add(line.substring(0, indent) + "# " + line.substring(indent));
                continue;
            }
            String key = null;
            if (line.startsWith("config-version:")) key = "comment-config-version";
            else if (line.startsWith("language:")) key = "comment-language";
            else if (line.equals("global:")) key = "comment-global";
            else if (line.equals("resource-pack:")) key = "comment-resource-pack";
            else if (line.equals("alerts:")) key = "comment-alerts";
            else if (section.equals("global") && line.equals("  conflux-map:")) key = "comment-conflux";
            else if (section.equals("alerts") && line.equals("  maps:")) key = "comment-alert-maps";
            else if (line.equals("worlds:")) key = "comment-worlds";
            else if (section.equals("resource-pack") && line.matches("^  enabled:.*")) key = "comment-resource-enabled";
            else if (section.equals("resource-pack") && line.matches("^  provider:.*")) key = "comment-provider";
            else if (section.equals("resource-pack") && line.matches("^  rebuild-on-reload:.*")) key = "comment-rebuild";
            else if (section.equals("worlds") && line.matches("^  enabled:.*")) key = "comment-worlds-enabled";
            if (key != null) out.add((line.startsWith("  ") ? "  " : "") + "# " + comments.get(key).getAsString().replace('\n', ' '));
            out.add(line);
            if (section.equals("alerts") && line.equals("  maps:") && !parsed.contains("alerts.maps.surveyor-framework") && !parsed.contains("alerts.maps.surveyor")) out.add("    surveyor-framework: true");
            if (section.equals("alerts") && line.matches("^  enabled:.*") && !hasAlertMaps) {
                out.add("  # " + comments.get("comment-alert-maps").getAsString());
                out.add("  maps:");
                for (String map : new String[]{"xaero-minimap", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map", "wmap", "surveyor-framework"}) {
                    out.add("    " + map + ": " + parsed.getBoolean("alerts." + map, true));
                }
            }
            if (line.equals("global:") && !parsed.contains("global.conflux-map")) {
                out.add("  # " + comments.get("comment-conflux").getAsString());
                out.add("  conflux-map:"); out.add("    blocked: true");
            }
            if (section.equals("worlds") && line.matches("^  [A-Za-z0-9_-]+:\\s*")) {
                String worldName = line.trim().substring(0, line.trim().length() - 1);
                if (parsed.isConfigurationSection("worlds." + worldName) && !parsed.contains("worlds." + worldName + ".conflux-map.blocked")) {
                    out.add("    conflux-map:");
                    out.add("      blocked: " + parsed.getBoolean("global.conflux-map.blocked", true));
                }
            }
            if (line.equals("worlds:")) {
                if (!hasWorldsEnabled) {
                    out.add("  # " + comments.get("comment-worlds-enabled").getAsString());
                    out.add("  enabled: false");
                }
                if (!hasCustomExample) {
                    out.add("  # " + comments.get("comment-custom-world").getAsString());
                    out.add("  # custom_better_world:");
                    for (String map : new String[]{"xaero-minimap", "diorama", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map"}) {
                        out.add("    # " + map + ":");
                        out.add("      # blocked: true");
                    }
                }
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    String name = world.getName();
                    if (configuredWorlds.contains(name)) continue;
                    String safe = name.matches("[A-Za-z0-9_-]+") && !name.matches("(?i:true|false|null|yes|no|on|off|[0-9]+)") ? name : "'" + name.replace("'", "''") + "'";
                    out.add("  " + safe + ":");
                    for (String map : new String[]{"xaero-minimap", "diorama", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map"}) {
                        out.add("    " + map + ":");
                        out.add("      blocked: " + parsed.getBoolean("global." + map + ".blocked", true));
                    }
                }
            }
            if (line.startsWith("config-version:") && !hasLanguage) {
                out.add(""); out.add("# " + comments.get("comment-language").getAsString()); out.add("language: ru");
            }
            if (section.equals("resource-pack") && line.matches("^  provider:.*") && !hasRebuild) {
                out.add("  # " + comments.get("comment-rebuild").getAsString()); out.add("  rebuild-on-reload: false");
            }
        }
        if (sharedIndent >= 0) throw new IOException("Cannot migrate incomplete shared minimap setting");
        int globalStart = out.indexOf("global:");
        if (globalStart >= 0) {
            int globalEnd = globalStart + 1;
            while (globalEnd < out.size() && (out.get(globalEnd).startsWith(" ") || out.get(globalEnd).isBlank())) globalEnd++;
            int confluxStart = -1;
            for (int i = globalStart + 1; i < globalEnd; i++) {
                if (out.get(i).equals("  conflux-map:")) { confluxStart = i; break; }
            }
            if (confluxStart >= 0) {
                int blockStart = confluxStart;
                String marker = "  # " + comments.get("comment-conflux").getAsString();
                if (blockStart > globalStart + 1 && out.get(blockStart - 1).equals(marker)) blockStart--;
                int blockEnd = confluxStart + 1;
                while (blockEnd < globalEnd && (out.get(blockEnd).startsWith("    ") || out.get(blockEnd).isBlank())) blockEnd++;
                while (blockEnd > confluxStart + 1 && out.get(blockEnd - 1).isBlank()) blockEnd--;
                List<String> block = new ArrayList<>(out.subList(blockStart, blockEnd));
                out.subList(blockStart, blockEnd).clear();
                globalEnd -= blockEnd - blockStart;
                while (globalEnd > globalStart + 1 && out.get(globalEnd - 1).isBlank()) globalEnd--;
                out.addAll(globalEnd, block);
            }
        }
        String updated = String.join("\n", out) + "\n";
        Path temp = Files.createTempFile(path.getParent(), ".nahnominimap-config-", ".tmp");
        try {
            Files.write(temp, updated.getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
