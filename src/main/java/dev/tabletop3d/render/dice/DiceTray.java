package dev.tabletop3d.render.dice;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.audio.TableSounds;
import dev.tabletop3d.render.TableAudience;
import dev.tabletop3d.render.TableGeometry;
import dev.tabletop3d.room.Room;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class DiceTray implements AutoCloseable {
    private static final double FELT=.015;
    private final Tabletop3D plugin;
    private final Room room;
    private final NamespacedKey tag;
    private final Location center;
    private final double width,size,innerHalf;
    private final TableAudience audience;
    private final boolean refreshAudience;
    private final List<Entity> entities=new ArrayList<>();
    private final List<Entity> nativeTable=new ArrayList<>();
    private ItemDisplay packedTable;
    private YachtDie nativeDie,packedDie;
    private final TextDisplay label;
    private Component lastLabel=Component.empty();
    private DiceMotion motion;
    private DiceMotion.Pose pose;
    private int frame;
    private boolean closed;

    public DiceTray(Tabletop3D plugin,Room room,Location tableOrigin,NamespacedKey tag,boolean compact) {
        this(plugin,room,tableOrigin,tag,compact,new TableAudience(plugin,tableOrigin),true);
    }

    public DiceTray(Tabletop3D plugin,Room room,Location tableOrigin,NamespacedKey tag,boolean compact,TableAudience audience) {
        this(plugin,room,tableOrigin,tag,compact,audience,false);
    }

    private DiceTray(Tabletop3D plugin,Room room,Location tableOrigin,NamespacedKey tag,boolean compact,
            TableAudience audience,boolean refreshAudience) {
        this.plugin=plugin;this.room=room;this.tag=tag;
        this.audience=audience;this.refreshAudience=refreshAudience;
        center=tableOrigin.clone().add(compact?1.42:2,TableGeometry.SURFACE,0);
        width=compact?.5:1.4;size=compact?.17:.28;innerHalf=compact?.21:.61;
        double leg=compact?.07:.12,offset=width/2-leg*.8;
        for(double x:new double[]{-offset,offset})for(double z:new double[]{-offset,offset})
            entities.add(block(Material.STRIPPED_DARK_OAK_LOG,x,-TableGeometry.SURFACE,z,leg,TableGeometry.SURFACE-.12,leg));
        label=center.getWorld().spawn(center.clone().add(0,.125,width/2+.015),TextDisplay.class,d->{
            display(d);d.setRotation(0,-90);d.setBillboard(Display.Billboard.FIXED);d.text(lastLabel);
            d.setLineWidth(Integer.MAX_VALUE);d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(false);d.setBackgroundColor(Color.fromARGB(0,0,0,0));d.setShadowed(false);d.setSeeThrough(false);
            d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(compact?.13f:.22f),new Quaternionf()));
        });entities.add(label);
        Interaction interaction=center.getWorld().spawn(center.clone().add(0,-.03,0),Interaction.class,e->{
            tag(e);e.setInteractionWidth((float)width);e.setInteractionHeight(.14f);e.setResponsive(true);
        });entities.add(interaction);
        entities.forEach(audience::common);
        settle(1);
        layers();
    }

    public void settle(int face){motion=null;frame=0;pose=DiceMotion.rest(size,face);render();}
    public void roll(int face,long variationSeed){if(closed)return;motion=new DiceMotion(size,innerHalf,pose,face,variationSeed);frame=0;}
    public void tick() {
        if(closed)return;
        if(refreshAudience)audience.refresh();
        layers();
        if(!rolling())return;
        frame++;pose=motion.pose(frame);render();
        if(motion.impact(frame)) {
            TableSounds.Cue cue=TableSounds.WOOD;
            TableSounds.play(plugin,center.clone().add(pose.x(),FELT,pose.z()),
                new TableSounds.Cue(cue.sound(),cue.volume()*(float)Math.pow(.66,(frame-8)/4.0),cue.pitch()+frame*.013f));
        }
        if(frame>=DiceMotion.FRAMES)motion=null;
    }
    public boolean rolling(){return !closed&&motion!=null;}
    public void label(Component text){if(!closed&&!text.equals(lastLabel)){lastLabel=text;label.text(text);}}
    Location center(){return center.clone();}

    public double hit(Location eye,Vector normalizedDirection) {
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
        DiceMotion.Pose at=new DiceMotion.Pose(pose.x(),FELT+pose.y(),pose.z(),pose.rotation());
        if(nativeDie!=null)nativeDie.pose(at);
        if(packedDie!=null)packedDie.pose(at);
    }

    private void layers() {
        if(audience.needed(false)&&nativeTable.isEmpty()) {
            double rim=(width-innerHalf*2)/2;
            nativeTable.add(block(Material.DARK_OAK_PLANKS,0,-.16,0,width,.13,width));
            nativeTable.add(block(Material.GREEN_CONCRETE,0,-.03,0,innerHalf*2,FELT+.03,innerHalf*2));
            for(double side:new double[]{-1,1}) {
                nativeTable.add(block(Material.STRIPPED_DARK_OAK_WOOD,side*(width-rim)/2,-.03,0,rim,.13,width));
                nativeTable.add(block(Material.STRIPPED_DARK_OAK_WOOD,0,-.03,side*(width-rim)/2,innerHalf*2,.13,rim));
            }
            nativeTable.forEach(entity->audience.add(entity,false));
            nativeDie=new YachtDie(plugin,room,center,tag,audience,false,size,"@roll");
            render();
        } else if(!audience.needed(false)&&!nativeTable.isEmpty()) {
            nativeTable.forEach(audience::remove);nativeTable.clear();nativeDie.close();nativeDie=null;
        }
        if(audience.needed(true)&&packedTable==null) {
            packedTable=center.getWorld().spawn(center,ItemDisplay.class,d->{
                display(d);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setItemStack(plugin.pack.item("ludo_dice_tray"));
                d.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f((float)width,1,(float)width),new Quaternionf()));
            });
            audience.add(packedTable,true);
            packedDie=new YachtDie(plugin,room,center,tag,audience,true,size,"@roll");
            render();
        } else if(!audience.needed(true)&&packedTable!=null) {
            audience.remove(packedTable);packedTable=null;packedDie.close();packedDie=null;
        }
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
        });return block;
    }
    @Override public void close(){
        if(closed)return;closed=true;motion=null;
        entities.forEach(audience::remove);entities.clear();nativeTable.forEach(audience::remove);nativeTable.clear();
        if(packedTable!=null){audience.remove(packedTable);packedTable=null;}
        if(nativeDie!=null){nativeDie.close();nativeDie=null;}
        if(packedDie!=null){packedDie.close();packedDie=null;}
    }
}
