package dev.tabletop3d;

import java.util.*;
import org.bukkit.Material;

/** Native circular card furniture, in block units relative to the playing surface. */
final class RoundCardTable {
    static final double RADIUS=1.5;
    static final double RIM_TOP=.017;
    record Part(double x,double y,double z,double w,double h,double d,Material material,float yaw) {}
    static List<Part> parts(){
        List<Part> parts=new ArrayList<>();
        double halfAngle=Math.PI/36, depth=.20;
        double apothem=RADIUS*Math.cos(halfAngle), center=apothem-depth/2;
        for(int side=0;side<36;side++){
            double angle=side*2*halfAngle;
            // Adjacent bars overlap inside the rim; offset their top and underside to avoid coplanar faces.
            double seam=(side%2)*.00025, bottom=-.19-seam;
            parts.add(new Part(-center*Math.sin(angle),bottom,center*Math.cos(angle),
                    2*RADIUS*Math.sin(halfAngle),RIM_TOP+seam-bottom,depth,
                    Material.STRIPPED_DARK_OAK_WOOD,(float)Math.toDegrees(angle)));
        }
        // The wider strip endpoints remain hidden beneath the rim's inner lip.
        double span=1.32, radius=1.48;
        for(int row=0;row<16;row++){
            double near=-span+2*span*row/16, far=-span+2*span*(row+1)/16;
            double edge=Math.max(Math.abs(near),Math.abs(far));
            parts.add(new Part(0,.005,(near+far)/2,2*Math.sqrt(radius*radius-edge*edge),.01,far-near,
                    Material.GREEN_TERRACOTTA,0));
        }
        parts.add(new Part(0,-TableGeometry.SURFACE,0,.35,TableGeometry.SURFACE+.005,.35,Material.STRIPPED_DARK_OAK_LOG,0));
        parts.add(new Part(0,-TableGeometry.SURFACE,0,.98,.08,.98,Material.DARK_OAK_PLANKS,0));
        return List.copyOf(parts);
    }
    private RoundCardTable(){}
}
