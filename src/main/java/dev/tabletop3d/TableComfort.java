package dev.tabletop3d;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import java.util.*;
/** Owns table collision and the temporary, seat-relative Mahjong focus position. */
final class TableComfort implements Listener,AutoCloseable {
    private final Tabletop3D plugin;private final Map<UUID,Boolean> previous=new HashMap<>();
    private record Focus(Room room,int seat,Location original,Location anchor,boolean gravity,boolean invisible) {}
    private final Map<UUID,Focus> focus=new HashMap<>();
    private final Map<UUID,Location> internalTeleport=new HashMap<>();
    private final Set<UUID> held=new HashSet<>();
    private final Set<UUID> menuClosing=new HashSet<>();
    TableComfort(Tabletop3D plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private boolean active(Player player){
        Room room=plugin.room(player);
        return room!=null&&plugin.allowed(player)&&plugin.arena.atTableWorld(player,room)
            &&player.getLocation().distanceSquared(plugin.arena.center(room.table))<=36;
    }
    void sync(){for(Player player:Bukkit.getOnlinePlayers())sync(player);}
    private void sync(Player player){
        Focus current=focus.get(player.getUniqueId());
        if(current!=null&&(!valid(player,current)||!atAnchor(player,current)))finish(player,atAnchor(player,current));
        if(focused(player))hideFocusedPlayer(player);
        if(!active(player)){restore(player);return;}
        if(!previous.containsKey(player.getUniqueId())){
            boolean collidable=player.isCollidable();previous.put(player.getUniqueId(),collidable);
            if(collidable)player.setCollidable(false);
        }
    }
    boolean focused(Player player){return focus.containsKey(player.getUniqueId());}
    void release(Player player){finish(player,true);}
    void menuClosed(Player player){if(held.contains(player.getUniqueId()))menuClosing.add(player.getUniqueId());}
    private boolean valid(Player player,Focus state){
        return !player.isDead()&&!player.isInsideVehicle()&&player.getGameMode()!=GameMode.SPECTATOR
            &&plugin.room(player)==state.room()&&state.room().seat(player.getUniqueId())==state.seat()&&active(player);
    }
    private static boolean atAnchor(Player player,Focus state){
        Location now=player.getLocation();return now.getWorld().equals(state.anchor().getWorld())&&now.distanceSquared(state.anchor())<.01;
    }
    private void begin(Player player){
        Room room=plugin.room(player);
        if(room==null||!room.kind.equals("mahjong")||room.seat(player.getUniqueId())<0||!active(player)
            ||player.isDead()||player.isInsideVehicle()||player.getGameMode()==GameMode.SPECTATOR)return;
        int seat=room.seat(player.getUniqueId());double angle=2*Math.PI*seat/room.capacity;
        Location anchor=plugin.arena.center(room.table).clone().add(Math.sin(angle)*1.65,1.08,Math.cos(angle)*1.65);
        // Shift lowers the eye to 1.27 blocks. Aim at the felt; players can still aim at individual tiles.
        anchor.setDirection(new Vector(-Math.sin(angle)*1.65,TableGeometry.SURFACE+.05-1.08-1.27,-Math.cos(angle)*1.65));
        if(!clear(anchor))return;
        Focus state=new Focus(room,seat,player.getLocation().clone(),anchor,player.hasGravity(),player.isInvisible());
        sync(player);focus.put(player.getUniqueId(),state);player.setGravity(false);player.setInvisible(true);player.setVelocity(new Vector());player.setFallDistance(0);
        hideFocusedPlayer(player);
        if(!teleport(player,anchor)||!atAnchor(player,state))finish(player,false);
    }
    private static boolean clear(Location target){
        for(double x:new double[]{-.3,.3})for(double z:new double[]{-.3,.3})for(double y:new double[]{0,.9,1.8})
            if(!target.clone().add(x,y,z).getBlock().isPassable())return false;
        return true;
    }
    private boolean teleport(Player player,Location target){
        internalTeleport.put(player.getUniqueId(),target);
        try{return player.teleport(target);}finally{internalTeleport.remove(player.getUniqueId());}
    }
    private void finish(Player player,boolean returnToSeat){
        Focus state=focus.remove(player.getUniqueId());if(state==null)return;
        player.setGravity(state.gravity());player.setInvisible(state.invisible());player.setVelocity(new Vector());player.setFallDistance(0);
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.getUniqueId().equals(player.getUniqueId()))viewer.showPlayer(plugin,player);
        if(returnToSeat&&!player.isDead()&&atAnchor(player,state)&&clear(state.original()))teleport(player,state.original());
    }
    private void hideFocusedPlayer(Player player){
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.getUniqueId().equals(player.getUniqueId()))viewer.hidePlayer(plugin,player);
    }
    @EventHandler public void input(PlayerInputEvent event){
        Player player=event.getPlayer();UUID id=player.getUniqueId();
        // Dialogs reset client key bindings; keep the physical Shift latch until the menu closes.
        if(plugin.menus!=null&&plugin.menus.active(player)){release(player);return;}
        if(event.getInput().isSneak()){if(held.add(id))begin(player);}
        else{held.remove(id);release(player);}
    }
    @EventHandler public void clientTick(io.papermc.paper.event.packet.ClientTickEndEvent event){
        Player player=event.getPlayer();UUID id=player.getUniqueId();
        if(!menuClosing.remove(id)||plugin.menus!=null&&plugin.menus.active(player))return;
        if(!player.getCurrentInput().isSneak())held.remove(id);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent event){
        if(event instanceof PlayerTeleportEvent)return;
        Player player=event.getPlayer();Focus state=focus.get(player.getUniqueId());if(state==null||event.getTo()==null)return;
        if(!valid(player,state)){release(player);event.setTo(player.getLocation());return;}
        Location target=state.anchor().clone();target.setYaw(event.getTo().getYaw());target.setPitch(event.getTo().getPitch());event.setTo(target);
        player.setVelocity(new Vector());player.setFallDistance(0);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void externalTeleport(PlayerTeleportEvent event){
        Player player=event.getPlayer();Location expected=internalTeleport.get(player.getUniqueId());
        if(expected!=null&&expected.equals(event.getTo()))return;
        finish(player,false);restore(player);
    }
    private void restore(Player p){Boolean value=previous.remove(p.getUniqueId());if(value!=null)p.setCollidable(value);}
    @EventHandler public void hunger(FoodLevelChangeEvent event){if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);}
    @EventHandler public void world(PlayerChangedWorldEvent event){finish(event.getPlayer(),false);restore(event.getPlayer());sync(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){release(event.getPlayer());restore(event.getPlayer());held.remove(event.getPlayer().getUniqueId());menuClosing.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){finish(event.getEntity(),false);restore(event.getEntity());held.remove(event.getEntity().getUniqueId());}
    public void close(){for(Player p:Bukkit.getOnlinePlayers()){release(p);restore(p);}focus.clear();held.clear();menuClosing.clear();previous.clear();}
}
