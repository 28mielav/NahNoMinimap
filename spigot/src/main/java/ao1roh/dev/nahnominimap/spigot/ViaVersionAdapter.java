package ao1roh.dev.nahnominimap.spigot;

import ao1roh.dev.nahnominimap.Main;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import ao1roh.dev.nahnominimap.spigot.impl.PaperClientSession;
import ao1roh.dev.nahnominimap.spigot.impl.PaperServerContext;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ViaVersionAdapter {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+|x))?.*");
    private final Set<Integer> warned = ConcurrentHashMap.newKeySet();

    public ViaVersionAdapter() throws ClassNotFoundException { Via.getAPI(); }

    public MinecraftVersion getPlayerVersion(ClientSession player) {
        int protocol = Via.getAPI().getPlayerVersion(((PaperClientSession) player).getNativePlayer());
        if (ProtocolVersion.isRegistered(protocol)) {
            Matcher match = VERSION.matcher(ProtocolVersion.getProtocol(protocol).getName());
            if (match.matches()) {
                try {
                    String patch = match.group(3);
                    return new MinecraftVersion(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                            patch == null || "x".equals(patch) ? 0 : Integer.parseInt(patch));
                } catch (NumberFormatException ignored) {
                    // Fall back to the server protocol for unrecognized versions.
                }
            }
        }
        if (warned.add(protocol)) Main.getInstance().getLogger().warning("Unrecognized ViaVersion protocol " + protocol + "; using the server version.");
        return new PaperServerContext().getMinecraftVersion();
    }
}
