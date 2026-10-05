package net.momirealms.craftengine.bukkit.api;

import java.util.HashMap;
import java.util.Map;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Counts real bridge builds against a registry that can be replaced or unloaded. */
public final class CraftEngineItems {
    public static final Map<String, Definition> REGISTRY = new HashMap<>();
    public static int builds;

    public static Definition byId(String id) {
        return REGISTRY.get(id);
    }

    public static final class Definition {
        private final Material material;

        public Definition(Material material) {
            this.material = material;
        }

        public BuiltItem buildItem(ItemBuildContext context, int amount) {
            builds++;
            return new BuiltItem(new ItemStack(material, amount));
        }
    }

    public record BuiltItem(ItemStack platformItem) {}
}
