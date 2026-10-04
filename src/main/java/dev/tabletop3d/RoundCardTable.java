package dev.tabletop3d;

import java.util.*;
import org.bukkit.Material;

/** Native circular card furniture, in block units relative to the playing surface. */
final class RoundCardTable {
    static final double RADIUS=1.5;
    record Part(double x,double y,double z,double w,double h,double d,Material material,float yaw) {}
    static List<Part> parts(){
        List<Part> parts=new ArrayList<>();
        disc(parts,RADIUS,-.19,.19,Material.STRIPPED_DARK_OAK_WOOD,96);
        disc(parts,1.41,.005,.01,Material.GREEN_TERRACOTTA,96);
        disc(parts,.25,-TableGeometry.SURFACE,TableGeometry.SURFACE-.19,Material.STRIPPED_DARK_OAK_LOG,8);
        disc(parts,.70,-TableGeometry.SURFACE,.08,Material.DARK_OAK_PLANKS,16);
        return List.copyOf(parts);
    }
    private static void disc(List<Part> parts,double radius,double y,double height,Material material,int rows){
        // Adjoining horizontal rows share one level; the larger wood disc supplies the rim.
        for(int i=0;i<rows;i++){
            double near=radius*Math.sin(-Math.PI/2+Math.PI*i/rows);
            double far=radius*Math.sin(-Math.PI/2+Math.PI*(i+1)/rows);
            double depth=far-near, z=(near+far)/2;
            double edge=Math.max(Math.abs(near),Math.abs(far));
            double width=2*Math.sqrt(Math.max(0,radius*radius-edge*edge));
            if(width==0)continue;
            parts.add(new Part(0,y,z,width,height,depth,material,0));
        }
    }
    private RoundCardTable(){}
}
