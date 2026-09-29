package dev.tabletop3d;

import com.google.gson.*;
import dev.tabletop3d.rules.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

public final class Tabletop3D extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    static final Map<String,String> NAMES=new LinkedHashMap<>();
    static { for(String kind:List.of("xiangqi","gomoku","chess","ludo","checkers","draughts","reversi","go","go9","go13","connectfour","lastcard","mahjong")) NAMES.put(kind,switch(kind){case "xiangqi"->"中国象棋";case "gomoku"->"五子棋";case "chess"->"国际象棋";case "ludo"->"英国十字戏";case "checkers"->"中国跳棋";case "draughts"->"西洋跳棋";case "reversi"->"黑白棋";case "connectfour"->"四子棋";case "lastcard"->"Last Card";case "mahjong"->"Mahjong";default->"围棋";}); }
    static String gameName(String kind){return kind.equals("go9")?"围棋 · 9路":kind.equals("go13")?"围棋 · 13路":NAMES.getOrDefault(kind,kind);}
    final Map<UUID,Room> rooms=new LinkedHashMap<>();
    final Map<UUID,Location> returns=new HashMap<>();
    final SecureRandom random=new SecureRandom();
    GameMenus menus; GameWorld arena; TableComfort comfort; TableLobby tableLobby;
    BoardOccupancy coordinator;
    boolean stopping=false; private boolean loaded=false; private boolean authWarned=false; private int pulse=0;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    File jarFile(){return getFile();}
    @Override public void onEnable(){
        try {
            DataMigration.copyLegacy(getDataFolder().toPath().resolveSibling("ServerBoards"), getDataFolder().toPath());
            saveDefaultConfig();
            Language.load(this);
            menus=new GameMenus(this,new BoardWindow(this)); arena=new GameWorld(this);
            coordinator=new BoardOccupancy();
            comfort=new TableComfort(this); tableLobby=new TableLobby(this);tableLobby.start();
            Objects.requireNonNull(getCommand("3dtabletop")).setExecutor(this);getCommand("3dtabletop").setTabCompleter(this);
            Bukkit.getPluginManager().registerEvents(this,this);
            Bukkit.getScheduler().runTask(this,()->{try{arena.initialize();restore();loaded=true;}catch(Exception ex){getLogger().log(java.util.logging.Level.SEVERE,"Could not restore the board world or rooms; original records retained",ex);Bukkit.getPluginManager().disablePlugin(this);}});
            Bukkit.getScheduler().runTaskTimer(this,this::tick,20,20);
            getLogger().info("3dtabletop enabled.");
        } catch(Exception|LinkageError ex){getLogger().log(java.util.logging.Level.SEVERE,"Could not initialize table rooms",ex);Bukkit.getPluginManager().disablePlugin(this);}
    }
    @Override public void onDisable(){stopping=true;save();if(tableLobby!=null)tableLobby.close();if(coordinator!=null)coordinator.close();if(comfort!=null)comfort.close();if(menus!=null)menus.close();if(arena!=null)arena.close();}
    public void suspendView(Player player){if(comfort!=null)comfort.release(player);if(menus!=null)menus.forget(player);if(arena!=null)arena.clearSelection(player);}
    public boolean hasActiveGame(Player player){return room(player)!=null;}
    boolean mainMenuAvailable(){return Bukkit.getPluginCommand("servermenu:servermenu")!=null;}

    boolean allowed(Player p){
        if(!p.isOnline()||!p.hasPermission("3dtabletop.use"))return false;
        if(Bukkit.getPluginManager().getPlugin("AuthMe")==null)return true;
        if(!Bukkit.getPluginManager().isPluginEnabled("AuthMe"))return false;
        try{Class<?> api=Class.forName("fr.xephi.authme.api.v3.AuthMeApi");return (boolean)api.getMethod("isAuthenticated",Player.class).invoke(api.getMethod("getInstance").invoke(null),p);}
        catch(ReflectiveOperationException|LinkageError ex){if(!authWarned){authWarned=true;getLogger().warning("Could not verify AuthMe login; board interactions are disabled.");}return false;}
    }
    Room room(Player p){return room(p.getUniqueId());}
    Room room(UUID p){return rooms.values().stream().filter(r->r.seat(p)>=0&&r.phase!=Room.Phase.ABORTED).findFirst().orElse(null);}
    void tell(Player p,String message){tell(p,Language.legacy(message));}
    void tell(Player p,Component message){p.sendMessage(Language.component("chat.prefix","message",message));}
    void announce(Room r,Component message){for(Room.Seat s:r.seats){Player p=Bukkit.getPlayer(s.id());if(p!=null&&!s.bot())tell(p,message);}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage(Language.component("chat.console"));if(args.length>0&&args[0].equals("status"))sender.sendMessage(Language.component("chat.status","rooms",rooms.size(),"world",arena.world!=null));return true;}
        if(!allowed(p)){tell(p,Language.component("chat.permission"));return true;}
        try{
            String sub=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            switch(sub){
                case "menu"->menus.main(p);
                case "click"->{if(args.length==3)menus.handle(p,args[1]+" "+args[2]);}
                case "move"->{if(args.length==2){Room r=requireRoom(p);action(p,r,r.revision,new JsonPrimitive(args[1]));}}
                case "resume"->{Room r=room(p);if(r==null){menus.main(p);}else resume(p,r);}
                case "create"->{if(args.length<2)menus.main(p);else create(p,args[1],args.length>2?Integer.parseInt(args[2]):defaultCapacity(args[1]));}
                case "join"->{if(args.length<2)menus.main(p);else join(p,find(args[1]));}
                case "ready"->{Room r=requireRoom(p);ready(p,r);}
                case "bots"->{Room r=requireRoom(p);startWithBots(p,r);}
                case "leave"->{menus.confirmLeave(p);}
                case "undo"->requestUndo(p,requireRoom(p));
                case "rematch"->rematch(p,requireRoom(p));
                case "rules"->{Room r=room(p);menus.rules(p,args.length>1&&NAMES.containsKey(args[1])?args[1]:r==null?"gomoku":r.kind);}
                default->menus.main(p);
            }
        }catch(IllegalArgumentException ex){tell(p,ex.getMessage()==null?Language.component("error.invalid"):Language.legacy(ex.getMessage()));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(sender instanceof Player player){Room own=room(player);if(args.length==2&&args[0].equalsIgnoreCase("move")&&own!=null&&own.board instanceof HandGame&&own.seat(player.getUniqueId())!=own.turn())return List.of();return CommandSuggestions.complete(args,rooms.values(),own,false,allowed(player));}
        return CommandSuggestions.complete(args,rooms.values(),null,true,true);
    }
    @EventHandler public void commandSuggestions(PlayerCommandSendEvent event){
        CommandSuggestions.hideDuplicateRoot(event.getCommands());
    }
    private void requireLiveRoom(Room room){if(rooms.get(room.id)!=room)throw new IllegalArgumentException("房间已关闭");}
    Room requireRoom(Player p){Room r=room(p);if(r==null)throw new IllegalArgumentException("你尚未加入房间");return r;}
    Room find(String text){return rooms.values().stream().filter(r->r.id.toString().startsWith(text)).findFirst().orElseThrow(()->new IllegalArgumentException("房间已关闭"));}
    static int defaultCapacity(String kind){return switch(kind){case"checkers"->6;case"ludo","aeroplane","lastcard","mahjong"->4;default->2;};}
    static boolean capacityValid(String kind,int n){return switch(kind){case"mahjong"->n==4;case"checkers"->Set.of(2,3,4,6).contains(n);case"ludo","aeroplane","yacht","lastcard"->n>=2&&n<=4;case"gomoku","xiangqi","chess","draughts","reversi","go","go9","go13","connectfour"->n==2;default->false;};}
    void create(Player p,String kind,int capacity){
        create(p,kind,capacity,Map.of());
    }
    void create(Player p,String kind,int capacity,Map<String,String> options){
        if(!allowed(p))throw new IllegalArgumentException("请先登录并取得棋类权限");
        if(!coordinator.reserve(p.getUniqueId(),kind))throw new IllegalArgumentException("请先离开当前对局");
        try{createReserved(p,kind,capacity,options);}catch(RuntimeException|Error ex){try{Room partial=room(p);if(partial!=null)remove(partial);save();}finally{coordinator.release(p.getUniqueId(),kind);}throw ex;}
    }
    void createReserved(Player p,String kind,int capacity){
        createReserved(p,kind,capacity,Map.of());
    }
    void createReserved(Player p,String kind,int capacity,Map<String,String> options){
        if(Set.of("uno","doudizhu","yacht","aeroplane","flying").contains(kind))throw new IllegalArgumentException("这个游戏暂时停用，请在菜单选择其他游戏。");
        if(!capacityValid(kind,capacity))throw new IllegalArgumentException("不支持的游戏或人数");
        if(room(p)!=null)throw new IllegalArgumentException("请先离开当前对局");
        if(rooms.size()>=getConfig().getInt("max-rooms",12))throw new IllegalArgumentException("房间已满，请先加入现有房间");
        int index=0;Set<Integer> used=new HashSet<>();rooms.values().forEach(r->used.add(r.table));while(used.contains(index))index++;
        Room r=new Room(UUID.randomUUID(),kind,capacity,random.nextLong(),index,options);r.sideTray=Set.of("ludo","aeroplane").contains(kind);arena.anchor(r,p.getLocation());
        r.join(p.getUniqueId(),p.getName());rooms.put(r.id,r);
        try{for(int seat=0;seat<r.capacity;seat++)arena.seatLocation(r,seat);arena.platform(index);r.board=r.newBoard();arena.render(r);}
        catch(RuntimeException ex){arena.remove(r);rooms.remove(r.id);throw ex;}
        if(!enterArena(p,r)){remove(r);returns.remove(p.getUniqueId());throw new IllegalArgumentException("传送被取消，未创建房间。请解除传送限制后再试。");}save();if(comfort!=null)comfort.sync();menus.room(p,r);
    }
    void join(Player p,Room r){
        if(!allowed(p))return;requireLiveRoom(r);
        if(r.seat(p.getUniqueId())>=0){resume(p,r);return;}
        if(!coordinator.reserve(p.getUniqueId(),r.kind))throw new IllegalArgumentException("请先离开当前对局");
        try{joinReserved(p,r);}catch(RuntimeException|Error ex){try{if(r.seats.removeIf(seat->seat.id().equals(p.getUniqueId()))){r.offline.remove(p.getUniqueId());r.ready.remove(p.getUniqueId());r.revision++;save();}}finally{coordinator.release(p.getUniqueId(),r.kind);}throw ex;}
    }
    void joinReserved(Player p,Room r){
        if(!allowed(p))return;requireLiveRoom(r);
        Room old=room(p);if(old!=null&&old!=r)throw new IllegalArgumentException("请先离开当前对局");
        boolean already=r.seat(p.getUniqueId())>=0;r.join(p.getUniqueId(),p.getName());
        if(!enterArena(p,r)){if(!already){r.seats.removeIf(s->s.id().equals(p.getUniqueId()));r.offline.remove(p.getUniqueId());r.revision++;returns.remove(p.getUniqueId());save();}throw new IllegalArgumentException("传送被取消，无法入座。请解除限制后再试。");}
        announce(r,Language.component("chat.joined","player",p.getName()));save();if(comfort!=null)comfort.sync();menus.room(p,r);
    }
    boolean enterArena(Player p,Room r){
        if(!allowed(p))return false;requireLiveRoom(r);
        if(r.seat(p.getUniqueId())<0)throw new IllegalArgumentException("你尚未加入房间");
        if(!arena.atTableWorld(p,r))returns.putIfAbsent(p.getUniqueId(),p.getLocation().clone());
        if(!p.teleport(arena.seatLocation(r,r.seat(p.getUniqueId())))){r.offline.putIfAbsent(p.getUniqueId(),System.currentTimeMillis());return false;}
        r.offline.remove(p.getUniqueId());return true;
    }
    void resume(Player p,Room r){if(!allowed(p))return;requireLiveRoom(r);if(!enterArena(p,r)){tell(p,Language.component("chat.resume.cancelled"));menus.room(p,r);return;}menus.room(p,r);}
    boolean isBedrock(Player p){
        var geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser!=null&&geyser.isEnabled())try{Class<?> c=Class.forName("org.geysermc.geyser.api.GeyserApi",true,geyser.getClass().getClassLoader());return (boolean)c.getMethod("isBedrockPlayer",UUID.class).invoke(c.getMethod("api").invoke(null),p.getUniqueId());}
        catch(ReflectiveOperationException|LinkageError ignored){return true;}
        try{Class<?> c=Class.forName("org.geysermc.floodgate.api.FloodgateApi");return (boolean)c.getMethod("isFloodgatePlayer",UUID.class).invoke(c.getMethod("getInstance").invoke(null),p.getUniqueId());}
        catch(ReflectiveOperationException|LinkageError ignored){return false;}
    }
    void ready(Player p,Room r){
        if(!allowed(p))return;requireLiveRoom(r);
        if(r.phase!=Room.Phase.LOBBY||r.seat(p.getUniqueId())<0)return;
        if(!r.ready.add(p.getUniqueId()))r.ready.remove(p.getUniqueId());r.revision++;
        if(r.seats.size()==r.capacity&&r.seats.stream().allMatch(s->s.bot()||r.ready.contains(s.id())))start(r);
        else{save();menus.room(p,r);}
    }
    void startWithBots(Player p,Room r){
        if(!allowed(p))return;requireLiveRoom(r);
        if(r.phase!=Room.Phase.LOBBY||!r.host(p.getUniqueId()))throw new IllegalArgumentException("仅房主可以添加陪练");
        if(r.seats.stream().filter(s->!s.bot()&&!s.id().equals(p.getUniqueId())).anyMatch(s->!r.ready.contains(s.id())))throw new IllegalArgumentException("请等待其他玩家准备");
        r.fillBots();start(r);
    }
    void start(Room r){
        if(!prepareSeats(r))return;
        r.phase=Room.Phase.STARTING;r.busy=true;r.changed=System.currentTimeMillis();

            try{r.board=r.newBoard();for(JsonElement e:r.history){JsonObject j=e.getAsJsonObject();r.board.apply(j.get("seat").getAsInt(),j.get("action").getAsString());}
                r.phase=r.completed||r.board.finished()?Room.Phase.FINISHED:Room.Phase.PLAYING;r.busy=false;arena.render(r);save();announce(r,Language.component("chat.start"));
                if(!r.restoring&&r.phase==Room.Phase.PLAYING){arena.sound(r,TableSounds.START);arena.turnSound(r);}}
            catch(RuntimeException ex){r.busy=false;if(r.restoring)throw ex;pause(r,"规则初始化失败，房间记录已保留");getLogger().log(java.util.logging.Level.WARNING,"Board start failed",ex);}
        
    }
    boolean prepareSeats(Room room){
        List<Room.Seat> previous=List.copyOf(room.seats);room.prepareSeats();
        if(room.restoring||previous.equals(room.seats))return true;
        Map<Player,Location> moved=new LinkedHashMap<>();boolean success=false;
        try{
            for(int seat=0;seat<room.seats.size();seat++){
                Room.Seat member=room.seats.get(seat);if(member.bot()||previous.indexOf(member)==seat)continue;
                Player player=Bukkit.getPlayer(member.id());if(player==null||!allowed(player)||!arena.atTableWorld(player,room))continue;
                Location target=arena.seatLocation(room,seat);suspendView(player);Location before=player.getLocation().clone();
                if(!player.teleport(target))return false;moved.put(player,before);
            }
            success=true;return true;
        }catch(IllegalArgumentException ex){return false;}
        finally{
            if(!success){
                room.seats.clear();room.seats.addAll(previous);moved.forEach(Player::teleport);
                room.ready.clear();room.revision++;if(room.board==null)room.board=room.newBoard();arena.render(room);
                save();announce(room,Language.component("chat.seat-change-blocked"));showRoomToHumans(room);
            }
        }
    }
    void action(Player p,Room r,long revision,JsonElement action){
        if(!allowed(p))return;requireLiveRoom(r);if(!arena.atTableWorld(p,r))throw new IllegalArgumentException("请先用 /3dtabletop resume 回到棋桌所在世界");r.requireAction(p.getUniqueId(),revision);apply(r,r.seat(p.getUniqueId()),action,p);
    }
    void apply(Room r,int seat,JsonElement action,Player source){
        requireLiveRoom(r);
        if(r.phase!=Room.Phase.PLAYING||r.busy||r.undo!=null)return;
        if(arena.rolling(r)){if(source!=null)tell(source,Language.component("hint.roll.wait"));return;}
        if(!ReplayBudget.allows(r.history,action)){finish(r,"本局达到休闲对局长度上限，按和局结束");save();return;}

            try{List<Cell> before=r.board.cells();int previousTurn=r.turn();r.board.apply(seat,action.getAsString());r.event(seat,action);r.revision++;r.changed=System.currentTimeMillis();arena.render(r);
                arena.sound(r,TableSounds.move(r.kind,seat,action.getAsString(),before,r.board.cells()));
                if(r.board.finished())finish(r,r.board.outcome());else if(r.turn()!=previousTurn)arena.turnSound(r);save();if(source!=null&&source.isOnline())menus.room(source,r);
            }catch(IllegalArgumentException ex){if(source!=null)tell(source,ex.getMessage());}
        
    }
    void finish(Room r,String result){boolean first=r.phase!=Room.Phase.FINISHED;r.phase=Room.Phase.FINISHED;r.completed=true;r.result=result;r.ready.clear();r.undo=null;r.changed=System.currentTimeMillis();if(first)arena.sound(r,result.startsWith("winner:")?TableSounds.WIN:TableSounds.DRAW);announce(r,Language.component("chat.finished","result",RoomText.outcome(r,result)));onMain(()->showRoomToHumans(r));}
    void showRoomToHumans(Room r){if(!rooms.containsKey(r.id))return;for(Room.Seat s:r.seats){Player p=Bukkit.getPlayer(s.id());if(!s.bot()&&p!=null&&allowed(p)&&arena.atTableWorld(p,r))menus.room(p,r);}}
    void requestUndo(Player p,Room r){if(!allowed(p))return;requireLiveRoom(r);if(!arena.atTableWorld(p,r))return;if(arena.rolling(r)){tell(p,Language.component("hint.roll.wait"));return;}RoundActions.request(r,p.getUniqueId(),System.currentTimeMillis());if(r.undo.pending.isEmpty())completeUndo(r);else{announce(r,Language.component("chat.undo.requested","player",p.getName()));showRoomToHumans(r);}}
    void approveUndo(Player p,Room r){if(!allowed(p))return;requireLiveRoom(r);if(!arena.atTableWorld(p,r))return;if(RoundActions.approve(r,p.getUniqueId(),System.currentTimeMillis()))completeUndo(r);else showRoomToHumans(r);}
    void rejectUndo(Player p,Room r){if(!allowed(p))return;requireLiveRoom(r);RoundActions.reject(r,p.getUniqueId());announce(r,Language.component("chat.undo.cancelled"));showRoomToHumans(r);}
    void completeUndo(Room r){RoundActions.apply(r);arena.render(r);arena.sound(r,TableSounds.UNDO);save();announce(r,Language.component("chat.undo.complete"));showRoomToHumans(r);}
    void rematch(Player p,Room r){
        if(!allowed(p))return;requireLiveRoom(r);if(!arena.atTableWorld(p,r))return;
        if(RoundActions.rematchReady(r,p.getUniqueId())){for(Room.Seat s:r.seats){Player other=Bukkit.getPlayer(s.id());if(!s.bot()&&other!=null)suspendView(other);}arena.remove(r);RoundActions.fresh(r,random.nextLong());arena.platform(r.table);start(r);showRoomToHumans(r);}
        else{announce(r,Language.component("chat.rematch.ready","player",p.getName()));save();showRoomToHumans(r);}
    }
    void leave(Player p){
        Room r=room(p);suspendView(p);if(r!=null){
            if(r.phase==Room.Phase.PLAYING||r.phase==Room.Phase.STARTING||r.phase==Room.Phase.PAUSED){abort(r,p.getName()+" 离开，免费对局已结束",Language.component("result.left","player",p.getName()));}
            else if(r.phase==Room.Phase.FINISHED){remove(r);}
            else{r.seats.removeIf(s->s.id().equals(p.getUniqueId()));if(p.getUniqueId().equals(r.owner))r.owner=r.seats.isEmpty()?null:r.seats.getFirst().id();r.offline.remove(p.getUniqueId());r.ready.remove(p.getUniqueId());r.revision++;if(r.seats.stream().noneMatch(s->!s.bot()))remove(r);}
        }
        if(r!=null)coordinator.release(p.getUniqueId(),r.kind);
        returns.remove(p.getUniqueId());save();if(comfort!=null)comfort.sync();menus.main(p);
    }
    void returnFromArena(Player p){Location back=returns.get(p.getUniqueId());if(back!=null&&back.getWorld()!=null&&p.getWorld().equals(arena.world)){if(p.teleport(back))returns.remove(p.getUniqueId());}else if(back!=null&&!p.getWorld().equals(arena.world))returns.remove(p.getUniqueId());}
    void abort(Room r,String reason){abort(r,reason,Language.legacy(reason));}
    void abort(Room r,String reason,Component message){r.phase=Room.Phase.ABORTED;r.result=reason;announce(r,message);remove(r);save();}
    void pause(Room r,String reason){r.phase=Room.Phase.PAUSED;r.busy=false;r.result=reason;announce(r,Language.legacy(reason));save();}
    void remove(Room r){for(Room.Seat seat:r.seats)if(!seat.bot())coordinator.release(seat.id(),r.kind);arena.remove(r);rooms.remove(r.id);for(Room.Seat s:r.seats){Player p=Bukkit.getPlayer(s.id());if(p!=null)suspendView(p);}}
    void onMain(Runnable action){if(!stopping&&isEnabled())Bukkit.getScheduler().runTask(this,action);}
    long turnWaitMillis(Room room){
        int turn=room.turn();if(turn<0||turn>=room.seats.size())return 0;
        Room.Seat seat=room.seats.get(turn);
        return seat.bot()?2000:room.offline.containsKey(seat.id())?5000:getConfig().getLong("turn-seconds",60)*1000L;
    }
    void tick(){
        if(!loaded)return;if(comfort!=null)comfort.sync();long now=System.currentTimeMillis();pulse++;
        for(Room r:new ArrayList<>(rooms.values())){
            if(r.undo!=null){if(now>=r.undo.expires){r.undo=null;r.revision++;r.changed=now;announce(r,Language.component("chat.undo.expired"));showRoomToHumans(r);}else continue;}
            if(r.phase==Room.Phase.PAUSED)continue;
            for(Room.Seat s:r.seats)if(!s.bot()){
                Player p=Bukkit.getPlayer(s.id());boolean present=p!=null&&allowed(p)&&arena.atTableWorld(p,r);
                if(present)r.offline.remove(s.id());else r.offline.putIfAbsent(s.id(),now);
            }
            if(r.offline.values().stream().anyMatch(t->now-t>getConfig().getLong("reconnect-seconds",120)*1000L)){abort(r,"玩家离线或离开棋牌世界超过保留时间，对局已结束");continue;}
            if(r.phase==Room.Phase.LOBBY&&now-r.changed>getConfig().getLong("idle-room-minutes",30)*60_000L){abort(r,"等候房间超时关闭");continue;}
            if(r.phase==Room.Phase.FINISHED&&now-r.changed>600_000L){remove(r);continue;}
            if(r.phase!=Room.Phase.PLAYING||r.busy)continue;
            int turn=r.turn();if(turn<0||turn>=r.seats.size())continue;
            Room.Seat s=r.seats.get(turn);long wait=turnWaitMillis(r);
            if(r.board instanceof GoGame go&&go.scoring()&&!s.bot())continue; // A timeout is not a human's agreement to dead stones.
            if(now-r.changed<wait)continue;
    String choice=BoardBots.choose(r.board,turn,random);if(choice!=null)apply(r,turn,new JsonPrimitive(choice),null);
        }
        if(pulse%30==0)save();
    }
    @EventHandler public void quit(PlayerQuitEvent e){suspendView(e.getPlayer());Room r=room(e.getPlayer());if(r!=null)r.offline.putIfAbsent(e.getPlayer().getUniqueId(),System.currentTimeMillis());save();}


    @EventHandler public void world(PlayerChangedWorldEvent e){suspendView(e.getPlayer());Room r=room(e.getPlayer());if(r!=null&&!arena.atTableWorld(e.getPlayer(),r))r.offline.putIfAbsent(e.getPlayer().getUniqueId(),System.currentTimeMillis());}
    @EventHandler public void joined(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->{if(room(e.getPlayer())!=null)tell(e.getPlayer(),Language.component("chat.seat-held"));},60);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){String c=e.getMessage().split(" ",2)[0].toLowerCase(Locale.ROOT);if(Set.of("/servermenu","/servermenu:servermenu","/menu","/skin","/skins","/skinsrestorer:skin","/skinsrestorer:skins").contains(c))suspendView(e.getPlayer());}
    void save(){
        if(!loaded||!getDataFolder().isDirectory())return;
        JsonObject root=new JsonObject();root.addProperty("schema",1);JsonArray array=new JsonArray();
        for(Room r:rooms.values())if(r.phase!=Room.Phase.ABORTED){JsonObject j=new JsonObject();j.addProperty("id",r.id.toString());j.addProperty("kind",r.kind);j.addProperty("capacity",r.capacity);j.addProperty("seed",r.seed);j.addProperty("table",r.table);j.addProperty("rulesVersion",1);j.add("options",gson.toJsonTree(r.options));if(r.owner!=null)j.addProperty("owner",r.owner.toString());j.addProperty("sideTray",r.sideTray);if(r.anchorWorld!=null){j.addProperty("anchorWorld",r.anchorWorld.toString());j.addProperty("anchorX",r.anchorX);j.addProperty("anchorY",r.anchorY);j.addProperty("anchorZ",r.anchorZ);}j.addProperty("phase",r.phase.name());j.addProperty("completed",r.completed);j.addProperty("result",r.result);j.addProperty("revision",r.revision);j.add("seats",gson.toJsonTree(r.seats));j.add("history",r.history.deepCopy());array.add(j);}root.add("rooms",array);
        JsonObject backs=new JsonObject();returns.forEach((id,l)->{JsonObject v=new JsonObject();v.addProperty("world",l.getWorld().getName());v.addProperty("x",l.getX());v.addProperty("y",l.getY());v.addProperty("z",l.getZ());v.addProperty("yaw",l.getYaw());v.addProperty("pitch",l.getPitch());backs.add(id.toString(),v);});root.add("returns",backs);
        Path file=getDataFolder().toPath().resolve("rooms.json"),temp=file.resolveSibling("rooms.json.tmp");
        try{Files.writeString(temp,gson.toJson(root),StandardCharsets.UTF_8);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException ex){getLogger().warning("Could not save room records: "+ex.getClass().getSimpleName());}
    }
    void restore(){
        Path file=getDataFolder().toPath().resolve("rooms.json");if(!Files.exists(file))return;
        try{JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.getAsJsonObject("returns").entrySet()){JsonObject j=entry.getValue().getAsJsonObject();World w=Bukkit.getWorld(j.get("world").getAsString());if(w!=null)returns.put(UUID.fromString(entry.getKey()),new Location(w,j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble(),j.get("yaw").getAsFloat(),j.get("pitch").getAsFloat()));}
            for(JsonElement e:root.getAsJsonArray("rooms")){JsonObject j=e.getAsJsonObject();Room r=new Room(UUID.fromString(j.get("id").getAsString()),j.get("kind").getAsString(),j.get("capacity").getAsInt(),j.get("seed").getAsLong(),j.get("table").getAsInt(),Room.readOptions(j));r.sideTray=j.has("sideTray")&&j.get("sideTray").getAsBoolean();r.owner=j.has("owner")?UUID.fromString(j.get("owner").getAsString()):null;
                if(j.has("anchorWorld")){r.anchorWorld=UUID.fromString(j.get("anchorWorld").getAsString());r.anchorX=j.get("anchorX").getAsDouble();r.anchorY=j.get("anchorY").getAsDouble();r.anchorZ=j.get("anchorZ").getAsDouble();}
                for(JsonElement s:j.getAsJsonArray("seats")){Room.Seat seat=gson.fromJson(s,Room.Seat.class);r.seats.add(seat);if(!seat.bot()&&!coordinator.restoreReservation(seat.id(),r.kind))throw new IllegalStateException("恢复座位与其他游戏冲突");if(!seat.bot())r.offline.put(seat.id(),System.currentTimeMillis());}
                if(r.owner==null&&!r.seats.isEmpty())r.owner=r.seats.getFirst().id();
                r.history.addAll(j.getAsJsonArray("history"));r.revision=j.get("revision").getAsLong();r.completed=j.has("completed")&&j.get("completed").getAsBoolean();r.result=j.has("result")?j.get("result").getAsString():"";rooms.put(r.id,r);arena.platform(r.table);
                if(!j.get("phase").getAsString().equals("LOBBY")){r.restoring=true;start(r);}else if(r.board==null){r.board=r.newBoard();arena.render(r);}
            }
            getLogger().info("Restored "+rooms.size()+" board rooms.");
        }catch(Exception ex){try{Files.copy(file,file.resolveSibling("rooms.unreadable-"+System.currentTimeMillis()+".json"));}catch(IOException ignored){}throw new IllegalStateException("对局记录恢复失败，禁止覆盖原文件",ex);}
    }




}
