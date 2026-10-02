package dev.tabletop3d;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Only a successful status for the current request selects resource-pack rendering. */
final class TabletopPack implements Listener,AutoCloseable {
    enum Mode { VANILLA, RESOURCE_PACK, MIXED }
    private enum Stage { LOADING, READY, FAILED }
    private record Request(UUID id,Stage stage) {}
    private final Tabletop3D plugin;
    private final BooleanSupplier available;
    private final Function<String,ItemStack> models;
    private final Map<UUID,Request> requests=new HashMap<>();
    private final NamespacedKey preference=new NamespacedKey("tabletop3d","resource_pack");
    final Mode mode;
    TabletopPack(Tabletop3D plugin){this(plugin,new CraftEngineModels());}
    private TabletopPack(Tabletop3D plugin,CraftEngineModels models){this(plugin,models::ready,models::item);}
    TabletopPack(Tabletop3D plugin,BooleanSupplier available,Function<String,ItemStack> models){
        this.plugin=plugin;this.available=available;this.models=models;
        mode=switch(plugin.getConfig().getString("rendering.mode","vanilla")){
            case "resource-pack"->Mode.RESOURCE_PACK;case "mixed"->Mode.MIXED;case "vanilla"->Mode.VANILLA;
            default->throw new IllegalArgumentException("rendering.mode must be vanilla, resource-pack or mixed");
        };
    }
    boolean hasToggle(){return mode==Mode.MIXED;}
    boolean preferred(Player player){return mode==Mode.RESOURCE_PACK||mode==Mode.MIXED&&player.getPersistentDataContainer().getOrDefault(preference,PersistentDataType.BYTE,(byte)0)==1;}
    boolean packed(Player player){Request r=requests.get(player.getUniqueId());return mode!=Mode.VANILLA&&preferred(player)&&r!=null&&r.stage()==Stage.READY&&available.getAsBoolean();}
    boolean canPlay(Player player,String kind){return mode!=Mode.RESOURCE_PACK||!supported(kind)||packed(player);}
    static boolean supported(String kind){return kind.equals("mahjong")||kind.equals("lastcard");}
    ItemStack item(String id){return models.apply(id);}
    UUID requestId(Player player){Request r=requests.get(player.getUniqueId());return r==null?null:r.id();}
    void require(Player player,String kind){
        if(canPlay(player,kind))return;
        Request r=requests.get(player.getUniqueId());if(r==null||r.stage()==Stage.FAILED)request(player);
        throw new IllegalArgumentException(Language.text("请先成功加载桌游资源包后再入座或操作；可重新打开菜单重试。"));
    }
    Component button(Player player){Request r=requests.get(player.getUniqueId());return Language.component(r!=null&&r.stage()==Stage.LOADING?"pack.loading":packed(player)?"pack.disable":"pack.enable");}
    void toggle(Player player){
        if(!hasToggle())return;Request r=requests.get(player.getUniqueId());
        if(r!=null&&r.stage()!=Stage.FAILED){player.getPersistentDataContainer().set(preference,PersistentDataType.BYTE,(byte)0);remove(player);}
        else{player.getPersistentDataContainer().set(preference,PersistentDataType.BYTE,(byte)1);request(player);}
    }
    void request(Player player){
        if(mode==Mode.VANILLA||!preferred(player))return;
        if(!available.getAsBoolean()){plugin.tell(player,Language.component("pack.unavailable"));return;}
        String url=plugin.getConfig().getString("rendering.resource-pack.url","");
        String hash=plugin.getConfig().getString("rendering.resource-pack.sha1","");UUID namespace;
        try{
            URI uri=URI.create(url);if((!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))||uri.getHost()==null||!hash.matches("(?i)[0-9a-f]{40}"))throw new IllegalArgumentException();
            namespace=UUID.fromString(plugin.getConfig().getString("rendering.resource-pack.uuid","40ae45a0-4d81-4c07-8e68-807e85168a09"));
        }catch(IllegalArgumentException ex){plugin.tell(player,Language.component("pack.configuration"));return;}
        remove(player);
        // Per-attempt IDs reject delayed callbacks after disabling or retrying the pack.
        UUID id=UUID.nameUUIDFromBytes((namespace+":"+player.getUniqueId()+":"+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        requests.put(player.getUniqueId(),new Request(id,Stage.LOADING));
        try{player.addResourcePack(id,url,HexFormat.of().parseHex(hash),dev.tabletop3d.ui.MessageText.plain(Language.component("pack.prompt")),false);}
        catch(IllegalArgumentException ex){requests.put(player.getUniqueId(),new Request(id,Stage.FAILED));plugin.tell(player,Language.component("pack.failed"));}
    }
    void status(Player player,UUID id,PlayerResourcePackStatusEvent.Status status){
        Request r=requests.get(player.getUniqueId());if(r==null||!r.id().equals(id)||r.stage()==Stage.FAILED||!preferred(player))return;
        switch(status){
            case SUCCESSFULLY_LOADED->{requests.put(player.getUniqueId(),new Request(id,Stage.READY));plugin.tell(player,Language.component("pack.ready"));}
            case ACCEPTED,DOWNLOADED->{}
            default->{requests.put(player.getUniqueId(),new Request(id,Stage.FAILED));plugin.tell(player,Language.component("pack.failed"));}
        }
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event){status(event.getPlayer(),event.getID(),event.getStatus());}
    @EventHandler public void joined(PlayerJoinEvent event){org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())request(event.getPlayer());},20);}
    @EventHandler public void quit(PlayerQuitEvent event){requests.remove(event.getPlayer().getUniqueId());}
    private void remove(Player player){Request r=requests.remove(player.getUniqueId());if(r!=null)player.removeResourcePack(r.id());}
    @Override public void close(){for(Player player:org.bukkit.Bukkit.getOnlinePlayers())remove(player);requests.clear();}
}
