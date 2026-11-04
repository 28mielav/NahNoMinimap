package ao1roh.dev.nahnominimap.common.jm;

import ao1roh.dev.nahnominimap.common.ProtocolCoordinator;
import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import ao1roh.dev.nahnominimap.common.version.MinecraftVersion;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class JourneyMapProtocol {
    private static final Gson JSON = new Gson();
    private static final MinecraftVersion MODERN_PROTOCOL = new MinecraftVersion(1, 21, 0);
    private static final MinecraftVersion SPLIT_CHANNELS = new MinecraftVersion(1, 18, 0);
    private final ProtocolCoordinator plugin;

    public JourneyMapProtocol(ProtocolCoordinator plugin) {
        this.plugin = plugin;
    }

    public boolean isLegacy(ClientSession player) {
        return !player.getVersion().greaterThanEqual(SPLIT_CHANNELS);
    }

    private boolean hasModernFraming(ClientSession player) {
        return player.getVersion().greaterThanEqual(MODERN_PROTOCOL);
    }

    private ByteArrayDataOutput response(ClientSession player, int legacyType) {
        ByteArrayDataOutput buffer = ByteStreams.newDataOutput();
        if (!hasModernFraming(player)) buffer.writeByte(legacyType);
        return buffer;
    }

    public void handlePerm(ClientSession player, byte[] ignored, String channel, int legacyType) {
        ByteArrayDataOutput buffer = response(player, legacyType);
        buffer.writeByte(42);
        buffer.writeBoolean(player.hasPermission("minimap.jm.admin"));
        WireCodec.writeUtf(JSON.toJson(plugin.getEffectiveJMConfig(player)), buffer);
        buffer.writeBoolean(true);
        player.sendPluginMessage(buffer.toByteArray(), channel);
    }

    private void sendVersion(ClientSession player, byte[] payload, String channel) {
        ByteArrayDataInput input = ByteStreams.newDataInput(payload);
        if (!hasModernFraming(player)) input.readByte();
        JsonElement request = new JsonParser().parse(WireCodec.readUtf(input));
        if (!request.isJsonObject()) throw new IllegalArgumentException("Invalid JourneyMap version");
        JsonObject object = request.getAsJsonObject();
        JsonElement version = object.get("journeymap_version");
        if (version == null || !version.isJsonObject()) throw new IllegalArgumentException("Missing JourneyMap version");
        JsonElement major = version.getAsJsonObject().get("major");
        if (major == null || !major.isJsonPrimitive() || !major.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid JourneyMap version number");
        }
        boolean oldClient = major.getAsInt() <= 5;
        JsonObject details = new JsonObject();
        details.addProperty("full", oldClient ? "6.0.0" : "6.1.0-beta99");
        details.addProperty("major", 6);
        details.addProperty("minor", oldClient ? 0 : 1);
        details.addProperty("micro", 0);
        if (!oldClient) details.addProperty("patch", "-beta99");
        JsonObject reply = new JsonObject();
        reply.add("journeymap_version", details);
        reply.addProperty("loader_version", plugin.getServer().getLoaderVersion());
        reply.addProperty("loader", plugin.getServer().getLoaderName());
        reply.addProperty("minecraft_version", plugin.getServer().getMinecraftVersion().toString());
        ByteArrayDataOutput buffer = response(player, 0);
        WireCodec.writeUtf(JSON.toJson(reply), buffer);
        player.sendPluginMessage(buffer.toByteArray(), channel);
    }

    public void onPluginMessage(String channel, ClientSession player, byte[] payload) {
        if ("journeymap:version".equals(channel)) {
            sendVersion(player, payload, channel);
        } else if ("journeymap:perm_req".equals(channel)) {
            handlePerm(player, payload, channel, 0);
        } else if ("journeymap:common".equals(channel)) {
            int type = ByteStreams.newDataInput(payload).readUnsignedByte();
            if (type == 2) {
                handlePerm(player, payload, channel, type);
            } else if (type == 5) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Multiplayer options are not implemented."));
            }
        }
    }
}
