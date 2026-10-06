package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Wall {
    private final List<Tiles.Tile> tiles;
    private final boolean deadWall;
    private int front,back,replacements;
    public Wall(List<Tiles.Tile> tiles,long seed,boolean deadWall) {
        this.tiles=new ArrayList<>(tiles);Collections.shuffle(this.tiles,new Random(seed));this.deadWall=deadWall;
        back=tiles.size()-(deadWall?14:0);
    }
    public int remaining(){return back-front;}
    public Tiles.Tile draw(){return front<back?tiles.get(front++):null;}
    public boolean canReplace(){return remaining()>0&&(!deadWall||replacements<4);}
    public Tiles.Tile replacement() {
        if(!canReplace())return null;
        if(!deadWall)return tiles.get(--back);
        back--;return tiles.get(tiles.size()-14+replacements++);
    }
    public List<Tiles.Tile> indicators(){return indicators(false);}
    public List<Tiles.Tile> uraIndicators(){return indicators(true);}
    private List<Tiles.Tile> indicators(boolean ura) {
        if(!deadWall)return List.of();
        List<Tiles.Tile> result=new ArrayList<>();int first=tiles.size()-10+(ura?1:0);
        for(int i=0;i<=replacements;i++)result.add(tiles.get(first+i*2));
        return List.copyOf(result);
    }
}
