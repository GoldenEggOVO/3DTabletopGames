package dev.tabletop3d;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;

/** One nearby-player snapshot per table refresh, shared by all public model parts. */
final class TableAudience {
    private final Tabletop3D plugin;
    private final Location origin;
    private Set<Player> nativeViewers=Set.of(),packViewers=Set.of();
    private final Map<Entity,Boolean> layers=new HashMap<>();
    long generation;
    TableAudience(Tabletop3D plugin,Location origin){this.plugin=plugin;this.origin=origin;refresh();}
    boolean managed(){return plugin.pack!=null&&plugin.pack.mode!=TabletopPack.Mode.VANILLA;}
    boolean packed(Player player){return plugin.pack!=null&&plugin.pack.packed(player);}
    boolean needed(boolean packed){return !managed()?!packed:!(packed?packViewers:nativeViewers).isEmpty();}
    Set<Player> viewers(boolean packed){return packed?packViewers:nativeViewers;}
    Set<Player> all(){Set<Player> all=new HashSet<>(nativeViewers);all.addAll(packViewers);return all;}
    void refresh(){
        if(!managed())return;
        Set<Player> natives=new HashSet<>(),packs=new HashSet<>();
        // Display view range .35 corresponds to 22.4 blocks; include the whole visible envelope.
        for(Player player:origin.getWorld().getPlayers())if(player.isOnline()&&plugin.allowed(player)&&player.getLocation().distanceSquared(origin)<=24*24){
            if(packed(player))packs.add(player);
            else if(plugin.pack.mode!=TabletopPack.Mode.RESOURCE_PACK)natives.add(player);
        }
        if(natives.equals(nativeViewers)&&packs.equals(packViewers))return;
        Set<Player> old=all();nativeViewers=Set.copyOf(natives);packViewers=Set.copyOf(packs);generation++;
        old.addAll(all());
        for(var entry:layers.entrySet())for(Player player:old){
            if((entry.getValue()==null?all():viewers(entry.getValue())).contains(player))player.showEntity(plugin,entry.getKey());
            else player.hideEntity(plugin,entry.getKey());
        }
    }
    void add(Display entity,boolean packed){
        if(!managed())return;
        entity.setVisibleByDefault(false);layers.put(entity,packed);
        for(Player player:viewers(packed))player.showEntity(plugin,entity);
    }
    void common(Display entity){if(managed()){layers.put(entity,null);for(Player player:all())player.showEntity(plugin,entity);}}
    void remove(Entity entity){layers.remove(entity);entity.remove();}
}
