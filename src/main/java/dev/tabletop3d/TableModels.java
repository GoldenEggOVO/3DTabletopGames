package dev.tabletop3d;

import dev.tabletop3d.ui.GameSymbols;

import dev.tabletop3d.rules.Cell;
import org.bukkit.Material;
import java.util.*;

/** Small original voxel meshes: dimensions are in units of one cell spacing. */
final class TableModels {
    record Part(double x,double y,double z,double w,double h,double d,Material material){}
    static final Material[] COLORS={Material.RED_CONCRETE,Material.LIGHT_BLUE_CONCRETE,Material.GREEN_CONCRETE,Material.YELLOW_CONCRETE,Material.PURPLE_CONCRETE,Material.PINK_CONCRETE};
    static List<Part> piece(String kind,Cell cell,Map<String,String> info){
        List<Part> p=new ArrayList<>();int owner=cell.owner();
        Material color=COLORS[Math.floorMod(GameWorld.actualColor(info,owner),6)];
        switch(kind){
            case "gomoku", "go", "go9", "go13", "reversi" -> {Material stone=owner==0?Material.BLACK_CONCRETE:Material.WHITE_CONCRETE;disc(p,stone,.76,.13);if(cell.piece().contains("×"))p.addAll(deadStoneMarks());}
            case "draughts" -> {Material stone=owner==0?Material.POLISHED_BLACKSTONE:Material.SMOOTH_QUARTZ;disc(p,stone,.76,.19);if(cell.piece().equals(GameSymbols.KING)){disc(p,Material.GOLD_BLOCK,.54,.30);box(p,0,.31,0,.18,.07,.18,stone);}}
            case "xiangqi" -> {roundedDisc(p,Material.STRIPPED_BIRCH_WOOD,.78,.19);}
            case "checkers" -> {roundedDisc(p,color,.68,.18);box(p,0,.18,0,.38,.20,.38,color);box(p,-.07,.38,-.07,.15,.035,.15,Material.WHITE_CONCRETE);}
            case "ludo" -> {
                roundedDisc(p,color,.64,.12);
                box(p,0,.12,0,.34,.27,.34,color);
                box(p,0,.39,0,.24,.08,.24,color);
                box(p,0,.47,0,.36,.30,.46,color);
                for(double side:new double[]{-.21,.21})box(p,side,.51,0,.06,.22,.34,color);
            }
            case "chess" -> {
                Material body=owner==0?Material.SMOOTH_QUARTZ:Material.POLISHED_BLACKSTONE;
                Material trim=owner==0?Material.GOLD_BLOCK:Material.COPPER_BLOCK;
                disc(p,body,.76,.12);box(p,0,.13,0,.49,.10,.49,body);box(p,0,.23,0,.28,.28,.28,body);
                switch(cell.piece()){
                    case GameSymbols.PAWN -> {box(p,0,.49,0,.39,.24,.39,body);}
                    case GameSymbols.ROOK -> {box(p,0,.50,0,.57,.14,.57,body);for(double x:new double[]{-.20,.20})for(double z:new double[]{-.20,.20})box(p,x,.64,z,.16,.16,.16,body);}
                    case GameSymbols.HORSE -> {box(p,0,.49,.04,.28,.33,.30,body);box(p,0,.70,-.15,.28,.22,.45,body);box(p,0,.92,.02,.25,.12,.11,body);}
                    case GameSymbols.ELEPHANT -> {box(p,0,.51,0,.46,.08,.46,trim);box(p,0,.59,0,.33,.25,.33,body);box(p,0,.84,0,.16,.13,.16,body);}
                    case GameSymbols.QUEEN -> {box(p,0,.51,0,.42,.25,.42,body);box(p,0,.76,0,.55,.08,.55,trim);for(double x:new double[]{-.19,.19})box(p,x,.84,0,.13,.15,.34,body);}
                    case GameSymbols.KING -> {box(p,0,.51,0,.44,.28,.44,body);box(p,0,.79,0,.15,.32,.15,trim);box(p,0,.93,0,.40,.10,.15,trim);}
                    default -> throw new IllegalArgumentException("Unknown chess piece "+cell.piece());
                }
            }
            default -> throw new IllegalArgumentException(kind);
        }
        return List.copyOf(p);
    }
    static void disc(List<Part> parts,Material material,double width,double height){
        // Adjacent strips preserve the octagonal footprint without overlapping top faces.
        box(parts,0,0,0,width*.68,height,width,material);
        for(double side:new double[]{-.42,.42})box(parts,side*width,0,0,width*.16,height,width*.68,material);
    }
    /** Five adjoining strips soften larger pieces; dense Go boards keep the compact mesh. */
    static void roundedDisc(List<Part> parts,Material material,double width,double height){
        box(parts,0,0,0,width*.4,height,width,material);
        for(double side:new double[]{-1,1}){
            box(parts,side*width*.3,0,0,width*.2,height,width*.88,material);
            box(parts,side*width*.45,0,0,width*.1,height,width*.60,material);
        }
    }
    static List<Part> deadStoneMarks(){
        return List.of(new Part(0,.16,0,.65,.035,.10,Material.RED_CONCRETE),new Part(0,.16,0,.10,.035,.65,Material.RED_CONCRETE));
    }
    /** Five horizontal strips approximate a round vertical chip, in world block units. */
    static List<Part> connectFour(int owner) {
        List<Part> parts=new ArrayList<>();
        Material material=owner==0?Material.RED_CONCRETE:Material.YELLOW_CONCRETE;
        double[] widths={.13,.21,.24,.21,.13};
        for(int i=0;i<widths.length;i++)box(parts,0,i*.048,0,widths[i],.048,.085,material);
        return List.copyOf(parts);
    }
    /** Black upper face and white lower face; owner changes rotate this same mesh. */
    static List<Part> reversi() {
        List<Part> parts=new ArrayList<>();
        for(int side=0;side<2;side++) {
            Material material=side==0?Material.WHITE_CONCRETE:Material.BLACK_CONCRETE;
            double y=side*.065;
            box(parts,0,y,0,.52,.065,.76,material);
            box(parts,0,y,0,.76,.065,.52,material);
        }
        return List.copyOf(parts);
    }
    static void box(List<Part> parts,double x,double y,double z,double w,double h,double d,Material mat){parts.add(new Part(x,y,z,w,h,d,mat));}
}
