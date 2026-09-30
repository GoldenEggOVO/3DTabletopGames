package dev.tabletop3d;

import dev.tabletop3d.rules.HandGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Public backs and public discards are separate from owner-only face entities. */
final class HandTable implements AutoCloseable {
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    private final boolean mahjong;
    private final Map<String,PieceView> publicPieces = new LinkedHashMap<>();
    private final Map<UUID,PrivateView> privateViews = new HashMap<>();
    private final List<Entity> furniture = new ArrayList<>();
    private final TextDisplay deckLabel;
    private final TurnRing turnRing;
    private final MahjongTableHud mahjongHud;
    private HandGame rendered;
    private long revision = -1, language = -1;
    private boolean closed;
    static final double CARD_LIFT=.27;

    record Pose(double x, double z, float yaw, double lift) {
        Pose(double x,double z,float yaw){this(x,z,yaw,0);}
    }
    private record Spec(String id, String face, Pose pose, boolean back, boolean standing, int seat) {}
    private final class PieceView {
        Spec spec;
        final List<Entity> parts = new ArrayList<>();
        final List<Vector> offsets = new ArrayList<>();
        final List<Material> materials = new ArrayList<>();
        boolean disabled;
        PieceView(Spec spec, Player viewer) { this.spec = spec; build(viewer); }
        void add(Entity entity, Vector offset,Material material) { parts.add(entity); offsets.add(offset); materials.add(material); }
        Vector displacement=new Vector(), start=new Vector(), target=new Vector();
        int frame=4, duration=4, delay;
        boolean drawing;
        double width(){return mahjong?.094:.168;}
        double height(){return spec.standing()?(mahjong?.14:.25):.012;}
        double depth(){return spec.standing()?(mahjong?.052:.008):(mahjong?.146:.245);}
        void build(Player viewer) {
            Location at=at(spec.pose());String id=viewer==null?"@board":"@hand:"+spec.id();
            double scale=1;
            double w=width()*scale,h=height()*scale,d=depth()*scale;
            add(block(at,Material.SMOOTH_QUARTZ,w,h,d,id,viewer),new Vector(),Material.SMOOTH_QUARTZ);
            if(spec.back()&&mahjong){
                Vector panel=spec.standing()?rotated(0,.012,d/2+.002,spec.pose()):new Vector(0,.014*scale,0);
                add(block(at.clone().add(panel),Material.GREEN_CONCRETE,w*.84,spec.standing()?h-.024:.002,spec.standing()?.002:d*.86,id,viewer),panel,Material.GREEN_CONCRETE);
            }else{
                var sprite=HandSprites.of(mahjong,spec.back()?"back":spec.face());
                Map<Material,Integer> area=new EnumMap<>(Material.class);
                for(var rect:sprite)area.merge(rect.material(),rect.width()*rect.height(),Integer::sum);
                Material background=area.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                List<HandSprites.Rect> layers=new ArrayList<>();layers.add(new HandSprites.Rect(0,0,32,48,background));
                sprite.stream().filter(rect->rect.material()!=background).forEach(layers::add);
                for(var rect:layers){
                    double rw=rect.width()/32.0*w,rh=rect.height()/48.0*(spec.standing()?h:d);
                    double x=((rect.x()+rect.width()/2.0)/32-.5)*w;
                    Vector offset=spec.standing()?rotated(x,h-(rect.y()+rect.height())/48.0*h,d/2+.003+(rect==layers.getFirst()?0:.0008),spec.pose()):
                        rotated(x,.014*scale+(rect==layers.getFirst()?0:.0008),((rect.y()+rect.height()/2.0)/48-.5)*d,spec.pose());
                    add(block(at.clone().add(offset),rect.material(),rw,spec.standing()?rh:.002,spec.standing()?.001:rh,id,viewer),offset,rect.material());
                }
            }
            if(spec.standing()){
                Vector back=rotated(0,.012,-d/2-.002,spec.pose());
                Material backMaterial=mahjong?Material.GREEN_CONCRETE:Material.BLACK_CONCRETE;
                add(block(at.clone().add(back),backMaterial,w*.84,h-.024,.002,id,viewer),back,backMaterial);
                if(!mahjong){
                    Material[] colors={Material.RED_CONCRETE,Material.BLUE_CONCRETE,Material.YELLOW_CONCRETE,Material.PURPLE_CONCRETE};
                    for(int i=0;i<4;i++){
                        Vector mark=rotated((i%2==0?-.018:.018),.10+(i/2)*.037,-d/2-.004,spec.pose());
                        add(block(at.clone().add(mark),colors[i],.035,.035,.001,id,viewer),mark,colors[i]);
                    }
                }
            }
        }
        void move(Spec next) {
            if(!spec.pose().equals(next.pose())){
                float delta=next.pose().yaw()-spec.pose().yaw();
                for(int i=0;i<offsets.size();i++){
                    Vector old=offsets.get(i);offsets.set(i,rotated(old.getX(),old.getY(),old.getZ(),new Pose(0,0,delta)));
                }
                spec=next;position();
            }else spec=next;
        }
        void position(){
            for(int i=0;i<parts.size();i++)parts.get(i).teleport(at(spec.pose()).add(offsets.get(i)).add(displacement));
        }
        void shift(Vector next){
            if(drawing){target=next.clone();return;}
            if(target.distanceSquared(next)<1e-10)return;
            start=displacement.clone();target=next.clone();duration=4;frame=0;
        }
        void drawFromDeck(int delay){
            this.delay=delay;drawing=true;duration=12;frame=0;target=new Vector();
            start=origin.clone().add(-.22,.07,0).toVector().subtract(at(spec.pose()).toVector());
            displacement=start.clone();position();
        }
        void tick(){
            if(delay>0){delay--;return;}if(frame>=duration)return;
            double t=++frame/(double)duration,eased=t*t*(3-2*t);
            displacement=start.clone().multiply(1-eased).add(target.clone().multiply(eased));
            if(drawing)displacement.setY(displacement.getY()+Math.sin(Math.PI*t)*.14);
            position();if(frame==duration)drawing=false;
        }
        boolean valid() { return parts.stream().allMatch(Entity::isValid); }
        void remove() { parts.forEach(Entity::remove); }
        void highlight(boolean selected) {
            ((Display)parts.getFirst()).setGlowColorOverride(Color.YELLOW);
            parts.getFirst().setGlowing(selected);
        }
        void playable(boolean allowed){
            if(disabled==!allowed)return;disabled=!allowed;
            for(int i=0;i<parts.size();i++){
                Material original=materials.get(i),gray=switch(original){
                    case WHITE_CONCRETE,SMOOTH_QUARTZ -> Material.LIGHT_GRAY_CONCRETE;
                    case BLACK_CONCRETE,BLUE_CONCRETE,GREEN_CONCRETE -> Material.GRAY_CONCRETE;
                    default -> Material.GRAY_CONCRETE;
                };
                ((BlockDisplay)parts.get(i)).setBlock((allowed?original:i==0?Material.GRAY_CONCRETE:gray).createBlockData());
            }
        }
        BoundingBox bounds(boolean sticky) {
            Location base=at(spec.pose()),at=base.clone().add(displacement);
            double scale=1;
            double angle=Math.toRadians(spec.pose().yaw()),w=width()*scale/2+.003,d=depth()*scale/2+.003;
            double rx=Math.abs(Math.cos(angle))*w+Math.abs(Math.sin(angle))*d;
            double rz=Math.abs(Math.sin(angle))*w+Math.abs(Math.cos(angle))*d;
            BoundingBox box=new BoundingBox(at.getX()-rx,at.getY(),at.getZ()-rz,at.getX()+rx,at.getY()+height()*scale,at.getZ()+rz);
            if(sticky&&!drawing)box.union(new BoundingBox(base.getX()-rx,base.getY(),base.getZ()-rz,base.getX()+rx,base.getY()+height(),base.getZ()+rz));
            return box;
        }
        double cardHitDistance(Location eye,Vector ray,double reach,boolean sticky) {
            double angle=-Math.toRadians(spec.pose().yaw()),cos=Math.cos(angle),sin=Math.sin(angle);
            Vector offset=eye.toVector().subtract(at(spec.pose()).toVector());
            Vector localEye=new Vector(offset.getX()*cos-offset.getZ()*sin,offset.getY(),offset.getX()*sin+offset.getZ()*cos);
            Vector localRay=new Vector(ray.getX()*cos-ray.getZ()*sin,ray.getY(),ray.getX()*sin+ray.getZ()*cos);
            double x=displacement.getX()*cos-displacement.getZ()*sin,y=displacement.getY(),z=displacement.getX()*sin+displacement.getZ()*cos;
            double w=width()/2+.003,d=depth()/2+.003;
            BoundingBox box=new BoundingBox(x-w,y,z-d,x+w,y+height(),z+d);
            if(sticky&&!drawing)box.union(new BoundingBox(-w,0,-d,w,height(),d));
            var hit=box.rayTrace(localEye,localRay,reach);
            return hit==null?Double.POSITIVE_INFINITY:hit.getHitPosition().distance(localEye);
        }
    }
    private static final class PrivateView {
        final Player player;
        final Map<String,PieceView> pieces = new LinkedHashMap<>();
        final Map<String,CallView> calls = new LinkedHashMap<>();
        final Map<String,CardButton> cardButtons = new LinkedHashMap<>();
        final Map<String,Entity> matches = new LinkedHashMap<>();
        HandGame board;
        long revision = -1, language = -1;
        int seat = -1, historySize;
        String hover;
        String callGroup;
        String pressedTile;
        Location pressedEye;
        long pressedAt;
        TextDisplay remaining;
        boolean callsEnabled;
        long dismissedRevision=-1;
        PrivateView(Player player) { this.player=player; }
        void remove() { pieces.values().forEach(PieceView::remove); pieces.clear(); clearCalls(); if(remaining!=null){remaining.remove();remaining=null;} }
        void clearCalls(){calls.values().forEach(CallView::remove);calls.clear();cardButtons.values().forEach(CardButton::remove);cardButtons.clear();}
    }

    private final class CardButton {
        final List<Entity> parts;
        final BoundingBox bounds;
        CardButton(String action,Pose pose,Material material,Component caption,Player player,double width){
            Location at=at(pose);String id="@call:card:"+action;
            BlockDisplay plate=block(at,material,width,.085,.014,id,player);
            TextDisplay label=text(at.clone().add(rotated(0,.035,.011,pose)),caption,.15f,pose.yaw(),false,id,player);
            parts=List.of(plate,label);
            double angle=Math.toRadians(pose.yaw()),x=Math.abs(Math.cos(angle))*width/2+Math.abs(Math.sin(angle))*.02,
                z=Math.abs(Math.sin(angle))*width/2+Math.abs(Math.cos(angle))*.02;
            bounds=new BoundingBox(at.getX()-x,at.getY(),at.getZ()-z,at.getX()+x,at.getY()+.085,at.getZ()+z);
        }
        boolean valid(){return parts.stream().allMatch(Entity::isValid);}
        void remove(){parts.forEach(Entity::remove);}
    }

    private final class CallView {
        final List<Entity> parts;
        final BoundingBox bounds;
        CallView(String group,Pose pose,Player player,List<String> faces){
            Location at=at(pose);String id="@call:"+group;
            BlockDisplay plate=block(at,Material.GRAY_CONCRETE,.34,.105,.014,id,player);
            var fit=dev.tabletop3d.ui.LabelLayout.fit(Language.component("table.mahjong."+group),.30f,.06f);
            TextDisplay label=text(at.clone().add(rotated(0,.047,.010,pose)),fit.text().colorIfAbsent(NamedTextColor.GOLD),fit.scale(),pose.yaw(),false,id,player);
            parts=new ArrayList<>(List.of(plate,label));
            for(int i=0;i<faces.size();i++){
                Vector offset=rotated((i-(faces.size()-1)/2.0)*.101,0,0,pose);
                PieceView tile=new PieceView(new Spec("preview:"+group+":"+i,faces.get(i),
                    new Pose(pose.x()+offset.getX(),pose.z()+offset.getZ(),pose.yaw(),pose.lift()+.115),false,true,-1),player);
                parts.addAll(tile.parts);
            }
            double half=Math.max(.17,faces.size()*.101/2),a=Math.toRadians(pose.yaw()),
                x=Math.abs(Math.cos(a))*half+Math.abs(Math.sin(a))*.03,
                z=Math.abs(Math.sin(a))*half+Math.abs(Math.cos(a))*.03;
            bounds=new BoundingBox(at.getX()-x,at.getY(),at.getZ()-z,at.getX()+x,at.getY()+(faces.isEmpty()?.105:.255),at.getZ()+z);
        }
        boolean valid(){return parts.stream().allMatch(Entity::isValid);}
        void remove(){parts.forEach(Entity::remove);}
    }

    HandTable(Tabletop3D plugin, Room room, Location surfaceOrigin, NamespacedKey tag) {
        this.plugin=plugin; this.room=room; this.origin=surfaceOrigin.clone(); this.tag=tag; mahjong=room.kind.equals("mahjong");
        double felt=mahjong?2.77:2.02;
        if(mahjong)furniture.add(block(origin.clone().add(0,.003,0),Material.GREEN_TERRACOTTA,felt,.008,felt,"@board",null));
        double deckX=-.22;
        Location deck=origin.clone().add(deckX,.015,0);
        if(!mahjong){
            furniture.add(block(deck,Material.SMOOTH_QUARTZ,.18,.045,.27,"@board",null));
            furniture.add(block(deck.clone().add(0,.046,0),Material.BLACK_CONCRETE,.16,.003,.25,"@board",null));
            deckLabel=text(origin.clone().add(deckX,.058,-.14),Component.empty(),.16f,0,true,"@board",null);
            furniture.add(deckLabel);
        }else deckLabel=null;
        turnRing=mahjong?null:new TurnRing(plugin,room,origin,tag);
        mahjongHud=mahjong?new MahjongTableHud(plugin,room,origin,tag):null;
        sync();
    }
    static Pose handPose(int seat,int players,int index,int count,boolean mahjong) {
        if(!mahjong){
            double centered=index-(count-1)/2.0,unit=Math.min(.15,(players==5?1.18:1.50)/Math.max(1,count-1));
            double fraction=count<2?0:centered/((count-1)/2.0);
            Pose base=seatPose(seat,players,centered*unit,1.02+index*.0025);
            return new Pose(base.x(),base.z(),base.yaw()-(float)(fraction*4),.015*(1-fraction*fraction));
        }
        int columns=mahjong?17:18,row=index/columns,inRow=Math.min(columns,Math.max(1,count-row*columns));
        if(mahjong&&inRow%3==2)inRow--;
        double unit=mahjong?.101:Math.min(.155,(players==3?1.24:1.62)/Math.max(1,inRow-1));
        double centered=index%columns-(inRow-1)/2.0,tangent=centered*unit;
        if(mahjong&&index%columns>=inRow)tangent+=.075;
        Pose base=seatPose(seat,players,tangent,(mahjong?1.27:players==3?.78:.91)-row*(mahjong?.085:.27));
        return mahjong?base:new Pose(base.x(),base.z(),base.yaw()+(float)(centered*1.2),row*.055);
    }
    static Pose riverPose(int seat,int players,int index) {
        return seatPose(seat,players,(index%6-2.5)*.101,.97-(index/6)*.16);
    }
    static Pose exposedPose(int seat,int players,int index) {
        return seatPose(seat,players,1.22-(index%7)*.101,1.10-(index/7)*.16);
    }
    private static Pose seatPose(int seat,int players,double tangent,double radius) {
        double angle=2*Math.PI*seat/players;
        return new Pose(tangent*Math.cos(angle)+radius*Math.sin(angle),-tangent*Math.sin(angle)+radius*Math.cos(angle),(float)-Math.toDegrees(angle));
    }
    private Location at(Pose pose) {
        Location at=origin.clone().add(pose.x(),.017+pose.lift(),pose.z()); at.setYaw(pose.yaw()); return at;
    }
    private static Vector rotated(double x,double y,double z,Pose pose) {
        double angle=-Math.toRadians(pose.yaw());
        return new Vector(x*Math.cos(angle)+z*Math.sin(angle),y,-x*Math.sin(angle)+z*Math.cos(angle));
    }
    private void configure(Display display,String id,Player viewer) {
        // Set before materials/text are assigned in the spawn callback.
        if (viewer!=null) display.setVisibleByDefault(false);
        display.setPersistent(false); display.setGravity(false); display.setInvulnerable(true);
        display.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|"+id);
        display.setBrightness(new Display.Brightness(15,15)); display.setViewRange(.35f);
        display.setTeleportDuration(2); display.setInterpolationDuration(2);
    }
    private BlockDisplay block(Location at,Material material,double width,double height,double depth,String id,Player viewer) {
        BlockDisplay entity=origin.getWorld().spawn(at,BlockDisplay.class,d -> {
            configure(d,id,viewer); d.setBlock(material.createBlockData());
            d.setTransformation(new Transformation(new Vector3f((float)-width/2,0,(float)-depth/2),new Quaternionf(),new Vector3f((float)width,(float)height,(float)depth),new Quaternionf()));
        });
        if (viewer!=null) viewer.showEntity(plugin,entity);
        return entity;
    }
    private TextDisplay text(Location at,Component value,float scale,float yaw,boolean flat,String id,Player viewer) {
        TextDisplay entity=origin.getWorld().spawn(at,TextDisplay.class,d -> {
            configure(d,id,viewer); d.setBillboard(Display.Billboard.FIXED); d.setRotation(yaw,flat?-90:0);
            d.text(value); d.setLineWidth(Integer.MAX_VALUE); d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(0,0,0,0)); d.setShadowed(false); d.setSeeThrough(false);
            d.setTransformation(new Transformation(new Vector3f(0,-.125f*scale,0),new Quaternionf(),new Vector3f(scale),new Quaternionf()));
        });
        if (viewer!=null) viewer.showEntity(plugin,entity);
        return entity;
    }
    void sync() {
        if (closed) return;
        if (!(room.board instanceof HandGame game)) {
            for (PrivateView view:List.copyOf(privateViews.values())) clear(view.player);
            publicPieces.values().forEach(PieceView::remove); publicPieces.clear(); rendered=null; return;
        }
        for (PrivateView view : List.copyOf(privateViews.values())) if (!eligible(view.player)) clear(view.player);
        if (rendered==game && revision==room.revision && language==Language.generation() && publicPieces.values().stream().allMatch(PieceView::valid)) return;
        List<Spec> wanted=new ArrayList<>();
        for (int seat=0;seat<game.playerCount();seat++) {
            int count=Math.min(54,game.handSize(seat));
            for (int i=0;i<count;i++) wanted.add(new Spec("back:"+seat+":"+i,"",handPose(seat,game.playerCount(),i,count,mahjong),true,true,seat));
            if (mahjong) {
                publicRow(wanted,game.discards(seat),"discard:"+seat,seat,game.playerCount(),false);
                publicRow(wanted,game.exposed(seat),"exposed:"+seat,seat,game.playerCount(),true);
            }
        }
        if(mahjong){
            Map<String,String> info=game.publicInfo();String[] faces=info.getOrDefault("dora","").split(","),ids=info.getOrDefault("doraIds","").split(",");
            for(int i=0;i<faces.length;i++)if(!faces[i].isEmpty()){
                String id=i<ids.length&&!ids[i].isEmpty()?ids[i]:Integer.toString(i);
                Pose frame=seatPose(2,4,(i-(faces.length-1)/2.0)*.101,1.44);
                // Raise the frame indicators above the opposing concealed row for the lowered Shift view.
                wanted.add(new Spec("dora:"+id,faces[i],new Pose(frame.x(),frame.z(),frame.yaw(),.15),false,false,-1));
            }
        }
        if (!mahjong) game.cells().stream().filter(cell -> cell.id().equals("discard") && !cell.piece().isEmpty())
                .findFirst().ifPresent(cell -> wanted.add(new Spec("discard",cell.piece(),new Pose(.16,0,0),false,false,-1)));
        reconcile(publicPieces,wanted,null);
        if(deckLabel!=null)deckLabel.text(Language.component("table.hand.deck","count",game.deckSize()).append(Component.newline()).append(HandText.tableHint(room.kind,game)));
        if(turnRing!=null)turnRing.direction(game.publicInfo().getOrDefault("direction","Clockwise").equals("Counterclockwise")?-1:1);
        rendered=game; revision=room.revision; language=Language.generation();
        for (PrivateView view : privateViews.values()) hideOwnBacks(view.player,view.seat);
    }
    private void publicRow(List<Spec> wanted,List<HandGame.Piece> pieces,String prefix,int seat,int players,boolean exposed) {
        // Older river tiles remain in the authoritative history and personal menu.
        int start=Math.max(0,pieces.size()-(exposed?28:18));
        for (int i=start;i<pieces.size();i++) {
            HandGame.Piece piece=pieces.get(i);
            Pose pose=exposed?exposedPose(seat,players,i-start):riverPose(seat,players,i-start);
            wanted.add(new Spec(prefix+":"+piece.id(),piece.face(),pose,piece.face().equals("back"),false,seat));
        }
    }
    private void reconcile(Map<String,PieceView> existing,List<Spec> wanted,Player viewer) {
        Set<String> kept=new HashSet<>();
        for (Spec spec:wanted) {
            kept.add(spec.id()); PieceView old=existing.get(spec.id());
            if (old!=null && old.valid() && old.spec.face().equals(spec.face()) && old.spec.back()==spec.back() && old.spec.standing()==spec.standing()) old.move(spec);
            else { if (old!=null) old.remove(); existing.put(spec.id(),new PieceView(spec,viewer)); }
        }
        for (var iterator=existing.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next(); if (!kept.contains(entry.getKey())) { entry.getValue().remove(); iterator.remove(); }
        }
    }
    private boolean eligible(Player player) {
        return !closed && (room.phase==Room.Phase.PLAYING || room.phase==Room.Phase.FINISHED)
                && room.board instanceof HandGame && plugin.allowed(player) && room.seat(player.getUniqueId())>=0 && origin.getWorld().equals(player.getWorld());
    }
    void show(Player player) {
        if (!eligible(player)) { clear(player); return; }
        HandGame game=(HandGame)room.board; int seat=room.seat(player.getUniqueId());
        PrivateView view=privateViews.computeIfAbsent(player.getUniqueId(),id -> new PrivateView(player));
        boolean callsEnabled=mahjong&&room.phase==Room.Phase.PLAYING&&room.undo==null;
        if (view.board==game && view.revision==room.revision && view.language==Language.generation() && view.seat==seat && view.callsEnabled==callsEnabled
                && view.pieces.values().stream().allMatch(PieceView::valid)&&view.calls.values().stream().allMatch(CallView::valid)&&view.cardButtons.values().stream().allMatch(CardButton::valid)) return;
        if ((view.seat!=seat || view.board!=game) && view.seat>=0) { restoreBacks(player,view.seat); view.remove();view.dismissedRevision=-1; }
        List<HandGame.Piece> hand=game.hand(seat); List<Spec> wanted=new ArrayList<>();
        for (int i=0;i<Math.min(54,hand.size());i++) {
            HandGame.Piece piece=hand.get(i);
            wanted.add(new Spec(piece.id(),piece.face(),handPose(seat,game.playerCount(),i,hand.size(),mahjong),false,true,seat));
        }
        Set<String> previous=Set.copyOf(view.pieces.keySet());
        boolean animate=!mahjong&&view.board==game&&room.history.size()==view.historySize+1&&!room.restoring;
        reconcile(view.pieces,wanted,player); hideOwnBacks(player,seat);
        clearMatches(view);view.hover=null;view.callGroup=null;int drawn=0;
        if(mahjong){
            if(view.remaining==null)view.remaining=text(origin,Component.empty(),.13f,(float)(-360.0*seat/game.playerCount()),false,"@remaining",player);
            else view.remaining.text(Component.empty());
        }
        for(var piece:view.pieces.values()){
            if(mahjong)piece.highlight(false);
            if(animate&&!previous.contains(piece.spec.id()))piece.drawFromDeck(Math.min(3,drawn++));
            else piece.shift(new Vector());
        }
        view.historySize=room.history.size();
        view.board=game; view.revision=room.revision; view.language=Language.generation(); view.seat=seat;
        updatePlayable(view);
        view.callsEnabled=callsEnabled;buildCalls(view);if(!mahjong)buildCardButtons(view);
    }
    private String pendingCard(PrivateView view){return room.board instanceof dev.tabletop3d.rules.LastCardGame game?game.pendingCard(view.seat):null;}
    private void buildCardButtons(PrivateView view){
        if(room.phase!=Room.Phase.PLAYING||room.undo!=null)return;
        List<String> legal=room.board.legalActions(view.seat);String pending=pendingCard(view);
        PieceView piece=pending==null?null:view.pieces.get(pending);
        if(piece!=null){
            piece.highlight(true);piece.shift(new Vector(0,CARD_LIFT,0));
            String[] colors={"r","b","y","p"};Material[] materials={Material.RED_CONCRETE,Material.BLUE_CONCRETE,Material.YELLOW_CONCRETE,Material.PURPLE_CONCRETE};
            for(int i=0;i<4;i++){
                Vector delta=rotated((i-1.5)*.10,0,0,piece.spec.pose());Pose base=piece.spec.pose();
                String action="play:"+pending+":"+colors[i];
                if(legal.contains(action))view.cardButtons.put(action,new CardButton(action,new Pose(base.x()+delta.getX(),base.z()+delta.getZ(),base.yaw(),base.lift()+CARD_LIFT+.28),materials[i],Component.text("8",NamedTextColor.WHITE),view.player,.085));
            }
        }else if(legal.contains("pass"))view.cardButtons.put("pass",new CardButton("pass",seatPose(view.seat,room.board.playerCount(),0,.65),Material.GRAY_CONCRETE,Language.component("table.card.pass"),view.player,.25));
    }
    String cardAction(Player player,String id){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null||mahjong)return null;
        List<String> legal=room.board.legalActions(view.seat);
        if(legal.contains("choose:"+id))return "choose:"+id;
        return legal.contains("play:"+id)?"play:"+id:null;
    }
    private void buildCalls(PrivateView view){
        view.clearCalls();
        if(!view.callsEnabled||view.dismissedRevision==room.revision)return;
        HandGame game=(HandGame)room.board;var groups=MahjongControls.groups(game.legalActions(view.seat));
        Map<String,String> buttons=new LinkedHashMap<>();
        if("riichi".equals(view.callGroup)){
            buttons.put("back","back");buttons.put("dismiss","dismiss");
        }else if(view.callGroup!=null){
            for(String choice:groups.getOrDefault(view.callGroup,List.of()))buttons.put("choice:"+choice,view.callGroup);
            buttons.put("back","back");
            if(groups.containsKey("pass"))buttons.put("pass","pass");
        }else for(String group:groups.keySet())buttons.put(group,group);
        int i=0;
        for(var button:buttons.entrySet()){
            int row=i/3,count=Math.min(3,buttons.size()-row*3);
            Pose base=seatPose(view.seat,game.playerCount(),(i%3-(count-1)/2.0)*.43,.79);i++;
            String action=button.getKey().startsWith("choice:")?button.getKey().substring(7):groups.getOrDefault(button.getKey(),List.of()).stream().findFirst().orElse("");
            List<String> faces=button.getKey().startsWith("choice:")&&Set.of("chi","pon","kan").contains(button.getValue())?MahjongPresentation.choiceFaces(game,view.seat,action):List.of();
            view.calls.put(button.getKey(),new CallView(button.getValue(),new Pose(base.x(),base.z(),base.yaw(),.30+row*.30),view.player,faces));
        }
    }
    boolean expandCall(Player player,String group){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null||!view.callsEnabled)return false;
        if(group.equals("back")){view.callGroup=null;updatePlayable(view);buildCalls(view);return true;}
        if(!Set.of("chi","pon","kan","riichi").contains(group))return false;
        if(MahjongControls.groups(((HandGame)room.board).legalActions(view.seat)).getOrDefault(group,List.of()).isEmpty())return false;
        view.callGroup=group;updatePlayable(view);buildCalls(view);
        if(group.equals("riichi"))player.sendActionBar(Language.component("hint.mahjong.riichi").colorIfAbsent(NamedTextColor.GOLD));
        return true;
    }
    private void updatePlayable(PrivateView view){
        Set<String> legal=Set.copyOf(((HandGame)room.board).legalActions(view.seat));
        if(!mahjong){
            for(PieceView piece:view.pieces.values())piece.playable(view.seat!=room.board.currentPlayer()||room.phase!=Room.Phase.PLAYING||legal.stream().anyMatch(a->a.equals("play:"+piece.spec.id())||a.startsWith("play:"+piece.spec.id()+":")));
            return;
        }
        String prefix="riichi".equals(view.callGroup)?"riichi:":"discard:";
        for(PieceView piece:view.pieces.values()){
            String id=piece.spec.id();boolean selected=legal.contains("exchange-remove:"+id);
            piece.playable(room.phase==Room.Phase.PLAYING&&(legal.contains(prefix+id)||legal.contains("exchange-add:"+id)||selected));
            if(selected){piece.highlight(true);piece.shift(new Vector(0,.035,0));}
        }
    }
    void keepHandPress(Player player){keepHandPress(player,System.nanoTime());}
    void keepHandPress(Player player,long now){PrivateView view=privateViews.get(player.getUniqueId());if(view!=null&&view.pressedTile!=null)view.pressedAt=now;}
    String handAction(Player player,String id){return handAction(player,id,System.nanoTime());}
    String handAction(Player player,String id,long now){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null||!mahjong)return null;
        String action=("riichi".equals(view.callGroup)?"riichi:":"discard:")+id;
        List<String> legal=((HandGame)room.board).legalActions(view.seat);
        if(legal.contains("exchange-remove:"+id))return "exchange-remove:"+id;
        if(legal.contains("exchange-add:"+id))return "exchange-add:"+id;
        if(!view.pieces.containsKey(id)||!legal.contains(action)){keepHandPress(player,now);return null;}
        Location eye=player.getEyeLocation();
        if(view.pressedEye!=null&&eye!=null&&!Objects.equals(id,view.pressedTile)&&now-view.pressedAt<600_000_000L
                &&eye.getWorld().equals(view.pressedEye.getWorld())&&eye.distanceSquared(view.pressedEye)<.0064
                &&eye.getDirection().dot(view.pressedEye.getDirection())>Math.cos(Math.toRadians(2))){view.pressedAt=now;return null;}
        view.pressedTile=id;view.pressedEye=eye==null?null:eye.clone();view.pressedAt=now;return action;
    }
    void hover(Player player,String selected){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null)return;
        PieceView target=selected!=null&&selected.startsWith("public:")?publicPieces.get(selected.substring(7)):view.pieces.get(selected);
        if(target==null||target.spec.back())selected=null;
        if(Objects.equals(view.hover,selected))return;view.hover=selected;
        List<PieceView> pieces=new ArrayList<>(view.pieces.values());int chosen=-1;
        List<String> legal=mahjong?((HandGame)room.board).legalActions(view.seat):List.of();
        for(int i=0;i<pieces.size();i++)if(pieces.get(i).spec.id().equals(selected))chosen=i;
        for(int i=0;i<pieces.size();i++){
            if(mahjong){boolean exchanging=legal.contains("exchange-remove:"+pieces.get(i).spec.id());pieces.get(i).highlight(exchanging||selected!=null&&MahjongPresentation.sameType(pieces.get(i).spec.face(),target.spec.face()));pieces.get(i).shift(new Vector(0,i==chosen?.065:exchanging?.035:0,0));continue;}
            PieceView piece=pieces.get(i);boolean raised=i==chosen||Objects.equals(piece.spec.id(),pendingCard(view));
            piece.highlight(raised);piece.shift(new Vector(0,raised?CARD_LIFT:0,0));
        }
        if(mahjong&&view.remaining!=null){
            clearMatches(view);
            if(selected==null)view.remaining.text(Component.empty());
            else{
                view.remaining.teleport(at(target.spec.pose()).add(0,.24,0));
                int remaining=MahjongPresentation.remaining((HandGame)room.board,view.seat,target.spec.face());
                Component hint=Language.component("table.mahjong.remaining","count",remaining).colorIfAbsent(remaining==0?NamedTextColor.RED:NamedTextColor.YELLOW);
                if(room.board instanceof dev.tabletop3d.rules.MahjongGame game&&game.noYaku(view.seat,target.spec.id().replaceFirst("^discard:[0-3]:","")))
                    hint=hint.append(Component.newline()).append(Language.component("table.mahjong.no-yaku").color(NamedTextColor.RED));
                view.remaining.text(hint);
                for(var entry:publicPieces.entrySet()){
                    PieceView piece=entry.getValue();
                    if(piece.spec.back()||!MahjongPresentation.sameType(piece.spec.face(),target.spec.face()))continue;
                    // Replace only this viewer's public body with an identical private glowing body.
                    Entity body=piece.parts.getFirst();player.hideEntity(plugin,body);
                    BlockDisplay copy=block(at(piece.spec.pose()),Material.SMOOTH_QUARTZ,piece.width(),piece.height(),piece.depth(),"@board",player);
                    copy.setGlowColorOverride(Color.YELLOW);copy.setGlowing(true);view.matches.put(entry.getKey(),copy);
                }
            }
        }
    }
    void dismissCalls(Player player){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());
        if(view!=null&&view.calls.containsKey("dismiss")){view.dismissedRevision=room.revision;view.callGroup=null;updatePlayable(view);view.clearCalls();}
    }
    String callHit(Player player,Location eye,Vector direction){
        if(!eligible(player)){clear(player);return null;}
        if(!origin.getWorld().equals(eye.getWorld())||direction.lengthSquared()<1e-12)return null;
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null)return null;
        Vector ray=direction.clone().normalize();double nearest=TableGeometry.REACH;String selected=null;
        for(var button:view.cardButtons.entrySet()){
            var hit=button.getValue().bounds.rayTrace(eye.toVector(),ray,nearest);
            if(hit!=null){nearest=hit.getHitPosition().distance(eye.toVector());selected="card:"+button.getKey();}
        }
        for(var call:view.calls.entrySet()){
            var hit=call.getValue().bounds.rayTrace(eye.toVector(),ray,nearest);
            if(hit!=null){nearest=hit.getHitPosition().distance(eye.toVector());selected=call.getKey();}
        }
        if(selected==null||origin.getWorld().rayTraceBlocks(eye,ray,Math.max(.001,nearest-.015),FluidCollisionMode.NEVER,true)!=null)return null;
        return selected;
    }
    void tick(){if(turnRing!=null)turnRing.tick();if(mahjongHud!=null)mahjongHud.tick();for(PrivateView view:privateViews.values())for(PieceView piece:view.pieces.values())piece.tick();}
    boolean deckHit(Location eye,Vector direction){
        if(mahjong||closed||!origin.getWorld().equals(eye.getWorld())||direction.lengthSquared()<1e-12)return false;
        Vector ray=direction.clone().normalize();
        BoundingBox box=new BoundingBox(origin.getX()-.32,origin.getY(),origin.getZ()-.145,origin.getX()-.12,origin.getY()+.09,origin.getZ()+.145);
        var hit=box.rayTrace(eye.toVector(),ray,TableGeometry.REACH);if(hit==null)return false;
        double distance=hit.getHitPosition().distance(eye.toVector());
        return origin.getWorld().rayTraceBlocks(eye,ray,Math.max(.001,distance-.015),FluidCollisionMode.NEVER,true)==null;
    }
    private void hideOwnBacks(Player player,int seat) {
        publicPieces.values().stream().filter(piece -> piece.spec.id().startsWith("back:") && piece.spec.seat()==seat).forEach(piece -> piece.parts.forEach(entity -> player.hideEntity(plugin,entity)));
    }
    private void restoreBacks(Player player,int seat) {
        publicPieces.values().stream().filter(piece -> piece.spec.id().startsWith("back:") && piece.spec.seat()==seat).forEach(piece -> piece.parts.forEach(entity -> player.showEntity(plugin,entity)));
    }
    void clear(Player player) {
        PrivateView view=privateViews.remove(player.getUniqueId());
        if (view!=null) { clearMatches(view);view.remove(); restoreBacks(player,view.seat); }
    }
    private void clearMatches(PrivateView view){
        for(var entry:view.matches.entrySet()){
            PieceView piece=publicPieces.get(entry.getKey());
            if(piece!=null)view.player.showEntity(plugin,piece.parts.getFirst());
            entry.getValue().remove();
        }
        view.matches.clear();
    }
    String hit(Player player,Location eye,Vector direction) {
        if (!eligible(player)) { clear(player); return null; }
        if (!origin.getWorld().equals(eye.getWorld()) || direction.lengthSquared()<1e-12) return null;
        show(player); PrivateView view=privateViews.get(player.getUniqueId());
        if (view==null) return null;
        Vector ray=direction.clone().normalize(); String selected=null; double nearest=TableGeometry.REACH;
        for (PieceView piece:view.pieces.values()) {
            if(!mahjong){
                double distance=piece.cardHitDistance(eye,ray,nearest,piece.spec.id().equals(view.hover));
                if(distance<=nearest){nearest=distance;selected=piece.spec.id();}
                continue;
            }
            var hit=piece.bounds(piece.spec.id().equals(view.hover)).rayTrace(eye.toVector(),ray,nearest);
            if (hit!=null) { nearest=hit.getHitPosition().distance(eye.toVector()); selected=piece.spec.id(); }
        }
        if(mahjong)for(PieceView piece:publicPieces.values()){
            if(piece.spec.back())continue;
            var hit=piece.bounds(false).rayTrace(eye.toVector(),ray,nearest);
            if(hit!=null){nearest=hit.getHitPosition().distance(eye.toVector());selected="public:"+piece.spec.id();}
        }
        if (selected==null || origin.getWorld().rayTraceBlocks(eye,ray,Math.max(.001,nearest-.015),FluidCollisionMode.NEVER,true)!=null) return null;
        return selected;
    }
    static String faceLabel(String face,boolean mahjong) {
        if (face.isEmpty() || face.equals("back")) return "";
        if (!mahjong) return face.equals("wild") ? "8" : face.equals("swap")?"Swap":switch (face.substring(1)) {
            case "Skip" -> "X"; case "Reverse" -> "<>"; case "Draw1" -> "+1"; default -> face.substring(1);
        };
        if (face.charAt(0)=='z') return switch(face.substring(1)) {case "1"->"E";case "2"->"S";case "3"->"W";case "4"->"N";case "5"->"Wh";case "6"->"G";case "7"->"R";default->"?";};
        if (face.charAt(0)=='f') return "F"+face.substring(1);
        return (face.charAt(1)=='0'?"5":face.substring(1))+face.charAt(0);
    }
    @Override public void close() {
        if (closed) return; closed=true;
        if(turnRing!=null)turnRing.close();
        if(mahjongHud!=null)mahjongHud.close();
        privateViews.values().forEach(view->{clearMatches(view);view.remove();}); privateViews.clear();
        publicPieces.values().forEach(PieceView::remove); publicPieces.clear(); furniture.forEach(Entity::remove); furniture.clear();
    }
}
