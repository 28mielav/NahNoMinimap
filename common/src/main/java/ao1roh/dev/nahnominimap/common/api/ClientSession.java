package ao1roh.dev.nahnominimap.common.api;

import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public interface ClientSession {
    void sendPluginMessage(byte[] message, String channel);
    void sendMessage(Component message);
    String getWorldName();
    void disconnect(Component reason);

    UUID getUniqueId();
    String getUsername();
    boolean hasPermission(String string);
    MinecraftVersion getVersion();
}
