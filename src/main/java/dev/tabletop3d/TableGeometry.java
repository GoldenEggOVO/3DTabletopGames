package dev.tabletop3d;

import dev.tabletop3d.rules.Cell;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

/** Shared coordinates for the map, models and hit testing; never clamps a miss to an edge cell. */
final class TableGeometry {
    // Up-facing item frames are snapped to integer block height + 1/32 by Purpur.
    static final double SURFACE = 1.03125, REACH = 5.5;
    final String kind;
    final GameWorld.Layout layout;
    final List<Cell> cells;
    final Map<String, Cell> byId;
    final double spacing;

    TableGeometry(String kind, List<Cell> cells) {
        this.kind = kind;
        this.cells = List.copyOf(cells);
        layout = GameWorld.layout(kind, cells);
        byId = new LinkedHashMap<>();
        cells.forEach(c -> byId.put(c.id(), c));
        spacing =
                kind.equals("checkers")
                        ? layout.xUnit() * 2
                        : Math.min(layout.xUnit(), layout.zUnit());
    }

    double x(Cell c) {
        return layout.x(c);
    }

    double z(Cell c) {
        return layout.z(c);
    }

    boolean squares() {
        return Set.of("chess", "draughts", "reversi").contains(kind);
    }

    double radius() {
        return spacing * (squares() ? .499 : .46);
    }

    String hit(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return null;
        }
        if (Math.abs(x - 1.30) < .20 && Math.abs(z) < .22 && (kind.equals("ludo"))) {
            return "@roll";
        }
        if (Math.abs(x) < .45 && Math.abs(z - 1.27) < .15) {
            return "@menu";
        }
        Cell best = null;
        double distance = Double.MAX_VALUE;
        for (Cell c : cells) {
            double dx = x - x(c), dz = z - z(c);
            double d = squares() ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.hypot(dx, dz);
            if (d < distance) {
                distance = d;
                best = c;
            }
        }
        return best != null && distance <= radius() ? best.id() : null;
    }

    static double intersection(double eyeY, double dy, double planeY) {
        if (!Double.isFinite(eyeY)
                || !Double.isFinite(dy)
                || !Double.isFinite(planeY)
                || dy >= -1e-5) {
            return -1;
        }
        double t = (planeY - eyeY) / dy;
        return t >= 0 && t <= REACH ? t : -1;
    }

    static double menuHit(
            String kind, boolean sideTray, Location origin, Location eye, Vector direction) {
        if (!Objects.equals(origin.getWorld(), eye.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        Vector from = eye.toVector().subtract(origin.toVector()),
                ray = direction.clone().normalize();
        double nearest = Double.POSITIVE_INFINITY;
        if (Set.of("color-eight", "doudizhu", "liars-bar", "texas-holdem").contains(kind)) {
            nearest = roundHit(from, ray);
        } else {
            List<BoundingBox> bounds = new ArrayList<>();
            if (kind.equals("yacht")) {
                bounds.add(new BoundingBox(-2.45, -.19, -1.125, -.85, .055, 1.125));
                bounds.add(new BoundingBox(-.68, -.19, -1.125, 1.32, .055, 1.125));
            } else {
                double half = kind.equals("mahjong") ? 1.5 : 1.125;
                bounds.add(
                        new BoundingBox(
                                -half,
                                -.19,
                                -half,
                                half,
                                kind.equals("mahjong") ? .065 : .06,
                                half));
                if (kind.equals("connectfour")) {
                    bounds.add(new BoundingBox(-1.125, -.04, -.10, 1.125, 1.83, .10));
                    for (double x : new double[] {-1.06, 1.06}) {
                        bounds.add(new BoundingBox(x - .10, -.04, -.10, x + .10, 1.79, .10));
                        bounds.add(new BoundingBox(x - .13, -.04, -.325, x + .13, .06, .325));
                    }
                }
                if (kind.equals("ludo")) {
                    double x = sideTray ? 2 : 1.42, halfTray = sideTray ? .7 : .25;
                    bounds.add(
                            new BoundingBox(
                                    x - halfTray, -.16, -halfTray, x + halfTray, .11, halfTray));
                }
            }
            for (BoundingBox box : bounds) {
                var hit = box.rayTrace(from, ray, REACH);
                if (hit != null) {
                    nearest = Math.min(nearest, hit.getHitPosition().distance(from));
                }
            }
        }
        if (!Double.isFinite(nearest) || nearest > REACH) {
            return Double.POSITIVE_INFINITY;
        }
        return eye.getWorld()
                                .rayTraceBlocks(
                                        eye,
                                        ray,
                                        Math.max(.001, nearest - .035),
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null
                ? nearest
                : Double.POSITIVE_INFINITY;
    }

    private static double roundHit(Vector from, Vector ray) {
        double nearest = Double.POSITIVE_INFINITY;
        // The card table has a circular top, so its empty bounding-box corners are not targets.
        if (Math.abs(ray.getY()) > 1e-6) {
            for (double y : new double[] {-.20, .025}) {
                double t = (y - from.getY()) / ray.getY();
                double x = from.getX() + t * ray.getX(), z = from.getZ() + t * ray.getZ();
                if (t >= 0 && x * x + z * z <= 2.25) {
                    nearest = Math.min(nearest, t);
                }
            }
        }
        double a = ray.getX() * ray.getX() + ray.getZ() * ray.getZ();
        double b = 2 * (from.getX() * ray.getX() + from.getZ() * ray.getZ());
        double c = from.getX() * from.getX() + from.getZ() * from.getZ() - 2.25;
        double discriminant = b * b - 4 * a * c;
        if (a > 1e-6 && discriminant >= 0) {
            for (double sign : new double[] {-1, 1}) {
                double t = (-b + sign * Math.sqrt(discriminant)) / (2 * a),
                        y = from.getY() + t * ray.getY();
                if (t >= 0 && y >= -.20 && y <= .025) {
                    nearest = Math.min(nearest, t);
                }
            }
        }
        return nearest;
    }

    int px(Cell c) {
        return pixel(x(c));
    }

    int pz(Cell c) {
        return pixel(z(c));
    }

    static int pixel(double coordinate) {
        return (int) Math.round((coordinate + 1) * 128);
    }
}
