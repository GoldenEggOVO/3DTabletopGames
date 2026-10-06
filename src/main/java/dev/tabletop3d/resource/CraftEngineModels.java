package dev.tabletop3d.resource;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

public final class CraftEngineModels {
    public static final List<String> IDS = ids();
    private record Model(Object definition, ItemStack item) {}

    private final Map<String, Model> models = new HashMap<>();
    private long checked;
    private Plugin engine;

    boolean ready() {
        Plugin current = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (current == null || !current.isEnabled()) {
            models.clear();
            engine = null;
            return false;
        }
        long now = System.currentTimeMillis();
        if (current == engine && now - checked < 2000) return models.size() == IDS.size();
        if (current != engine) models.clear();
        engine = current;
        checked = now;
        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems", true, loader);
            Class<?> context = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext", true, loader);
            Object empty = context.getMethod("empty").invoke(null);
            Method find = api.getMethod("byId", String.class);
            Map<String, Model> next = new HashMap<>();
            Map<Class<?>, Method> builders = new HashMap<>(), converters = new HashMap<>();
            for (String id : IDS) {
                Object definition = find.invoke(null, "tabletop3d:" + id);
                if (definition == null) {
                    models.clear();
                    return false;
                }
                Model cached = models.get(id);
                if (cached == null || definition != cached.definition()) {
                    Class<?> type = definition.getClass();
                    Method build = builders.get(type);
                    if (build == null) {
                        build = type.getMethod("buildItem", context, int.class);
                        builders.put(type, build);
                    }
                    Object item = build.invoke(definition, empty, 1);
                    type = item.getClass();
                    Method convert = converters.get(type);
                    if (convert == null) {
                        convert = type.getMethod("platformItem");
                        converters.put(type, convert);
                    }
                    cached = new Model(definition, ((ItemStack) convert.invoke(item)).clone());
                }
                next.put(id, cached);
            }
            models.clear();
            models.putAll(next);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            models.clear();
            return false;
        }
    }

    ItemStack item(String id) {
        if (!ready()) throw new IllegalStateException("CraftEngine tabletop models are not registered");
        Model model = models.get(id);
        if (model == null) throw new IllegalArgumentException("Unknown tabletop model: " + id);
        return model.item().clone();
    }

    private static List<String> ids() {
        List<String> result = new ArrayList<>();
        for (char suit : new char[] {'m', 'p', 's'})
            for (int number = 1; number <= 9; number++) result.add("mahjong_" + suit + number);
        for (char suit : new char[] {'m', 'p', 's'}) result.add("mahjong_" + suit + "0");
        for (int number = 1; number <= 7; number++) result.add("mahjong_z" + number);
        for (int number = 1; number <= 8; number++) result.add("mahjong_f" + number);
        for (char color : new char[] {'r', 'b', 'y', 'p'})
            for (String rank : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "draw", "skip", "reverse"))
                result.add("card_" + color + rank);
        result.addAll(List.of("mahjong_back", "card_back", "card_wild", "card_swap", "card_table",
                "mahjong_table", "mahjong_panel", "ring_forward", "ring_reverse", "button_r",
                "button_b", "button_y", "button_p", "button_pass"));
        result.addAll(PackedBoardModels.ids());
        result.addAll(List.of("yacht_table", "yacht_die", "ludo_dice_tray"));
        return List.copyOf(result);
    }
}
