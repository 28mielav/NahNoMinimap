package ao1roh.dev.nahnominimap.spigot;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PacketRateLimiterTest {
    @Test public void xaeroLimitDoesNotSuppressOtherChannels() {
        PacketRateLimiter limiter = new PacketRateLimiter();
        UUID player = UUID.randomUUID();
        assertTrue(limiter.allow(player, "xaerolib:main", 10, 1));
        assertFalse(limiter.allow(player, "xaerolib:main", 11, 1));
        assertTrue(limiter.allow(player, "journeymap:common", 11));
        assertTrue(limiter.allow(player, "xaerolib:main", 1_000_000_010L, 1));
    }
    @Test public void limitsEachPlayerAndChannelWithoutBlockingNormalBursts() {
        PacketRateLimiter limiter = new PacketRateLimiter();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        for (int i = 0; i < 20; i++) assertTrue(limiter.allow(first, "journeymap:common", 1));
        assertFalse(limiter.allow(first, "journeymap:common", 1));
        assertTrue(limiter.allow(first, "confluxmap:map_sync", 1));
        assertTrue(limiter.allow(second, "journeymap:common", 1));
        assertTrue(limiter.allow(first, "journeymap:common", 1_000_000_001L));
    }

    @Test public void reconnectClearsState() {
        PacketRateLimiter limiter = new PacketRateLimiter();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 20; i++) assertTrue(limiter.allow(player, "channel", 0));
        assertFalse(limiter.allow(player, "channel", 0));
        limiter.remove(player);
        assertTrue(limiter.allow(player, "channel", 0));
    }

    @Test public void oneHundredPlayersHaveIndependentWindows() {
        PacketRateLimiter limiter = new PacketRateLimiter();
        for (int i = 0; i < 100; i++) {
            UUID player = new UUID(0, i + 1);
            for (int n = 0; n < 20; n++) assertTrue(limiter.allow(player, "journeymap:common", 10));
            assertFalse(limiter.allow(player, "journeymap:common", 10));
        }
    }
}
