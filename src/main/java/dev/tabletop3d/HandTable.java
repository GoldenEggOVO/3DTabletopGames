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
    private HandGame rendered;
    private long revision = -1, language = -1;
    private boolean closed;

    record Pose(double x, double z, float yaw) {}
    private record Spec(String id, String face, Pose pose, boolean back, boolean standing, int seat) {}
    private final class PieceView {
        Spec spec;
        final List<Entity> parts = new ArrayList<>();
        final List<Vector> offsets = new ArrayList<>();
        PieceView(Spec spec, Player viewer) { this.spec = spec; build(viewer); }
        void add(Entity entity, Vector offset) { parts.add(entity); offsets.add(offset); }
        void build(Player viewer) {
            Location at = at(spec.pose());
            String id = viewer == null ? "@board" : "@hand:" + spec.id();
            double scale=mahjong && !spec.standing() && viewer==null ? .66 : 1;
            double width = .094*scale, depth = (spec.standing() ? .052 : .146)*scale, height = (spec.standing() ? .14 : .012)*scale;
            add(block(at, Material.SMOOTH_QUARTZ, width, height, depth, id, viewer), new Vector());
            Vector panel = spec.standing() ? rotated(0,.015,.029,spec.pose()) : new Vector(0,.013*scale,0);
            Material color = spec.back() ? Material.GREEN_CONCRETE : faceMaterial(spec.face(),mahjong);
            add(block(at.clone().add(panel),color,width*.82,spec.standing()?.115:.003,spec.standing()?.003:depth*.80,id,viewer),panel);
            if (mahjong && spec.standing()) {
                Vector back=rotated(0,.015,-.029,spec.pose());
                add(block(at.clone().add(back),Material.GREEN_CONCRETE,width*.82,.115,.003,id,viewer),back);
            }
            if (!spec.back()) {
                Vector label = spec.standing() ? rotated(0,.055,.032,spec.pose()) : new Vector(0,.018*scale,0);
                NamedTextColor ink = mahjong ? faceInk(spec.face()) : NamedTextColor.WHITE;
                add(text(at.clone().add(label),Component.text(faceLabel(spec.face(),mahjong),ink),(float)(.18*scale),spec.pose().yaw(),!spec.standing(),id,viewer),label);
                if (!mahjong) {
                    Vector corner = rotated(-.025,.019,-.043,spec.pose());
                    add(text(at.clone().add(corner),Component.text(faceLabel(spec.face(),false),NamedTextColor.WHITE),.075f,spec.pose().yaw(),true,id,viewer),corner);
                }
            }
        }
        void move(Spec next) {
            if (!spec.pose().equals(next.pose())) for (int i=0;i<parts.size();i++) {
                Location nextAt = at(next.pose()).add(offsets.get(i));
                nextAt.setYaw(next.pose().yaw());
                nextAt.setPitch(parts.get(i) instanceof TextDisplay && !next.standing() ? -90 : 0);
                parts.get(i).teleport(nextAt);
            }
            spec = next;
        }
        boolean valid() { return parts.stream().allMatch(Entity::isValid); }
        void remove() { parts.forEach(Entity::remove); }
        BoundingBox bounds() {
            Location at = at(spec.pose());
            double angle = Math.toRadians(spec.pose().yaw()), w=.049, d=spec.standing()?.031:.076;
            double rx=Math.abs(Math.cos(angle))*w+Math.abs(Math.sin(angle))*d;
            double rz=Math.abs(Math.sin(angle))*w+Math.abs(Math.cos(angle))*d;
            return new BoundingBox(at.getX()-rx,at.getY(),at.getZ()-rz,at.getX()+rx,at.getY()+(spec.standing()?.15:.025),at.getZ()+rz);
        }
    }
    private static final class PrivateView {
        final Player player;
        final Map<String,PieceView> pieces = new LinkedHashMap<>();
        HandGame board;
        long revision = -1, language = -1;
        int seat = -1;
        PrivateView(Player player) { this.player=player; }
        void remove() { pieces.values().forEach(PieceView::remove); pieces.clear(); }
    }

    HandTable(Tabletop3D plugin, Room room, Location surfaceOrigin, NamespacedKey tag) {
        this.plugin=plugin; this.room=room; this.origin=surfaceOrigin.clone(); this.tag=tag; mahjong=room.kind.equals("mahjong");
        furniture.add(block(origin.clone().add(0,.003,0),Material.GREEN_TERRACOTTA,2.02,.008,2.02,"@board",null));
        double deckX=mahjong?-.085:-.19;
        Location deck=origin.clone().add(deckX,.015,0);
        furniture.add(block(deck,Material.SMOOTH_QUARTZ,.13,.035,.18,"@board",null));
        furniture.add(block(deck.clone().add(0,.036,0),Material.GREEN_CONCRETE,.11,.003,.16,"@board",null));
        deckLabel=text(origin.clone().add(deckX,.058,-.14),Component.empty(),.16f,0,true,"@board",null);
        furniture.add(deckLabel); sync();
    }
    static Pose handPose(int seat,int players,int index,int count,boolean mahjong) {
        int columns=14, row=index/columns, inRow=Math.min(columns,Math.max(1,count-row*columns));
        double tangent=(index%columns-(inRow-1)/2.0)*.101;
        return seatPose(seat,players,tangent,.79-row*(mahjong?.085:.16));
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
        Location at=origin.clone().add(pose.x(),.017,pose.z()); at.setYaw(pose.yaw()); return at;
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
            for (int i=0;i<count;i++) wanted.add(new Spec("back:"+seat+":"+i,"",handPose(seat,game.playerCount(),i,count,mahjong),true,mahjong,seat));
            if (mahjong) {
                publicRow(wanted,game.discards(seat),"discard:"+seat,seat,game.playerCount(),false);
                publicRow(wanted,game.exposed(seat),"exposed:"+seat,seat,game.playerCount(),true);
            }
        }
        if (!mahjong) game.cells().stream().filter(cell -> cell.id().equals("discard") && !cell.piece().isEmpty())
                .findFirst().ifPresent(cell -> wanted.add(new Spec("discard",cell.piece(),new Pose(.12,0,0),false,false,-1)));
        reconcile(publicPieces,wanted,null);
        deckLabel.text(Language.component("table.hand.deck","count",game.deckSize()).append(Component.newline()).append(HandText.tableHint(room.kind,game)));
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
        if (view.seat!=seat && view.seat>=0) { restoreBacks(player,view.seat); view.remove(); }
        List<HandGame.Piece> hand=game.hand(seat); List<Spec> wanted=new ArrayList<>();
        for (int i=0;i<Math.min(52,hand.size());i++) {
            HandGame.Piece piece=hand.get(i);
            wanted.add(new Spec(piece.id(),piece.face(),handPose(seat,game.playerCount(),i,hand.size(),mahjong),false,mahjong,seat));
        }
        reconcile(view.pieces,wanted,player); hideOwnBacks(player,seat);
        view.board=game; view.revision=room.revision; view.language=Language.generation(); view.seat=seat;
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
            var hit=piece.bounds().rayTrace(eye.toVector(),ray,nearest);
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
    private static NamedTextColor faceInk(String face) {
        if (face.endsWith("0") || face.equals("z7")) return NamedTextColor.DARK_RED;
        if (face.startsWith("s") || face.equals("z6")) return NamedTextColor.DARK_GREEN;
        return NamedTextColor.BLACK;
    }
    private static Material faceMaterial(String face,boolean mahjong) {
        if (mahjong) return Material.WHITE_CONCRETE;
        if (face.isEmpty()) return Material.GREEN_CONCRETE;
        return switch(face.charAt(0)) {case 'r'->Material.RED_CONCRETE;case 'b'->Material.BLUE_CONCRETE;case 'y'->Material.YELLOW_TERRACOTTA;case 'p'->Material.PURPLE_CONCRETE;default->Material.BLACK_CONCRETE;};
    }
    @Override public void close() {
        if (closed) return; closed=true;
        privateViews.values().forEach(PrivateView::remove); privateViews.clear();
        publicPieces.values().forEach(PieceView::remove); publicPieces.clear(); furniture.forEach(Entity::remove); furniture.clear();
    }
}
