package ao1roh.dev.nahnominimap.common;

import ao1roh.dev.nahnominimap.common.api.ClientSession;
import ao1roh.dev.nahnominimap.common.api.ServerContext;
import ao1roh.dev.nahnominimap.common.jm.JourneyMapProtocol;
import ao1roh.dev.nahnominimap.common.voxel.VoxelMapProtocol;
import ao1roh.dev.nahnominimap.common.worldinfo.WorldIdentityProtocol;
import ao1roh.dev.nahnominimap.common.xaeros.XaeroProtocol;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ProtocolCoordinator {
    private final JourneyMapProtocol journey = new JourneyMapProtocol(this);
    private final XaeroProtocol xaero = new XaeroProtocol(this);
    private final VoxelMapProtocol voxel = new VoxelMapProtocol(this);
    private final WorldIdentityProtocol worldInfo = new WorldIdentityProtocol(this);

    public abstract void loadConfig();
    public abstract void registerChannel(String channel);
    public abstract ServerContext getServer();

    public void enableSelf() {
        for (String channel : new String[]{"journeymap:version", "journeymap:perm_req", "journeymap:common",
                "worldinfo:world_id", "xaerominimap:main", "xaeroworldmap:main", "xaerolib:main", "voxelmap:settings"}) {
            registerChannel(channel);
        }
        loadConfig();
        getServer().getServerId();
    }

    public boolean isBlocked(String map, String world) { return true; }
    public boolean isBlockedFor(String map, ClientSession player) {
        return isBlocked(map, player.getWorldName()) && !player.hasPermission("nahnominimap.bypass." + map)
                && !player.hasPermission("nahnominimap.bypass.*");
    }

    private void sendJourneySettings(ClientSession player) {
        boolean legacy = journey.isLegacy(player);
        journey.handlePerm(player, new byte[0], legacy ? "journeymap:common" : "journeymap:perm_req", legacy ? 2 : 0);
    }

    public void handleXaeroRetry(ClientSession player) {
        xaero.sendXaerosHandshake(player);
        xaero.sendXaerosConfig(player);
    }

    public void handleInitialJoin(ClientSession player) {
        handleXaeroRetry(player);
        voxel.sendSettings(player);
        worldInfo.sendPacket(player);
    }

    public void handlePlayerJoined(ClientSession player) {
        handleInitialJoin(player);
        sendJourneySettings(player);
    }

    public void handleSwitchWorld(ClientSession player) {
        handleXaeroRetry(player);
        voxel.sendSettings(player);
        sendJourneySettings(player);
    }

    public void sendVoxelSettings(ClientSession player) { voxel.sendSettings(player); }

    public void reloadConfigAndPush() {
        loadConfig();
        for (ClientSession player : getServer().getPlayers()) handlePlayerJoined(player);
    }

    public Map<String, String> getEffectiveJMConfig(ClientSession player) {
        boolean blocked = isBlockedFor("journeymap", player);
        Map<String, String> values = new LinkedHashMap<>();
        String enabled = Boolean.toString(!blocked);
        String access = blocked ? "NONE" : "ALL";
        for (String key : new String[]{"journeymapEnabled", "minimapEnabled", "allowDeathPoints", "showInGameBeacons",
                "allowWaypoints", "allowRightClickTeleport", "crossDimTeleport", "playerRadarEnabled",
                "playerRadarNamesEnabled", "villagerRadarEnabled", "animalRadarEnabled", "mobRadarEnabled"}) values.put(key, enabled);
        for (String key : new String[]{"allowMultiplayerSettings", "worldPlayerRadar", "seeUndergroundPlayers",
                "surfaceMapping", "topoMapping", "biomeMapping", "caveMapping", "radarEnabled"}) values.put(key, access);
        values.put("hideCoordinates", Boolean.toString(blocked));
        values.put("useWorldId", "true");
        values.put("viewOnlyServerProperties", "true");
        values.put("hideOps", "false");
        values.put("hideSpectators", "false");
        values.put("teleportEnabled", "false");
        values.put("worldPlayerRadarUpdateTime", "5");
        values.put("radarLateralDistance", "512");
        values.put("radarVerticalDistance", "320");
        for (String key : new String[]{"maxAnimalsData", "maxAmbientCreaturesData", "maxMobsData", "maxPlayersData", "maxVillagersData"}) values.put(key, "128");
        for (String key : new String[]{"renderRange", "surfaceRenderRange", "caveRenderRange"}) values.put(key, "0");
        values.put("configVersion", "6.1.0-beta99");
        return values;
    }

    public void onPluginMessage(String channel, ClientSession player, byte[] message) {
        switch (channel) {
            case "journeymap:version":
            case "journeymap:perm_req":
            case "journeymap:common": journey.onPluginMessage(channel, player, message); break;
            case "xaerominimap:main":
            case "xaeroworldmap:main": xaero.onPluginMessage(channel, player, message); break;
            case "xaerolib:main": xaero.onXaeroLibMessage(player, message); break;
            case "worldinfo:world_id": worldInfo.onPluginMessage(channel, player, message); break;
            default: break;
        }
    }
}
