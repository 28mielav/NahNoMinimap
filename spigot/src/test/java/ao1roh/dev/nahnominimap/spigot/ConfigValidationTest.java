package ao1roh.dev.nahnominimap.spigot;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public class ConfigValidationTest {
    @Test public void acceptsCurrentServerConfigWhenProvided() throws Exception {
        String sample = System.getenv("NAHNOMINIMAP_CONFIG_SAMPLE");
        Assume.assumeTrue(sample != null && !sample.isEmpty());
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.load(new java.io.File(sample));
        ConfigValidation.validate(config);
    }
    @Test public void validValuesAndMissingOptionalSectionsAreAccepted() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("global.xaero-minimap.blocked", true);
        config.set("worlds.enabled", true);
        config.set("worlds.world_nether.journeymap.blocked", false);
        config.set("alerts.maps.wmap", true);
        config.set("resource-pack.provider", "Nexo");
        ConfigValidation.validate(config);
        ConfigValidation.validate(new MemoryConfiguration());
    }

    @Test public void invalidValuesAreRejectedWithTheirPath() {
        for (String path : new String[]{"global.voxelmap.blocked", "worlds.enabled", "alerts.chat", "alerts.maps.wmap", "resource-pack.rebuild-on-reload"}) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set(path, "false");
            try { ConfigValidation.validate(config); fail("Accepted invalid " + path); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains(path)); }
        }
    }

    @Test public void rejectsUnknownProvidersAndNonSectionWorlds() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("resource-pack.provider", "bogus");
        try { ConfigValidation.validate(config); fail("Accepted unknown provider"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("provider")); }
        config.set("resource-pack.provider", "auto");
        config.set("worlds.custom", "false");
        try { ConfigValidation.validate(config); fail("Accepted invalid world"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("worlds.custom")); }
    }
}
