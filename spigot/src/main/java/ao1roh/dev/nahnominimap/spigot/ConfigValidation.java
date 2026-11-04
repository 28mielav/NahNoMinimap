package ao1roh.dev.nahnominimap.spigot;

import java.util.Map;
import java.util.Arrays;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

final class ConfigValidation {
    private static final String[] MAPS = {"xaero-minimap", "diorama", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map"};
    private static final String[] ALERTS = {"xaero-minimap", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map", "wmap", "surveyor-framework"};
    private ConfigValidation() {}

    static void validate(ConfigurationSection root) {
        value(root, "language", String.class, "language");
        ConfigurationSection global = section(root, "global", "global");
        validateMaps(global, "global");
        ConfigurationSection pack = section(root, "resource-pack", "resource-pack");
        value(pack, "enabled", Boolean.class, "resource-pack.enabled");
        value(pack, "provider", String.class, "resource-pack.provider");
        if (pack != null && pack.getValues(false).containsKey("provider")) {
            String provider = ((String) pack.getValues(false).get("provider")).trim().toLowerCase(Locale.ROOT);
            if (!Arrays.asList("none", "auto", "itemsadder", "nexo", "craftengine", "oraxen").contains(provider)) throw new IllegalArgumentException("Unknown resource-pack.provider: " + provider);
        }
        value(pack, "rebuild-on-reload", Boolean.class, "resource-pack.rebuild-on-reload");
        ConfigurationSection alerts = section(root, "alerts", "alerts");
        for (String key : new String[]{"enabled", "chat", "console", "notify-on-rejoin"}) value(alerts, key, Boolean.class, "alerts." + key);
        ConfigurationSection alertMaps = section(alerts, "maps", "alerts.maps");
        for (String key : ALERTS) value(alertMaps, key, Boolean.class, "alerts.maps." + key);
        ConfigurationSection worlds = section(root, "worlds", "worlds");
        value(worlds, "enabled", Boolean.class, "worlds.enabled");
        if (worlds != null) for (Map.Entry<String, Object> world : worlds.getValues(false).entrySet()) {
            if (world.getKey().equals("enabled")) continue;
            if (!(world.getValue() instanceof ConfigurationSection)) throw new IllegalArgumentException("worlds." + world.getKey() + " must be a section");
            validateMaps((ConfigurationSection) world.getValue(), "worlds." + world.getKey());
        }
    }

    private static void validateMaps(ConfigurationSection parent, String path) {
        for (String map : MAPS) {
            ConfigurationSection config = section(parent, map, path + "." + map);
            value(config, "blocked", Boolean.class, path + "." + map + ".blocked");
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key, String path) {
        if (parent == null) return null;
        Object raw = parent.getValues(false).get(key);
        if (raw == null) return null;
        if (!(raw instanceof ConfigurationSection)) throw new IllegalArgumentException(path + " must be a section");
        return (ConfigurationSection) raw;
    }

    private static void value(ConfigurationSection parent, String key, Class<?> type, String path) {
        if (parent == null) return;
        Map<String, Object> values = parent.getValues(false);
        if (values.containsKey(key) && !type.isInstance(values.get(key))) throw new IllegalArgumentException(path + " must be " + (type == Boolean.class ? "true or false" : "text"));
    }
}
