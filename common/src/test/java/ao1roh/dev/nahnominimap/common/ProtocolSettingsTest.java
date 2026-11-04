package ao1roh.dev.nahnominimap.common;

import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.api.ServerContext;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import ao1roh.dev.nahnominimap.common.voxel.VoxelMapProtocol;
import ao1roh.dev.nahnominimap.common.xaeros.XaeroProtocol;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ProtocolSettingsTest {
    private static ProtocolCoordinator plugin(boolean blocked) {
        return new ProtocolCoordinator() {
            public void loadConfig() {}
            public void registerChannel(String channel) {}
            public ServerContext getServer() { return null; }
            public boolean isBlockedFor(String map, ClientSession player) { return blocked; }
        };
    }

    @Test public void journeyMapPoliciesCoverMapWaypointsAndRadar() {
        Map<String, String> blocked = plugin(true).getEffectiveJMConfig(null);
        Map<String, String> allowed = plugin(false).getEffectiveJMConfig(null);
        for (String key : new String[]{"journeymapEnabled", "minimapEnabled", "allowWaypoints", "playerRadarEnabled"}) {
            assertEquals("false", blocked.get(key));
            assertEquals("true", allowed.get(key));
        }
        assertEquals("NONE", blocked.get("surfaceMapping"));
        assertEquals("ALL", allowed.get("surfaceMapping"));
        assertEquals("true", blocked.get("hideCoordinates"));
        assertEquals("false", allowed.get("hideCoordinates"));
        assertEquals("false", blocked.get("teleportEnabled"));
    }

    @Test public void voxelSendsBothRestrictionStatesWithoutSharedMutablePackets() {
        AtomicReference<byte[]> captured = new AtomicReference<>();
        ClientSession player = (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class},
                (proxy, method, args) -> { if (method.getName().equals("sendPluginMessage")) { captured.set((byte[]) args[0]); return null; } throw new AssertionError(method.getName()); });
        VoxelMapProtocol handler = new VoxelMapProtocol(plugin(true));
        handler.sendSettings(player);
        ByteArrayDataInput input = ByteStreams.newDataInput(captured.get());
        assertEquals(0, input.readUnsignedByte());
        JsonObject json = new JsonParser().parse(WireCodec.readUtf(input)).getAsJsonObject();
        assertFalse(json.get("minimapAllowed").getAsBoolean());
        assertFalse(json.get("worldmapAllowed").getAsBoolean());
        captured.get()[0] = 99;
        handler.sendSettings(player);
        assertEquals(0, captured.get()[0]);
        new VoxelMapProtocol(plugin(false)).sendSettings(player);
        input = ByteStreams.newDataInput(captured.get());
        input.readByte();
        json = new JsonParser().parse(WireCodec.readUtf(input)).getAsJsonObject();
        assertTrue(json.get("minimapAllowed").getAsBoolean());
    }

    @Test public void truncatedXaeroMessagesDoNotInvokePlayer() {
        ClientSession player = (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class},
                (proxy, method, args) -> { throw new AssertionError(method.getName()); });
        XaeroProtocol handler = new XaeroProtocol(plugin(true));
        handler.onPluginMessage("xaerominimap:main", player, new byte[0]);
        handler.onPluginMessage("xaerominimap:main", player, new byte[]{1, 0, 0, 0});
    }
}
