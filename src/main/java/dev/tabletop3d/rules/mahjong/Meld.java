package dev.tabletop3d.rules.mahjong;

import java.util.List;

public record Meld(
        Kind kind,
        List<Tiles.Tile> tiles,
        boolean open,
        int fromSeat,
        String calledTileId,
        String addedTileId) {
    public enum Kind {
        SEQUENCE,
        TRIPLET,
        QUAD
    }

    public Meld(Kind kind, List<Tiles.Tile> tiles, boolean open, int fromSeat) {
        this(kind, tiles, open, fromSeat, open && !tiles.isEmpty() ? tiles.getLast().id() : "", "");
    }

    public Meld {
        tiles = List.copyOf(tiles);
        if (open && tiles.stream().noneMatch(tile -> tile.id().equals(calledTileId))
                || !open && !calledTileId.isEmpty()
                || !addedTileId.isEmpty()
                        && (addedTileId.equals(calledTileId)
                                || tiles.stream().noneMatch(tile -> tile.id().equals(addedTileId))))
            throw new IllegalArgumentException("Invalid called or added tile identity");
        if (!addedTileId.isEmpty() && (calledTileId.isEmpty() || kind != Kind.QUAD || !open))
            throw new IllegalArgumentException("An added kong must retain its called tile");
        if (tiles.size() != (kind == Kind.QUAD ? 4 : 3)
                || tiles.stream().map(Tiles.Tile::id).distinct().count() != tiles.size())
            throw new IllegalArgumentException("Invalid mahjong meld");
        int[] values = tiles.stream().mapToInt(Tiles.Tile::type).sorted().toArray();
        if (kind == Kind.SEQUENCE) {
            if (values[0] >= 27
                    || values[0] / 9 != values[2] / 9
                    || values[1] != values[0] + 1
                    || values[2] != values[0] + 2)
                throw new IllegalArgumentException("Invalid mahjong sequence");
        } else if (values[0] != values[values.length - 1] || values[0] >= 34)
            throw new IllegalArgumentException("Invalid mahjong triplet or quad");
    }

    public int type() {
        return tiles.stream().mapToInt(Tiles.Tile::type).min().orElseThrow();
    }
}
