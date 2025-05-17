package io.github.veragotze;

import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.distance.DistanceOp;

import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.util.conversion.JtsGeOxygene;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.geometry.Primitive;

public class TwoSidedAttachedCuboid extends Cuboid {
    double distance;
    LineString referenceLine;
    LengthIndexedLine index;
    LineString referenceLine1;
    LineString referenceLine2;
    private Polygon geomJTS = null;

    public TwoSidedAttachedCuboid(double distance, double width, double height, LineString referenceLine1,
            LineString referenceLine2) {
        super(0, 0, 0, width, height, 0);
        this.distance = distance;
        this.referenceLine1 = referenceLine1;
        this.referenceLine2 = referenceLine2;
        computeBisector();
        update();
        // System.err.println("TwoSidedAttachedCuboid\n"+this.geomJTS);
    }

    private void update() {
        double segmentLength = this.index.getEndIndex();
        Coordinate center = this.index.extractPoint(segmentLength * distance);
        this.centerx = center.x;
        this.centery = center.y;
        Coordinate[] pts = new Coordinate[5];
        double index1 = Math.max(segmentLength * distance - width / 2,0);
        double index2 = Math.min(segmentLength * distance + width / 2,segmentLength);
        Coordinate p1 = this.index.extractPoint(index1);
        Coordinate p2 = this.index.extractPoint(index2);
        // System.out.println("center = " + this.referenceLine.getFactory().createPoint(center));
        // System.out.println("p1 " + index1 + " = " + this.referenceLine.getFactory().createPoint(p1));
        // System.out.println("p2 " + index2 + " = " + this.referenceLine.getFactory().createPoint(p2));
        DistanceOp distOp1 = new DistanceOp(referenceLine1, referenceLine.getFactory().createPoint(p1));
        DistanceOp distOp2 = new DistanceOp(referenceLine2, referenceLine.getFactory().createPoint(p1));
        DistanceOp distOp3 = new DistanceOp(referenceLine1, referenceLine.getFactory().createPoint(p2));
        DistanceOp distOp4 = new DistanceOp(referenceLine2, referenceLine.getFactory().createPoint(p2));
        pts[0] = distOp1.nearestPoints()[0];
        pts[1] = distOp2.nearestPoints()[0];
        pts[2] = distOp4.nearestPoints()[0];
        pts[3] = distOp3.nearestPoints()[0];
        pts[4] = new Coordinate(pts[0]);
        LinearRing ring = this.referenceLine.getFactory().createLinearRing(pts);
        if (!Orientation.isCCW(pts)) ring = ring.reverse();
        Polygon poly = this.referenceLine.getFactory().createPolygon(ring, null);
        this.geomJTS = poly;
    }

    private void computeBisector() {
        LineString baseLine1 = referenceLine1.getFactory().createLineString(new Coordinate[] {
                referenceLine1.getStartPoint().getCoordinate(), referenceLine2.getStartPoint().getCoordinate() });
        LineString baseLine2 = referenceLine1.getFactory().createLineString(new Coordinate[] {
                referenceLine1.getEndPoint().getCoordinate(), referenceLine2.getEndPoint().getCoordinate() });
        if (baseLine1.intersects(baseLine2)) {
            baseLine1 = referenceLine1.getFactory().createLineString(new Coordinate[] {
                    referenceLine1.getStartPoint().getCoordinate(), referenceLine2.getEndPoint().getCoordinate() });
            baseLine2 = referenceLine1.getFactory().createLineString(new Coordinate[] {
                    referenceLine1.getEndPoint().getCoordinate(), referenceLine2.getStartPoint().getCoordinate() });
        }
        this.referenceLine = referenceLine1.getFactory().createLineString(
                new Coordinate[] { baseLine1.getCentroid().getCoordinate(), baseLine2.getCentroid().getCoordinate() });
        this.index = new LengthIndexedLine(referenceLine);
    }

    @Override
    public Object[] getArray() {
        return new Object[] { this.distance, this.width, this.height };
    }

    @Override
    public double[] toArray() {
        return new double[] { this.distance, this.width, this.height };
    }

    @Override
    public void set(List<Double> list) {
        this.distance = list.get(0);
        this.width = list.get(1);
        this.height = list.get(2);
        update();
        this.setNew(true);
    }

    @Override
    public void setCoordinates(double[] val1) {
        val1[0] = this.distance;
        val1[1] = this.width;
        val1[2] = this.height;
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public Polygon toGeometry() {
        return this.geomJTS;
    }
    @Override
    public double getArea() {
		return this.geomJTS.getArea();
	}

    public static void main(String[] args) {
        GeometryFactory fact = new GeometryFactory();
        LineString l1 = fact.createLineString(new Coordinate[] {
                new Coordinate(687536.73037157126236707, 5339815.34319678135216236),
                new Coordinate(687553.75536076375283301, 5339815.08207731507718563)
        });
        LineString l2 = fact.createLineString(new Coordinate[] {
                new Coordinate(687536.57369989168364555, 5339798.00486422888934612),
                new Coordinate(687553.65091297728940845, 5339797.48262529727071524)
        });
        TwoSidedAttachedCuboid t = new TwoSidedAttachedCuboid(0.2, 10.0, 10.0, l1, l2);
        System.err.println(t.referenceLine);
        System.err.println(t.toGeometry());
        System.err.println(t.getArea());
    }
        @Override
	public IOrientableSurface getFootprint() {
        try {
            return (IOrientableSurface) JtsGeOxygene.makeGeOxygeneGeom(this.geomJTS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
	public double getVolume() {
		return this.geomJTS.getArea() * this.height;
	}
	@Override
	public double intersectionArea(Primitive p) {
		return this.toGeometry().intersection(p.toGeometry()).getArea();
	}
}
