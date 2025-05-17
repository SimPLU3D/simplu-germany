package io.github.veragotze;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.rjmcmc.energy.UnaryEnergy;

public class AlignmentEnergy<T extends Cuboid> implements UnaryEnergy<T> {
    public AlignmentEnergy() {
    }

    @Override
    public double getValue(T t) {
        if (t instanceof WindowCuboid) {
            Geometry geom = t.toGeometry();
            WindowCuboid wCuboid = (WindowCuboid) t;
            Geometry windowContour = wCuboid.getWindow().getBoundary();
            Coordinate[] coords = geom.getCoordinates();
            List<LineSegment> segments = IntStream.range(0, coords.length-1)
                    .mapToObj(i -> new LineSegment(coords[i], coords[(i + 1) % coords.length])).toList();// geom.getFactory().createLineString(new
                                                                                                         // Coordinate[]{coords[i],coords[(i+1)%coords.length]})).toList();
            // segments.forEach(l -> System.err.println(l));
            List<Polygon> poly = segments.stream().flatMap(s -> {
                Coordinate c0 = s.pointAlongOffset(0, -10);
                Coordinate c1 = s.pointAlongOffset(1, -10);
                LineString l0 = geom.getFactory().createLineString(new Coordinate[]{s.p0, c0});
                Point p0 = geom.getFactory().createPoint(s.p0);
                double d0 = l0.intersects(windowContour) ? l0.intersection(windowContour).distance(p0) : -1;
                LineString l1 = geom.getFactory().createLineString(new Coordinate[]{s.p1, c1});
                Point p1 = geom.getFactory().createPoint(s.p1);
                double d1 = l1.intersects(windowContour) ? l1.intersection(windowContour).distance(p1) : -1;
                boolean segmentIntersectsWindow = geom.getFactory().createLineString(new Coordinate[]{s.p0,s.p1}).intersects(windowContour);
                if ((d0 >= 0 && d1 >= 0) || (segmentIntersectsWindow && (d0 >= 0 || d1 >= 0))) {
                    double min = segmentIntersectsWindow ? 0 : Math.min(d0, d1);
                    double max = Math.max(d0, d1);
                    Coordinate c00 = s.pointAlongOffset(0, -min);
                    Coordinate c10 = s.pointAlongOffset(1, -min);
                    Coordinate c01 = s.pointAlongOffset(0, -max);
                    Coordinate c11 = s.pointAlongOffset(1, -max);
                    Polygon p = geom.getFactory().createPolygon(new Coordinate[] { c00, c01, c11, c10, c00 });
                    return Stream.of(p);
                }
                return Stream.empty();
            }).toList();
            // poly.forEach(p-> System.err.println(p));
            return poly.stream().mapToDouble(p->p.getArea()).sum();
        }
        return 0;
    }
    public static void main(String[] args) {
        GeometryFactory factory = new GeometryFactory();
        Coordinate c1 = new Coordinate(687536.97819999977946281, 5339797.92889999970793724);
        Coordinate c2 = new Coordinate(687559.36089999973773956, 5339797.23289999924600124);
        Coordinate c3 = new Coordinate(687558.92410000041127205, 5339758.48619999922811985);
        Coordinate c4 = new Coordinate(687536.48680000007152557, 5339758.73189999908208847);
        Polygon window = factory.createPolygon(new Coordinate[]{ c1,c2,c3,c4,c1 });
        WindowCuboid c = new WindowCuboid(687546.855944621376693, 5339786.896694597788155, 18.956851894547327, 16.258754458341237, 17.499802175142197, 0.11360764712666, window);
        System.err.println("Window\n"+window);
        System.err.println("Building\n"+c.toGeometry());
        AlignmentEnergy<Cuboid> e = new AlignmentEnergy<Cuboid>();
        System.err.println(e.getValue(c));
    }
}
