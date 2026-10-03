package dev.tabletop3d;

import java.util.*;
import org.bukkit.Material;

/** Native circular card furniture, in block units relative to the playing surface. */
final class RoundCardTable {
    static final double RADIUS=1.5;
    record Part(double x,double y,double z,double w,double h,double d,Material material,float yaw) {}
    static List<Part> parts(){
        List<Part> parts=new ArrayList<>();
        disc(parts,RADIUS,-.19,.19,Material.STRIPPED_DARK_OAK_WOOD,48);
        disc(parts,1.41,.005,.01,Material.GREEN_TERRACOTTA,48);
        disc(parts,.25,-TableGeometry.SURFACE,TableGeometry.SURFACE-.19,Material.STRIPPED_DARK_OAK_LOG,16);
        disc(parts,.70,-TableGeometry.SURFACE,.08,Material.DARK_OAK_PLANKS,32);
        return List.copyOf(parts);
    }
    private static void disc(List<Part> parts,double radius,double y,double height,Material material,int sides){
        // Centered rotated bars form a regular polygon; all corners stay on the radius.
        // Center the bounded relief around the existing surface height.
        double width=2*radius*Math.cos(Math.PI/sides),depth=2*radius*Math.sin(Math.PI/sides);
        int bars=sides/2;
        for(int i=0;i<bars;i++)parts.add(new Part(0,y+(i-(bars-1)/2.0)*.0006,0,width,height,depth,material,360f*i/sides));
    }
    private RoundCardTable(){}
}
