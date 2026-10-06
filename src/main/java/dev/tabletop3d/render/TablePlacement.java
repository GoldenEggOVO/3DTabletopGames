package dev.tabletop3d.render;

import org.bukkit.Location;

public final class TablePlacement {
    public static boolean overlaps(
            double x,
            double y,
            double z,
            boolean tray,
            double otherX,
            double otherY,
            double otherZ,
            boolean otherTray) {
        return overlaps(x, y, z, tray, 1.125, otherX, otherY, otherZ, otherTray, 1.125);
    }

    public static boolean overlaps(
            double x,
            double y,
            double z,
            boolean tray,
            double halfWidth,
            double otherX,
            double otherY,
            double otherZ,
            boolean otherTray,
            double otherHalfWidth) {
        if (Math.abs(y - otherY) >= 3) {
            return false;
        }
        if (Math.abs(x - otherX) < Math.max(3, halfWidth + otherHalfWidth)
                && Math.abs(z - otherZ) < 3) {
            return true;
        }

        if (tray
                && Math.abs(x + 2 - otherX) < otherHalfWidth + .925
                && Math.abs(z - otherZ) < otherHalfWidth + .925) {
            return true;
        }
        if (otherTray
                && Math.abs(otherX + 2 - x) < halfWidth + .925
                && Math.abs(z - otherZ) < halfWidth + .925) {
            return true;
        }
        return tray && otherTray && Math.abs(x - otherX) < 1.55 && Math.abs(z - otherZ) < 1.55;
    }

    public static Location snap(Location source, int width) {
        double offset = width % 2 == 0 ? 0.0 : 0.5;
        return new Location(
                source.getWorld(),
                Math.floor(source.getX() - offset + .5) + offset,
                Math.floor(source.getY()),
                Math.floor(source.getZ() - offset + .5) + offset);
    }
}
