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
    private final List<TextDisplay> arrows = new ArrayList<>();
    private HandGame rendered;
    private long revision = -1, language = -1;
    private boolean closed;

    record Pose(double x, double z, float yaw, double lift) {
        Pose(double x,double z,float yaw){this(x,z,yaw,0);}
    }
    private record Spec(String id, String face, Pose pose, boolean back, boolean standing, int seat) {}
    private final class PieceView {
        Spec spec;
        final List<Entity> parts = new ArrayList<>();
        final List<Vector> offsets = new ArrayList<>();
        PieceView(Spec spec, Player viewer) { this.spec = spec; build(viewer); }
        void add(Entity entity, Vector offset) { parts.add(entity); offsets.add(offset); }
        Vector displacement=new Vector(), start=new Vector(), target=new Vector();
        int frame=4, duration=4, delay;
        boolean drawing;
        double width(){return mahjong?.094:.168;}
        double height(){return spec.standing()?(mahjong?.14:.25):.012;}
        double depth(){return spec.standing()?(mahjong?.052:.008):(mahjong?.146:.245);}
        void build(Player viewer) {
            Location at=at(spec.pose());String id=viewer==null?"@board":"@hand:"+spec.id();
            double scale=mahjong&&!spec.standing()&&viewer==null?.66:1;
            double w=width()*scale,h=height()*scale,d=depth()*scale;
            add(block(at,Material.SMOOTH_QUARTZ,w,h,d,id,viewer),new Vector());
            if(spec.back()){
                Vector panel=spec.standing()?rotated(0,.012,d/2+.002,spec.pose()):new Vector(0,.014*scale,0);
                add(block(at.clone().add(panel),Material.GREEN_CONCRETE,w*.84,spec.standing()?h-.024:.002,spec.standing()?.002:d*.86,id,viewer),panel);
            }else{
                var sprite=HandSprites.of(mahjong,spec.face());
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
                    add(block(at.clone().add(offset),rect.material(),rw,spec.standing()?rh:.002,spec.standing()?.001:rh,id,viewer),offset);
                }
            }
            if(spec.standing()){
                Vector back=rotated(0,.012,-d/2-.002,spec.pose());
                add(block(at.clone().add(back),Material.GREEN_CONCRETE,w*.84,h-.024,.002,id,viewer),back);
                if(!mahjong){
                    for(int i=0;i<3;i++){
                        Vector stripe=rotated(0,.055+i*.052,-d/2-.004,spec.pose());
                        add(block(at.clone().add(stripe),Material.SMOOTH_QUARTZ,w*.55,.006,.001,id,viewer),stripe);
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
        BoundingBox bounds(boolean sticky) {
            Location base=at(spec.pose()),at=base.clone().add(displacement);
            double angle=Math.toRadians(spec.pose().yaw()),w=width()/2+.003,d=spec.standing()?depth()/2+.003:depth()/2;
            double rx=Math.abs(Math.cos(angle))*w+Math.abs(Math.sin(angle))*d;
            double rz=Math.abs(Math.sin(angle))*w+Math.abs(Math.cos(angle))*d;
            BoundingBox box=new BoundingBox(at.getX()-rx,at.getY(),at.getZ()-rz,at.getX()+rx,at.getY()+height(),at.getZ()+rz);
            if(sticky&&!drawing)box.union(new BoundingBox(base.getX()-rx,base.getY(),base.getZ()-rz,base.getX()+rx,base.getY()+height(),base.getZ()+rz));
            return box;
        }
    }
    private static final class PrivateView {
        final Player player;
        final Map<String,PieceView> pieces = new LinkedHashMap<>();
        HandGame board;
        long revision = -1, language = -1;
        int seat = -1, historySize;
        String hover;
        PrivateView(Player player) { this.player=player; }
        void remove() { pieces.values().forEach(PieceView::remove); pieces.clear(); }
    }

    HandTable(Tabletop3D plugin, Room room, Location surfaceOrigin, NamespacedKey tag) {
        this.plugin=plugin; this.room=room; this.origin=surfaceOrigin.clone(); this.tag=tag; mahjong=room.kind.equals("mahjong");
        furniture.add(block(origin.clone().add(0,.003,0),Material.GREEN_TERRACOTTA,2.02,.008,2.02,"@board",null));
        double deckX=mahjong?-.085:-.22;
        Location deck=origin.clone().add(deckX,.015,0);
        furniture.add(block(deck,Material.SMOOTH_QUARTZ,mahjong?.13:.18,.045,mahjong?.18:.27,"@board",null));
        furniture.add(block(deck.clone().add(0,.046,0),Material.GREEN_CONCRETE,mahjong?.11:.16,.003,mahjong?.16:.25,"@board",null));
        deckLabel=text(origin.clone().add(deckX,.058,-.14),Component.empty(),.16f,0,true,"@board",null);
        furniture.add(deckLabel);
        if(!mahjong)for(int i=0;i<4;i++){
            Pose pose=seatPose(i,4,0,.40);
            TextDisplay arrow=text(origin.clone().add(pose.x(),.018,pose.z()),Component.text("➜",NamedTextColor.WHITE),.32f,pose.yaw()+90,true,"@board",null);
            arrows.add(arrow);furniture.add(arrow);
        }
        sync();
    }
    static Pose handPose(int seat,int players,int index,int count,boolean mahjong) {
        int columns=mahjong?14:18,row=index/columns,inRow=Math.min(columns,Math.max(1,count-row*columns));
        double unit=mahjong?.101:Math.min(.155,(players==3?1.24:1.62)/Math.max(1,inRow-1));
        double centered=index%columns-(inRow-1)/2.0,tangent=centered*unit;
        Pose base=seatPose(seat,players,tangent,(mahjong?.79:players==3?.78:.91)-row*(mahjong?.085:.27));
        return mahjong?base:new Pose(base.x(),base.z(),base.yaw()+(float)(centered*1.2),row*.055);
    }
    static Pose riverPose(int seat,int players,int index) {
        return seatPose(seat,players,(index%6-2.5)*.068,.49-(index/6)*.10);
    }
    static Pose exposedPose(int seat,int players,int index) {
        return seatPose(seat,players,(index%14-6.5)*.068,.61+(index/14)*.10);
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
            int count=Math.min(52,game.handSize(seat));
            for (int i=0;i<count;i++) wanted.add(new Spec("back:"+seat+":"+i,"",handPose(seat,game.playerCount(),i,count,mahjong),true,true,seat));
            if (mahjong) {
                publicRow(wanted,game.discards(seat),"discard:"+seat,seat,game.playerCount(),false);
                publicRow(wanted,game.exposed(seat),"exposed:"+seat,seat,game.playerCount(),true);
            }
        }
        if (!mahjong) game.cells().stream().filter(cell -> cell.id().equals("discard") && !cell.piece().isEmpty())
                .findFirst().ifPresent(cell -> wanted.add(new Spec("discard",cell.piece(),new Pose(.16,0,0),false,false,-1)));
        reconcile(publicPieces,wanted,null);
        deckLabel.text(Language.component("table.hand.deck","count",game.deckSize()).append(Component.newline()).append(HandText.tableHint(room.kind,game)));
        if(!mahjong){boolean clockwise=!game.publicInfo().getOrDefault("direction","Clockwise").equals("Counterclockwise");
            for(int i=0;i<arrows.size();i++)arrows.get(i).setRotation(-i*90+(clockwise?0:180),-90);
        }
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
        if (view.board==game && view.revision==room.revision && view.language==Language.generation() && view.seat==seat && view.pieces.values().stream().allMatch(PieceView::valid)) return;
        if ((view.seat!=seat || view.board!=game) && view.seat>=0) { restoreBacks(player,view.seat); view.remove(); }
        List<HandGame.Piece> hand=game.hand(seat); List<Spec> wanted=new ArrayList<>();
        for (int i=0;i<Math.min(52,hand.size());i++) {
            HandGame.Piece piece=hand.get(i);
            wanted.add(new Spec(piece.id(),piece.face(),handPose(seat,game.playerCount(),i,hand.size(),mahjong),false,true,seat));
        }
        Set<String> previous=Set.copyOf(view.pieces.keySet());
        boolean animate=!mahjong&&view.board==game&&room.history.size()==view.historySize+1&&!room.restoring;
        reconcile(view.pieces,wanted,player); hideOwnBacks(player,seat);
        view.hover=null;int drawn=0;
        for(var piece:view.pieces.values()){
            if(animate&&!previous.contains(piece.spec.id()))piece.drawFromDeck(Math.min(3,drawn++));
            else piece.shift(new Vector());
        }
        view.historySize=room.history.size();
        view.board=game; view.revision=room.revision; view.language=Language.generation(); view.seat=seat;
    }
    void hover(Player player,String selected){
        show(player);PrivateView view=privateViews.get(player.getUniqueId());if(view==null)return;
        if(selected!=null&&!view.pieces.containsKey(selected))selected=null;
        if(Objects.equals(view.hover,selected))return;view.hover=selected;
        List<PieceView> pieces=new ArrayList<>(view.pieces.values());int chosen=-1;
        for(int i=0;i<pieces.size();i++)if(pieces.get(i).spec.id().equals(selected))chosen=i;
        for(int i=0;i<pieces.size();i++){
            PieceView piece=pieces.get(i);double lift=i==chosen?.105:0;
            double gap=chosen<0||i==chosen?0:Math.signum(i-chosen)*.045/Math.max(1,Math.abs(i-chosen));
            piece.shift(rotated(gap,lift,0,new Pose(0,0,(float)(-360.0*view.seat/((HandGame)room.board).playerCount()))));
        }
    }
    void tick(){for(PrivateView view:privateViews.values())for(PieceView piece:view.pieces.values())piece.tick();}
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
        if (view!=null) { view.remove(); restoreBacks(player,view.seat); }
    }
    String hit(Player player,Location eye,Vector direction) {
        if (!eligible(player)) { clear(player); return null; }
        if (!origin.getWorld().equals(eye.getWorld()) || direction.lengthSquared()<1e-12) return null;
        show(player); PrivateView view=privateViews.get(player.getUniqueId());
        if (view==null) return null;
        Vector ray=direction.clone().normalize(); String selected=null; double nearest=TableGeometry.REACH;
        for (PieceView piece:view.pieces.values()) {
            var hit=piece.bounds(piece.spec.id().equals(view.hover)).rayTrace(eye.toVector(),ray,nearest);
            if (hit!=null) { nearest=hit.getHitPosition().distance(eye.toVector()); selected=piece.spec.id(); }
        }
        if (selected==null || origin.getWorld().rayTraceBlocks(eye,ray,Math.max(.001,nearest-.015),FluidCollisionMode.NEVER,true)!=null) return null;
        return selected;
    }
    static String faceLabel(String face,boolean mahjong) {
        if (face.isEmpty() || face.equals("back")) return "";
        if (!mahjong) return face.equals("wild") ? "W" : switch (face.substring(1)) {
            case "Skip" -> "X"; case "Reverse" -> "<>"; case "Draw2" -> "+2"; case "Draw3" -> "+3"; default -> face.substring(1);
        };
        if (face.charAt(0)=='z') return switch(face.substring(1)) {case "1"->"E";case "2"->"S";case "3"->"W";case "4"->"N";case "5"->"Wh";case "6"->"G";case "7"->"R";default->"?";};
        if (face.charAt(0)=='f') return "F"+face.substring(1);
        return (face.charAt(1)=='0'?"5":face.substring(1))+face.charAt(0);
    }
    @Override public void close() {
        if (closed) return; closed=true;
        privateViews.values().forEach(PrivateView::remove); privateViews.clear();
        publicPieces.values().forEach(PieceView::remove); publicPieces.clear(); furniture.forEach(Entity::remove); furniture.clear();
    }
}
