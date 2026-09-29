package dev.tabletop3d;

import org.bukkit.Location;

/** Even-width map mosaics meet at a block corner; odd-width mosaics at a block centre. */
final class TablePlacement {
    static boolean overlaps(double x,double y,double z,boolean tray,double otherX,double otherY,double otherZ,boolean otherTray){
        return overlaps(x,y,z,tray,1.125,otherX,otherY,otherZ,otherTray,1.125);
    }
    static boolean overlaps(double x,double y,double z,boolean tray,double halfWidth,double otherX,double otherY,double otherZ,boolean otherTray,double otherHalfWidth){
        if(Math.abs(y-otherY)>=3)return false;
        if(Math.abs(x-otherX)<3&&Math.abs(z-otherZ)<3)return true;
        // Retain the original board/seat clearance; add only the side stand's footprint.
        if(tray&&Math.abs(x+2-otherX)<otherHalfWidth+.925&&Math.abs(z-otherZ)<otherHalfWidth+.925)return true;
        if(otherTray&&Math.abs(otherX+2-x)<halfWidth+.925&&Math.abs(z-otherZ)<halfWidth+.925)return true;
        return tray&&otherTray&&Math.abs(x-otherX)<1.55&&Math.abs(z-otherZ)<1.55;
    }
    static Location snap(Location source, int width) {
        double offset = width % 2 == 0 ? 0.0 : 0.5;
        return new Location(source.getWorld(), Math.floor(source.getX() - offset + .5) + offset,
                Math.floor(source.getY()), Math.floor(source.getZ() - offset + .5) + offset);
    }
}
