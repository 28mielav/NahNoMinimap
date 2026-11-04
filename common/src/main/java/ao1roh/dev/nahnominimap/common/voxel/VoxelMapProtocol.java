package ao1roh.dev.nahnominimap.common.voxel;

import ao1roh.dev.nahnominimap.common.ProtocolCoordinator;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VoxelMapProtocol {
    public static final String VOXEL_SETTINGS_CHANNEL = "voxelmap:settings";
    private static final byte[] PERMITTED = encodeSettings(true);
    private static final byte[] RESTRICTED = encodeSettings(false);
    private final ProtocolCoordinator plugin;

    public VoxelMapProtocol(ProtocolCoordinator plugin) { this.plugin = plugin; }

    public void sendSettings(ClientSession player) {
        byte[] packet = plugin.isBlockedFor("voxelmap", player) ? RESTRICTED : PERMITTED;
        player.sendPluginMessage(packet.clone(), VOXEL_SETTINGS_CHANNEL);
    }

    private static byte[] encodeSettings(boolean allowed) {
        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (String key : new String[]{"radarAllowed", "radarMobsAllowed", "radarPlayersAllowed", "cavesAllowed",
                "minimapAllowed", "worldmapAllowed", "waypointsAllowed", "deathWaypointAllowed"}) {
            settings.put(key, allowed);
        }
        ByteArrayDataOutput packet = ByteStreams.newDataOutput();
        packet.writeByte(0);
        WireCodec.writeUtf(new Gson().toJson(settings), packet);
        return packet.toByteArray();
    }
}
