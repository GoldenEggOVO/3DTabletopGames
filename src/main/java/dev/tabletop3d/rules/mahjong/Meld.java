package dev.tabletop3d.rules.mahjong;

import java.util.List;

/** An exposed group retains every original physical tile, including an added fourth tile. */
public record Meld(Kind kind,List<Tiles.Tile> tiles,boolean open,int fromSeat) {
    public enum Kind { SEQUENCE,TRIPLET,QUAD }
    public Meld {
        tiles=List.copyOf(tiles);
        if(tiles.size()!=(kind==Kind.QUAD?4:3)||tiles.stream().map(Tiles.Tile::id).distinct().count()!=tiles.size())
            throw new IllegalArgumentException("Invalid mahjong meld");
        int[] values=tiles.stream().mapToInt(Tiles.Tile::type).sorted().toArray();
        if(kind==Kind.SEQUENCE) {
            if(values[0]>=27||values[0]/9!=values[2]/9||values[1]!=values[0]+1||values[2]!=values[0]+2)
                throw new IllegalArgumentException("Invalid mahjong sequence");
        }else if(values[0]!=values[values.length-1]||values[0]>=34)throw new IllegalArgumentException("Invalid mahjong triplet or quad");
    }
    public int type(){return tiles.stream().mapToInt(Tiles.Tile::type).min().orElseThrow();}
}
