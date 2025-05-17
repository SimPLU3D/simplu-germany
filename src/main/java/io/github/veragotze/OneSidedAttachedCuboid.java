package io.github.veragotze;

import java.util.List;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;

public class OneSidedAttachedCuboid extends Cuboid {
    LineString referenceLine;
    double px;
    double py;

    public OneSidedAttachedCuboid(double px, double py, double width, double height, LineString referenceLine) {
        super(0, 0, 0.0, width, height, 0.0);
        this.px = px;
        this.py = py;
        this.referenceLine = referenceLine;
        update();
    }

    /**
     * Get projected point P' of P on line e1. Faster version.
     * 
     * @return projected point p.
     */
    public Coordinate projectedPointOnLine(Coordinate p) {
        // get dot product of e1, e2
        double e1x = referenceLine.getEndPoint().getX() - referenceLine.getStartPoint().getX();
        double e1y = referenceLine.getEndPoint().getY() - referenceLine.getStartPoint().getY();
        double e2x = p.getX() - referenceLine.getStartPoint().getX();
        double e2y = p.getY() - referenceLine.getStartPoint().getY();
        // dotProduct
        double valDp = e1x * e2x + e1y * e2y;
        // get length                                                           
        double lenE1 = Math.sqrt(e1x * e1x + e1y * e1y);
        double lenE2 = Math.sqrt(e2x * e2x + e2y * e2y);
        double cos = valDp / (lenE1 * lenE2);
        // length of AP'                                                        
        double projLenOfLine = cos * lenE2;
        return new Coordinate(
                        referenceLine.getStartPoint().getX() + (projLenOfLine * e1x) / lenE1,
                        referenceLine.getStartPoint().getY() + (projLenOfLine * e1y) / lenE1);
    }

    private void update() {
        Coordinate c = new Coordinate(px, py);
        Coordinate projection = projectedPointOnLine(c);
        this.orientation = Angle.angle(c, projection);
        this.centerx = (px + projection.getX()) / 2;
        this.centery = (py + projection.getY()) / 2;
        this.length = c.distance(projection);
        // System.out.println("Point=\n"+referenceLine.getFactory().createPoint(c));
        // System.out.println("Proj=\n"+referenceLine.getFactory().createPoint(projection));
        // System.out.println("Center=\n"+referenceLine.getFactory().createPoint(new Coordinate(centerx,centery)));
    }

    @Override
    public Object[] getArray() {
        return new Object[] { this.px, this.py, this.width, this.height };
    }

    @Override
    public double[] toArray() {
        return new double[] { this.px, this.py, this.width, this.height };
    }

    @Override
    public void set(List<Double> list) {
        this.py = list.get(0);
        this.py = list.get(1);
        this.width = list.get(2);
        this.height = list.get(3);
        update();
        this.setNew(true);
    }

    @Override
    public void setCoordinates(double[] val1) {
        val1[0] = this.px;
        val1[1] = this.py;
        val1[2] = this.width;
        val1[3] = this.height;
    }

    @Override
    public int size() {
        return 4;
    }
    public static void main(String[] args) {
        LineString line = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(687630.061593, 5339942.640821), new Coordinate(687617.162712, 5339950.306113)});
        OneSidedAttachedCuboid b = new OneSidedAttachedCuboid(687619.57, 5339938.96, 10, 10, line);
        System.out.println(b.toGeometry());
    }
}
