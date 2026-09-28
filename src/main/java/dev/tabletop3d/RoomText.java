package dev.tabletop3d;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Set;

/** Shared presentation for menus, chat and physical tables; never changes stored rule data. */
final class RoomText {
    private RoomText() {}

    static Component game(String kind) {
        if(kind.equals("aeroplane"))return Language.component("game.legacy","game",Language.component("game.aeroplane"));
        return Tabletop3D.NAMES.containsKey(kind)||Set.of("yacht","aeroplane").contains(kind)?Language.component("game."+kind):Component.text(kind);
    }

    static Component name(Room room) {
        return Language.component("room.name","game",game(room.kind),"id",room.id.toString().substring(0,6));
    }

    static Component player(Room.Seat seat,int number) {
        return seat.bot()?Language.component("room.bot","number",number):Component.text(seat.name());
    }

    static Component phase(Room room) {
        return Language.component("status."+room.phase.name().toLowerCase(java.util.Locale.ROOT));
    }

    static Component roster(List<Room.Seat> seats,int capacity) {
        Component names=Component.empty();
        for(int i=0;i<seats.size();i++) {
            if(i>0)names=names.append(Language.component("room.roster.separator"));
            names=names.append(player(seats.get(i),i+1));
        }
        return rosterNames(seats.isEmpty()?Language.component("room.roster.empty"):names,seats.size(),capacity);
    }

    static Component rosterNames(Component names,int size,int capacity) {
        return Language.component("room.roster","players",names,"empty",Math.max(0,capacity-size),"capacity",capacity);
    }

    static Component outcome(Room room,String outcome) {
        if(outcome.startsWith("winner:")) {
            try {
                int seat=Integer.parseInt(outcome.substring(7));
                return Language.component("result.winner","player",player(room.seats.get(seat),seat+1));
            }catch(IndexOutOfBoundsException|NumberFormatException ignored){}
        }
        return outcome.startsWith("draw:")?Language.component("result.draw","reason",Language.legacy(outcome.substring(5))):Language.legacy(outcome);
    }
}
