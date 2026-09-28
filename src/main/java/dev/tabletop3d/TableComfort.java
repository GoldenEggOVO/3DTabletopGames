package dev.tabletop3d;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
/** Restore only collision flags owned by the board session; no external scoreboard dependency. */
final class TableComfort implements Listener,AutoCloseable {
    private final Tabletop3D plugin;private final Map<UUID,Boolean> previous=new HashMap<>();
    TableComfort(Tabletop3D plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private boolean active(Player player){
        Room room=plugin.room(player);
        return room!=null&&plugin.allowed(player)&&plugin.arena.atTableWorld(player,room)
            &&player.getLocation().distanceSquared(plugin.arena.center(room.table))<=36;
    }
    void sync(){for(Player player:Bukkit.getOnlinePlayers())sync(player);}
    private void sync(Player player){
        if(!active(player)){restore(player);return;}
        if(!previous.containsKey(player.getUniqueId())){
            boolean collidable=player.isCollidable();previous.put(player.getUniqueId(),collidable);
            if(collidable)player.setCollidable(false);
        }
    }
    private void restore(Player p){Boolean value=previous.remove(p.getUniqueId());if(value!=null)p.setCollidable(value);}
    @EventHandler public void hunger(FoodLevelChangeEvent event){if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);}
    @EventHandler public void world(PlayerChangedWorldEvent event){sync(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){restore(event.getPlayer());}
    public void close(){for(Player p:Bukkit.getOnlinePlayers())restore(p);previous.clear();}
}
