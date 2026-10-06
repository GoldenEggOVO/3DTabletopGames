package dev.tabletop3d.render;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.resource.TabletopPack;

import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.*;

public final class TableAudience {
    private final Tabletop3D plugin;
    private final Location origin;
    private final boolean enabled;
    private Set<Player> nativeViewers = Set.of(), packViewers = Set.of(), allViewers = Set.of();
    private final Map<Entity, Boolean> layers = new HashMap<>();
    public long generation;

    public TableAudience(Tabletop3D plugin, Location origin) {
        this(plugin, origin, true);
    }

    TableAudience(Tabletop3D plugin, Location origin, boolean enabled) {
        this.plugin = plugin;
        this.origin = origin;
        this.enabled = enabled;
        refresh();
    }

    public boolean managed() {
        return enabled && plugin.pack != null && plugin.pack.mode != TabletopPack.Mode.VANILLA;
    }

    public boolean packed(Player player) {
        return plugin.pack != null && plugin.pack.packed(player);
    }

    public boolean needed(boolean packed) {
        return !managed() ? !packed : !viewers(packed).isEmpty();
    }

    Set<Player> viewers(boolean packed) {
        return packed ? packViewers : nativeViewers;
    }

    public Set<Player> all() {
        return allViewers;
    }

    public void refresh() {
        if (!managed()) return;
        Set<Player> natives = new HashSet<>(), packs = new HashSet<>();

        for (Player player : origin.getWorld().getPlayers()) {
            if (!player.isOnline() || !plugin.allowed(player)
                    || player.getLocation().distanceSquared(origin) > 24 * 24) continue;
            if (packed(player)) packs.add(player);
            else if (plugin.pack.mode != TabletopPack.Mode.RESOURCE_PACK) natives.add(player);
        }
        if (natives.equals(nativeViewers) && packs.equals(packViewers)) return;
        Set<Player> oldNative = nativeViewers, oldPacked = packViewers, oldAll = allViewers;
        nativeViewers = Set.copyOf(natives);
        packViewers = Set.copyOf(packs);
        natives.addAll(packs);
        allViewers = Set.copyOf(natives);
        generation++;
        for (var entry : layers.entrySet()) {
            Boolean packed = entry.getValue();
            Set<Player> before = packed == null ? oldAll : packed ? oldPacked : oldNative;
            Set<Player> after = packed == null ? allViewers : viewers(packed);
            for (Player player : before)
                if (!after.contains(player)) player.hideEntity(plugin, entry.getKey());
            for (Player player : after)
                if (!before.contains(player)) player.showEntity(plugin, entry.getKey());
        }
    }

    public void add(Entity entity, boolean packed) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, packed);
        for (Player player : viewers(packed)) player.showEntity(plugin, entity);
    }

    public void common(Entity entity) {
        if (!managed()) return;
        entity.setVisibleByDefault(false);
        layers.put(entity, null);
        for (Player player : allViewers) player.showEntity(plugin, entity);
    }

    public void remove(Entity entity) {
        layers.remove(entity);
        entity.remove();
    }
}
