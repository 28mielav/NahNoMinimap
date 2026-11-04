package ao1roh.dev.nahnominimap.spigot.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PackFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void mergesOwnedKeysAndPreservesExistingTranslations() throws Exception {
        Path assets = temporary.newFolder("assets").toPath();
        Path path = assets.resolve("minecraft/lang/ru_ru.json");
        Files.createDirectories(path.getParent());
        Files.write(path, "{\"unrelated.key\":\"привет\",\"block.minecraft.test_instance_block\":\"old\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(PackFiles.sync(assets, "§cЗапрещено", "Запрещено"));
        JsonObject object = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("привет", object.get("unrelated.key").getAsString());
        assertEquals("§0", object.get("block.minecraft.test_instance_block").getAsString());
        assertTrue(Files.exists(assets.resolve("xaeroworldmap/lang/en_us.json")));
        for (String locale : new String[]{"ru_ru", "en_us"}) {
            assertTranslation(assets, "journeymap", locale, "jm.common.disabled_by_server", "§cЗапрещено");
            assertTranslation(assets, "xaeroworldmap", locale, "gui.xaero_no_world_map_item_message", "§cЗапрещено");
            assertTranslation(assets, "voxelmap", locale, "worldmap.disabled", "Запрещено");
            assertTranslation(assets, "minecraft", locale, "block.minecraft.test_instance_block", "§0");
        }
        assertFalse(PackFiles.sync(assets, "§cЗапрещено", "Запрещено"));
        assertTrue(PackFiles.sync(assets, "§aChanged", "Changed"));
    }

    @Test public void rejectsInvalidJsonBeforeAnyChanges() throws Exception {
        Path assets = temporary.newFolder("bad").toPath();
        Path invalid = assets.resolve("voxelmap/lang/en_us.json");
        Files.createDirectories(invalid.getParent());
        Files.write(invalid, "not-json".getBytes(StandardCharsets.UTF_8));
        try { PackFiles.sync(assets, "x", "x"); fail("Expected invalid JSON to be rejected"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("JSON")); }
        assertEquals("not-json", new String(Files.readAllBytes(invalid), StandardCharsets.UTF_8));
        assertFalse(Files.exists(assets.resolve("journeymap/lang/ru_ru.json")));
    }

    @Test public void findsCraftEngineLanguageConflictsWithoutTreatingOwnPackAsConflict() throws Exception {
        Path resources = temporary.newFolder("resources").toPath();
        Path own = resources.resolve("nahnominimap");
        Path ownFile = own.resolve("resourcepack/assets/minecraft/lang/en_us.json");
        Files.createDirectories(ownFile.getParent());
        Files.write(ownFile, "{}".getBytes(StandardCharsets.UTF_8));
        assertFalse(PackFiles.hasCraftEngineLangConflicts(resources, own));
        Path other = resources.resolve("other/resourcepack/assets/minecraft/lang/en_us.json");
        Files.createDirectories(other.getParent());
        Files.write(other, "{}".getBytes(StandardCharsets.UTF_8));
        assertTrue(PackFiles.hasCraftEngineLangConflicts(resources, own));
    }

    @Test public void selectsAutomaticAndExplicitProviders() {
        ProviderPlan.Provider ia = ProviderPlan.Provider.ITEMSADDER;
        ProviderPlan.Provider nexo = ProviderPlan.Provider.NEXO;
        assertNull(ProviderPlan.select("none", Collections.emptyList()));
        assertEquals(ia, ProviderPlan.select("none", Arrays.asList(ia, nexo)));
        assertEquals(nexo, ProviderPlan.select("nexo", Arrays.asList(ia, nexo)));
        try { ProviderPlan.select("oraxen", Arrays.asList(ia, nexo)); fail("Disabled provider accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("not enabled")); }
        try { ProviderPlan.select("bad", Arrays.asList(ia)); fail("Unknown provider accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Unknown")); }
    }

    @Test public void resolvesProviderSpecificAssetLayouts() throws Exception {
        Path root = temporary.newFolder("provider").toPath();
        assertEquals(root.resolve("contents/dev/resourcepack/assets"), ProviderPlan.assets(ProviderPlan.Provider.ITEMSADDER, root));
        assertEquals(root.resolve("pack/external_packs/NahNoMinimap/assets"), ProviderPlan.assets(ProviderPlan.Provider.NEXO, root));
        assertEquals(root.resolve("resources/nahnominimap/resourcepack/assets"), ProviderPlan.assets(ProviderPlan.Provider.CRAFTENGINE, root));
        assertEquals(root.resolve("pack/assets"), ProviderPlan.assets(ProviderPlan.Provider.ORAXEN, root));
    }

    private static void assertTranslation(Path assets, String namespace, String locale, String key, String expected) throws IOException {
        Path path = assets.resolve(namespace + "/lang/" + locale + ".json");
        JsonObject object = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(expected, object.get(key).getAsString());
    }
}
