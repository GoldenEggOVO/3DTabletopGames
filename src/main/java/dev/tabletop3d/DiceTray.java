package dev.tabletop3d;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native side table and one reusable die, advanced by the owning table's existing tick. */
final class DiceTray implements AutoCloseable {
    private static final double FELT=.015;
    private final Tabletop3D plugin;
    private final Room room;
    private final NamespacedKey tag;
    private final Location center;
    private final double width,size,innerHalf;
    private final List<Entity> entities=new ArrayList<>();
    private final List<Pip> pips=new ArrayList<>();
    private final BlockDisplay die;
    private final TextDisplay label;
    private Component lastLabel=Component.empty();
    private DiceMotion motion;
    private DiceMotion.Pose pose;
    private int frame;
    private boolean closed;
    private record Pip(BlockDisplay display,Vector3f center,Quaternionf face) {}

    DiceTray(Tabletop3D plugin,Room room,Location tableOrigin,NamespacedKey tag,boolean compact) {
        this.plugin=plugin;this.room=room;this.tag=tag;
        center=tableOrigin.clone().add(compact?1.42:2,TableGeometry.SURFACE,0);
        width=compact?.5:1.4;size=compact?.17:.28;innerHalf=compact?.21:.61;
        double rim=(width-innerHalf*2)/2;
        block(Material.DARK_OAK_PLANKS,0,-.16,0,width,.13,width);
        block(Material.GREEN_CONCRETE,0,-.03,0,innerHalf*2,FELT+.03,innerHalf*2);
        double leg=compact?.07:.12,offset=width/2-leg*.8;
        for(double x:new double[]{-offset,offset})for(double z:new double[]{-offset,offset})
            block(Material.STRIPPED_DARK_OAK_LOG,x,-TableGeometry.SURFACE,z,leg,TableGeometry.SURFACE-.12,leg);
        for(double side:new double[]{-1,1}) {
            block(Material.STRIPPED_DARK_OAK_WOOD,side*(width-rim)/2,-.03,0,rim,.13,width);
            block(Material.STRIPPED_DARK_OAK_WOOD,0,-.03,side*(width-rim)/2,innerHalf*2,.13,rim);
        }
        die=block(Material.WHITE_CONCRETE,0,0,0,size,size,size);
        int[][] coordinates={{0,0},{-1,-1},{1,1},{-1,1},{1,-1},{-1,0},{1,0}};
        for(int face=1;face<=6;face++) {
            Quaternionf rotation=TableView.faceRotation(face);
            for(int index:TableView.pipIndices(face)) {
                Vector3f offset3=new Vector3f((float)(coordinates[index][0]*size*.28),(float)(size/2+.002),
                    (float)(coordinates[index][1]*size*.28)).rotate(rotation);
                BlockDisplay dot=block(Material.BLACK_CONCRETE,0,0,0,size*.13,.004,size*.13);
                pips.add(new Pip(dot,offset3,rotation));
            }
        }
        label=center.getWorld().spawn(center.clone().add(0,.125,width/2+.015),TextDisplay.class,d->{
            display(d);d.setRotation(0,-90);d.setBillboard(Display.Billboard.FIXED);d.text(lastLabel);
            d.setLineWidth(Integer.MAX_VALUE);d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);d.setBackgroundColor(Color.fromARGB(0,0,0,0));d.setShadowed(false);d.setSeeThrough(false);
            d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(compact?.13f:.22f),new Quaternionf()));
        });entities.add(label);
        Interaction interaction=center.getWorld().spawn(center.clone().add(0,-.03,0),Interaction.class,e->{
            tag(e);e.setInteractionWidth((float)width);e.setInteractionHeight(.14f);e.setResponsive(true);
        });entities.add(interaction);
        settle(1);
    }

    void settle(int face){motion=null;frame=0;pose=DiceMotion.rest(size,face);render();}
    void roll(int face,long variationSeed){if(closed)return;motion=new DiceMotion(size,innerHalf,pose,face,variationSeed);frame=0;}
    void tick() {
        if(!rolling())return;
        frame++;pose=motion.pose(frame);render();
        if(motion.impact(frame)) {
            TableSounds.Cue cue=TableSounds.WOOD;
            TableSounds.play(plugin,center.clone().add(pose.x(),FELT,pose.z()),
                new TableSounds.Cue(cue.sound(),cue.volume()*(float)Math.pow(.66,(frame-8)/4.0),cue.pitch()+frame*.013f));
        }
        if(frame>=DiceMotion.FRAMES)motion=null;
    }
    boolean rolling(){return !closed&&motion!=null;}
    void label(Component text){if(!closed&&!text.equals(lastLabel)){lastLabel=text;label.text(text);}}
    Location center(){return center.clone();}

    double hit(Location eye,Vector normalizedDirection) {
        if(closed||!center.getWorld().equals(eye.getWorld()))return -1;
        Vector from=eye.toVector();double nearest=-1;
        BoundingBox tabletop=new BoundingBox(center.getX()-width/2,center.getY()-.03,center.getZ()-width/2,
            center.getX()+width/2,center.getY()+.11,center.getZ()+width/2);
        double radius=size*Math.sqrt(3)/2;
        BoundingBox cube=new BoundingBox(center.getX()+pose.x()-radius,center.getY()+FELT+pose.y()-radius,center.getZ()+pose.z()-radius,
            center.getX()+pose.x()+radius,center.getY()+FELT+pose.y()+radius,center.getZ()+pose.z()+radius);
        for(BoundingBox box:List.of(tabletop,cube)) {
            var hit=box.rayTrace(from,normalizedDirection,TableGeometry.REACH);
            if(hit!=null){double distance=hit.getHitPosition().distance(from);if(nearest<0||distance<nearest)nearest=distance;}
        }
        return nearest;
    }

    private void render() {
        if(closed)return;
        Quaternionf rotation=pose.rotation();Vector3f position=new Vector3f((float)pose.x(),(float)(FELT+pose.y()),(float)pose.z());
        transform(die,new Vector3f((float)(-size/2)).rotate(rotation).add(position),rotation,new Vector3f((float)size));
        for(Pip pip:pips) {
            Quaternionf orientation=new Quaternionf(rotation).mul(pip.face());
            Vector3f corner=new Vector3f((float)(size*.065),.002f,(float)(size*.065)).rotate(orientation);
            Vector3f at=new Vector3f(pip.center()).rotate(rotation).add(position).sub(corner);
            transform(pip.display(),at,orientation,new Vector3f((float)(size*.13),.004f,(float)(size*.13)));
        }
    }

    private void transform(BlockDisplay display,Vector3f translation,Quaternionf rotation,Vector3f scale) {
        display.setInterpolationDelay(0);display.setTransformation(new Transformation(translation,new Quaternionf(rotation),scale,new Quaternionf()));
    }
    private void tag(Entity entity) {
        entity.setPersistent(false);entity.setGravity(false);entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|@roll");
    }
    private void display(Display display) {
        tag(display);display.setBrightness(new Display.Brightness(15,15));display.setViewRange(.35f);
        display.setTeleportDuration(2);display.setInterpolationDuration(2);
    }
    private BlockDisplay block(Material material,double x,double y,double z,double w,double h,double depth) {
        BlockDisplay block=center.getWorld().spawn(center,BlockDisplay.class,d->{
            display(d);d.setBlock(material.createBlockData());
            d.setTransformation(new Transformation(new Vector3f((float)(x-w/2),(float)y,(float)(z-depth/2)),new Quaternionf(),
                new Vector3f((float)w,(float)h,(float)depth),new Quaternionf()));
        });entities.add(block);return block;
    }
    @Override public void close(){if(closed)return;closed=true;motion=null;entities.forEach(Entity::remove);entities.clear();pips.clear();}
}
