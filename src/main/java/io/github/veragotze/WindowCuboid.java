package io.github.veragotze;

import org.locationtech.jts.geom.Geometry;

import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;

public class WindowCuboid extends Cuboid {

    private Geometry window;
    public WindowCuboid(double centerx, double centery, double length, double width, double height,
            double orientation, Geometry window) {
        super(centerx, centery, length, width, height, orientation);
        this.window = window;
    }
    public Geometry getWindow() {
        return this.window;
    }
}
