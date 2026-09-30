package dev.tabletop3d;

import java.util.*;
import org.bukkit.Material;

/** Native circular card furniture, in block units relative to the playing surface. */
final class RoundCardTable {
    static final double RADIUS=1.5;
    static List<TableModels.Part> parts(){
        List<TableModels.Part> parts=new ArrayList<>();
        disc(parts,RADIUS,-.19,.19,Material.STRIPPED_DARK_OAK_WOOD,48);
        disc(parts,1.41,.001,.01,Material.GREEN_TERRACOTTA,48);
        disc(parts,.25,-TableGeometry.SURFACE,TableGeometry.SURFACE-.19,Material.STRIPPED_DARK_OAK_LOG,12);
        disc(parts,.70,-TableGeometry.SURFACE,.08,Material.DARK_OAK_PLANKS,24);
        return List.copyOf(parts);
    }
    private static void disc(List<TableModels.Part> parts,double radius,double y,double height,Material material,int rows){
        double step=2*radius/rows;
        for(int row=0;row<rows;row++){
            double z=-radius+(row+.5)*step;
            double width=2*Math.sqrt(radius*radius-Math.pow(Math.abs(z)+step/2,2));
            if(width>0)parts.add(new TableModels.Part(0,y,z,width,height,step,material));
        }
    }
    private RoundCardTable(){}
}
