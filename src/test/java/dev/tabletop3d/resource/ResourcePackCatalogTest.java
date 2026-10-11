package dev.tabletop3d.resource;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackCatalogTest {
    @Test
    void bundledDefaultsMatchThePublishedChecksumAndEditableFiles() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/tabletop-resource-pack.zip")) {
            assertNotNull(input, "Editable defaults must be bundled with the plugin");
            bytes = input.readAllBytes();
        }
        try (var hash = getClass().getResourceAsStream("/resource-pack.sha1")) {
            assertEquals(new String(hash.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).trim(),
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes)));
        }
        int files = 0;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                assertTrue(name.equals("pack.mcmeta") || name.startsWith("assets/") || name.startsWith("licenses/"), name);
                assertArrayEquals(Files.readAllBytes(Path.of("resource-pack").resolve(name)), zip.readAllBytes(), name);
                files++;
            }
        }
        assertTrue(files > 500, "The plugin needs the complete client pack");
    }

    @Test
    void staticItemsAndRegistrationsMatchEveryRuntimeModel() throws Exception {
        Path pack = Path.of("resource-pack");
        Path items = pack.resolve("assets/tabletop3d/items");
        Map<String, Path> definitions;
        try (var paths = Files.walk(items)) {
            definitions = paths.filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toMap(path -> path.getFileName().toString().replace(".json", ""), path -> path));
        }
        Set<String> names = definitions.keySet();
        assertEquals(Set.copyOf(CraftEngineModels.IDS), names);
        var registration = YamlConfiguration.loadConfiguration(
                pack.resolve("craftengine/resources/tabletop3d/configuration/items.yml").toFile());
        for (String name : names) {
            Path path = definitions.get(name);
            var item = JsonParser.parseString(Files.readString(path))
                    .getAsJsonObject().getAsJsonObject("model");
            assertEquals("minecraft:model", item.get("type").getAsString(), name);
            String model = item.get("model").getAsString();
            assertTrue(model.startsWith("tabletop3d:item/"), name);
            assertEquals(name, Path.of(model.split(":", 2)[1]).getFileName().toString(), name);
            assertTrue(Files.isRegularFile(pack.resolve("assets/" + model.replace(":", "/models/") + ".json")), name);
            assertEquals("paper", registration.getString("items.tabletop3d:" + name + ".material"), name);
            String itemModel = "tabletop3d:" + items.relativize(path).toString().replace('\\', '/').replace(".json", "");
            assertEquals(model.replace(":item/", ":"), itemModel, name);
            assertEquals(itemModel, registration.getString("items.tabletop3d:" + name + ".item_model"), name);
        }
    }
}
