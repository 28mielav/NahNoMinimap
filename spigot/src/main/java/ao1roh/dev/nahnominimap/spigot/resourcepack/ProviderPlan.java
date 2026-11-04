package ao1roh.dev.nahnominimap.spigot.resourcepack;

import java.util.List;
import java.nio.file.Path;

final class ProviderPlan {
    enum Provider {
        ITEMSADDER("itemsadder", "ItemsAdder", "iazip"),
        NEXO("nexo", "Nexo", "nexo reload pack"),
        CRAFTENGINE("craftengine", "CraftEngine", "craftengine reload all"),
        ORAXEN("oraxen", "Oraxen", "oraxen reload pack");
        final String key, plugin, rebuild;
        Provider(String key, String plugin, String rebuild) {
            this.key = key; this.plugin = plugin; this.rebuild = rebuild;
        }
    }

    private ProviderPlan() {}

    static Provider select(String choice, List<Provider> available) {
        if (choice.equals("none") || choice.equals("auto")) return available.isEmpty() ? null : available.get(0);
        for (Provider provider : Provider.values()) {
            if (!provider.key.equals(choice)) continue;
            if (!available.contains(provider)) throw new IllegalArgumentException("Resource-pack provider is not enabled: " + provider.plugin);
            return provider;
        }
        throw new IllegalArgumentException("Unknown resource-pack provider: " + choice);
    }

    static Path assets(Provider provider, Path root) {
        switch (provider) {
            case ITEMSADDER: return root.resolve("contents/dev/resourcepack/assets");
            case NEXO: return root.resolve("pack/external_packs/NahNoMinimap/assets");
            case CRAFTENGINE: return root.resolve("resources/nahnominimap/resourcepack/assets");
            case ORAXEN: return root.resolve("pack/assets");
            default: throw new IllegalArgumentException("Unknown provider");
        }
    }
}
