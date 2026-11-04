package ao1roh.dev.nahnominimap.spigot.impl;

import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.api.ServerContext;
import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import ao1roh.dev.nahnominimap.spigot.ServerIdentity;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PaperServerContext implements ServerContext {
    @Override public String getServerId() { return ServerIdentity.get(); }
    @Override public String getLoaderVersion() { return Bukkit.getVersion(); }
    @Override public String getLoaderName() { return Bukkit.getName(); }

    @Override public MinecraftVersion getMinecraftVersion() {
        String[] components = Bukkit.getBukkitVersion().split("-", 2)[0].split("\\.");
        int release = Integer.parseInt(components[0]);
        int major = components.length > 1 ? Integer.parseInt(components[1]) : 0;
        int minor = components.length > 2 && components[2].matches("\\d+") ? Integer.parseInt(components[2]) : 0;
        return new MinecraftVersion(release, major, minor);
    }

    @Override public List<ClientSession> getPlayers() {
        List<ClientSession> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) players.add(new PaperClientSession(player));
        return players;
    }
}
