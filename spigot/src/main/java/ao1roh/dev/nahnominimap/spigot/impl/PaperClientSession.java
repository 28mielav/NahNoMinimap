package ao1roh.dev.nahnominimap.spigot.impl;

import ao1roh.dev.nahnominimap.Main;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PaperClientSession implements ClientSession {
    private final Player player;

    public PaperClientSession(Player player) { this.player = player; }
    public Player getNativePlayer() { return player; }
    @Override public UUID getUniqueId() { return player.getUniqueId(); }
    @Override public String getUsername() { return player.getName(); }
    @Override public String getWorldName() { return player.getWorld().getName(); }
    @Override public boolean hasPermission(String permission) { return player.hasPermission(permission); }

    @Override public void sendPluginMessage(byte[] message, String channel) {
        Main plugin = Main.getInstance();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.sendPluginMessage(plugin, channel, message);
        });
    }

    @Override public void sendMessage(Component message) {
        Main.getInstance().adventure().player(player).sendMessage(message);
    }

    @Override public void disconnect(Component reason) {
        player.kickPlayer(LegacyComponentSerializer.legacy('\u00a7').serialize(reason));
    }

    @Override public MinecraftVersion getVersion() {
        Main plugin = Main.getInstance();
        return plugin.viaHooked ? plugin.viaHook.getPlayerVersion(this) : new PaperServerContext().getMinecraftVersion();
    }
}
