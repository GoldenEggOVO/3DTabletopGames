package dev.tabletop3d.resource;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackCatalogTest {
    @Test
    void staticItemsAndRegistrationsMatchEveryRuntimeModel() throws Exception {
        Path pack = Path.of("resource-pack");
        Path items = pack.resolve("assets/tabletop3d/items");
        Set<String> names;
        try (var paths = Files.list(items)) {
            names = paths.map(path -> path.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toSet());
        }
        assertEquals(Set.copyOf(CraftEngineModels.IDS), names);
        var registration = YamlConfiguration.loadConfiguration(
                pack.resolve("craftengine/resources/tabletop3d/configuration/items.yml").toFile());
        for (String name : names) {
            var item = JsonParser.parseString(Files.readString(items.resolve(name + ".json")))
                    .getAsJsonObject().getAsJsonObject("model");
            assertEquals("minecraft:model", item.get("type").getAsString(), name);
            String model = item.get("model").getAsString();
            assertTrue(model.startsWith("tabletop3d:item/"), name);
            assertEquals(name, Path.of(model.split(":", 2)[1]).getFileName().toString(), name);
            assertTrue(Files.isRegularFile(pack.resolve("assets/" + model.replace(":", "/models/") + ".json")), name);
            assertEquals("paper", registration.getString("items.tabletop3d:" + name + ".material"), name);
            assertEquals("tabletop3d:" + name, registration.getString("items.tabletop3d:" + name + ".item_model"), name);
        }
    }
}
