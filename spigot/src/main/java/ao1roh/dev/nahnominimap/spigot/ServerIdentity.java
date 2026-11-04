package ao1roh.dev.nahnominimap.spigot;

import ao1roh.dev.nahnominimap.Main;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class ServerIdentity {
    private static String cached;

    private ServerIdentity() {}

    public static synchronized String get() {
        if (cached != null) return cached;
        Main plugin = Main.getInstance();
        if (Bukkit.getWorlds().isEmpty()) throw new IllegalStateException("No loaded world for server identity");
        World world = Bukkit.getWorlds().get(0);
        try {
            PersistentDataContainer data = (PersistentDataContainer) Class.forName("org.bukkit.persistence.PersistentDataHolder")
                    .getMethod("getPersistentDataContainer").invoke(world);
            NamespacedKey key = new NamespacedKey(plugin, "server_identity");
            String identity = data.get(key, PersistentDataType.STRING);
            Path legacy = plugin.getDataFolder().toPath().resolve(".internal").resolve("internal-state.yml");
            if (identity == null) {
                if (Files.isRegularFile(legacy)) {
                    YamlConfiguration old = new YamlConfiguration();
                    old.load(legacy.toFile());
                    identity = old.getString("world-id");
                    if (identity == null) throw new IllegalStateException("Missing existing world identity");
                    UUID.fromString(identity);
                } else {
                    identity = world.getUID().toString();
                }
                data.set(key, PersistentDataType.STRING, identity);
                try {
                    world.save();
                } catch (RuntimeException ex) {
                    data.remove(key);
                    throw ex;
                }
            }
            if (Files.isRegularFile(legacy)) {
                try {
                    Path backup = plugin.getDataFolder().toPath().getParent().resolve("NahNoMinimap-state-backup.yml");
                    if (Files.exists(backup)) backup = Files.createTempFile(backup.getParent(), "NahNoMinimap-state-backup-", ".yml");
                    Files.copy(legacy, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(legacy);
                    try { Files.deleteIfExists(legacy.getParent()); } catch (java.nio.file.DirectoryNotEmptyException ignored) {}
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("Could not archive legacy state; the original file was retained: " + e.getMessage());
                }
            }
            cached = identity;
            return identity;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load server identity", e);
        }
    }
}
