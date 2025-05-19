package io.github.veragotze;

import java.util.Collection;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.rjmcmc.energy.CollectionEnergy;

public class BuildingDistanceEnergy implements CollectionEnergy<Cuboid> {

    private List<Window<Geometry>> windows;
    private double tolerance = 0.1;
    private double threshold = 3.0;

    public BuildingDistanceEnergy(List<Window<Geometry>> windows) {
        this.windows = windows;
    }

    @Override
    public double getValue(Collection<Cuboid> t) {
        return windows.stream().mapToDouble(w -> {
            List<Cuboid> cuboids = t.stream().filter(c -> w.geometry.contains(c.toGeometry().getCentroid())).toList();
            List<Polygon> cPolygons = cuboids.stream().map(c->c.toGeometry()).toList();
            double maxHeight = cuboids.stream().mapToDouble(c->c.height).max().orElse(0);
            switch (w.buildingStyle) {
                case "g":
                    // we augment the building geometries to make their chance to touch better
                    Geometry bUnion = w.geometry.getFactory().createGeometryCollection(cPolygons.stream()
                            .map(p -> p.buffer(tolerance)).toList().toArray(new Geometry[cPolygons.size()])).union();
                    if (bUnion.getNumGeometries() <= 1)
                        return 0;
                    double maxDistance = IntStream.range(0, bUnion.getNumGeometries() - 1).mapToDouble(index -> {
                        Geometry g1 = bUnion.getGeometryN(index);
                        return IntStream.range(index + 1, bUnion.getNumGeometries())
                                .mapToDouble(index2 -> bUnion.getGeometryN(index2).distance(g1)).max().orElse(0);
                    }).max().orElse(0);
                    Geometry closure = bUnion.buffer(maxDistance).buffer(-maxDistance);
                    Geometry difference = closure.difference(bUnion);
                    // System.err.println("union=\n" + bUnion);
                    // System.err.println("closure=\n" + closure);
                    // System.err.println("difference=\n" + difference);
                    return difference.getArea() * maxHeight;
                default:
                    Geometry union = w.geometry.getFactory()
                            .createGeometryCollection(cPolygons.toArray(new Geometry[cPolygons.size()])).union();
                    if (union.getNumGeometries() <= 1)
                        return 0;
                    return IntStream.range(0, union.getNumGeometries() - 1).mapToDouble(index -> {
                        Geometry g1 = union.getGeometryN(index);
                        return IntStream.range(index + 1, union.getNumGeometries())
                                .mapToObj(index2 -> union.getGeometryN(index2)).flatMapToDouble(g2 -> {
                                    if (g2.distance(g1) < threshold)
                                        return DoubleStream.of(g1.buffer(threshold / 2)
                                                .intersection(g2.buffer(threshold / 2)).getArea());
                                    return DoubleStream.empty();
                                }).sum();
                    }).sum() * maxHeight;
            }
        }).sum();
    }

    public static void main(String[] args) {
        
    }
}
