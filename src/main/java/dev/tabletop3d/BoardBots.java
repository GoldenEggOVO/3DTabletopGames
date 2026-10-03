package dev.tabletop3d;

import dev.tabletop3d.rules.*;
import dev.tabletop3d.rules.mahjong.Tiles;
import java.util.*;

/** Small bounded casual heuristics; never simulate on the live rules engine. */
final class BoardBots {
    static String choose(BoardGame board,int seat,Random random){
        List<String> legal=board.legalActions(seat);if(legal.isEmpty())return null;
        if(board instanceof DoudizhuGame landlord){
            List<String> plays=legal.stream().filter(a -> a.startsWith("play:")).toList();
            if(!plays.isEmpty())return plays.stream().max(Comparator.comparingInt(a -> a.split(",").length)).orElseThrow();
            if(legal.contains("pass"))return "pass";
            return legal.stream().filter(a -> !a.equals("bid:0")).findFirst().orElse("bid:0");
        }
        if(board instanceof LiarsBarGame liar){
            if(legal.contains("challenge")&&random.nextInt(4)==0)return "challenge";
            List<String> honest=liar.hand(seat).stream()
                    .filter(card -> card.face().equals(liar.publicInfo().get("declaration"))||card.face().startsWith("joker_"))
                    .limit(3).map(HandGame.Piece::id).toList();
            if(!honest.isEmpty()&&liar.controls(seat).contains("play"))return liar.selectionAction(seat,honest);
            return legal.get(random.nextInt(legal.size()));
        }
        if(board instanceof TexasHoldemGame){
            if(legal.contains("continue"))return "continue";
            if(legal.contains("check"))return "check";
            if(legal.contains("call")&&random.nextInt(5)!=0)return "call";
            return "fold";
        }
        if(board instanceof HandGame hand){
            for(String action:List.of("ron","tsumo","declare"))if(legal.contains(action))return action;
            if(board.id().equals("mahjong")){
                String choice=mahjong(hand,seat,legal,random);if(choice!=null)return choice;
            }
            List<String> plays=legal.stream().filter(a->a.startsWith("play:")).toList();
            if(!plays.isEmpty())return plays.get(random.nextInt(plays.size()));
        }
        if(board instanceof YachtGame yacht){
            if(yacht.rolls()==0)return "roll";
            if(yacht.rolls()<3){int[] dice=yacht.dice();int[] counts=new int[7];for(int d:dice)counts[d]++;int mode=1;for(int d=2;d<=6;d++)if(counts[d]>=counts[mode])mode=d;
                for(int i=0;i<5;i++)if((dice[i]==mode)!=yacht.held(i))return "hold:die"+i;
                if(legal.contains("roll"))return "roll";
            }
            return legal.stream().filter(a->a.startsWith("score:")).max(Comparator.comparingInt(a->YachtGame.score(YachtGame.CATEGORIES.indexOf(a.substring(6)),yacht.dice()))).orElseThrow();
        }
        if(board instanceof GoGame go){
            if(go.scoring())return legal.contains("accept")?"accept":"resume";
            if(legal.size()==1||go.cells().stream().filter(c->c.owner()>=0).count()>go.size()*go.size()*.78)return "pass";
            List<String> placements=legal.stream().filter(a->a.startsWith("place:")).toList();return placements.get(random.nextInt(placements.size()));
        }
        if(board instanceof ReversiGame){List<String> corners=legal.stream().filter(a->Set.of("place:0,0","place:7,0","place:0,7","place:7,7").contains(a)).toList();if(!corners.isEmpty())return corners.getFirst();}
        return legal.get(random.nextInt(legal.size()));
    }
    private static String mahjong(HandGame game,int seat,List<String> legal,Random random){
        if(legal.contains("exchange-confirm"))return "exchange-confirm";
        if(legal.contains("riichi"))return "riichi";
        List<String> missing=legal.stream().filter(a->a.startsWith("missing:")).toList();
        List<String> candidates=List.of();
        for(String prefix:List.of("exchange-add:","riichi:","discard:")){
            candidates=legal.stream().filter(a->a.startsWith(prefix)).toList();if(!candidates.isEmpty())break;
        }
        if(missing.isEmpty()&&candidates.isEmpty())return null;
        List<HandGame.Piece> hand=game.hand(seat);int[] counts=new int[34],suits=new int[3];
        Map<String,HandGame.Piece> pieces=new HashMap<>();
        for(var piece:hand){int type=Tiles.type(piece.face());counts[type]++;if(type<27)suits[type/9]++;pieces.put(piece.id(),piece);}
        if(!missing.isEmpty())return least(missing,a->suits["mps".indexOf(a.charAt(8))],random);
        return least(candidates,a->retention(pieces.get(a.substring(a.indexOf(':')+1)),counts),random);
    }
    /** Keep pairs/triplets and nearby suited tiles; isolated honors and terminals leave first. */
    private static int retention(HandGame.Piece piece,int[] counts){
        int type=Tiles.type(piece.face()),value=(counts[type]-1)*8;
        if(type<27){
            value+=type%9==0||type%9==8?1:2;
            for(int distance:new int[]{-2,-1,1,2}){
                int next=type+distance;
                if(next>=0&&next<27&&next/9==type/9&&counts[next]>0)value+=Math.abs(distance)==1?4:2;
            }
        }
        return value+(piece.face().charAt(1)=='0'?1:0);
    }
    private static String least(List<String> actions,java.util.function.ToIntFunction<String> score,Random random){
        int best=Integer.MAX_VALUE;List<String> choices=new ArrayList<>();
        for(String action:actions){int value=score.applyAsInt(action);if(value<best){best=value;choices.clear();}if(value==best)choices.add(action);}
        return choices.get(random.nextInt(choices.size()));
    }
}
