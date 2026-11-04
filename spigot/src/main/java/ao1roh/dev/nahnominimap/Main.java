package ao1roh.dev.nahnominimap;

import ao1roh.dev.nahnominimap.spigot.PaperCoordinator;
import ao1roh.dev.nahnominimap.spigot.ViaVersionAdapter;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class Main extends JavaPlugin {
    private static Main instance;
    private final PaperCoordinator main = new PaperCoordinator(this);

    public ViaVersionAdapter viaHook;
    public boolean viaHooked;

    private BukkitAudiences adventure;

    public BukkitAudiences adventure() {
        if(this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    @Override
    public void onEnable() {
        instance = this;
        org.bukkit.plugin.PluginManager manager = getServer().getPluginManager();
        for (String permission : new String[]{"nahnominimap.reload", "nahnominimap.alert"}) {
            if (manager.getPermission(permission) == null) manager.addPermission(new org.bukkit.permissions.Permission(permission, org.bukkit.permissions.PermissionDefault.OP));
        }
        for (String map : new String[]{"xaero-minimap", "xaero-worldmap", "diorama", "journeymap", "voxelmap", "conflux-map"}) {
            String permission = "nahnominimap.bypass." + map;
            if (manager.getPermission(permission) == null) manager.addPermission(new org.bukkit.permissions.Permission(permission, org.bukkit.permissions.PermissionDefault.FALSE));
        }
        for (String permission : new String[]{"nahnominimap.bypass.*", "nahnominimap.alert-exempt", "nahnominimap.alert.*"}) {
            if (manager.getPermission(permission) == null) manager.addPermission(new org.bukkit.permissions.Permission(permission, org.bukkit.permissions.PermissionDefault.FALSE));
        }
        for (String map : new String[]{"xaero-minimap", "xaero-worldmap", "journeymap", "voxelmap", "conflux-map", "wmap", "surveyor-framework"}) {
            String permission = "nahnominimap.alert." + map;
            if (manager.getPermission(permission) == null) manager.addPermission(new org.bukkit.permissions.Permission(permission, org.bukkit.permissions.PermissionDefault.OP));
        }
        this.adventure = BukkitAudiences.create(this);
        getServer().getPluginManager().registerEvents(main, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "journeymap:overlay_show");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "journeymap:overlay_remove");
        main.enableSelf();
        main.registerChannel("confluxmap:map_sync");
        main.registerChannel("worldmap:handshake");

        try (java.io.InputStream input = getResource("banner.txt")) {
            if (input != null) {
                String banner = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                getLogger().info("\n\u200b\n" + formatBanner(banner, getDescription().getVersion()) + "\n\u200b");
            }
        } catch (java.io.IOException e) {
            getLogger().warning("Could not load startup banner: " + e.getMessage());
        }

        try {
            this.viaHook = new ViaVersionAdapter();
            this.viaHooked = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e ) {
        }
    }

    private static String formatBanner(String banner, String version) {
        StringBuilder result = new StringBuilder();
        String[] lines = banner.stripTrailing().split("\\R");
        String title = "HahNoMinimap " + version;
        int suffixStart = 0;
        int suffixWidth = title.length();
        for (String line : lines) {
            int note = line.indexOf("//");
            if (note < 0) continue;
            suffixStart = Math.max(suffixStart, note);
            String suffix = line.substring(note + 2).trim();
            if (!suffix.regionMatches(true, 0, "version", 0, 7)) suffixWidth = Math.max(suffixWidth, suffix.length());
        }
        for (String line : lines) {
            result.append("\u001b[1m");
            int note = line.indexOf("//");
            String art = note < 0 ? line : line.substring(0, note);
            int width = Math.max(1, art.length() - 1);
            int previous = -1;
            for (int i = 0; i < art.length(); i++) {
                int grey = 110 + (int) (125.0 * i / width);
                grey = (grey / 16) * 16;
                if (grey != previous) {
                    result.append("\u001b[38;2;").append(grey).append(';').append(grey).append(';').append(grey).append('m');
                    previous = grey;
                }
                result.append(art.charAt(i));
            }
            if (note >= 0) {
                String suffix = line.substring(note + 2).trim();
                if (suffix.regionMatches(true, 0, "version", 0, 7)) {
                    result.append(" ".repeat(suffixStart - note + (suffixWidth - title.length()) / 2));
                    result.append("\u001b[22;97mHahNoMinimap ").append("\u001b[37m").append(version);
                } else {
                    result.append(" ".repeat(suffixStart - note + (suffixWidth - suffix.length()) / 2));
                    result.append("\u001b[22;90m").append(suffix);
                }
            }
            result.append("\u001b[0m\n");
        }
        return result.toString().stripTrailing();
    }

    @Override
    public void onDisable() {
        if(this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("nahnominimap")) return false;
        if (args.length == 1 && (args[0].equalsIgnoreCase("overlay") || args[0].equalsIgnoreCase("overlaytest")) && sender instanceof org.bukkit.entity.Player && sender.isOp()) {
            main.sendJourneyMapOverlay((org.bukkit.entity.Player) sender);
            sender.sendMessage("JourneyMap overlay update requested.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nahnominimap.reload")) {
                main.language().send(sender, "no-permission");
                return true;
            }
            try {
                main.reloadConfigAndPush();
                main.language().sendReload(sender);
            } catch (RuntimeException e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not reload configuration", e);
                main.language().send(sender, "reload-error");
            }
            return true;
        }
        main.language().send(sender, "usage");
        return true;
    }

    public static Main getInstance() {
        return instance;
    }
}
