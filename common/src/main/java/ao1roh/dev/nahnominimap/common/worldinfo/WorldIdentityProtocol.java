package ao1roh.dev.nahnominimap.common.worldinfo;

import ao1roh.dev.nahnominimap.common.ProtocolCoordinator;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public final class WorldIdentityProtocol {
    public static final String WORLDINFO_CHANNEL = "worldinfo:world_id";
    private final ProtocolCoordinator plugin;

    public WorldIdentityProtocol(ProtocolCoordinator plugin) { this.plugin = plugin; }

    public void sendPacket(ClientSession recipient) {
        String identity = plugin.getServer().getServerId();
        if (identity == null || identity.isEmpty()) return;
        ByteArrayDataOutput packet = ByteStreams.newDataOutput();
        packet.writeByte(0);
        packet.writeByte(42);
        WireCodec.writeUtf(identity, packet);
        recipient.sendPluginMessage(packet.toByteArray(), WORLDINFO_CHANNEL);
    }

    public void onPluginMessage(String channel, ClientSession recipient, byte[] payload) {
        sendPacket(recipient);
    }
}
