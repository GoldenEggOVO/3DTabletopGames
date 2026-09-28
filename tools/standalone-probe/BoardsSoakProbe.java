package dev.tabletop3d.probe;

import dev.tabletop3d.internal.gson.*;
import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Test-only long-running room/model/replay exercise on an isolated loopback server. */
public final class BoardsSoakProbe extends JavaPlugin {
    private Plugin boards;private Object arena;private Class<?> roomType,roundType;
    private final List<Object> rooms=new ArrayList<>();
    private final Map<String,Integer> moves=new LinkedHashMap<>(),rounds=new LinkedHashMap<>();
    private final Map<UUID,Integer> lengths=new HashMap<>();
    private long started;private int seconds,samples,undos,replays,peakEntities;private boolean finished;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::begin,80);}
    @SuppressWarnings("unchecked") private void begin(){
        try{
            require(Bukkit.getIp().equals("127.0.0.1")&&Bukkit.getPort()==25618,"isolated loopback server");
            for(String name:List.of("ServerGames","ServerMenu","ServerCasino","KaMenu"))require(Bukkit.getPluginManager().getPlugin(name)==null,"unexpected dependency "+name);
            boards=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3dtabletop"));arena=field(boards,"arena");
            roomType=Class.forName("dev.tabletop3d.Room",true,boards.getClass().getClassLoader());
            roundType=Class.forName("dev.tabletop3d.RoundActions",true,boards.getClass().getClassLoader());
            Map<UUID,Object> registry=(Map<UUID,Object>)field(boards,"rooms");require(registry.isEmpty(),"fresh fixture required");
            var ctor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);ctor.setAccessible(true);
            List<String> kinds=List.of("chess","xiangqi","gomoku","aeroplane","checkers","draughts","reversi","go9","go13","go","connectfour");
            for(int i=0;i<kinds.size();i++){
                String kind=kinds.get(i);int capacity=kind.equals("checkers")?6:kind.equals("aeroplane")?4:2;
                Object room=ctor.newInstance(UUID.randomUUID(),kind,capacity,1000L+i,i);
                call(arena,"anchor",new Class<?>[]{roomType,Location.class},room,new Location(Bukkit.getWorlds().getFirst(),(i%4)*12,83,(i/4)*12));
                call(room,"fillBots",new Class<?>[0]);registry.put((UUID)field(room,"id"),room);rooms.add(room);
                call(arena,"platform",new Class<?>[]{int.class},i);call(boards,"start",new Class<?>[]{roomType},room);
                moves.put(kind,0);rounds.put(kind,0);lengths.put((UUID)field(room,"id"),0);
            }
            seconds=Integer.getInteger("tabletop.soak.seconds",3600);require(seconds>=60&&seconds<=7200,"bounded duration");
            started=System.currentTimeMillis();getLogger().info("TABLETOP_SOAK_STARTED seconds="+seconds+" games="+kinds.size());
            Bukkit.getScheduler().runTaskTimer(this,this::sample,20,20);
        }catch(Throwable error){fail(error);}
    }
    private void sample(){
        if(finished)return;
        try{
            samples++;long elapsed=(System.currentTimeMillis()-started)/1000;
            for(Object room:rooms){
                String kind=(String)field(room,"kind");UUID id=(UUID)field(room,"id");JsonArray history=(JsonArray)field(room,"history");
                int last=lengths.get(id);if(history.size()>last)moves.merge(kind,history.size()-last,Integer::sum);
                String phase=field(room,"phase").toString();require(!phase.equals("PAUSED")&&!phase.equals("ABORTED"),kind+" unexpected "+phase);
                if(phase.equals("FINISHED")){
                    replay(room);rounds.merge(kind,1,Integer::sum);call(arena,"remove",new Class<?>[]{roomType},room);
                    invoke(roundType,null,"fresh",new Class<?>[]{roomType,long.class},room,2000L+rounds.get(kind));
                    call(arena,"platform",new Class<?>[]{int.class},(int)field(room,"table"));call(boards,"start",new Class<?>[]{roomType},room);
                }else if(samples%180==0&&history.size()>2){
                    int seat=history.get(history.size()-1).getAsJsonObject().get("seat").getAsInt();
                    Object player=((List<?>)field(room,"seats")).get(seat);UUID actor=(UUID)call(player,"id",new Class<?>[0]);
                    invoke(roundType,null,"request",new Class<?>[]{roomType,UUID.class,long.class},room,actor,System.currentTimeMillis());
                    call(boards,"completeUndo",new Class<?>[]{roomType},room);undos++;replay(room);
                }
                lengths.put(id,((JsonArray)field(room,"history")).size());
            }
            if(samples%60==0){
                validateEntities();replay(rooms.get((samples/60-1)%rooms.size()));call(boards,"save",new Class<?>[0]);
                write(false,elapsed,-1);getLogger().info("TABLETOP_SOAK_PROGRESS elapsed="+elapsed+" moves="+moves.values().stream().mapToInt(Integer::intValue).sum()+" rounds="+rounds.values().stream().mapToInt(Integer::intValue).sum()+" entities="+peakEntities);
            }
            if(elapsed>=seconds){
                for(Object room:rooms)replay(room);validateEntities();
                for(Object room:rooms)call(boards,"remove",new Class<?>[]{roomType},room);call(boards,"save",new Class<?>[0]);
                int remaining=ownedEntities().size();require(remaining==0,"owned entities remain after room removal: "+remaining);
                require(moves.values().stream().allMatch(n->n>0),"every game must make progress");
                write(true,elapsed,remaining);finished=true;Bukkit.getScheduler().cancelTasks(this);getLogger().info("TABLETOP_SOAK_PASS elapsed="+elapsed+" moves="+moves+" rounds="+rounds+" undos="+undos+" replays="+replays);
            }
        }catch(Throwable error){fail(error);}
    }
    private void replay(Object room)throws Exception{
        String kind=(String)field(room,"kind");BoardGame live=(BoardGame)field(room,"board");
        BoardGame copy=GameFactory.create(kind,(int)field(room,"capacity"),(long)field(room,"seed"));
        for(JsonElement e:(JsonArray)field(room,"history")){var move=e.getAsJsonObject();copy.apply(move.get("seat").getAsInt(),move.get("action").getAsString());}
        require(copy.cells().equals(live.cells())&&copy.currentPlayer()==live.currentPlayer()&&copy.finished()==live.finished()&&Objects.equals(copy.outcome(),live.outcome()),kind+" replay differs");replays++;
    }
    @SuppressWarnings("unchecked") private void validateEntities()throws Exception{
        Set<UUID> expected=new HashSet<>();Map<?,?> views=(Map<?,?>)field(arena,"views");
        require(views.size()==rooms.size(),"one view per room");
        for(Object view:views.values()){
            for(String key:List.of("furniture","lastMove"))for(Entity e:(List<Entity>)field(view,key)){require(e.isValid(),"tracked entity is invalid");expected.add(e.getUniqueId());}
            for(Object token:((Map<?,?>)field(view,"tokens")).values())for(Entity e:(List<Entity>)field(token,"parts")){require(e.isValid(),"tracked piece is invalid");expected.add(e.getUniqueId());}
        }
        Set<UUID> actual=ownedEntities();require(actual.equals(expected),"model entity leak or missing part: actual="+actual.size()+" expected="+expected.size());peakEntities=Math.max(peakEntities,actual.size());
    }
    private Set<UUID> ownedEntities(){Set<UUID> ids=new HashSet<>();NamespacedKey key=new NamespacedKey("3dtabletop","board-cell");for(World world:Bukkit.getWorlds())for(Entity entity:world.getEntities())if(entity.isValid()&&entity.getPersistentDataContainer().has(key))ids.add(entity.getUniqueId());return ids;}
    private void write(boolean pass,long elapsed,int remaining)throws Exception{
        JsonObject report=new JsonObject();report.addProperty("pass",pass);report.addProperty("elapsed_seconds",elapsed);report.addProperty("requested_seconds",seconds);report.addProperty("samples",samples);report.addProperty("undos",undos);report.addProperty("replays",replays);report.addProperty("peak_owned_entities",peakEntities);report.addProperty("final_owned_entities",remaining);report.add("moves",new Gson().toJsonTree(moves));report.add("rounds",new Gson().toJsonTree(rounds));
        Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("soak.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private void fail(Throwable error){finished=true;getLogger().log(java.util.logging.Level.SEVERE,"TABLETOP_SOAK_FAIL",error);Bukkit.getScheduler().cancelTasks(this);}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{return invoke(target.getClass(),target,name,types,args);}
    private static Object invoke(Class<?> type,Object target,String name,Class<?>[] types,Object... args)throws Exception{Method method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
