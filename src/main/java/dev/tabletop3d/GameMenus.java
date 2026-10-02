package dev.tabletop3d;

import com.google.gson.*;
import dev.tabletop3d.rules.YachtGame;
import dev.tabletop3d.rules.GameOptions;
import org.bukkit.*;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import dev.tabletop3d.ui.MessageText;

import java.util.*;

final class GameMenus implements AutoCloseable {
    private static final int ROOM_PAGE_SIZE=8;
    record Button(String id,String label,Runnable action,Component component){
        Button(String id,String label,Runnable action){this(id,label,action,null);}
        Button(String label,Runnable action){this("entry",label,action);}
        Button(String id,Component label,Runnable action){this(id,MessageText.plain(label),action,label);}
        Button(Component label,Runnable action){this("entry",label,action);}
    }
    record Session(UUID token,UUID world,long expires,List<Button> buttons,Runnable refresh){}
    record Setup(String kind,int capacity,boolean bots,Map<String,String> options){
        Setup { options=GameOptions.validate(kind,options); }
    }
    private final Tabletop3D plugin;private final Map<UUID,Session> sessions=new HashMap<>();
    private final BoardWindow window;
    GameMenus(Tabletop3D p){this(p,null);}
    GameMenus(Tabletop3D p,BoardWindow window){plugin=p;this.window=window;}
    void show(Player p,String title,String description,List<Button> buttons,Runnable back){
        show(p,title,description,buttons,back,"dialog");
    }
    void show(Player p,String title,String description,List<Button> buttons,Runnable back,String page){
        show(p,BoardWindow.text(title),BoardWindow.text(description),buttons,back,page);
    }
    void show(Player p,Component title,Component description,List<Button> buttons,Runnable back){
        show(p,title,description,buttons,back,"dialog");
    }
    void show(Player p,Component title,Component description,List<Button> buttons,Runnable back,String page){
        if(!plugin.allowed(p))return;
        List<Button> entries=new ArrayList<>(buttons);
        if(plugin.pack!=null&&plugin.pack.hasToggle())entries.addFirst(new Button("resource-pack",plugin.pack.button(p),()->{
            plugin.pack.toggle(p);show(p,title,description,buttons,back,page);
        }));
        if(back!=null)entries.add(new Button("back",Language.component("menu.back"),back));
        else if(plugin.mainMenuAvailable())entries.add(new Button("main",Language.component("menu.main"),()->{forget(p);Bukkit.dispatchCommand(p,"servermenu:servermenu main");}));
        entries.add(new Button("close",Language.component("menu.close"),()->forget(p)));
        UUID token=UUID.randomUUID();
        Runnable refresh=()->show(p,title,description,buttons,back,page);
        if(window!=null){
            var rendered=window.render(page,title,description,entries,token);
            entries=rendered.buttons();
            sessions.put(p.getUniqueId(),new Session(token,p.getWorld().getUID(),System.currentTimeMillis()+120000,List.copyOf(entries),refresh));
            if(window.open(p,rendered.config(),page))return;
        }
        sessions.put(p.getUniqueId(),new Session(token,p.getWorld().getUID(),System.currentTimeMillis()+120000,List.copyOf(entries),refresh));
        p.sendMessage(title.append(Component.newline()).append(description));
        for(int i=0;i<entries.size();i++){var b=entries.get(i);p.sendMessage(Component.text("[ ").append(b.component()!=null?b.component():BoardWindow.text(b.label())).append(Component.text(" ] ")).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/3dtabletop click 3dtabletop:"+token+" "+i)));}
    }
    void handle(Player p,String action){
        String[] a=action.split(" ");if(a.length!=2||!a[0].startsWith("3dtabletop:"))return;
        try{UUID token=UUID.fromString(a[0].substring("3dtabletop:".length()));int index=Integer.parseInt(a[1]);Session s=sessions.get(p.getUniqueId());
            if(s==null||!s.token().equals(token)||!s.world().equals(p.getWorld().getUID())||System.currentTimeMillis()>=s.expires()||index<0||index>=s.buttons().size())return;
            sessions.remove(p.getUniqueId());if(plugin.allowed(p))s.buttons().get(index).action().run();
        }catch(IllegalArgumentException ex){plugin.tell(p,ex.getMessage()==null?Language.component("error.page-changed"):Language.legacy(ex.getMessage()));}
    }
    void forget(Player p){sessions.remove(p.getUniqueId());if(plugin.comfort!=null)plugin.comfort.menuClosed(p);}
    boolean active(Player p){Session s=sessions.get(p.getUniqueId());return s!=null&&s.world().equals(p.getWorld().getUID())&&System.currentTimeMillis()<s.expires();}
    void refresh(Player p){if(active(p)&&p.isOnline())sessions.get(p.getUniqueId()).refresh().run();}
    void main(Player p){
        List<Button> b=new ArrayList<>();Room current=plugin.room(p);
        if(current!=null)b.add(new Button("resume",Language.component("menu.resume"),()->plugin.resume(p,current)));
        if(!plugin.rooms.isEmpty())b.add(new Button("rooms",Language.component("setup.rooms"),()->rooms(p)));
        for(String kind:Tabletop3D.NAMES.keySet())if(!Set.of("go9","go13").contains(kind))b.add(new Button(kind,RoomText.game(kind),()->setup(p,kind)));
        show(p,Language.component("menu.title"),Component.empty(),b,null,"catalog");
    }
    void setup(Player p,String kind){setup(p,new Setup(kind,Tabletop3D.defaultCapacity(kind),false,Map.of()));}
    void setup(Player p,Setup draft){
        String kind=draft.kind();List<Button> buttons=new ArrayList<>();
        List<GameOptions.Option> settings=GameOptions.forGame(kind,draft.options());
        buttons.add(new Button("start",Language.component(draft.bots()?"setup.start":"menu.create"),()->{
            plugin.create(p,kind,draft.capacity(),draft.options());Room room=plugin.room(p);
            if(draft.bots()&&room!=null)plugin.startWithBots(p,room);
        }));
        buttons.add(new Button("mode",Language.component("setup.mode","mode",Language.component(draft.bots()?"setup.bots":"setup.friends")),
            ()->setup(p,new Setup(kind,draft.capacity(),!draft.bots(),draft.options()))));
        int[] sizes=switch(kind){case "checkers"->new int[]{2,3,4,6};case "lastcard"->new int[]{2,3,4,5};case "ludo","aeroplane","yacht"->new int[]{2,3,4};default->new int[]{Tabletop3D.defaultCapacity(kind)};};
        if(sizes.length>1)buttons.add(new Button("capacity",Language.component("setup.capacity","count",draft.capacity()),()->{
            int index=0;while(index<sizes.length&&sizes[index]!=draft.capacity())index++;
            setup(p,new Setup(kind,sizes[(index+1)%sizes.length],draft.bots(),draft.options()));
        }));
        if(Set.of("go","go9","go13").contains(kind))buttons.add(new Button("board",Language.component("setup.board","size",kind.equals("go9")?9:kind.equals("go13")?13:19),
            ()->setup(p,new Setup(kind.equals("go9")?"go13":kind.equals("go13")?"go":"go9",2,draft.bots(),draft.options()))));
        for(var option:settings)if(option.key().equals("profile"))buttons.add(new Button("rule-profile",RoomText.option(kind,option,draft.options()),
            ()->setup(p,new Setup(kind,draft.capacity(),draft.bots(),GameOptions.change(kind,draft.options(),option)))));
        if(settings.stream().anyMatch(option->!option.key().equals("profile")))buttons.add(new Button("details",Language.component("setup.details"),()->setupRules(p,draft,0)));
        Component description=Language.component("setup.summary","mode",Language.component(draft.bots()?"setup.bots":"setup.friends"),"count",draft.capacity())
            .append(Component.newline()).append(Language.component("setup.basic-hint"));
        show(p,Language.component("setup.title","game",RoomText.game(kind)),description,buttons,()->main(p),"setup");
    }
    private void setupRules(Player p,Setup draft,int requestedPage){
        String kind=draft.kind();List<Button> buttons=new ArrayList<>();
        List<GameOptions.Option> settings=GameOptions.forGame(kind,draft.options()).stream().filter(option->!option.key().equals("profile")).toList();
        int pages=Math.max(1,(settings.size()+7)/8),page=Math.max(0,Math.min(requestedPage,pages-1));
        for(var option:settings.subList(page*8,Math.min((page+1)*8,settings.size())))buttons.add(new Button("rule-"+option.key(),RoomText.option(kind,option,draft.options()),
            ()->setupRules(p,new Setup(kind,draft.capacity(),draft.bots(),GameOptions.change(kind,draft.options(),option)),page)));
        if(page>0)buttons.add(new Button("previous",Language.component("menu.previous"),()->setupRules(p,draft,page-1)));
        if(page+1<pages)buttons.add(new Button("next",Language.component("menu.next"),()->setupRules(p,draft,page+1)));
        Component description=Language.component("setup.rules-hint");
        if(pages>1)description=description.append(Component.newline()).append(Language.component("setup.page","page",page+1,"pages",pages));
        show(p,Language.component("setup.details-title","game",RoomText.game(kind)),description,buttons,()->setup(p,draft),"setup");
    }
    void games(Player p,String kind){games(p,kind,0);}
    void rooms(Player p){games(p,null,0);}
    private void games(Player p,String kind,int requestedPage){
        List<Room> rooms=plugin.rooms.values().stream().filter(r->kind==null||r.kind.equals(kind)||kind.equals("go")&&Set.of("go9","go13").contains(r.kind))
            .sorted(Comparator.comparingInt(r->r.seat(p.getUniqueId())>=0?0:r.phase==Room.Phase.LOBBY&&r.seats.size()<r.capacity?1:2)).toList();
        int pages=Math.max(1,(rooms.size()+ROOM_PAGE_SIZE-1)/ROOM_PAGE_SIZE),page=Math.max(0,Math.min(requestedPage,pages-1));
        List<Button>b=new ArrayList<>();if(kind!=null)b.add(new Button("entry",Language.component("menu.create"),()->setup(p,kind)));
        for(Room r:rooms.subList(page*ROOM_PAGE_SIZE,Math.min((page+1)*ROOM_PAGE_SIZE,rooms.size()))){
            String action=r.seat(p.getUniqueId())>=0?"menu.rooms.resume":r.phase==Room.Phase.LOBBY&&r.seats.size()<r.capacity?"menu.rooms.join":"menu.rooms.view";
            Component label=Language.component("room.list","room",RoomText.name(r),"occupied",r.seats.size(),"capacity",r.capacity,"phase",RoomText.phase(r));
            b.add(new Button("room-"+r.id,Language.component("menu.rooms.entry","action",Language.component(action),"room",label),()->{
                if(plugin.rooms.get(r.id)!=r){plugin.tell(p,Language.component("error.page-changed"));games(p,kind,page);return;}
                if(r.seat(p.getUniqueId())>=0)plugin.resume(p,r);else if(r.phase==Room.Phase.LOBBY&&r.seats.size()<r.capacity)plugin.join(p,r);else observe(p,r);
            }));
        }
        if(page>0)b.add(new Button("previous",Language.component("menu.previous"),()->games(p,kind,page-1)));
        if(page+1<pages)b.add(new Button("next",Language.component("menu.next"),()->games(p,kind,page+1)));
        Component description=rooms.isEmpty()?Language.component("menu.rooms.empty"):Language.component("menu.rooms.page","count",rooms.size(),"page",page+1,"pages",pages);
        show(p,kind==null?Language.component("setup.rooms"):RoomText.game(kind),description,b,()->{Room current=plugin.room(p);if(kind!=null&&current!=null)room(p,current);else main(p);});
    }
    void sizes(Player p,String kind){if(kind.equals("go")){show(p,Language.component("menu.go.title"),Language.component("menu.go.description"),List.of(new Button(Language.component("menu.go.small"),()->plugin.create(p,"go9",2)),new Button(Language.component("menu.go.medium"),()->plugin.create(p,"go13",2)),new Button(Language.component("menu.go.full"),()->plugin.create(p,"go",2))),()->games(p,kind));return;}int[] sizes=switch(kind){case"uno"->new int[]{2,3,4,6,8,10};case"checkers"->new int[]{2,3,4,6};case"ludo","aeroplane","yacht"->new int[]{2,3,4};default->new int[]{Tabletop3D.defaultCapacity(kind)};};
        if(sizes.length==1){plugin.create(p,kind,sizes[0]);return;}List<Button>b=new ArrayList<>();for(int size:sizes)b.add(new Button(Language.component("menu.capacity.option","count",size),()->plugin.create(p,kind,size)));show(p,Language.component("menu.capacity.title"),Language.component("menu.capacity.description"),b,()->games(p,kind));}
    Component status(Room r){
        Component text=RoomText.phase(r).append(Component.newline());int turn=r.turn();
        for(int i=0;i<r.seats.size();i++){Room.Seat seat=r.seats.get(i);text=text.append(Language.component("room.seat","number",i+1,"player",RoomText.player(seat,i+1),"ready",r.ready.contains(seat.id())?" ✓":"","turn",turn==i?Component.space().append(Language.component("room.turn")):Component.empty())).append(Component.newline());}
        if(r.board instanceof dev.tabletop3d.rules.HandGame hand)text=text.append(HandText.status(r.kind,hand)).append(RoomText.scores(r)).append(Component.newline());
        else if(r.board!=null)for(var entry:r.board.publicInfo().entrySet())if(!Set.of("rules","rulesVariant").contains(entry.getKey()))text=text.append(Language.legacy(entry.getValue())).append(Component.newline());
        if(r.undo!=null)text=text.append(Language.component("room.undo.paused"));
        if(r.phase==Room.Phase.FINISHED||r.phase==Room.Phase.PAUSED)text=text.append(RoomText.outcome(r,r.result));return text;
    }
    void room(Player p,Room r){
        if(plugin.rooms.get(r.id)!=r){main(p);return;}int seat=r.seat(p.getUniqueId());if(seat<0){observe(p,r);return;}
        List<Button>b=new ArrayList<>();
        if(r.phase==Room.Phase.LOBBY){b.add(new Button("ready",Language.component(r.ready.contains(p.getUniqueId())?"menu.unready":"menu.ready"),()->plugin.ready(p,r)));if(r.host(p.getUniqueId()))b.add(new Button("bots",Language.component("menu.bots"),()->plugin.startWithBots(p,r)));}
        if(r.phase==Room.Phase.PLAYING){
            b.add(new Button("play",Language.component("menu.play"),()->{forget(p);plugin.enterArena(p,r);}));
            if(r.board!=null&&!r.busy&&r.undo==null){
                List<String> actions=Set.of("go","go9","go13").contains(r.kind)?List.of("pass","accept","resume"):List.of();
                if(!actions.isEmpty()){
                    long revision=r.revision;List<String> legal=r.board.legalActions(seat);
                    for(String action:actions)if(legal.contains(action))b.add(new Button(action,r.kind.equals("lastcard")?HandText.action(r,seat,action):actionLabel(r,action),()->plugin.action(p,r,revision,new JsonPrimitive(action))));
                }
            }
        }
        if(r.undo!=null){if(r.undo.pending.contains(p.getUniqueId()))b.add(new Button(Language.component("menu.undo.approve"),()->plugin.approveUndo(p,r)));b.add(new Button(Language.component(r.undo.requester.equals(p.getUniqueId())?"menu.undo.cancel":"menu.undo.reject"),()->plugin.rejectUndo(p,r)));}
        if(r.phase==Room.Phase.FINISHED)b.add(new Button("rematch",Language.component(r.ready.contains(p.getUniqueId())?"menu.rematch.waiting":"menu.rematch"),()->plugin.rematch(p,r)));
        b.add(new Button("options",Language.component("menu.options"),()->roomOptions(p,r)));
        show(p,RoomText.name(r),compactRoomSummary(r),b,()->main(p),"room");
    }
    private Component compactRoomSummary(Room r){
        Component text=Language.component("room.summary","phase",RoomText.phase(r),"occupied",r.seats.size(),"capacity",r.capacity);
        if(r.phase==Room.Phase.LOBBY||r.phase==Room.Phase.FINISHED)text=text.append(Component.newline()).append(Language.component("room.ready-count","ready",r.ready.size(),"occupied",r.seats.size()));
        else if(r.turn()>=0&&r.turn()<r.seats.size())text=text.append(Component.newline()).append(Language.component("room.current-turn","player",RoomText.player(r.seats.get(r.turn()),r.turn()+1)));
        if(r.phase==Room.Phase.LOBBY)for(int i=0;i<r.seats.size();i++){
            Room.Seat seat=r.seats.get(i);text=text.append(Component.newline()).append(Language.component("room.member","player",RoomText.player(seat,i+1),"state",Language.component(r.ready.contains(seat.id())?"room.ready":"room.unready")));
        }
        if(r.phase==Room.Phase.FINISHED)text=text.append(RoomText.scores(r));
        if(r.undo!=null)text=text.append(Component.newline()).append(Language.component("room.undo.pending"));
        if(r.phase==Room.Phase.FINISHED||r.phase==Room.Phase.PAUSED)text=text.append(Component.newline()).append(RoomText.outcome(r,r.result)).append(RoomText.ranking(r));
        return text;
    }
    Component roomSummary(Room r){
        Component text=Language.component("room.summary","phase",RoomText.phase(r),"occupied",r.seats.size(),"capacity",r.capacity);int turn=r.turn();
        for(int i=0;i<r.seats.size();i++){Room.Seat seat=r.seats.get(i);Component state=Component.empty();
            if(r.phase==Room.Phase.LOBBY||r.phase==Room.Phase.FINISHED)state=Language.component(r.ready.contains(seat.id())?"room.ready":"room.unready");
            else if(turn==i)state=Language.component("room.turn");
            text=text.append(Component.newline()).append(Language.component("room.member","player",RoomText.player(seat,i+1),"state",state));}
        if(r.undo!=null)text=text.append(Component.newline()).append(Language.component("room.undo.pending"));
        text=text.append(Component.newline()).append(RoomText.options(r.kind,r.options));
        text=text.append(RoomText.ranking(r));
        if(r.board instanceof dev.tabletop3d.rules.HandGame hand)text=text.append(Component.newline()).append(HandText.status(r.kind,hand)).append(RoomText.scores(r));
        if(r.phase==Room.Phase.FINISHED||r.phase==Room.Phase.PAUSED)text=text.append(Component.newline()).append(RoomText.outcome(r,r.result));
        return text;
    }
    void roomOptions(Player p,Room r){
        if(plugin.rooms.get(r.id)!=r||r.seat(p.getUniqueId())<0){main(p);return;}
        List<Button>b=new ArrayList<>();
        if(Set.of("xiangqi","gomoku","chess","checkers","draughts","reversi","go","go9","go13","connectfour").contains(r.kind)&&r.undo==null&&r.board!=null&&!r.history.isEmpty()&&(r.phase==Room.Phase.PLAYING||r.phase==Room.Phase.FINISHED))b.add(new Button(Language.component("menu.undo.request"),()->plugin.requestUndo(p,r)));
        b.add(new Button("leave",Language.component("menu.leave.title"),()->confirmLeave(p)));
        show(p,Language.component("menu.options"),compactRoomSummary(r),b,()->room(p,r));
    }
    void yacht(Player p,Room r){
        if(!(r.board instanceof YachtGame g))return;int seat=r.seat(p.getUniqueId());long rev=r.revision;List<String> legal=g.legalActions(seat);List<Button>b=new ArrayList<>();
        if(legal.contains("roll"))b.add(new Button("roll",Language.component("menu.yacht.roll","count",3-g.rolls()),()->yachtAction(p,r,rev,"roll")));
        for(int i=0;i<5;i++){String a="hold:die"+i;if(legal.contains(a))b.add(new Button(Language.component("menu.yacht.die","held",Language.component(g.held(i)?"menu.yacht.hold":"menu.yacht.reroll"),"number",i+1,"value",g.dice()[i]),()->yachtAction(p,r,rev,a)));}
        StringBuilder dice=new StringBuilder();for(int i=0;i<5;i++)dice.append(g.dice()[i]).append(g.held(i)?"✓  ":"  ");
        b.add(new Button("score",Language.component(legal.stream().anyMatch(a->a.startsWith("score:"))?"menu.yacht.choose-score":"menu.yacht.view-score"),()->yachtScores(p,r)));
        show(p,RoomText.game("yacht"),Language.component("menu.yacht.dice","dice",dice).append(Component.newline()).append(roomSummary(r)),b,()->room(p,r),"yacht");
    }
    void yachtScores(Player p,Room r){
        if(!(r.board instanceof YachtGame g)||r.seat(p.getUniqueId())<0)return;
        long revision=r.revision;List<String> legal=g.legalActions(r.seat(p.getUniqueId()));
        List<Button> buttons=new ArrayList<>();Component sheet=Component.empty();
        for(int i=0;i<12;i++){
            String action="score:"+YachtGame.CATEGORIES.get(i);
            Component category=Language.component("score.category."+i),scores=Component.empty();
            for(int seat=0;seat<r.capacity;seat++)scores=scores.append(Language.component("menu.yacht.seat-score","number",seat+1,"score",g.written(seat,i)<0?"—":g.written(seat,i)));
            sheet=sheet.append(Language.component("menu.yacht.score-row","category",category,"scores",scores)).append(Component.newline());
            if(legal.contains(action))buttons.add(new Button(Language.component("menu.yacht.score","category",category,"score",YachtGame.score(i,g.dice())),()->yachtAction(p,r,revision,action)));
        }
        show(p,Language.component("menu.yacht.score-sheet"),sheet,buttons,()->yacht(p,r),"yacht");
    }
    void yachtAction(Player p,Room r,long revision,String action){plugin.action(p,r,revision,new JsonPrimitive(action));if(plugin.rooms.containsKey(r.id)&&r.phase==Room.Phase.PLAYING)yacht(p,r);}
    void observe(Player p,Room r){
        if(plugin.rooms.get(r.id)!=r){plugin.tell(p,Language.component("error.page-changed"));games(p,r.kind);return;}
        if(plugin.room(p)!=null){plugin.tell(p,Language.component("chat.observe.own-game"));return;}
        List<Button>b=new ArrayList<>();b.add(new Button(Language.component("menu.observe.refresh"),()->observe(p,r)));
        if(r.board!=null)b.add(new Button(Language.component("menu.observe.visit"),()->{if(plugin.rooms.get(r.id)!=r){plugin.tell(p,Language.component("error.page-changed"));games(p,r.kind);return;}if(!p.getWorld().equals(plugin.arena.world))plugin.returns.putIfAbsent(p.getUniqueId(),p.getLocation());p.teleport(plugin.arena.seatLocation(r,0));}));
        show(p,Language.component("menu.observe.title","room",RoomText.name(r)),status(r).append(Language.component("menu.observe.description")),b,()->games(p,r.kind));
    }
    void boardSources(Player p,Room r,int page){
        if(plugin.rooms.get(r.id)!=r){main(p);return;}
        if(r.board instanceof dev.tabletop3d.rules.HandGame){hand(p,r,page);return;}
        int seat=r.seat(p.getUniqueId());if(r.board==null)return;long revision=r.revision;
        List<String> legal=r.board.legalActions(seat);Map<String,List<String>> bySource=new LinkedHashMap<>();
        for(String action:legal){String[] split=action.split(":");String key=split.length>=2?split[1]:action;bySource.computeIfAbsent(key,k->new ArrayList<>()).add(action);}
        List<String> keys=new ArrayList<>(bySource.keySet());List<Button>b=new ArrayList<>();int from=Math.max(0,Math.min(page*12,keys.size()));
        for(String key:keys.subList(from,Math.min(from+12,keys.size()))){
            List<String> choices=bySource.get(key);String action=choices.getFirst();
            boolean direct=choices.size()==1&&(r.kind.equals("ludo")||action.startsWith("place:")||action.startsWith("drop:")||action.startsWith("dead:"));
            Component label=direct?actionLabel(r,action):Language.component("menu.pieces.option","piece",labelCell(r,key),"count",choices.size());
            b.add(new Button(label,()->{if(plugin.rooms.get(r.id)!=r||r.revision!=revision)boardSources(p,r,0);
                else if(direct)plugin.action(p,r,revision,new JsonPrimitive(action));else boardChoices(p,r,choices,0);}));
        }
        if(from>0)b.add(new Button(Language.component("menu.previous"),()->boardSources(p,r,page-1)));if(from+12<keys.size())b.add(new Button(Language.component("menu.next"),()->boardSources(p,r,page+1)));
        boolean placement=Set.of("gomoku","go","go9","go13","reversi","connectfour").contains(r.kind);
        show(p,Language.component(placement?"menu.positions.title":"menu.pieces.title"),Language.component(legal.isEmpty()?"menu.pieces.empty":placement?"menu.positions.description":"menu.pieces.description"),b,()->room(p,r));
    }
    void hand(Player p,Room r,int requestedPage){
        if(plugin.rooms.get(r.id)!=r){main(p);return;}
        int seat=r.seat(p.getUniqueId());if(seat<0||!(r.board instanceof dev.tabletop3d.rules.HandGame hand)){observe(p,r);return;}
        long revision=r.revision;List<String> legal=r.board.legalActions(seat);
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(legal.size()-1)/12))),from=page*12;
        List<Button> buttons=new ArrayList<>();
        for(String action:legal.subList(from,Math.min(from+12,legal.size())))buttons.add(new Button("hand-action",HandText.action(r,seat,action),()->{
            plugin.action(p,r,revision,new JsonPrimitive(action));if(plugin.rooms.get(r.id)==r&&r.phase==Room.Phase.PLAYING)hand(p,r,0);
        }));
        if(page>0)buttons.add(new Button("previous",Language.component("menu.previous"),()->hand(p,r,page-1)));
        if(from+12<legal.size())buttons.add(new Button("next",Language.component("menu.next"),()->hand(p,r,page+1)));
        Component cards=Component.empty();for(var piece:hand.hand(seat)){
            if(!cards.equals(Component.empty()))cards=cards.append(Component.text(" · "));cards=cards.append(HandText.piece(r.kind,piece.face()));
        }
        Component description=Language.component("hand.private","cards",cards).append(Component.newline())
            .append(Language.component(legal.isEmpty()?"hand.wait":"hand.choose")).append(Component.newline()).append(compactRoomSummary(r))
            .append(Component.newline()).append(HandText.status(r.kind,hand));
        show(p,RoomText.name(r),description,buttons,()->room(p,r),"hand");
    }
    Component labelCell(Room r,String id){return r.board.cells().stream().filter(c->c.id().equals(id)).map(c->Component.text(c.id()+" "+Language.glyph(c.piece()))).map(c->(Component)c).findFirst().orElseGet(()->id.equals("roll")?Language.component("action.roll"):Component.text(id));}
    void boardChoices(Player p,Room r,List<String> choices,int page){
        if(plugin.rooms.get(r.id)!=r){main(p);return;}
        long revision=r.revision;List<Button>b=new ArrayList<>();int from=Math.max(0,Math.min(page*12,choices.size()));
        for(String action:choices.subList(from,Math.min(from+12,choices.size())))b.add(new Button(r.board instanceof dev.tabletop3d.rules.HandGame?HandText.action(r,r.seat(p.getUniqueId()),action):actionLabel(r,action),()->plugin.action(p,r,revision,new JsonPrimitive(action))));
        if(from>0)b.add(new Button(Language.component("menu.previous"),()->{if(r.revision!=revision)room(p,r);else boardChoices(p,r,choices,page-1);}));if(from+12<choices.size())b.add(new Button(Language.component("menu.next"),()->{if(r.revision!=revision)room(p,r);else boardChoices(p,r,choices,page+1);}));
        show(p,Language.component("menu.moves.title"),Language.component(r.kind.equals("chess")?"menu.moves.chess":"menu.moves.description"),b,()->room(p,r));
    }
    static Component actionLabel(Room r,String action){String[] s=action.split(":");
        if(r.kind.equals("ludo")&&s.length==3&&s[0].equals("move")){
            var cell=r.board.cells().stream().filter(c->c.id().equals(s[2])).findFirst().orElseThrow();
            return Language.component("action.ludo.move","pawn",Integer.parseInt(s[1])%4+1,"destination",GameWorld.coordinate("ludo",cell));
        }
        if(Set.of("roll","pass","accept","resume").contains(action))return Language.component("action."+action);if(s.length==2&&s[0].equals("drop"))return Language.component("action.drop","column",Integer.parseInt(s[1])+1);if(s.length==2)return Language.component(s[0].equals("dead")?"action.dead":"action.place","cell",s[1]);if(s.length>=3)return s.length==4?Language.component("action.promote","from",s[1],"to",s[2],"piece",Set.of("q","r","b","n").contains(s[3])?Language.component("promotion."+s[3]):Component.text(s[3])):Language.component("action.move","from",s[1],"to",s[2]);return Component.text(action);}



    void confirmLeave(Player p){Room r=plugin.room(p);if(r==null){main(p);return;}show(p,Language.component("menu.leave.title"),Language.component("menu.leave.description"),List.of(new Button("entry",Language.component("menu.leave.confirm"),()->{if(plugin.room(p)==r)plugin.leave(p);else main(p);})),()->room(p,r));}
    void rules(Player p,String kind){
        Room r=plugin.room(p);rules(p,kind,()->{if(r!=null)room(p,r);else main(p);});
    }
    private void rules(Player p,String kind,Runnable back){
        String key=Set.of("go9","go13").contains(kind)?"go":Tabletop3D.NAMES.containsKey(kind)||Set.of("yacht","aeroplane").contains(kind)?kind:"default";
        Component text=Language.component("rules."+(key.equals("lastcard")?"color-eight":key));Room r=plugin.room(p);
        if(r!=null&&r.board!=null&&(r.kind.equals(kind)||kind.equals("go")&&Set.of("go","go9","go13").contains(r.kind))){var info=r.board.publicInfo();text=text.append(Component.newline()).append(Component.newline()).append(Language.legacy(info.getOrDefault("rules",""))).append(Component.newline()).append(Language.legacy(info.getOrDefault("rulesVariant","")));}
        text=text.append(Component.newline()).append(Component.newline()).append(Language.component("rules.footer","seconds",plugin.getConfig().getLong("reconnect-seconds",120)));
        show(p,Language.component("rules.title","game",RoomText.game(kind)),text,List.of(),back);
    }
    static String roomState(String state){return switch(state){case "WAITING","LOBBY"->"等候";case "PLAYING","RUNNING"->"对局中";case "STARTING"->"正在准备";case "FINISHED","ENDED"->"已结束";case "PAUSED"->"已暂停";case "ABORTED","CLOSED"->"已关闭";default->state;};}
    static String roomLabel(String game,String id,int occupied,int capacity,String state){return game+" · "+id+"  "+occupied+"/"+capacity+" · "+roomState(state);}


    @Override public void close(){sessions.clear();}
}
