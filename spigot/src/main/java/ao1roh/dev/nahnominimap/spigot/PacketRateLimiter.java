package ao1roh.dev.nahnominimap.spigot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PacketRateLimiter {
    private final Map<UUID, Map<String, long[]>> players = new HashMap<>();

    boolean allow(UUID player, String channel, long now) {
        return allow(player, channel, now, 20);
    }

    boolean allow(UUID player, String channel, long now, int limit) {
        Map<String, long[]> channels = players.computeIfAbsent(player, ignored -> new HashMap<>());
        long[] window = channels.computeIfAbsent(channel, ignored -> new long[]{now, 0});
        if (now - window[0] >= 1_000_000_000L || now - window[0] < 0) { window[0] = now; window[1] = 0; }
        return ++window[1] <= limit;
    }

    void remove(UUID player) { players.remove(player); }
}
