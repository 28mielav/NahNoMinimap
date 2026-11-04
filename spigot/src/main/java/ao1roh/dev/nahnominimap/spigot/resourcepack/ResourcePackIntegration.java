package ao1roh.dev.nahnominimap.spigot.resourcepack;

import ao1roh.dev.nahnominimap.Main;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ao1roh.dev.nahnominimap.spigot.resourcepack.ProviderPlan.Provider;

public final class ResourcePackIntegration {
    private ResourcePackIntegration() {}

    public static void sync(Main plugin, YamlConfiguration config, boolean reloading, String formatted, String plain) {
        if (!config.getBoolean("resource-pack.enabled", true)) return;
        String configured = config.getString("resource-pack.provider", "none");
        String choice = configured == null ? "none" : configured.trim().toLowerCase(Locale.ROOT);
        List<Provider> available = new ArrayList<>();
        for (Provider item : Provider.values()) if (Bukkit.getPluginManager().isPluginEnabled(item.plugin)) available.add(item);
        Provider selected;
        try { selected = ProviderPlan.select(choice, available); }
        catch (IllegalArgumentException e) { plugin.getLogger().warning(e.getMessage()); return; }
        if ((choice.equals("none") || choice.equals("auto")) && available.size() > 1) plugin.getLogger().warning("Multiple resource-pack providers are enabled; using " + selected.plugin + ". Set resource-pack.provider to choose another.");
        if (selected == null) return;
        Plugin provider = Bukkit.getPluginManager().getPlugin(selected.plugin);
        if (provider == null) return;
        try {
            Path root = provider.getDataFolder().toPath();
            Path assets = ProviderPlan.assets(selected, root);
            if (selected == Provider.CRAFTENGINE) {
                    Path pack = root.resolve("resources/nahnominimap");
                    if (PackFiles.hasCraftEngineLangConflicts(root.resolve("resources"), pack)) {
                        plugin.getLogger().warning("CraftEngine has overlapping language files. Check its duplicated-files-handler merges lang JSON before rebuilding; pack priority may affect translations.");
                    }
                    Files.createDirectories(pack);
                    Files.createDirectories(pack.resolve("configuration"));
                    Path metadata = pack.resolve("pack.yml");
                    if (!Files.exists(metadata)) Files.write(metadata, ("author: Valeim82\nversion: 1.0\ndescription: NahNoMinimap translations\nnamespace: nahnominimap\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            }
            boolean changed = PackFiles.sync(assets, formatted, plain);
            if (!changed) return;
            if (reloading && config.getBoolean("resource-pack.rebuild-on-reload", false)) {
                Provider target = selected;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!provider.isEnabled()) {
                        plugin.getLogger().warning("Cannot rebuild " + target.plugin + ": the provider is no longer enabled.");
                        return;
                    }
                    try {
                        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), target.rebuild)) plugin.getLogger().warning("Resource-pack rebuild command was not found: /" + target.rebuild);
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("Resource-pack rebuild failed for " + target.plugin + ": " + e.getMessage());
                    }
                }, 20L);
            } else {
                plugin.getLogger().info(targetMessage(selected));
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Cannot update " + selected.plugin + " resource-pack translations: " + e.getMessage());
        }
    }

    private static String targetMessage(Provider provider) {
        return provider.plugin + " translations updated. Run /" + provider.rebuild + " to rebuild the pack.";
    }
}
