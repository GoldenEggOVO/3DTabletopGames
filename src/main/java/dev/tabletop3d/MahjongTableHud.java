package dev.tabletop3d;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.ui.LabelLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Public table information only; concealed hands never enter this renderer. */
final class MahjongTableHud implements AutoCloseable {
    private final Tabletop3D plugin;
    private final Room room;
    private final Location origin;
    private final NamespacedKey tag;
    final List<Entity> entities = new ArrayList<>();
    private final List<TextDisplay> counts = new ArrayList<>();
    private final List<TextDisplay> winds = new ArrayList<>(), scores = new ArrayList<>();
    private final List<List<BlockDisplay>> sticks = new ArrayList<>();
    private final boolean[] declared = new boolean[4];
    private final Map<TextDisplay,Component> labels = new HashMap<>();
    private final TextDisplay status, discard;
    private HandGame rendered;
    private long revision = -1;
    private Map<String,String> info = Map.of();
    private int remaining, turn = -1;
    private boolean closed;

    MahjongTableHud(Tabletop3D plugin,Room room,Location surfaceOrigin,NamespacedKey tag) {
        this.plugin=plugin;this.room=room;this.origin=surfaceOrigin.clone();this.tag=tag;
        block(origin.clone().add(0,.015,0),Material.BLACK_CONCRETE,.60f,.012f,.60f);
        for(int seat=0;seat<4;seat++) {
            double angle=seat*Math.PI/2;
            Location number=origin.clone().add(.17*Math.sin(angle),.029,.17*Math.cos(angle));
            number.setYaw(-90*seat);
            counts.add(text(number,true));
            Location stick=origin.clone().add(.26*Math.sin(angle),.029,.26*Math.cos(angle));
            stick.setYaw(-90*seat);
            BlockDisplay body=block(stick,Material.WHITE_CONCRETE,.12f,.006f,.018f);
            BlockDisplay dot=block(stick.clone().add(0,.007,0),Material.RED_CONCRETE,.012f,.002f,.012f);
            sticks.add(List.of(body,dot));
            visible(body,false,.12f,.006f,.018f);visible(dot,false,.012f,.002f,.012f);
        }
        status=text(origin.clone().add(0,.85,0),false);
        discard=text(origin.clone().add(0,.72,0),false);
        for(int seat=0;seat<4;seat++) {
            double angle=seat*Math.PI/2;
            Location wind=origin.clone().add(.95*Math.cos(angle)+.82*Math.sin(angle),.02,
                -.95*Math.sin(angle)+.82*Math.cos(angle));
            wind.setYaw(-90*seat);winds.add(text(wind,true));
            Location score=origin.clone().add(2.8*Math.sin(angle),1.4,2.8*Math.cos(angle));
            score.setYaw(180-90*seat);scores.add(text(score,false,true));
        }
        tick();
    }

    void tick(){tick(System.currentTimeMillis());}

    void tick(long now) {
        if(closed)return;
        // Rules mutate on the room executor. Keep the last public snapshot while it runs.
        if(!room.busy&&room.board instanceof HandGame game&&(rendered!=game||revision!=room.revision)) {
            rendered=game;revision=room.revision;info=Map.copyOf(game.publicInfo());
            remaining=game.deckSize();turn=game.currentPlayer();
        }
        for(TextDisplay count:counts)label(count,Component.text(remaining,NamedTextColor.WHITE),.15f,.065f);
        int dealer=Integer.parseInt(info.getOrDefault("dealer","0"));
        for(int seat=0;seat<4;seat++) {
            int wind=Math.floorMod(seat-dealer,4);
            label(winds.get(seat),Component.text(List.of("東","南","西","北").get(wind),
                wind==0?NamedTextColor.RED:NamedTextColor.WHITE),.24f,.22f);
            label(scores.get(seat),Component.text(info.getOrDefault("score."+seat,""),NamedTextColor.WHITE),.8f,.18f);
        }
        boolean lobby=room.phase==Room.Phase.LOBBY||room.phase==Room.Phase.STARTING;
        for(int seat=0;seat<4;seat++) {
            boolean next=!lobby&&Boolean.parseBoolean(info.getOrDefault("riichi."+seat,"false"));
            if(declared[seat]==next)continue;
            declared[seat]=next;
            visible(sticks.get(seat).get(0),next,.12f,.006f,.018f);
            visible(sticks.get(seat).get(1),next,.012f,.002f,.012f);
        }
        if(lobby) {
            label(status,Component.empty(),.95f,.105f);
            label(discard,Component.empty(),.95f,.042f);
            return;
        }
        int round=Integer.parseInt(info.getOrDefault("round","1"));
        if(room.phase==Room.Phase.FINISHED)round=Math.min(round,Integer.parseInt(info.getOrDefault("rounds",Integer.toString(round))));
        Component heading=(info.getOrDefault("profile","riichi").equals("riichi")?
            Language.component("table.mahjong.round",
                "wind",Language.component("table.mahjong.wind."+Math.floorMod((round-1)/4,4)),"hand",Math.floorMod(round-1,4)+1):
            Language.component("table.mahjong.round-number","round",round)).colorIfAbsent(NamedTextColor.GOLD);
        Component clock;
        if(room.phase==Room.Phase.FINISHED)clock=Language.component("table.mahjong.finished");
        else if(room.phase!=Room.Phase.PLAYING||room.busy||room.undo!=null)clock=Language.component("table.mahjong.paused");
        else {
            long left=Math.max(0,plugin.turnWaitMillis(room)-Math.max(0,now-room.changed));
            clock=Language.component("table.mahjong.timer","seconds",(left+999)/1000);
        }
        Component current=Language.component("room.current-turn","player",seatName(turn));
        Component summary=heading.append(Component.text(" · ")).append(current)
            .append(Component.newline()).append(clock.colorIfAbsent(NamedTextColor.WHITE));
        int deposits=Integer.parseInt(info.getOrDefault("riichiSticks","0"));
        if(deposits>0)summary=summary.append(Component.text(" · "))
            .append(Language.component("table.mahjong.deposits","count",deposits));
        label(status,summary,.95f,.105f);
        String tile=info.getOrDefault("lastDiscardTile","");
        Component last=tile.isEmpty()?Language.component("table.mahjong.no-discard"):
            Language.component("table.mahjong.last-discard",
                "player",seatName(Integer.parseInt(info.getOrDefault("lastDiscardBy","-1"))),
                "tile",HandText.piece("mahjong",tile));
        label(discard,last.colorIfAbsent(NamedTextColor.WHITE),.95f,.042f);
    }

    private String seatName(int seat){return seat>=0&&seat<room.seats.size()?Language.text(room.seats.get(seat).name()):"—";}

    private void configure(Display display) {
        display.setPersistent(false);display.setGravity(false);display.setInvulnerable(true);
        display.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|@board");
        display.setBrightness(new Display.Brightness(15,15));display.setViewRange(.35f);
    }

    private BlockDisplay block(Location at,Material material,float width,float height,float depth) {
        BlockDisplay display=origin.getWorld().spawn(at,BlockDisplay.class,d->{
            configure(d);d.setBlock(material.createBlockData());visible(d,true,width,height,depth);
        });
        entities.add(display);return display;
    }

    private static void visible(BlockDisplay display,boolean visible,float width,float height,float depth) {
        display.setTransformation(new Transformation(new Vector3f(-width/2,0,-depth/2),new Quaternionf(),
            visible?new Vector3f(width,height,depth):new Vector3f(),new Quaternionf()));
    }

    private TextDisplay text(Location at,boolean flat) {
        return text(at,flat,flat);
    }

    private TextDisplay text(Location at,boolean flat,boolean fixed) {
        TextDisplay display=origin.getWorld().spawn(at,TextDisplay.class,d->{
            // Normal text uses native backface culling, so fixed upright scores face only the table.
            configure(d);d.setBillboard(fixed?Display.Billboard.FIXED:Display.Billboard.CENTER);
            d.setRotation(at.getYaw(),flat?-90:0);d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(Integer.MAX_VALUE);d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0,0,0,0));d.setShadowed(!flat);d.setSeeThrough(false);
        });
        entities.add(display);return display;
    }

    private void label(TextDisplay display,Component value,float width,float height) {
        if(value.equals(labels.get(display)))return;
        labels.put(display,value);
        var fit=LabelLayout.fit(value,width,height);
        display.text(fit.text());
        display.setTransformation(new Transformation(new Vector3f(0,-.125f*fit.scale(),0),new Quaternionf(),
            new Vector3f(fit.scale()),new Quaternionf()));
    }

    @Override public void close() {
        if(closed)return;closed=true;
        entities.forEach(Entity::remove);entities.clear();labels.clear();
    }
}
