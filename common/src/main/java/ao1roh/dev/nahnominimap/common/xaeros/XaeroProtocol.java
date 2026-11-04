package ao1roh.dev.nahnominimap.common.xaeros;

import ao1roh.dev.nahnominimap.common.ProtocolCoordinator;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class XaeroProtocol {
    public static final String XAEROS_CHANNEL = "xaerominimap:main";
    public static final String XAEROS_MAP_CHANNEL = "xaeroworldmap:main";
    private static final String LIB_CHANNEL = "xaerolib:main";
    private static final MinecraftVersion NAMELESS_NBT_VERSION = new MinecraftVersion(1, 20, 3);
    private final ProtocolCoordinator plugin;

    public XaeroProtocol(ProtocolCoordinator plugin) { this.plugin = plugin; }

    public void sendXaerosHandshake(ClientSession player) {
        ByteArrayDataOutput handshake = ByteStreams.newDataOutput();
        handshake.writeByte(1);
        handshake.writeInt(2);
        byte[] bytes = handshake.toByteArray();
        player.sendPluginMessage(bytes, XAEROS_CHANNEL);
        player.sendPluginMessage(bytes, XAEROS_MAP_CHANNEL);
        sendLibraryProfile(player);
    }

    private void sendLibraryProfile(ClientSession player) {
        sendLibraryPacket(player, 0, new byte[]{1});
        sendLibraryMap(player, XAEROS_MAP_CHANNEL, "xaero-worldmap", "map_item");
        sendLibraryMap(player, XAEROS_CHANNEL, "xaero-minimap", "minimap_item");
    }

    private void sendLibraryMap(ClientSession player, String channel, String map, String itemKey) {
        writeLibraryTag(player, 2, CompoundBinaryTag.builder().putString("c", channel).build());
        CompoundBinaryTag.Builder options = CompoundBinaryTag.builder();
        if (plugin.isBlockedFor(map, player)) options.putString(itemKey, "minecraft:test_instance_block");
        writeLibraryTag(player, 3, CompoundBinaryTag.builder().put("o", options.build()).putBoolean("r", true).build());
    }

    private void writeLibraryTag(ClientSession player, int id, CompoundBinaryTag tag) {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            BinaryTagIO.writer().writeNameless(tag, payload);
            sendLibraryPacket(player, id, payload.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode XaeroLib settings", e);
        }
    }

    private void sendLibraryPacket(ClientSession player, int id, byte[] data) {
        byte[] result = new byte[data.length + 1];
        result[0] = (byte) id;
        System.arraycopy(data, 0, result, 1, data.length);
        player.sendPluginMessage(result, LIB_CHANNEL);
    }

    public void onXaeroLibMessage(ClientSession player, byte[] message) { sendXaerosHandshake(player); }

    public void sendXaerosConfig(ClientSession player) {
        CompoundBinaryTag settings = CompoundBinaryTag.builder()
                .putBoolean("cm", player.hasPermission("minimap.override.cave-mode.enabled"))
                .putBoolean("ncm", player.hasPermission("minimap.override.nether-cave-mode.enabled"))
                .putBoolean("r", player.hasPermission("minimap.override.radar.enabled"))
                .build();
        ByteArrayDataOutput packet = ByteStreams.newDataOutput();
        packet.writeByte(4);
        try {
            if (player.getVersion().greaterThanEqual(NAMELESS_NBT_VERSION)) BinaryTagIO.writer().writeNameless(settings, packet);
            else BinaryTagIO.writer().write(settings, packet);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode Xaero settings", e);
        }
        byte[] bytes = packet.toByteArray();
        player.sendPluginMessage(bytes, XAEROS_CHANNEL);
        player.sendPluginMessage(bytes, XAEROS_MAP_CHANNEL);
    }

    public void onPluginMessage(String channel, ClientSession player, byte[] message) {
        if (message.length < 5 || message[0] != 1) return;
        int version = ((message[1] & 255) << 24) | ((message[2] & 255) << 16) | ((message[3] & 255) << 8) | (message[4] & 255);
        if (version < 2) {
            player.disconnect(MiniMessage.miniMessage().deserialize("<red>Xaero's Minimap is outdated.\n Please update to 23.7.0 or later."));
            return;
        }
        sendXaerosConfig(player);
    }
}
