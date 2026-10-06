package dev.tabletop3d.render;

import dev.tabletop3d.Tabletop3D;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Static native board maps shared by every table, with stable IDs across restarts. */
public final class TableMaps {
    private final File file;
    private final YamlConfiguration index;
    private final Map<String, List<ItemStack>> cache = new HashMap<>();

    public TableMaps(Tabletop3D plugin) {
        file = new File(plugin.getDataFolder(), "table-map-ids.yml");
        index = YamlConfiguration.loadConfiguration(file);
    }

    public List<ItemStack> get(World world, TableGeometry geometry) {
        String key = "v1." + world.getUID() + "." + geometry.kind;
        return cache.computeIfAbsent(key, ignored -> createBoardMaps(world, geometry, key));
    }

    private List<ItemStack> createBoardMaps(World world, TableGeometry geometry, String key) {
        BufferedImage board = TableArt.draw(geometry);
        List<ItemStack> items = new ArrayList<>();
        int columns = board.getWidth() / 128;
        int rows = board.getHeight() / 128;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                String path = key + "." + (row * columns + column);
                int id = index.getInt(path, -1);
                MapView map = id >= 0 ? Bukkit.getMap(id) : null;
                if (map == null) {
                    map = Bukkit.createMap(world);
                    index.set(path, map.getId());
                }
                map.setTrackingPosition(false);
                map.setUnlimitedTracking(false);
                map.setLocked(true);
                map.getRenderers().forEach(map::removeRenderer);
                BufferedImage tile = board.getSubimage(column * 128, row * 128, 128, 128);
                map.addRenderer(
                        new MapRenderer(false) {
                            private boolean painted;

                            @Override
                            public void render(MapView view, MapCanvas canvas, Player player) {
                                if (!painted) {
                                    canvas.drawImage(0, 0, tile);
                                    painted = true;
                                }
                            }
                        });
                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                meta.setMapView(map);
                item.setItemMeta(meta);
                items.add(item);
            }
        }
        try {
            index.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot save board map IDs", ex);
        }
        return List.copyOf(items);
    }
}
