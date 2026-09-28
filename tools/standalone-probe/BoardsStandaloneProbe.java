package dev.tabletop3d.probe;

import dev.tabletop3d.internal.gson.*;
import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only plugin for an isolated, loopback Purpur server. */
public final class BoardsStandaloneProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::runProbe, 80);
    }

    private void runProbe() {
        try {
            require(Bukkit.getIp().equals("127.0.0.1") && Bukkit.getPort() == 25617, "loopback fixture");
            if (Boolean.getBoolean("boards.probe.migration")) {
                migrationProbe();
                return;
            }
            if (Boolean.getBoolean("boards.probe.menu")) {
                menuProbe();
                return;
            }
            if (Boolean.getBoolean("boards.probe.sgmenu")) {
                sgMenuProbe();
                return;
            }
            for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
                require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
            Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3dtabletop"));
            require(boards.isEnabled(), "Boards enabled");
            if (Boolean.getBoolean("boards.probe.snapshot")) {
                snapshotProbe(boards);
                return;
            }
            World world = Bukkit.getWorlds().getFirst();
            soundProbe(boards, world);
            modelProbe(boards, world);
            PluginCommand command = Objects.requireNonNull(Bukkit.getPluginCommand("3dtabletop:3dtabletop"));
            AtomicInteger dialogs = new AtomicInteger();
            Player player = player(world, dialogs, true);
            Player denied = player(world, dialogs, false);
            Collection<String> suggestions = new LinkedHashSet<>(List.of("3dtabletop", "3dtabletop:3dtabletop"));
            Bukkit.getPluginManager().callEvent(new PlayerCommandSendEvent(player, suggestions));
            require(suggestions.equals(Set.of("3dtabletop")), "duplicate root suggestion hidden");
            int before = dialogs.get();
            command.execute(denied, "3dtabletop", new String[0]);
            require(dialogs.get() == before, "permission denied");
            command.execute(player, "3dtabletop", new String[0]);
            require(dialogs.get() == before + 1, "native catalog opened");
            Object menus = field(boards, "menus");
            menuExperienceProbe(boards,menus,player);
            command.execute(player, "3dtabletop", new String[0]);
            Map<?, ?> sessions = (Map<?, ?>) field(menus, "sessions");
            Object session = sessions.get(player.getUniqueId());
            require(session != null, "menu session exists");
            Method buttons = session.getClass().getDeclaredMethod("buttons");
            buttons.setAccessible(true);
            List<?> entries = (List<?>) buttons.invoke(session);
            Method token = session.getClass().getDeclaredMethod("token");
            token.setAccessible(true);
            String action = "3dtabletop:" + token.invoke(session) + " 0";
            int shown = dialogs.get();
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "menu callback opened next native dialog");
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "callback consumed once");
            require(!entries.isEmpty(), "catalog entries");

            Map<?, ?> rooms = (Map<?, ?>) field(boards, "rooms");
            Path marker = getDataFolder().toPath().resolve("created-room.txt");
            if (Files.exists(marker)) {
                require(rooms.size() == 1, "one room restored");
                Object room = rooms.values().iterator().next();
                require(field(room, "anchorWorld").equals(world.getUID()), "world anchor restored");
                require(field(room, "board") != null, "board replay restored");
                require(Files.readString(boards.getDataFolder().toPath().resolve("rooms.json")).contains("drop:3"), "move history retained");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                String markerName = Files.exists(boards.getDataFolder().toPath().resolve("migration-from-serverboards.txt"))
                    ? "BOARDS_STANDALONE_MIGRATION_PASS" : "BOARDS_STANDALONE_RESTORE_PASS";
                getLogger().info(markerName + " rooms=1 dialogs=" + dialogs.get());
            } else {
                require(rooms.isEmpty(), "fresh fixture");
                call(boards, "create", new Class<?>[]{Player.class, String.class, int.class}, player, "connectfour", 2);
                require(rooms.size() == 1, "room created");
                Object room = rooms.values().iterator().next();
                call(boards, "startWithBots", new Class<?>[]{Player.class, room.getClass()}, player, room);
                call(menus,"room",new Class<?>[]{Player.class,room.getClass()},player,room);
                require(clickMenu(menus,player,"id","controls"),"room controls entry");
                require(clickMenu(menus,player,"label","Drop in column 4"),"direct drop menu callback");
                Path data = boards.getDataFolder().toPath().resolve("rooms.json");
                require(Files.readString(data).contains("drop:3"), "core rule action persisted");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(marker, String.valueOf(field(room, "id")));
                getLogger().info("BOARDS_STANDALONE_CREATE_PASS rooms=1 dialogs=" + dialogs.get());
            }
        } catch (Throwable ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "BOARDS_STANDALONE_PROBE_FAIL", ex);
        }
    }

    /** Resolve native sounds against the real server registry and exercise their dispatch. */
    private void soundProbe(Plugin plugin, World world) throws Exception {
        ClassLoader loader=plugin.getClass().getClassLoader();
        Class<?> sounds=Class.forName("dev.tabletop3d.TableSounds",true,loader);
        Class<?> cueType=Class.forName("dev.tabletop3d.TableSounds$Cue",true,loader);
        Method move=sounds.getDeclaredMethod("move",String.class,int.class,String.class,List.class,List.class);move.setAccessible(true);
        Method play=sounds.getDeclaredMethod("play",plugin.getClass(),Location.class,cueType);play.setAccessible(true);
        Method nativeSound=cueType.getDeclaredMethod("sound");nativeSound.setAccessible(true);
        Location at=new Location(world,8,84,0);int games=0,cues=0;
        for(String kind:List.of("chess","xiangqi","gomoku","checkers","draughts","reversi","go9","go13","go","connectfour","ludo","aeroplane")){
            BoardGame game=GameFactory.create(kind,2,1);var before=game.cells();String action=game.legalActions(0).getFirst();game.apply(0,action);
            Object cue=move.invoke(null,kind,0,action,before,game.cells());play.invoke(null,plugin,at,cue);games++;
        }
        for(Field field:sounds.getDeclaredFields())if(field.getType()==cueType){
            field.setAccessible(true);Object cue=field.get(null);org.bukkit.Sound sound=(org.bukkit.Sound)nativeSound.invoke(cue);
            require(org.bukkit.Registry.SOUNDS.get(sound.getKey())!=null,"native sound registered: "+field.getName());
            play.invoke(null,plugin,at,cue);cues++;
        }
        getLogger().info("BOARDS_SOUNDS_PASS games="+games+" cues="+cues+" client_audio_test=false");
    }

    /** Compare an imported synthetic snapshot against the real startup restore path. */
    private void snapshotProbe(Plugin plugin) throws Exception {
        JsonObject source=JsonParser.parseString(Files.readString(Path.of("multi-room-snapshot.json"))).getAsJsonObject();
        Map<?,?> restored=(Map<?,?>)field(plugin,"rooms");Set<String> kinds=new HashSet<>();int events=0;
        require(restored.size()==source.getAsJsonArray("rooms").size(),"all snapshot rooms restored");
        for(JsonElement element:source.getAsJsonArray("rooms")){
            JsonObject saved=element.getAsJsonObject();Object room=restored.get(UUID.fromString(saved.get("id").getAsString()));
            require(room!=null,"original room ID restored");String kind=saved.get("kind").getAsString();kinds.add(kind);
            require(kind.equals(field(room,"kind"))&&saved.get("capacity").getAsInt()==(int)field(room,"capacity"),"kind and capacity preserved");
            require(saved.get("seed").getAsLong()==(long)field(room,"seed"),"seed preserved");
            require(saved.get("anchorWorld").getAsString().equals(field(room,"anchorWorld").toString()),"world preserved");
            for(String key:List.of("anchorX","anchorY","anchorZ"))require(saved.get(key).getAsDouble()==(double)field(room,key),"position preserved");
            require(new Gson().toJsonTree(field(room,"seats")).equals(saved.get("seats")),"seats preserved");
            JsonArray history=(JsonArray)field(room,"history"),before=saved.getAsJsonArray("history");
            require(history.size()>=before.size(),"saved history retained");
            for(int i=0;i<before.size();i++)require(history.get(i).equals(before.get(i)),"saved event unchanged");
            BoardGame live=(BoardGame)field(room,"board"),copy=GameFactory.create(kind,saved.get("capacity").getAsInt(),saved.get("seed").getAsLong());
            for(JsonElement e:history){JsonObject action=e.getAsJsonObject();copy.apply(action.get("seat").getAsInt(),action.get("action").getAsString());}
            require(copy.cells().equals(live.cells())&&copy.currentPlayer()==live.currentPlayer()&&copy.finished()==live.finished()&&Objects.equals(copy.outcome(),live.outcome()),"restored rule state matches history");
            require(Set.of("PLAYING","FINISHED").contains(field(room,"phase").toString()),"restored active or finished phase");events+=before.size();
        }
        Set<String> expected=new HashSet<>(Set.of("chess","xiangqi","gomoku","aeroplane","checkers","draughts","reversi","go9","go13","go","connectfour"));
        if(source.getAsJsonArray("rooms").size()==12)expected.add("ludo");
        require(kinds.equals(expected),"all snapshot kinds including legacy flight");
        require(((Map<?,?>)field(field(plugin,"arena"),"views")).size()==restored.size(),"one model per restored room");
        getLogger().info("BOARDS_SNAPSHOT_RESTORE_PASS rooms="+restored.size()+" saved_events="+events+" kinds="+kinds.size());
    }

    /** Build and navigate real Dialog objects while keeping synthetic rooms out of saves. */
    @SuppressWarnings("unchecked")
    private void menuExperienceProbe(Plugin plugin,Object menus,Player player) throws Exception {
        Map<UUID,Object> rooms=(Map<UUID,Object>)field(plugin,"rooms");List<UUID> added=new ArrayList<>();
        Class<?> roomType=Class.forName("dev.tabletop3d.Room",true,plugin.getClass().getClassLoader());
        var constructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);constructor.setAccessible(true);
        try {
            for(int i=0;i<25;i++){UUID id=UUID.randomUUID();rooms.put(id,constructor.newInstance(id,"chess",2,1L,i));added.add(id);}
            call(menus,"games",new Class<?>[]{Player.class,String.class},player,"chess");Set<String> seen=new HashSet<>();
            for(int page=0;page<4;page++){
                Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
                List<?> buttons=(List<?>)call(session,"buttons",new Class<?>[0]);
                for(Object button:buttons){String id=(String)call(button,"id",new Class<?>[0]);if(id.startsWith("room-"))seen.add(id);}
                if(!clickMenu(menus,player,"id","next"))break;
            }
            require(seen.size()==25,"every room reachable through native pagination");
            require(clickMenu(menus,player,"id","rules"),"rules entry on final room page");
            require(clickMenu(menus,player,"id","back"),"rules return to room browser");
            getLogger().info("BOARDS_MENU_FLOW_PASS rooms=25 pagination=4 rules=reachable");
        } finally {added.forEach(rooms::remove);call(menus,"forget",new Class<?>[]{Player.class},player);}
    }
    private boolean clickMenu(Object menus,Player player,String property,String value) throws Exception {
        Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
        List<?> buttons=(List<?>)call(session,"buttons",new Class<?>[0]);
        for(int i=0;i<buttons.size();i++)if(value.equals(call(buttons.get(i),property,new Class<?>[0]))){
            call(menus,"handle",new Class<?>[]{Player.class,String.class},player,"3dtabletop:"+call(session,"token",new Class<?>[0])+" "+i);return true;
        }
        return false;
    }

    /** Exercise real Display transforms without adding a persisted room or a client. */
    private void modelProbe(Plugin plugin, World world) throws Exception {
        // Match GameWorld.platform: keep the model's chunks active before spawning Displays.
        for(int z=-1;z<=0;z++)world.getChunkAt(0,z).addPluginChunkTicket(this);
        ClassLoader loader=plugin.getClass().getClassLoader();
        Class<?> roomType=Class.forName("dev.tabletop3d.Room",true,loader);
        Class<?> factory=Class.forName("dev.tabletop3d.rules.GameFactory",true,loader);
        Class<?> gameType=Class.forName("dev.tabletop3d.rules.BoardGame",true,loader);
        Class<?> viewType=Class.forName("dev.tabletop3d.TableView",true,loader);
        var roomConstructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);
        roomConstructor.setAccessible(true);
        for(String kind:List.of("connectfour","reversi","go9","chess","ludo")) {
            Object room=roomConstructor.newInstance(UUID.randomUUID(),kind,2,1L,0);
            Object game=factory.getMethod("create",String.class,int.class,long.class).invoke(null,kind,2,1L);
            Field board=roomType.getDeclaredField("board");board.setAccessible(true);board.set(room,game);
            Player viewer=player(world,new AtomicInteger(),true);
            call(room,"join",new Class<?>[]{UUID.class,String.class},viewer.getUniqueId(),"ModelProbe");
            Field phase=roomType.getDeclaredField("phase");phase.setAccessible(true);
            @SuppressWarnings({"rawtypes","unchecked"}) Object playing=Enum.valueOf((Class)phase.getType(),"PLAYING");phase.set(room,playing);
            if(kind.equals("go9"))for(String action:List.of("place:0,0","place:8,8","place:1,0","pass","pass"))
                gameType.getMethod("apply",int.class,String.class).invoke(game,gameType.getMethod("currentPlayer").invoke(game),action);
            var constructor=viewType.getDeclaredConstructors()[0];constructor.setAccessible(true);
            Object view=constructor.newInstance(plugin,room,new Location(world,8,83,0),
                new org.bukkit.NamespacedKey("3dtabletop","probe-model"),field(field(plugin,"arena"),"maps"));
            try {
                if(kind.equals("connectfour")){
                    Class<?> pickType=Class.forName("dev.tabletop3d.GameWorld$Pick",true,loader);
                    call(view,"cursor",new Class<?>[]{Player.class,pickType,String.class},viewer,null,"3,5");
                    Object overlay=((Map<?,?>)field(view,"overlays")).get(viewer.getUniqueId());
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.BlockDisplay> preview=(List<org.bukkit.entity.BlockDisplay>)field(overlay,"hover");
                    require(preview.size()==4,"four landing markers");
                    for(var marker:preview)require(marker.isValid()&&!marker.isVisibleByDefault(),"private live landing marker");
                    call(view,"clear",new Class<?>[]{Player.class},viewer);
                    for(var marker:preview)require(!marker.isValid(),"landing markers removed");
                }
                Map<?,?> previous=new HashMap<>((Map<?,?>)field(view,"tokens"));
                @SuppressWarnings("unchecked") List<String> legal=(List<String>)gameType.getMethod("legalActions",int.class).invoke(game,0);
                gameType.getMethod("apply",int.class,String.class).invoke(game,0,kind.equals("connectfour")?"drop:3":kind.equals("go9")?"dead:0,0":legal.getFirst());
                Field revision=roomType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
                call(view,"sync",new Class<?>[0]);
                Map<?,?> current=(Map<?,?>)field(view,"tokens");
                require(!current.isEmpty(),"model pieces rendered after the action");
                if(kind.equals("connectfour"))require(current.size()==1,"one dropped chip rendered");
                if(kind.equals("reversi")||kind.equals("go9"))for(var entry:previous.entrySet())
                    require(current.get(entry.getKey())==entry.getValue(),kind+" reuses existing tokens");
                for(int i=0;i<16;i++)call(view,"tick",new Class<?>[0]);
                for(Object token:current.values()) {
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(token,"parts");
                    require(parts.size()>=3,"multi-part piece");
                    Location target=(Location)field(token,"to");
                    for(var part:parts) {
                        require(part.isValid() && !part.isPersistent(),"live non-persistent model part: "+kind+" valid="+part.isValid()+" persistent="+part.isPersistent()+" dead="+part.isDead());
                        Location expected=target.clone();if(kind.equals("ludo")&&part instanceof TextDisplay)expected.add(0,((Number)field(field(view,"geometry"),"spacing")).doubleValue()*.82,0);
                        require(part.getLocation().distanceSquared(expected)<1e-8,"animation settled");
                        String transform=((org.bukkit.entity.Display)part).getTransformation().toString();
                        require(!transform.contains("NaN")&&!transform.contains("Infinity"),"finite server transformation");
                    }
                }
                if(kind.equals("go9")){
                    Object stone=current.get("0,0");
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(stone,"parts");
                    require(parts.size()==5,"Go stone plus two dead marks");
                    List<org.bukkit.entity.Entity> marks=List.copyOf(parts.subList(3,5));
                    gameType.getMethod("apply",int.class,String.class).invoke(game,0,"dead:0,0");revision.setLong(room,revision.getLong(room)+1);call(view,"sync",new Class<?>[0]);
                    require(current.get("0,0")==stone&&parts.size()==3,"Go unmark keeps stone");
                    for(var mark:marks)require(!mark.isValid(),"Go dead mark removed");
                }
                if(kind.equals("chess"))for(String id:List.of("b1","b8")){
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.Entity> parts=(List<org.bukkit.entity.Entity>)field(current.get(id),"parts");
                    getLogger().info("BOARDS_KNIGHT_HEADING "+id+" yaw="+parts.getFirst().getLocation().getYaw());
                    for(var part:parts)require(Math.abs(Math.IEEEremainder(part.getLocation().getYaw()-(id.equals("b1")?180:0),360))<.001,"knight faces opponent: "+id+" yaw="+part.getLocation().getYaw());
                }
                if(kind.equals("ludo")){
                    Class<?> pickType=Class.forName("dev.tabletop3d.GameWorld$Pick",true,loader);
                    call(view,"cursor",new Class<?>[]{Player.class,pickType,String.class},viewer,null,"sk0");
                    Object overlay=((Map<?,?>)field(view,"overlays")).get(viewer.getUniqueId());
                    @SuppressWarnings("unchecked") List<org.bukkit.entity.BlockDisplay> hints=(List<org.bukkit.entity.BlockDisplay>)field(overlay,"hover");
                    require(hints.size()==4,"Ludo previews the pawn destination");
                    for(var hint:hints)require(hint.isValid()&&!hint.isVisibleByDefault(),"private live Ludo preview");
                    call(view,"clear",new Class<?>[]{Player.class},viewer);for(var hint:hints)require(!hint.isValid(),"Ludo preview removed");
                    for(var entry:previous.entrySet())require(current.get(entry.getKey())==entry.getValue(),"Ludo roll retains pawn entities");
                }
            } finally {call(view,"close",new Class<?>[0]);}
        }
        world.removePluginChunkTickets(this);
        getLogger().info("BOARDS_MODELS_PASS connectfour=drop-and-private-preview reversi=reuse-and-flip go=dead-marker-reuse chess=knight-heading ludo=pawns-and-private-preview");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sgMenuProbe() throws Exception {
        Plugin games = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerGames"));
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Tabletop3D"));
        require(games.isEnabled() && boards.isEnabled(), "optional plugins enabled");
        ClassLoader loader = games.getClass().getClassLoader();
        Class<?> api = Class.forName("dev.server.games.api.GameCoordinator", true, loader);
        Object service = Bukkit.getServicesManager().load((Class) api);
        require(service != null, "optional coordinator exists");
        Collection<?> providers = (Collection<?>) api.getMethod("providers").invoke(service);
        Class<?> providerType = Class.forName("dev.server.games.api.GameProvider", true, loader);
        Set<String> kinds = new HashSet<>();
        for (Object provider : providers) kinds.add((String) providerType.getMethod("id").invoke(provider));
        require(Collections.disjoint(kinds, List.of("connectfour", "chess", "xiangqi", "go9")), "Boards has no ServerGames providers");
        UUID playerId = UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420");
        require(((Map<?, ?>) field(field(boards, "coordinator"), "seats")).containsKey(playerId), "restored seat owned by Boards");
        Optional<?> occupied = (Optional<?>) api.getMethod("occupiedBy", UUID.class).invoke(service, playerId);
        require(occupied.isEmpty(), "ServerGames does not own Boards seat");
        AtomicInteger dialogs = new AtomicInteger();
        Player player = player(Bukkit.getWorlds().getFirst(), dialogs, true);
        Objects.requireNonNull(Bukkit.getPluginCommand("servergames:servergames"))
            .execute(player, "sg", new String[]{"menu"});
        require(dialogs.get() == 1, "/sg menu opens Boards Dialog");
        getLogger().info("BOARDS_SG_MENU_PASS providers=" + kinds.size() + " dialogs=" + dialogs.get());
    }

    @SuppressWarnings("unchecked")
    private void menuProbe() throws Exception {
        Plugin menu = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerMenu"));
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Tabletop3D"));
        require(menu.isEnabled() && boards.isEnabled(), "optional menu enabled");
        AtomicInteger dialogs = new AtomicInteger();
        Player player = player(Bukkit.getWorlds().getFirst(), dialogs, true);
        ClassLoader loader = menu.getClass().getClassLoader();
        Class<?> view = Class.forName("dev.server.menu.DialogView", true, loader);
        Class<?> daily = Class.forName("dev.server.menu.DailyMenus", true, loader);
        var constructor = daily.getDeclaredConstructor(menu.getClass(), view);
        constructor.setAccessible(true);
        Object model = constructor.newInstance(menu, field(menu, "dialogs"));
        Object page = call(model, "build", new Class<?>[]{Player.class, String.class, int.class}, player, "main", 0);
        List<?> entries = (List<?>) call(page, "entries", new Class<?>[0]);
        int before = dialogs.get();
        boolean found = false;
        for (Object entry : entries) {
            if (!"games".equals(call(entry, "id", new Class<?>[0]))) continue;
            ((java.util.function.Consumer<Map<String, String>>) call(entry, "action", new Class<?>[0])).accept(Map.of());
            found = true;
            break;
        }
        require(found && dialogs.get() == before + 1, "ServerMenu games entry opens Boards Dialog");
        getLogger().info("BOARDS_MENU_PASS dialogs=" + dialogs.get());
    }

    private void migrationProbe() throws Exception {
        for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
            require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Tabletop3D"));
        require(boards.isEnabled(), "Boards restored legacy rooms");
        Map<?, ?> rooms = (Map<?, ?>) field(boards, "rooms");
        require(rooms.size() == 4, "four replayable legacy chess rooms restored");
        Set<String> kinds = new HashSet<>();
        int history = 0;
        for (Object room : rooms.values()) {
            kinds.add((String) field(room, "kind"));
            require(field(room, "board") != null, "rules replayed");
            require(field(room, "anchorWorld").equals(Bukkit.getWorlds().getFirst().getUID()), "explicit anchor used");
            Object moves = field(room, "history");
            history += (int) moves.getClass().getMethod("size").invoke(moves);
        }
        require(kinds.containsAll(List.of("gomoku", "xiangqi", "chess", "checkers")), "legacy kinds");
        require(history >= 21, "legacy history kept: " + history);
        require(!Bukkit.getWorlds().getFirst().getEntitiesByClass(TextDisplay.class).isEmpty(), "legacy boards rendered");
        getLogger().info("BOARDS_MIGRATION_PASS rooms=4 history=" + history);
    }

    private Player player(World world, AtomicInteger dialogs, boolean allowed) {
        UUID id = allowed ? UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420") : UUID.randomUUID();
        Location location = new Location(world, 0.5, 83, 0.5);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "BoardsProbe";
                case "getWorld" -> world;
                case "getLocation", "getEyeLocation" -> location.clone();
                case "getServer" -> getServer();
                case "isOnline", "isValid", "isPermissionSet" -> true;
                case "isDead", "isOp" -> false;
                case "hasPermission" -> allowed;
                case "teleport" -> true;
                case "showDialog" -> { require(args[0] != null, "native dialog built"); dialogs.incrementAndGet(); yield null; }
                case "sendMessage", "sendActionBar" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "BoardsProbe";
                default -> null;
            });
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
