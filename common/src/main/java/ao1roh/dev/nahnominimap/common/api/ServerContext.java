package ao1roh.dev.nahnominimap.common.api;

import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;

import java.util.List;

public interface ServerContext {
    MinecraftVersion getMinecraftVersion();
    String getLoaderVersion();
    String getLoaderName();
    String getServerId();

    List<ClientSession> getPlayers();
}
