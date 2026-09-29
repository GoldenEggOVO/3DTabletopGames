package dev.tabletop3d;

import dev.tabletop3d.rules.Cell;
import java.util.List;
import java.util.Set;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** Short native cues emitted by committed actions, never by rendering or replay. */
final class TableSounds {
    record Cue(Sound sound,float volume,float pitch) {}
    static final Cue WOOD=new Cue(Sound.BLOCK_WOOD_HIT,.28f,1.15f);
    static final Cue XIANGQI=new Cue(Sound.BLOCK_BAMBOO_WOOD_HIT,.28f,.9f);
    static final Cue STONE=new Cue(Sound.BLOCK_STONE_HIT,.25f,1.45f);
    static final Cue HOP=new Cue(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE,.2f,1.4f);
    static final Cue DROP=new Cue(Sound.BLOCK_DECORATED_POT_PLACE,.28f,1.5f);
    static final Cue FLIP=new Cue(Sound.BLOCK_BONE_BLOCK_PLACE,.25f,1.5f);
    static final Cue PAWN=new Cue(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,.25f,1.3f);
    static final Cue ROLL=new Cue(Sound.ITEM_CROSSBOW_LOADING_MIDDLE,.3f,1.5f);
    static final Cue CAPTURE=new Cue(Sound.BLOCK_WOOD_BREAK,.28f,1.5f);
    static final Cue HOME=new Cue(Sound.BLOCK_NOTE_BLOCK_PLING,.25f,1.8f);
    static final Cue SELECT=new Cue(Sound.BLOCK_NOTE_BLOCK_HAT,.15f,1.8f);
    static final Cue PASS=new Cue(Sound.BLOCK_NOTE_BLOCK_BASS,.2f,1.2f);
    static final Cue CONFIRM=new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME,.2f,1.2f);
    static final Cue START=new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME,.3f,1.5f);
    static final Cue UNDO=new Cue(Sound.BLOCK_NOTE_BLOCK_FLUTE,.25f,.8f);
    static final Cue WIN=new Cue(Sound.ENTITY_PLAYER_LEVELUP,.3f,1.3f);
    static final Cue DRAW=new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME,.25f,.8f);
    static final Cue TURN=new Cue(Sound.BLOCK_NOTE_BLOCK_PLING,.15f,1.2f);
    static final Cue CARD=new Cue(Sound.ITEM_BOOK_PAGE_TURN,.25f,1.4f);
    static final Cue TILE=new Cue(Sound.BLOCK_BONE_BLOCK_HIT,.24f,1.5f);

    static Cue move(String kind,int seat,String action,List<Cell> before,List<Cell> after){
        if(kind.equals("lastcard"))return action.equals("declare")?CONFIRM:CARD;
        if(kind.equals("mahjong"))return Set.of("ron","tsumo","riichi").contains(action.split(":")[0])?CONFIRM:action.equals("pass")?PASS:TILE;
        if(action.equals("roll"))return ROLL;
        if(action.equals("pass"))return PASS;
        if(action.startsWith("dead:")||action.startsWith("hold:"))return SELECT;
        if(action.equals("accept")||action.equals("resume")||action.startsWith("score:"))return CONFIRM;
        if(kind.equals("reversi"))return FLIP;
        if(opponents(kind,seat,before)>opponents(kind,seat,after))return CAPTURE;
        if((kind.equals("ludo")||kind.equals("aeroplane"))&&action.matches("move:\\d+:go\\d+"))return HOME;
        return switch(kind){
            case "xiangqi"->XIANGQI;
            case "gomoku","go","go9","go13"->STONE;
            case "checkers"->HOP;
            case "connectfour"->DROP;
            case "ludo","aeroplane"->PAWN;
            default->WOOD;
        };
    }
    private static int opponents(String kind,int seat,List<Cell> cells){
        boolean race=kind.equals("ludo")||kind.equals("aeroplane");int count=0;
        for(Cell cell:cells)if(cell.owner()>=0&&cell.owner()!=seat){
            if(!race)count++;
            else if(!cell.id().startsWith("ba"))count+=(int)cell.piece().chars().filter(c->c>='1'&&c<='4').count();
        }
        return count;
    }
    private static float volume(Tabletop3D plugin,Cue cue){
        var config=plugin.getConfig();if(!config.getBoolean("sounds.enabled",true))return 0;
        double scale=config.getDouble("sounds.volume",1);
        return Double.isFinite(scale)?cue.volume()*(float)Math.clamp(scale,0,1):0;
    }
    static void play(Tabletop3D plugin,Location at,Cue cue){
        float volume=volume(plugin,cue);if(volume>0)at.getWorld().playSound(at,cue.sound(),SoundCategory.BLOCKS,volume,cue.pitch());
    }
    private static void personal(Tabletop3D plugin,Player player,Location at,Cue cue){
        float volume=volume(plugin,cue);if(volume>0)player.playSound(at,cue.sound(),SoundCategory.BLOCKS,volume,cue.pitch());
    }
    static void select(Tabletop3D plugin,Player player){personal(plugin,player,player.getLocation(),SELECT);}
    static void turn(Tabletop3D plugin,Room room,Location at){
        if(room.phase!=Room.Phase.PLAYING||room.board.finished()||room.turn()<0||room.turn()>=room.seats.size())return;
        Room.Seat seat=room.seats.get(room.turn());if(seat.bot())return;
        Player player=Bukkit.getPlayer(seat.id());
        if(player!=null&&plugin.allowed(player)&&player.getWorld().equals(at.getWorld())&&player.getLocation().distanceSquared(at)<=64)
            personal(plugin,player,at,TURN);
    }
}
