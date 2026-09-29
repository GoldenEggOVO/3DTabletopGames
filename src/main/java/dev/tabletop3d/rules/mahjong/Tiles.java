package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.List;

/** Physical identity is separate from the normalized face used by hand rules. */
public final class Tiles {
    public record Tile(String id,int type,boolean red) {
        public Tile {
            if(id==null||id.isBlank()||type<0||type>=42||red&&(type>=27||type%9!=4))
                throw new IllegalArgumentException("Invalid mahjong tile");
        }
        public String face(){return red?"mps".charAt(type/9)+"0":Tiles.face(type);}
        public boolean flower(){return type>=34;}
    }
    private Tiles() {}
    public static List<Tile> set(int count,int redFives) {
        int types=switch(count){case 108->27;case 124->31;case 136,144->34;default->throw new IllegalArgumentException("Mahjong tile count");};
        if(redFives<0||redFives>4)throw new IllegalArgumentException("Red five count must be 0 to 4");
        List<Tile> tiles=new ArrayList<>();
        for(int type=0;type<types;type++)for(int copy=0;copy<4;copy++) {
            boolean red=type==4&&copy==0&&redFives>=1||type==13&&(copy==0&&redFives>=2||copy==1&&redFives==4)
                ||type==22&&copy==0&&redFives>=3;
            tiles.add(new Tile("t"+tiles.size(),type,red));
        }
        if(count==144)for(int type=34;type<42;type++)tiles.add(new Tile("t"+tiles.size(),type,false));
        return List.copyOf(tiles);
    }
    public static String face(int type) {
        if(type<0||type>=42)throw new IllegalArgumentException("Mahjong face");
        return type<27?"mps".charAt(type/9)+Integer.toString(type%9+1):type<34?"z"+(type-26):"f"+(type-33);
    }
    public static int type(String face) {
        if(face==null||face.length()!=2)throw new IllegalArgumentException("Mahjong face");
        String value=face.toLowerCase(java.util.Locale.ROOT);int number=value.charAt(1)-'0';
        int suit="mps".indexOf(value.charAt(0));
        if(suit>=0&&number>=0&&number<=9)return suit*9+(number==0?4:number-1);
        if(value.charAt(0)=='z'&&number>=1&&number<=7)return 26+number;
        if(value.charAt(0)=='f'&&number>=1&&number<=8)return 33+number;
        throw new IllegalArgumentException("Mahjong face");
    }
    public static int next(int type) {
        if(type<0||type>=34)throw new IllegalArgumentException("Mahjong indicator");
        if(type<27)return type/9*9+(type+1)%9;
        return type<31?27+(type-26)%4:31+(type-30)%3;
    }
    public static boolean terminalOrHonor(int type){return type>=27||type%9==0||type%9==8;}
    public static int[] counts(List<Tile> tiles) {
        int[] counts=new int[34];
        for(Tile tile:tiles){if(tile.flower())throw new IllegalArgumentException("Flowers are not concealed hand tiles");counts[tile.type()]++;}
        return counts;
    }
}
