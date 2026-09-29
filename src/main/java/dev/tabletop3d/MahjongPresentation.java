package dev.tabletop3d;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.mahjong.Tiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presentation uses only the seated owner's hand and public information. */
final class MahjongPresentation {
    private MahjongPresentation() {}
    static int remaining(HandGame game,int seat,String face) {
        int type=type(face);if(type<0)return 0;
        Map<String,String> info=game.publicInfo();
        int count=Integer.parseInt(info.getOrDefault("tileCount","136"));
        int total=type>=34?(count==144?1:0):type<(count==108?27:count==124?31:34)?4:0;
        Map<String,String> visible=new LinkedHashMap<>();
        for(var tile:game.hand(seat))visible.put(tile.id(),tile.face());
        for(int player=0;player<game.playerCount();player++) {
            for(var tile:game.discards(player))visible.put(tile.id(),tile.face());
            for(var tile:game.exposed(player))visible.put(tile.id(),tile.face());
        }
        if(info.containsKey("offeredId"))visible.put(info.get("offeredId"),info.get("offeredTile"));
        if(info.containsKey("indicator"))visible.put(info.getOrDefault("indicatorId","indicator"),info.get("indicator"));
        String[] dora=info.getOrDefault("dora","").split(","),ids=info.getOrDefault("doraIds","").split(",");
        for(int i=0;i<dora.length;i++)if(!dora[i].isEmpty())visible.put(i<ids.length&&!ids[i].isEmpty()?ids[i]:"dora-"+i,dora[i]);
        return Math.max(0,total-(int)visible.values().stream().filter(value->type(value)==type).count());
    }

    static List<String> choiceFaces(HandGame game,int seat,String action) {
        if(action==null||!game.legalActions(seat).contains(action))return List.of();
        int colon=action.indexOf(':');if(colon<0)return List.of();
        String kind=action.substring(0,colon);
        if(!List.of("chi","pon","kan-open","kan-closed","kan-added").contains(kind))return List.of();
        List<HandGame.Piece> hand=game.hand(seat);List<String> faces=new ArrayList<>();
        String[] ids=action.substring(colon+1).split(",");
        if(kind.equals("kan-closed")||kind.equals("kan-added")) {
            var selected=hand.stream().filter(tile->tile.id().equals(ids[0])).findFirst().orElse(null);
            if(selected==null)return List.of();
            int type=type(selected.face());
            if(kind.equals("kan-closed"))for(var tile:hand){if(type(tile.face())==type)faces.add(tile.face());}
            else {for(var tile:game.exposed(seat))if(type(tile.face())==type)faces.add(tile.face());faces.add(selected.face());}
        }else {
            String offered=game.publicInfo().get("offeredTile");if(type(offered)<0)return List.of();
            faces.add(offered);
            for(String id:ids) {
                var tile=hand.stream().filter(piece->piece.id().equals(id)).findFirst().orElse(null);
                if(tile==null)return List.of();faces.add(tile.face());
            }
        }
        if(faces.size()!=(kind.startsWith("kan-")?4:3))return List.of();
        faces.sort(Comparator.comparingInt(MahjongPresentation::type));return List.copyOf(faces);
    }

    private static int type(String face) {
        try{return Tiles.type(face);}catch(IllegalArgumentException ignored){return -1;}
    }
}
