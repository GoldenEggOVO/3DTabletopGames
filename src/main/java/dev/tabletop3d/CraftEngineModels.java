package dev.tabletop3d;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Optional public-API bridge. Third-party classes are never loaded in vanilla mode. */
final class CraftEngineModels {
    static final List<String> IDS=ids();
    private final Map<String,ItemStack> items=new HashMap<>();
    private long checked;
    private org.bukkit.plugin.Plugin engine;
    boolean ready(){
        var current=Bukkit.getPluginManager().getPlugin("CraftEngine");
        if(current==null||!current.isEnabled()){items.clear();engine=null;return false;}
        long now=System.currentTimeMillis();
        if(current==engine&&now-checked<2000)return items.size()==IDS.size();
        engine=current;checked=now;items.clear();
        try{
            var loader=engine.getClass().getClassLoader();
            var api=Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems",true,loader);
            var context=Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext",true,loader);
            Object empty=context.getMethod("empty").invoke(null);
            var find=api.getMethod("byId",String.class);
            for(String id:IDS){
                Object definition=find.invoke(null,"tabletop3d:"+id);
                if(definition==null){items.clear();return false;}
                Object item=definition.getClass().getMethod("buildItem",context,int.class).invoke(definition,empty,1);
                items.put(id,((ItemStack)item.getClass().getMethod("platformItem").invoke(item)).clone());
            }
            return true;
        }catch(ReflectiveOperationException|LinkageError|ClassCastException ex){items.clear();return false;}
    }
    ItemStack item(String id){
        if(!ready())throw new IllegalStateException("CraftEngine tabletop models are not registered");
        ItemStack item=items.get(id);if(item==null)throw new IllegalArgumentException("Unknown tabletop model: "+id);
        return item.clone();
    }
    private static List<String> ids(){
        List<String> result=new ArrayList<>();
        for(char suit:new char[]{'m','p','s'})for(int n=1;n<=9;n++)result.add("mahjong_"+suit+n);
        for(char suit:new char[]{'m','p','s'})result.add("mahjong_"+suit+"0");
        for(int n=1;n<=7;n++)result.add("mahjong_z"+n);
        for(int n=1;n<=8;n++)result.add("mahjong_f"+n);
        for(char color:new char[]{'r','b','y','p'})for(String rank:List.of("1","2","3","4","5","6","7","8","9","10","draw","skip","reverse"))result.add("card_"+color+rank);
        result.addAll(List.of("mahjong_back","card_back","card_wild","card_swap","card_table","mahjong_table","mahjong_panel","ring_forward","ring_reverse","button_r","button_b","button_y","button_p","button_pass"));
        return List.copyOf(result);
    }
}
