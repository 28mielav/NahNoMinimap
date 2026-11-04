package ao1roh.dev.nahnominimap.spigot;

import ao1roh.dev.nahnominimap.Main;

import ao1roh.dev.nahnominimap.common.ProtocolCoordinator;
import ao1roh.dev.nahnominimap.common.api.ServerContext;
import ao1roh.dev.nahnominimap.spigot.impl.PaperClientSession;
import ao1roh.dev.nahnominimap.spigot.impl.PaperServerContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import ao1roh.dev.nahnominimap.common.network.WireCodec;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.world.WorldLoadEvent;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;

public class PaperCoordinator extends ProtocolCoordinator implements PluginMessageListener, Listener {
    public Main plugin;
    private final Set<String> reportedChannels = new java.util.LinkedHashSet<>();
    private final Map<UUID, Boolean> dioramaRules = new HashMap<>();
    private final Set<UUID> confluxSessions = new HashSet<>();
    private final Set<UUID> pendingConfluxHello = new HashSet<>();
    private final Set<UUID> pendingInventoryUpdates = new HashSet<>();
    private final PacketRateLimiter packetRateLimiter = new PacketRateLimiter();
    private static final String CONFLUX_CHANNEL = "confluxmap:map_sync";

    private void sendConfluxPolicy(Player player, boolean initial) {
        if (!player.isOnline()) return;
        boolean blocked = isBlockedFor("conflux-map", new PaperClientSession(player));
        if (!initial && !confluxSessions.contains(player.getUniqueId())) return;
        try {
            java.io.ByteArrayOutputStream restrictionBuffer = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream restrictions = new java.io.DataOutputStream(restrictionBuffer);
            restrictions.writeByte(0x19);
            restrictions.writeByte(blocked ? 0x0f : 0);
            if (blocked) {
                String message = language.plain("blocked");
                if (message != null && !message.isEmpty()) {
                    StringBuilder limited = new StringBuilder();
                    int bytes = 0;
                    for (int offset = 0; offset < message.length();) {
                        int codePoint = message.codePointAt(offset);
                        String character = new String(Character.toChars(codePoint));
                        int length = character.getBytes(StandardCharsets.UTF_8).length;
                        if (bytes + length > 256) break;
                        limited.append(character);
                        bytes += length;
                        offset += Character.charCount(codePoint);
                    }
                    if (bytes > 0) writeConfluxString(restrictions, limited.toString());
                }
            }
            sendConfluxPacket(player, restrictionBuffer.toByteArray());
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(buffer);
            out.writeByte(initial ? 2 : 5);
            out.writeByte(blocked ? 0x94 : 0);
            if (initial) {
                String worldId = getServer().getServerId();
                writeConfluxString(out, worldId == null || worldId.isEmpty() ? player.getWorld().getUID().toString() : worldId);
                writeConfluxString(out, "unknown");
            }
            out.writeInt(262144); out.writeShort(8); out.writeShort(100); out.writeByte(4);
            if (initial) out.writeByte(0);
            sendConfluxPacket(player, buffer.toByteArray());
            if (initial) confluxSessions.add(player.getUniqueId());
        } catch (java.io.IOException | RuntimeException ex) {
            plugin.getLogger().warning("Could not send Conflux policy: " + ex.getMessage());
        }
    }

    private void sendConfluxPacket(Player player, byte[] payload) {
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage(CONFLUX_CHANNEL, payload));
        } else {
            player.sendPluginMessage(plugin, CONFLUX_CHANNEL, payload);
        }
    }

    private static void writeConfluxString(java.io.DataOutputStream out, String value) throws java.io.IOException {
        byte[] utf = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(utf.length); out.write(utf);
    }

    private YamlConfiguration settings;
    private Language language;
    private boolean reloading;

    public PaperCoordinator(Main plugin) {
        this.plugin = plugin;
        this.language = new Language(plugin);
    }

    public Language language() { return language; }

    private static long packPosition(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | ((long) y & 0xfffL);
    }

    public void sendJourneyMapOverlay(Player player) {
        if (!player.isOnline()) return;
        if (!isBlockedFor("journeymap", new PaperClientSession(player))) {
            if (player.getListeningPluginChannels().contains("journeymap:overlay_remove")) {
                ByteArrayDataOutput remove = ByteStreams.newDataOutput();
                WireCodec.writeUtf("nahnominimap", remove);
                WireCodec.writeUtf("blocked-test", remove);
                player.sendPluginMessage(plugin, "journeymap:overlay_remove", remove.toByteArray());
            }
            return;
        }
        String channel = "journeymap:overlay_show";
        if (!player.getListeningPluginChannels().contains(channel)) {
            return;
        }
        Location seen = player.getLocation().clone();
        Plugin coordinateOffset = Bukkit.getPluginManager().getPlugin("CoordinateOffset");
        if (coordinateOffset != null && coordinateOffset.isEnabled()) {
            try {
                ClassLoader loader = coordinateOffset.getClass().getClassLoader();
                Class<?> entry = loader.loadClass("com.jtprince.coordinateoffset.api.CoordinateOffset");
                Class<?> apiType = loader.loadClass("com.jtprince.coordinateoffset.api.CoordinateOffsetAPI");
                Class<?> offsetPlayerType = loader.loadClass("com.jtprince.coordinateoffset.adapter.OffsetPlayer");
                Class<?> fixedOffsetType = loader.loadClass("com.jtprince.coordinateoffset.FixedOffset");
                Object api = entry.getMethod("api").invoke(null);
                Object adapted = apiType.getMethod("adaptPlayer", Object.class).invoke(api, player);
                Object offset = apiType.getMethod("getOffset", offsetPlayerType).invoke(api, adapted);
                seen = (Location) fixedOffsetType.getMethod("apply", Object.class).invoke(offset, seen);
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("JourneyMap overlay cancelled: cannot resolve CoordinateOffset (" + e.getClass().getSimpleName() + ")");
                return;
            }
        }
        int x = seen.getBlockX(), y = seen.getBlockY(), z = seen.getBlockZ(), r = 128;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        WireCodec.writeUtf("nahnominimap", out);
        WireCodec.writeVarInt(1, out);
        WireCodec.writeUtf("blocked-test", out);
        WireCodec.writeUtf(((org.bukkit.Keyed) player.getWorld()).getKey().toString(), out);
        WireCodec.writeVarInt(1, out);
        WireCodec.writeVarInt(4, out);
        out.writeLong(packPosition(x-r, y, z-r));
        out.writeLong(packPosition(x+r, y, z-r));
        out.writeLong(packPosition(x+r, y, z+r));
        out.writeLong(packPosition(x-r, y, z+r));
        WireCodec.writeVarInt(0, out);
        out.writeInt(0x990000); out.writeFloat(0.25f);
        out.writeInt(0xff4444); out.writeFloat(2.0f); out.writeFloat(0.8f);
        WireCodec.writeVarInt(100, out);
        WireCodec.writeVarInt(0, out); WireCodec.writeVarInt(16384, out);
        WireCodec.writeVarInt(1, out); WireCodec.writeVarInt(31, out);
        out.writeBoolean(true);
        WireCodec.writeUtf(language.plain("blocked"), out);
        out.writeBoolean(false);
        player.sendPluginMessage(plugin, channel, out.toByteArray());
    }

    @Override
    public void registerChannel(String channel) {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
    }

    @Override
    public ServerContext getServer() {
        return new PaperServerContext();
    }

    @Override
    public void loadConfig() {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create plugin directory");
        File file = new File(dir, "config.yml");
        try {
            if (!file.exists()) {
                try (java.io.InputStream input = plugin.getResource("default-config.yml")) {
                    if (input == null) throw new java.io.IOException("Missing default configuration");
                    Files.copy(input, file.toPath());
                }
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot migrate config", e); }
        YamlConfiguration next = new YamlConfiguration();
        try { next.load(file); }
        catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) { throw new IllegalStateException("Invalid config.yml", e); }
        if (next.getInt("config-version", -1) != 1) throw new IllegalStateException("Unsupported config-version");
        ConfigValidation.validate(next);
        YamlConfiguration previous = settings;
        Language previousLanguage = language;
        Language nextLanguage = new Language(plugin);
        try {
            nextLanguage.load(next.getString("language", "ru"), next.getString("resource-pack.message"));
            nextLanguage.updateConfigComments(file.toPath());
            next.load(file);
            ConfigValidation.validate(next);
            settings = next;
            language = nextLanguage;
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException e) {
            settings = previous;
            language = previousLanguage;
            throw new IllegalStateException("Cannot load language or update config", e);
        }
        ao1roh.dev.nahnominimap.spigot.resourcepack.ResourcePackIntegration.sync(
                plugin, settings, reloading, language.legacy("blocked"), language.plain("blocked"));
    }

    @Override
    public boolean isBlocked(String map, String world) {
        boolean global = settings.getBoolean("global." + map + ".blocked", true);
        if (global || !settings.getBoolean("worlds.enabled", false)) return global;
        org.bukkit.configuration.ConfigurationSection worlds = settings.getConfigurationSection("worlds");
        Object selected = worlds == null ? null : worlds.getValues(false).get(world);
        return selected instanceof org.bukkit.configuration.ConfigurationSection
                ? ((org.bukkit.configuration.ConfigurationSection) selected).getBoolean(map + ".blocked", global) : global;
    }

    @Override
    public boolean isBlockedFor(String map, ao1roh.dev.nahnominimap.common.api.ClientSession wrapped) {
        if (!isBlocked(map, wrapped.getWorldName())) return false;
        Player player = ((PaperClientSession) wrapped).getNativePlayer();
        String specific = "nahnominimap.bypass." + map;
        boolean bypass = player.isPermissionSet(specific) ? player.hasPermission(specific)
                : player.hasPermission(specific) || player.hasPermission("nahnominimap.bypass.*");
        return !bypass;
    }

    private void updateDioramaRule(Player player) {
        if (!player.isOnline() || settings == null) return;
        Set<String> channels = player.getListeningPluginChannels();
        if (!channels.contains("xaerominimap:main") && !channels.contains("xaeroworldmap:main")) return;
        boolean blocked = isBlockedFor("diorama", new PaperClientSession(player));
        Boolean previous = dioramaRules.get(player.getUniqueId());
        if (previous != null && previous == blocked) return;
        if (previous == null && !blocked) return;
        String code = blocked ? "\u00a7n\u00a7o\u00a7m\u00a7i\u00a7n\u00a7i\u00a7m\u00a7a\u00a7p" : "\u00a7r\u00a7e\u00a7s\u00a7e\u00a7t\u00a7x\u00a7a\u00a7e\u00a7r\u00a7o";
        com.google.gson.JsonObject message = new com.google.gson.JsonObject();
        message.addProperty("text", code);
        boolean sent = false;
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSystemChatMessage(true, message.toString()));
                sent = true;
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("Could not send minimap action-bar signal: " + e.getClass().getSimpleName());
            }
        }
        if (!sent) {
            try {
                plugin.adventure().player(player).sendActionBar(net.kyori.adventure.text.Component.text(code));
                sent = true;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not send minimap action-bar signal: " + e.getClass().getSimpleName());
            }
        }
        if (sent) dioramaRules.put(player.getUniqueId(), blocked);
        else plugin.getLogger().warning("Could not send Diorama rule to " + player.getName());
    }

    @Override
    public void reloadConfigAndPush() {
        reloading = true;
        try { super.reloadConfigAndPush(); } finally { reloading = false; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateDioramaRule(player), 20L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendJourneyMapOverlay(player), 20L);
            sendConfluxPolicy(player, false);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (message.length > 16384) return;
        int limit = "xaerolib:main".equals(channel) ? 1 : 20;
        if (!packetRateLimiter.allow(player.getUniqueId(), channel, System.nanoTime(), limit)) return;
        try {
        if ("worldmap:handshake".equals(channel)) {
            if (message.length == 0) reportXaero(player, channel, "пакет");
            return;
        }
        reportXaero(player, channel, "пакет");
        if (CONFLUX_CHANNEL.equals(channel)) {
            UUID id = player.getUniqueId();
            if (message.length > 0 && (message[0] & 255) == 1 && !confluxSessions.contains(id) && pendingConfluxHello.add(id)) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    pendingConfluxHello.remove(id);
                    if (player.isOnline() && !confluxSessions.contains(id)) sendConfluxPolicy(player, true);
                }, 2L);
            }
            return;
        }
        this.onPluginMessage(channel, new PaperClientSession(player), message);
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Rejected malformed plugin message on " + channel + " from " + player.getName());
        }
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        if (!event.getChannel().equals("worldmap:handshake")) reportXaero(event.getPlayer(), event.getChannel(), "регистрация канала");
        if (event.getChannel().equals("xaerominimap:main") || event.getChannel().equals("xaeroworldmap:main")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateDioramaRule(event.getPlayer()), 80L);
        }
        if (event.getChannel().equals("voxelmap:settings")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) sendVoxelSettings(new PaperClientSession(event.getPlayer()));
            });
        }
    }

    private void reportXaero(Player player, String channel, String evidence) {
        if (!channel.equals("surveyor:s2c_group_changed") && !channel.equals("worldmap:handshake") && !channel.equals(CONFLUX_CHANNEL) && !channel.equals("confluxmap:waypoints_v1") && !channel.equals("xaerominimap:main") && !channel.equals("xaeroworldmap:main") && !channel.equals("journeymap:version") && !channel.equals("journeymap:perm_req") && !channel.equals("journeymap:common") && !channel.equals("voxelmap:settings")) return;
        if (settings == null || !settings.getBoolean("alerts.enabled", true)) return;
        if (player.hasPermission("nahnominimap.alert-exempt")) return;
        String map = channel.equals("surveyor:s2c_group_changed") ? "surveyor-framework"
                : channel.equals("worldmap:handshake") ? "wmap"
                : channel.startsWith("confluxmap:") ? "conflux-map"
                : channel.startsWith("xaerominimap:") ? "xaero-minimap"
                : channel.startsWith("xaeroworldmap:") ? "xaero-worldmap"
                : channel.startsWith("journeymap:") ? "journeymap" : "voxelmap";
        if (!settings.getBoolean("alerts.maps." + map, true)) return;
        String key = player.getUniqueId() + ":" + channel;
        if (reportedChannels.size() >= 10000 && !reportedChannels.contains(key)) {
            java.util.Iterator<String> oldest = reportedChannels.iterator();
            if (oldest.hasNext()) { oldest.next(); oldest.remove(); }
        }
        if (!reportedChannels.add(key)) return;
        String evidenceText = evidence.equals("пакет") ? language.raw("evidence-packet") : language.raw("evidence-register");
        String notice = language.legacy("detected").replace("{player}", player.getName()).replace("{channel}", channel).replace("{evidence}", evidenceText);
        if (settings.getBoolean("alerts.console", true)) language.console(language.raw("detected").replace("{player}", player.getName()).replace("{channel}", channel).replace("{evidence}", evidenceText));
        if (!settings.getBoolean("alerts.chat", true)) return;
        for (Player admin : Bukkit.getOnlinePlayers()) {
            String permission = "nahnominimap.alert." + map;
            boolean receive = admin.isPermissionSet(permission) ? admin.hasPermission(permission)
                    : admin.hasPermission(permission) || admin.hasPermission("nahnominimap.alert.*");
            if (receive) admin.sendMessage(notice);
        }
    }

    private static final String RU_TEST_BLOCK = "\u0411\u043b\u043e\u043a-\u0442\u0435\u0441\u0442\u0435\u0440 \u044d\u043a\u0437\u0435\u043c\u043f\u043b\u044f\u0440\u0430";
    private static final String EN_TEST_BLOCK = "Test Instance Block";

    private void restoreTestBlockName(Player player) {
        if (!player.isOnline() || settings == null || !settings.getBoolean("resource-pack.enabled", true)) return;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && nameTestBlock(item, player)) player.getInventory().setItem(slot, item);
        }
    }

    private boolean nameTestBlock(ItemStack item, Player player) {
        if (!item.getType().name().equals("TEST_INSTANCE_BLOCK")) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        try {
            java.lang.reflect.Method hasItemName = ItemMeta.class.getMethod("hasItemName");
            java.lang.reflect.Method setItemName = ItemMeta.class.getMethod("setItemName", String.class);
            if (Boolean.TRUE.equals(hasItemName.invoke(meta))) return false;
            if (meta.hasDisplayName()) {
                String oldName = meta.getDisplayName();
                if (!RU_TEST_BLOCK.equals(oldName) && !EN_TEST_BLOCK.equals(oldName)) return false;
                meta.setDisplayName(null);
            }
            String name = player.getLocale().toLowerCase(java.util.Locale.ROOT).startsWith("ru") ? RU_TEST_BLOCK : EN_TEST_BLOCK;
            setItemName.invoke(meta, name);
            item.setItemMeta(meta);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Cannot safely update test block name: " + exception.getMessage());
            return false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && isTestBlock(event.getItem().getItemStack())) scheduleNameUpdate((Player) event.getEntity());
    }

    private static boolean isTestBlock(ItemStack item) {
        return item != null && item.getType().name().equals("TEST_INSTANCE_BLOCK");
    }

    private void scheduleNameUpdate(Player player) {
        UUID id = player.getUniqueId();
        if (!pendingInventoryUpdates.add(id)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingInventoryUpdates.remove(id);
            if (player.isOnline()) restoreTestBlockName(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        if (isTestBlock(event.getPlayer().getInventory().getItem(event.getNewSlot()))) scheduleNameUpdate(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isTestBlock(event.getMainHandItem()) || isTestBlock(event.getOffHandItem())) scheduleNameUpdate(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int hotbar = event.getHotbarButton();
        if (isTestBlock(event.getCurrentItem()) || isTestBlock(event.getCursor()) ||
                (hotbar >= 0 && isTestBlock(player.getInventory().getItem(hotbar)))) scheduleNameUpdate(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && isTestBlock(event.getOldCursor())) scheduleNameUpdate((Player) event.getWhoClicked());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleNameUpdate(event.getPlayer());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateDioramaRule(event.getPlayer()), 100L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendJourneyMapOverlay(event.getPlayer()), 160L);
        // Xaero channels can register after the player joins.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            PaperClientSession joined = new PaperClientSession(event.getPlayer());
            this.handleInitialJoin(joined);
            AtomicInteger attempts = new AtomicInteger(0);
            BukkitTask[] retry = new BukkitTask[1];
            retry[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!event.getPlayer().isOnline() || attempts.incrementAndGet() > 10) {
                    retry[0].cancel();
                    return;
                }
                Set<String> channels = event.getPlayer().getListeningPluginChannels();
                if (channels.contains("xaerolib:main") || channels.contains("xaerominimap:main") || channels.contains("xaeroworldmap:main")) {
                    this.handleXaeroRetry(joined);
                }
            }, 20L, 20L);
        }, 40L);
    }

    @EventHandler
    public void onLeft(PlayerQuitEvent event) {
        packetRateLimiter.remove(event.getPlayer().getUniqueId());
        pendingInventoryUpdates.remove(event.getPlayer().getUniqueId());
        dioramaRules.remove(event.getPlayer().getUniqueId());
        confluxSessions.remove(event.getPlayer().getUniqueId());
        pendingConfluxHello.remove(event.getPlayer().getUniqueId());
        String prefix = event.getPlayer().getUniqueId().toString() + ":";
        if (settings.getBoolean("alerts.notify-on-rejoin", true)) reportedChannels.removeIf(key -> key.startsWith(prefix));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.handleSwitchWorld(new PaperClientSession(event.getPlayer()));
        sendConfluxPolicy(event.getPlayer(), false);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateDioramaRule(event.getPlayer()), 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendJourneyMapOverlay(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (settings == null) return;
            try {
                File file = new File(plugin.getDataFolder(), "config.yml");
                YamlConfiguration candidate = new YamlConfiguration();
                candidate.load(file);
                if (candidate.getInt("config-version", -1) != 1) throw new IllegalStateException("Unsupported config-version");
                ConfigValidation.validate(candidate);
                language.updateConfigComments(file.toPath());
                candidate.load(file);
                ConfigValidation.validate(candidate);
                settings = candidate;
            } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException | IllegalStateException | IllegalArgumentException e) {
                plugin.getLogger().warning("Could not update world settings: " + e.getMessage());
            }
        });
    }
}
