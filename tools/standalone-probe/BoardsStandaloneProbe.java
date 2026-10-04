package dev.tabletop3d.probe;
import static dev.tabletop3d.ui.MessageText.plain;

import dev.tabletop3d.internal.gson.*;
import dev.tabletop3d.rules.BoardGame;
import dev.tabletop3d.rules.GameFactory;
import dev.tabletop3d.rules.HandGame;
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
            for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
                require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
            Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("3dtabletop"));
            require(boards.isEnabled(), "Boards enabled");
            if (Boolean.getBoolean("boards.probe.snapshot")) {
                snapshotProbe(boards);
                return;
            }
            World world = Bukkit.getWorlds().getFirst();
            if(Boolean.getBoolean("boards.probe.craftengine")){craftEngineProbe(boards,world);}
            soundProbe(boards, world);
            modelProbe(boards, world);
            focusProbe(boards, world);
            handModelProbe(boards, world);
            playingCardProbe(boards, world);
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
            pokerRaiseProbe(boards, menus, world);
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
                String markerName = "BOARDS_STANDALONE_RESTORE_PASS";
                getLogger().info(markerName + " rooms=1 dialogs=" + dialogs.get());
            } else {
                require(rooms.isEmpty(), "fresh fixture");
                call(boards, "create", new Class<?>[]{Player.class, String.class, int.class}, player, "connectfour", 2);
                require(rooms.size() == 1, "room created");
                Object room = rooms.values().iterator().next();
                call(boards, "startWithBots", new Class<?>[]{Player.class, room.getClass()}, player, room);
                call(menus,"room",new Class<?>[]{Player.class,room.getClass()},player,room);
                require(!clickMenu(menus,player,"id","controls"),"room omits the full move selector");
                call(field(boards,"arena"),"pickCell",new Class<?>[]{Player.class,room.getClass(),int.class,String.class},player,room,0,"3,0");
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

    /** Uses the real event bus and world with a stateful player proxy, not a connected player. */
    @SuppressWarnings("unchecked")
    private void focusProbe(Plugin plugin,World world) throws Exception {
        Class<?> roomType=Class.forName("dev.tabletop3d.Room",true,plugin.getClass().getClassLoader());
        var constructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);constructor.setAccessible(true);
        UUID id=UUID.randomUUID(),roomId=UUID.randomUUID();Object room=constructor.newInstance(roomId,"mahjong",4,1L,999);
        Object arena=field(plugin,"arena"),comfort=field(plugin,"comfort");Map<UUID,Object> rooms=(Map<UUID,Object>)field(plugin,"rooms");
        call(room,"join",new Class<?>[]{UUID.class,String.class},id,"FocusProbe");
        call(arena,"anchor",new Class<?>[]{roomType,Location.class},room,new Location(world,32,83,0));rooms.put(roomId,room);
        Location original=new Location(world,32,83,2.25,33,21);
        var location=new java.util.concurrent.atomic.AtomicReference<>(original.clone());
        boolean[] gravity={true},collision={true},cancelReturn={false},invisible={false};
        var mode=new java.util.concurrent.atomic.AtomicReference<>(org.bukkit.GameMode.SURVIVAL);
        var camera=new java.util.concurrent.atomic.AtomicReference<org.bukkit.entity.Entity>();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()){
            case "getUniqueId"->id;case "getName"->"FocusProbe";case "getWorld"->location.get().getWorld();
            case "getLocation"->location.get().clone();case "getY"->location.get().getY();case "getGameMode"->mode.get();
            case "setGameMode"->{mode.set((org.bukkit.GameMode)args[0]);yield null;}
            case "getAllowFlight","isFlying"->false;
            case "getPersistentDataContainer"->world.getPersistentDataContainer();
            case "getSpectatorTarget"->camera.get();
            case "setSpectatorTarget"->{camera.set((org.bukkit.entity.Entity)args[0]);yield null;}
            case "isOnline","isValid","hasPermission","isPermissionSet"->true;
            case "isDead","isInsideVehicle","isOp"->false;
            case "hasGravity"->gravity[0];case "setGravity"->{gravity[0]=(boolean)args[0];yield null;}
            case "isCollidable"->collision[0];case "setCollidable"->{collision[0]=(boolean)args[0];yield null;}
            case "isInvisible"->invisible[0];case "setInvisible"->{invisible[0]=(boolean)args[0];yield null;}
            case "teleport"->{
                Location to=((Location)args[0]).clone();
                var event=new org.bukkit.event.player.PlayerTeleportEvent((Player)proxy,location.get().clone(),to,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                if(cancelReturn[0]&&to.equals(original))event.setCancelled(true);
                Bukkit.getPluginManager().callEvent(event);if(!event.isCancelled())location.set(event.getTo().clone());yield !event.isCancelled();
            }
            case "hashCode"->id.hashCode();case "equals"->proxy==args[0];case "toString"->"FocusProbe";
            default->null;
        });
        java.util.function.Consumer<Boolean> input=held->{
            org.bukkit.Input keys=(org.bukkit.Input)Proxy.newProxyInstance(org.bukkit.Input.class.getClassLoader(),new Class<?>[]{org.bukkit.Input.class},
                (proxy,method,args)->method.getName().equals("isSprint")&&held);
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInputEvent(player,keys));
        };
        try {
            input.accept(true);require((boolean)call(comfort,"focused",new Class<?>[]{Player.class},player),"real event registration enters focus");
            require(!gravity[0]&&!collision[0]&&invisible[0]&&Math.abs(location.get().getX()-32)<.0001&&Math.abs(location.get().getY()-85.15)<.0001&&Math.abs(location.get().getZ()-.65)<.0001,"focus owns gravity, collision and invisibility at close position");
            require(mode.get()==org.bukkit.GameMode.SPECTATOR&&camera.get() instanceof org.bukkit.entity.ArmorStand stand&&stand.isMarker()&&!stand.isVisible()&&!stand.isVisibleByDefault()&&stand.getEyeHeight()==0,"fixed private marker camera replaces the player and equipment view");
            org.bukkit.entity.Entity target=camera.get();
            Location anchor=location.get().clone(),attempt=anchor.clone().add(3,1,3);attempt.setYaw(70);
            var move=new org.bukkit.event.player.PlayerMoveEvent(player,anchor,attempt);Bukkit.getPluginManager().callEvent(move);
            getLogger().info("BOARDS_FOCUS_CAMERA cancelled="+move.isCancelled()+" anchored="+location.get().equals(anchor)+" anchor_yaw="+anchor.getYaw()+" camera_yaw="+target.getLocation().getYaw()+" anchor_pitch="+anchor.getPitch()+" camera_pitch="+target.getLocation().getPitch());
            require(move.isCancelled()&&location.get().equals(anchor)&&Math.abs(Math.IEEEremainder(target.getLocation().getYaw()-anchor.getYaw(),360))<.0001&&Math.abs(target.getLocation().getPitch()-anchor.getPitch())<.0001,"real event pipeline cancels movement and aim without a correction plugin teleport");
            var remote=new org.bukkit.event.player.PlayerTeleportEvent(player,anchor,anchor.clone().add(100,0,100),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE);
            Bukkit.getPluginManager().callEvent(remote);require(remote.isCancelled()&&(boolean)call(comfort,"focused",new Class<?>[]{Player.class},player),"spectator menu cannot grant remote teleport");
            var detach=new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(player,target);Bukkit.getPluginManager().callEvent(detach);
            require(detach.isCancelled(),"held sprint cannot detach the camera");
            input.accept(false);require(location.get().equals(original)&&gravity[0]&&!invisible[0]&&mode.get()==org.bukkit.GameMode.SURVIVAL&&camera.get()==null&&!target.isValid(),"input release restores mode, pose, gravity, visibility and removes camera");
            input.accept(true);Location external=new Location(world,50,83,0);player.teleport(external);input.accept(true);input.accept(false);
            require(location.get().equals(external)&&gravity[0]&&collision[0],"external teleport wins without focus reentry");
            location.set(original.clone());input.accept(true);cancelReturn[0]=true;input.accept(false);
            require(!(boolean)call(comfort,"focused",new Class<?>[]{Player.class},player)&&gravity[0]&&!invisible[0]&&Math.abs(location.get().getY()-85.15)<.0001,"cancelled return clears focus flags without overriding cancellation");
            getLogger().info("BOARDS_FOCUS_EVENTS_PASS proxy_player=true real_event_bus=true real_world_clearance=true native_player_physics=false client_visual_test=false");
        } finally {
            cancelReturn[0]=false;input.accept(false);call(comfort,"release",new Class<?>[]{Player.class},player);rooms.remove(roomId);
            call(comfort,"sync",new Class<?>[]{Player.class},player);
        }
    }

    /** Real Paper visibility metadata: no connected client or screenshot is implied. */
    private void handModelProbe(Plugin plugin,World world) throws Exception {
        ClassLoader loader=plugin.getClass().getClassLoader();Class<?> roomType=Class.forName("dev.tabletop3d.Room",true,loader);
        Class<?> viewType=Class.forName("dev.tabletop3d.TableView",true,loader),pickType=Class.forName("dev.tabletop3d.GameWorld$Pick",true,loader);
        var roomConstructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class,Map.class);roomConstructor.setAccessible(true);
        var constructor=viewType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        for(String kind:List.of("color-eight","mahjong")){
            int capacity=kind.equals("mahjong")?4:5;Map<String,String> options=kind.equals("mahjong")?Map.of("profile","taiwan"):Map.of();
            Object room=roomConstructor.newInstance(UUID.randomUUID(),kind,capacity,1L,0,options);
            Field board=roomType.getDeclaredField("board");board.setAccessible(true);board.set(room,GameFactory.create(kind,capacity,1L,options));
            Player owner=player(world,new AtomicInteger(),true),spectator=player(world,new AtomicInteger(),true,UUID.randomUUID());
            call(room,"join",new Class<?>[]{UUID.class,String.class},owner.getUniqueId(),"PrivateHandProbe");call(room,"fillBots",new Class<?>[0]);
            Field phase=roomType.getDeclaredField("phase");phase.setAccessible(true);
            @SuppressWarnings({"rawtypes","unchecked"}) Object playing=Enum.valueOf((Class)phase.getType(),"PLAYING");phase.set(room,playing);
            Object view=constructor.newInstance(plugin,room,new Location(world,8,83,0),new org.bukkit.NamespacedKey("3dtabletop","probe-hand"),field(field(plugin,"arena"),"maps"));
            List<org.bukkit.entity.Entity> privateParts=new ArrayList<>();
            try {
                Object hand=field(view,"handTable");Map<?,?> privateViews=(Map<?,?>)field(hand,"privateViews");
                call(view,"cursor",new Class<?>[]{Player.class,pickType,String.class},spectator,null,null);
                require(privateViews.isEmpty(),"spectator does not spawn private faces");
                call(view,"cursor",new Class<?>[]{Player.class,pickType,String.class},owner,null,null);
                require(privateViews.size()==1,"only requesting owner has a private view");
                Object own=privateViews.get(owner.getUniqueId());
                require(((Map<?,?>)field(own,"pieces")).size()==((HandGame)board.get(room)).handSize(0),"every own tile has a model");
                for(Object piece:((Map<?,?>)field(own,"pieces")).values())for(Object item:(List<?>)field(piece,"parts")){
                    var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                    require(part.isValid()&&!part.isPersistent()&&!part.isVisibleByDefault(),"private face is live, temporary and hidden by default");
                }
                if(kind.equals("color-eight")){
                    var pieces=(Map<?,?>)field(own,"pieces");Object first=pieces.values().iterator().next();
                    for(Object piece:pieces.values()) if(!((Boolean)field(piece,"packed"))) {
                        var parts=(List<?>)field(piece,"parts");
                        require(parts.size()==12,"Color Eight native face uses twelve heads");
                        for(Object part:parts) {
                            var head=(org.bukkit.entity.ItemDisplay)part;
                            require(head.getItemStack().getType()==org.bukkit.Material.PLAYER_HEAD,"Color Eight signed head material");
                            var meta=(org.bukkit.inventory.meta.SkullMeta)head.getItemStack().getItemMeta();
                            require(meta.getPlayerProfile().getProperties().stream().anyMatch(p->p.getName().equals("textures")&&p.getSignature()!=null),"Color Eight real signed texture");
                        }
                    }
                    for(Object piece:((Map<?,?>)field(hand,"publicPieces")).values())
                        if((Boolean)field(field(piece,"spec"),"back")) {
                            var parts=(List<?>)field(piece,"parts");
                            require(parts.size()==2&&parts.stream().allMatch(org.bukkit.entity.BlockDisplay.class::isInstance),"Color Eight public back uses two blocks");
                        }
                    var body=(org.bukkit.entity.Entity)((List<?>)field(first,"parts")).getFirst();double y=body.getLocation().getY();
                    String id=String.valueOf(pieces.keySet().iterator().next());
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,id);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(Math.abs(body.getLocation().getY()-y-.085)<.0001,"live native private card has a small hover lift");
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,null);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(Math.abs(body.getLocation().getY()-y)<.0001,"hover exit restores the same card entity");
                    Object ring=field(hand,"turnRing");List<?> segments=(List<?>)field(ring,"parts");
                    require(segments.size()==24,"native curved turn ring");
                    var segment=(org.bukkit.entity.Entity)call(segments.getFirst(),"entity",new Class<?>[0]);
                    Location before=segment.getLocation();
                    for(int tick=0;tick<10;tick++)call(hand,"tick",new Class<?>[0]);
                    require(segment.getLocation().distanceSquared(before)>.001,"native turn ring rotates");
                }else{
                    var pieces=(Map<?,?>)field(own,"pieces");Object first=pieces.values().iterator().next();
                    var body=(org.bukkit.entity.Entity)((List<?>)field(first,"parts")).getFirst();Location before=body.getLocation();
                    String id=String.valueOf(pieces.keySet().iterator().next());
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,id);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    require(body.isGlowing()&&Math.abs(body.getLocation().getY()-before.getY()-.065)<.0001
                        &&body.getLocation().getX()==before.getX()&&body.getLocation().getZ()==before.getZ(),"mahjong selected tile lifts without horizontal spreading");
                    var remaining=(org.bukkit.entity.TextDisplay)field(own,"remaining");privateParts.add(remaining);
                    require(!remaining.isVisibleByDefault()&&plain(remaining.text()).startsWith("Remaining: "),"native remaining label is owner-only");
                    Object hud=field(hand,"mahjongHud");
                    require(((List<?>)field(hud,"entities")).size()==28,"native Mahjong HUD has bounded entity inventory");
                    var count=(org.bukkit.entity.TextDisplay)((List<?>)field(hud,"counts")).getFirst();
                    require(plain(count.text()).equals(Integer.toString(((HandGame)board.get(room)).deckSize())),"native tabletop count matches drawable wall");
                    call(hand,"hover",new Class<?>[]{Player.class,String.class},owner,null);
                    for(int tick=0;tick<4;tick++)call(hand,"tick",new Class<?>[0]);
                    // Native teleport normalizes -0.0 yaw to +0.0; compare numeric pose, not Location.equals bits.
                    Location restored=body.getLocation();
                    require(!body.isGlowing()&&restored.distanceSquared(before)<1e-10
                        &&restored.getYaw()==before.getYaw()&&restored.getPitch()==before.getPitch()&&plain(remaining.text()).isEmpty(),
                        "mahjong hover restores position and clears hint: glowing="+body.isGlowing()+" before="+before+" after="+body.getLocation()+" hint="+plain(remaining.text()));
                    BoardGame game=(BoardGame)board.get(room);boolean offered=false;
                    for(int step=0;step<800&&!game.finished();step++){
                        int seat=game.currentPlayer();List<String> legal=game.legalActions(seat);
                        if(seat==0&&legal.stream().anyMatch(a->a.startsWith("chi:")||a.startsWith("pon:")||a.startsWith("kan-"))){offered=true;break;}
                        require(!legal.isEmpty(),"mahjong fixture can advance");game.apply(seat,legal.getFirst());
                    }
                    require(offered,"real Mahjong game reaches an owner call");
                    Field revision=roomType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
                    call(hand,"show",new Class<?>[]{Player.class},owner);
                    Map<?,?> calls=(Map<?,?>)field(own,"calls");require(!calls.isEmpty(),"available native Mahjong buttons");
                    for(Object button:calls.values())require(((List<?>)field(button,"parts")).size()==2,"first-stage call buttons do not expose a combination yet");
                    String group=calls.keySet().stream().map(String::valueOf).filter(key->List.of("chi","pon","kan").contains(key)).findFirst().orElseThrow();
                    require((boolean)call(hand,"expandCall",new Class<?>[]{Player.class,String.class},owner,group),"selecting a call opens its combinations");
                    boolean previews=false;for(Object button:calls.values())if(((List<?>)field(button,"parts")).size()>2)previews=true;
                    require(previews,"second-stage call buttons include native tile-face combination previews");
                    for(Object button:calls.values())for(Object item:(List<?>)field(button,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isPersistent()&&!part.isVisibleByDefault(),"Mahjong button is private and temporary");
                    }
                    HandGame riichi=null;
                    for(long seed=0;seed<50;seed++){
                        HandGame candidate=(HandGame)GameFactory.create("mahjong",4,seed,Map.of("profile","riichi"));
                        int indicator=dev.tabletop3d.rules.mahjong.Tiles.type(candidate.publicInfo().get("dora"));
                        int dora=dev.tabletop3d.rules.mahjong.Tiles.next(indicator);
                        if(candidate.hand(0).stream().anyMatch(tile->tile.face().endsWith("0")||dev.tabletop3d.rules.mahjong.Tiles.type(tile.face())==dora)){riichi=candidate;break;}
                    }
                    require(riichi!=null,"Riichi fixture has an owner bonus tile");board.set(room,riichi);
                    revision.setLong(room,revision.getLong(room)+1);call(hand,"sync",new Class<?>[0]);call(hand,"show",new Class<?>[]{Player.class},owner);
                    int glints=0;
                    for(Object piece:((Map<?,?>)field(own,"pieces")).values())for(Object item:(List<?>)field(piece,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(!part.isVisibleByDefault(),"Riichi bonus face remains owner-only");
                        if(part instanceof org.bukkit.entity.ItemDisplay display){
                            require(Boolean.TRUE.equals(display.getItemStack().getItemMeta().getEnchantmentGlintOverride()),"real ItemDisplay preserves native enchanted glint override");
                            require(display.getItemStack().getType()==org.bukkit.Material.SMOOTH_QUARTZ,"glint uses an opaque tile face substrate");glints++;
                        }
                    }
                    require(glints>0,"live owner bonus tile has an ItemDisplay glint");
                    var indicators=(Map<?,?>)field(own,"indicators");require(indicators.size()==5,"owner gets five inset Dora slots");
                    long faces=0;for(Object tile:indicators.values())if(!(boolean)field(field(tile,"spec"),"back"))faces++;
                    require(faces==1,"only the initial Dora indicator is revealed");
                    var assists=(Map<?,?>)field(own,"assistanceButtons");require(assists.size()==4,"owner gets four Riichi assistance controls");
                    for(Object button:assists.values())for(Object item:(List<?>)field(button,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isVisibleByDefault(),"assistance control is private");
                    }
                    var counters=(org.bukkit.entity.Entity)field(own,"counters");privateParts.add(counters);
                    require(counters.isValid()&&!counters.isVisibleByDefault(),"Dora counters are private");
                    require(((Map<?,?>)field(hand,"publicPieces")).keySet().stream().noneMatch(key->key.toString().startsWith("dora:")),"Dora indicator models are not public");
                    for(Object piece:indicators.values())for(Object item:(List<?>)field(piece,"parts")){
                        var part=(org.bukkit.entity.Entity)item;privateParts.add(part);
                        require(part.isValid()&&!part.isVisibleByDefault()&&!part.isPersistent(),"inset Dora indicator is private and temporary");
                    }
                    call(hand,"show",new Class<?>[]{Player.class},spectator);require(privateViews.size()==1,"spectator cannot request Dora indicators");
                    getLogger().info("BOARDS_DORA_GLINT_PASS owner_only=true private_indicators=true opaque_foil=true native_item_metadata=true client_visual_test=false");
                }
                call(view,"clear",new Class<?>[]{Player.class},owner);
                require(privateViews.isEmpty()&&privateParts.stream().noneMatch(org.bukkit.entity.Entity::isValid),"private hand removed when view ends");
            } finally {call(view,"close",new Class<?>[0]);}
        }
        getLogger().info("BOARDS_HAND_MODELS_PASS games=color-eight,taiwan,riichi private_by_default=true spectator_faces=0 client_visual_test=false");
    }

    /** Exercise new private hands and real ray targets without persisting synthetic rooms. */
    private void playingCardProbe(Plugin plugin, World world) throws Exception {
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> roomType = Class.forName("dev.tabletop3d.Room", true, loader);
        Class<?> viewType = Class.forName("dev.tabletop3d.TableView", true, loader);
        var roomConstructor = roomType.getDeclaredConstructor(UUID.class, String.class, int.class, long.class, int.class);
        roomConstructor.setAccessible(true);
        var viewConstructor = viewType.getDeclaredConstructors()[0];
        viewConstructor.setAccessible(true);
        for (String kind : List.of("doudizhu", "liars-bar", "texas-holdem")) {
            int capacity = kind.equals("doudizhu") ? 3 : kind.equals("liars-bar") ? 4 : 6;
            Object room = roomConstructor.newInstance(UUID.randomUUID(), kind, capacity, 7L, 0);
            Player owner = player(world, new AtomicInteger(), true);
            Player spectator = player(world, new AtomicInteger(), true, UUID.randomUUID());
            call(room, "join", new Class<?>[]{UUID.class, String.class}, owner.getUniqueId(), "CardProbe");
            call(room, "fillBots", new Class<?>[0]);
            Field board = roomType.getDeclaredField("board"); board.setAccessible(true);
            board.set(room, GameFactory.create(kind, capacity, 7L));
            Field phase = roomType.getDeclaredField("phase"); phase.setAccessible(true);
            @SuppressWarnings({"rawtypes", "unchecked"}) Object playing = Enum.valueOf((Class)phase.getType(), "PLAYING");
            phase.set(room, playing);
            Object view = viewConstructor.newInstance(plugin, room, new Location(world, 8, 83, 0),
                new org.bukkit.NamespacedKey("3dtabletop", "probe-cards"), field(field(plugin, "arena"), "maps"));
            try {
                Object table = field(view, "playingTable");
                call(table, "show", new Class<?>[]{Player.class}, spectator);
                Map<?, ?> views = (Map<?, ?>)field(table, "privateViews");
                require(views.isEmpty(), "new-game spectator cannot request faces");
                call(table, "show", new Class<?>[]{Player.class}, owner);
                require(views.size() == 1, "new-game owner private view");
                Object own = views.get(owner.getUniqueId());
                Map<?, ?> cards = (Map<?, ?>)field(own, "cards");
                require(cards.size() == ((HandGame)board.get(room)).handSize(0), "new-game complete own hand");
                List<org.bukkit.entity.Entity> privateParts = new ArrayList<>();
                for (Object card : cards.values()) {
                    List<?> parts = (List<?>)field(card, "parts");
                    boolean nativeHead = parts.getFirst() instanceof org.bukkit.entity.ItemDisplay item
                            && item.getItemStack().getType() == org.bukkit.Material.PLAYER_HEAD;
                    require(parts.size() == (nativeHead ? 24 : 1), "new-game card display budget");
                    for (Object entity : parts) {
                        var part = (org.bukkit.entity.Entity)entity;
                        require(part.isValid() && !part.isPersistent() && !part.isVisibleByDefault(), "new-game face private at spawn");
                        if (nativeHead) {
                            var head = (org.bukkit.entity.ItemDisplay)part;
                            require(head.getItemDisplayTransform() == org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE,
                                    "native head uses the verified NONE transform");
                            var meta = (org.bukkit.inventory.meta.SkullMeta)head.getItemStack().getItemMeta();
                            require(meta.getPlayerProfile().getProperties().stream().anyMatch(property ->
                                    property.getName().equals("textures") && property.getSignature() != null),
                                    "native head retains the signed Mojang property");
                        }
                        privateParts.add(part);
                    }
                }
                Object first = cards.values().iterator().next();
                var body = (org.bukkit.entity.Entity)((List<?>)field(first, "parts")).getFirst();
                Location eye = body.getLocation().add(0, .125, 1.5);
                String hit = (String)call(table, "handHit", new Class<?>[]{Player.class, Location.class, org.bukkit.util.Vector.class}, owner, eye, new org.bukkit.util.Vector(0, 0, -1));
                require(String.valueOf(cards.keySet().iterator().next()).equals(hit), "new-game upright card ray target");
                call(view, "clear", new Class<?>[]{Player.class}, owner);
                require(views.isEmpty() && privateParts.stream().noneMatch(org.bukkit.entity.Entity::isValid), "new-game private displays removed");
            } finally { call(view, "close", new Class<?>[0]); }
        }
        getLogger().info("BOARDS_NEW_CARDS_PASS games=doudizhu,liars-bar,texas-holdem private_by_default=true ray_targets=true client_visual_test=false");
    }

    private void craftEngineProbe(Plugin plugin,World world)throws Exception{
        world.getChunkAt(0,0).addPluginChunkTicket(this);
        Plugin ce=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("CraftEngine"));
        require(ce.isEnabled(),"CraftEngine enabled");
        Class<?> type=Class.forName("dev.tabletop3d.CraftEngineModels",true,plugin.getClass().getClassLoader());
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object models=ctor.newInstance();
        require((boolean)call(models,"ready",new Class<?>[0]),"all CE models registered and buildable");
        Field ids=type.getDeclaredField("IDS");ids.setAccessible(true);int count=0;
        for(Object id:(List<?>)ids.get(null)){
            org.bukkit.inventory.ItemStack item=(org.bukkit.inventory.ItemStack)call(models,"item",new Class<?>[]{String.class},id);
            require(item.getType()==org.bukkit.Material.PAPER,"CE carrier");
            require(new org.bukkit.NamespacedKey("tabletop3d",id.toString()).equals(item.getItemMeta().getItemModel()),"item model component: "+id+" actual="+item.getItemMeta().getItemModel());
            org.bukkit.entity.ItemDisplay display=world.spawn(new Location(world,8,84,0),org.bukkit.entity.ItemDisplay.class,d->{
                d.setVisibleByDefault(false);d.setPersistent(false);d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);d.setItemStack(item);
            });
            require(display.isValid()&&!display.isVisibleByDefault(),"packed display hidden before audience");
            require(display.getItemDisplayTransform()==org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED,"client-readable model display transform");
            display.remove();count++;
        }
        getLogger().info("BOARDS_CRAFTENGINE_PASS models="+count+" optional_api=true client_visual_test=false");
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
        for(String kind:List.of("chess","xiangqi","gomoku","checkers","draughts","reversi","go9","go13","go","connectfour","ludo","yacht")){
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
            Map<String,String> options=new LinkedHashMap<>();
            if(saved.has("options"))saved.getAsJsonObject("options").entrySet().forEach(e->options.put(e.getKey(),e.getValue().getAsString()));
            require(field(room,"options").equals(options),"selected rules preserved");
            require((boolean)field(room,"sideTray")== (saved.has("sideTray")&&saved.get("sideTray").getAsBoolean()),"dice stand placement preserved");
            BoardGame live=(BoardGame)field(room,"board"),copy=GameFactory.create(kind,saved.get("capacity").getAsInt(),saved.get("seed").getAsLong(),options);
            for(JsonElement e:history){JsonObject action=e.getAsJsonObject();copy.apply(action.get("seat").getAsInt(),action.get("action").getAsString());}
            require(copy.cells().equals(live.cells())&&copy.currentPlayer()==live.currentPlayer()&&copy.finished()==live.finished()&&Objects.equals(copy.outcome(),live.outcome()),"restored rule state matches history");
            if(copy instanceof HandGame expected&&live instanceof HandGame actual)for(int seat=0;seat<copy.playerCount();seat++)
                require(expected.hand(seat).equals(actual.hand(seat))&&expected.discards(seat).equals(actual.discards(seat))&&expected.exposed(seat).equals(actual.exposed(seat)),"private hands and public tiles replay identically");
            require(Set.of("PLAYING","FINISHED").contains(field(room,"phase").toString()),"restored active or finished phase");events+=before.size();
        }
        Set<String> expected=new HashSet<>();for(JsonElement room:source.getAsJsonArray("rooms"))expected.add(room.getAsJsonObject().get("kind").getAsString());
        require(kinds.equals(expected),"all current snapshot kinds");
        require(((Map<?,?>)field(field(plugin,"arena"),"views")).size()==restored.size(),"one model per restored room");
        getLogger().info("BOARDS_SNAPSHOT_RESTORE_PASS rooms="+restored.size()+" saved_events="+events+" kinds="+kinds.size());
    }

    /** Validate the raise input against the real Paper dialog provider. */
    @SuppressWarnings("unchecked")
    private void pokerRaiseProbe(Plugin plugin, Object menus, World world) throws Exception {
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> roomType = Class.forName("dev.tabletop3d.Room", true, loader);
        Class<?> factory = Class.forName("dev.tabletop3d.rules.GameFactory", true, loader);
        var constructor = roomType.getDeclaredConstructor(UUID.class, String.class, int.class, long.class, int.class);
        constructor.setAccessible(true);
        UUID id = UUID.randomUUID();
        Object room = constructor.newInstance(id, "texas-holdem", 2, 1L, 998);
        Object game = factory.getMethod("create", String.class, int.class, long.class).invoke(null, "texas-holdem", 2, 1L);
        int turn = (int) call(game, "currentPlayer", new Class<?>[0]);
        AtomicInteger shown = new AtomicInteger();
        Player actor = player(world, shown, true);
        Player other = player(world, shown, true, UUID.randomUUID());
        for (int seat = 0; seat < 2; seat++) {
            Player seated = seat == turn ? actor : other;
            call(room, "join", new Class<?>[]{UUID.class, String.class}, seated.getUniqueId(), seated.getName());
        }
        var board = roomType.getDeclaredField("board");
        board.setAccessible(true);
        board.set(room, game);
        Map<UUID, Object> rooms = (Map<UUID, Object>) field(plugin, "rooms");
        rooms.put(id, room);
        try {
            call(menus, "pokerRaise", new Class<?>[]{Player.class, roomType, long.class}, actor, room, 0L);
            require(shown.get() == 1, "legal Raise opens a native input dialog");
            call(menus, "pokerRaise", new Class<?>[]{Player.class, roomType, long.class}, other, room, 0L);
            require(shown.get() == 1, "non-acting seat cannot open Raise");
            getLogger().info("BOARDS_POKER_RAISE_PASS legal-dialog=opened other-seat=rejected");
        } finally {
            rooms.remove(id);
        }
    }

    /** Build and navigate real Dialog objects while keeping synthetic rooms out of saves. */
    @SuppressWarnings("unchecked")
    private void menuExperienceProbe(Plugin plugin,Object menus,Player player) throws Exception {
        Map<UUID,Object> rooms=(Map<UUID,Object>)field(plugin,"rooms");List<UUID> added=new ArrayList<>();
        Class<?> roomType=Class.forName("dev.tabletop3d.Room",true,plugin.getClass().getClassLoader());
        var constructor=roomType.getDeclaredConstructor(UUID.class,String.class,int.class,long.class,int.class);constructor.setAccessible(true);
        try {
            for(Object room:rooms.values())if((int)call(room,"seat",new Class<?>[]{UUID.class},player.getUniqueId())>=0){
                call(menus,"main",new Class<?>[]{Player.class},player);
                require(!clickMenu(menus,player,"id","rooms"),"seated player cannot browse other rooms from the catalog");
                require(!menuLabel(menus,player,"resume").isEmpty(),"seated player can resume");
            }
            player=player(player.getWorld(),new AtomicInteger(),true,UUID.randomUUID());
            for(int i=0;i<25;i++){UUID id=UUID.randomUUID();rooms.put(id,constructor.newInstance(id,"chess",2,1L,i));added.add(id);}
            call(menus,"games",new Class<?>[]{Player.class,String.class},player,"chess");Set<String> seen=new HashSet<>();
            for(int page=0;page<4;page++){
                Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
                List<?> buttons=(List<?>)call(session,"buttons",new Class<?>[0]);
                for(Object button:buttons){String id=(String)call(button,"id",new Class<?>[0]);if(id.startsWith("room-"))seen.add(id);}
                if(!clickMenu(menus,player,"id","next"))break;
            }
            require(seen.size()==25,"every room reachable through native pagination");
            UUID cardRoom=UUID.randomUUID();rooms.put(cardRoom,constructor.newInstance(cardRoom,"color-eight",5,1L,26));added.add(cardRoom);
            call(menus,"main",new Class<?>[]{Player.class},player);
            require(clickMenu(menus,player,"id","rooms"),"catalog opens the shared room browser");
            Set<String> globalSeen=new HashSet<>();
            for(int page=0;page<4;page++){
                Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
                for(Object button:(List<?>)call(session,"buttons",new Class<?>[0])){
                    String id=(String)call(button,"id",new Class<?>[0]);require(!id.equals("entry"),"global browser has no game-specific create button");
                    if(id.startsWith("room-")){globalSeen.add(id);if(id.equals("room-"+cardRoom))require(((String)call(button,"label",new Class<?>[0])).contains("Color Eight")&&((String)call(button,"label",new Class<?>[0])).contains(cardRoom.toString().substring(0,6)),"room name contains game and code");}
                }
                if(!clickMenu(menus,player,"id","next"))break;
            }
            for(UUID id:added)require(globalSeen.contains("room-"+id),"mixed-game room reachable through shared browser");
            require(clickMenu(menus,player,"id","back"),"shared browser returns to catalog");
            require(clickMenu(menus,player,"id","ludo"),"catalog selects game setup");
            Object setupSession=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
            for(Object button:(List<?>)call(setupSession,"buttons",new Class<?>[0]))require(!"rooms".equals(call(button,"id",new Class<?>[0])),"game setup omits duplicate browser");
            require(clickMenu(menus,player,"id","details"),"detailed Ludo rules reachable");
            require(clickMenu(menus,player,"id","rule-blocking"),"Ludo blocking setting cycles");
            require(menuLabel(menus,player,"rule-blocking").contains("Any Pawn Blocks"),"blocking change retained in setup");
            require(clickMenu(menus,player,"id","back"),"settings return preserves draft");
            require(clickMenu(menus,player,"id","mode"),"friends and bot modes cycle");
            require(menuLabel(menus,player,"mode").contains("Bots"),"bot mode retained");
            call(menus,"setup",new Class<?>[]{Player.class,String.class},player,"mahjong");
            Set<String> profiles=new HashSet<>();
            for(int i=0;i<4;i++){profiles.add(menuLabel(menus,player,"rule-profile"));require(clickMenu(menus,player,"id","rule-profile"),"regional profile cycles");}
            require(profiles.size()==4,"all four regional profiles reachable");
            getLogger().info("BOARDS_MENU_FLOW_PASS rooms=26 pagination=4 shared-browser=mixed-games setup-browser=absent setup-rules=reachable");
        } finally {added.forEach(rooms::remove);call(menus,"forget",new Class<?>[]{Player.class},player);}
    }
    private String menuLabel(Object menus,Player player,String id) throws Exception {
        Object session=((Map<?,?>)field(menus,"sessions")).get(player.getUniqueId());
        for(Object button:(List<?>)call(session,"buttons",new Class<?>[0]))if(id.equals(call(button,"id",new Class<?>[0])))return (String)call(button,"label",new Class<?>[0]);
        throw new IllegalStateException("Missing menu entry: "+id);
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
                String chosen=kind.equals("connectfour")?"drop:3":kind.equals("go9")?"dead:0,0":legal.getFirst();
                gameType.getMethod("apply",int.class,String.class).invoke(game,0,chosen);
                call(room,"event",new Class<?>[]{int.class,JsonElement.class},0,new JsonPrimitive(chosen));
                Field revision=roomType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(room,revision.getLong(room)+1);
                call(view,"sync",new Class<?>[0]);
                Map<?,?> current=(Map<?,?>)field(view,"tokens");
                require(!current.isEmpty(),"model pieces rendered after the action");
                if(kind.equals("connectfour"))require(current.size()==1,"one dropped chip rendered");
                if(kind.equals("reversi")||kind.equals("go9"))for(var entry:previous.entrySet())
                    require(current.get(entry.getKey())==entry.getValue(),kind+" reuses existing tokens");
                if(kind.equals("ludo"))require((boolean)call(view,"rolling",new Class<?>[0]),"dice rolling after recorded roll");
                for(int i=0;i<24;i++)call(view,"tick",new Class<?>[0]);
                require(!(boolean)call(view,"rolling",new Class<?>[0]),"dice settled after bounded animation");
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
                    require(parts.size()==6,"Go stone plus three non-overlapping dead marks");
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
    private Player player(World world, AtomicInteger dialogs, boolean allowed) {
        UUID id = allowed ? UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420") : UUID.randomUUID();
        return player(world,dialogs,allowed,id);
    }
    private Player player(World world,AtomicInteger dialogs,boolean allowed,UUID id) {
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
