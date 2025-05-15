package io.github.veragotze;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.vecmath.Point3d;
import javax.xml.bind.JAXBException;

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.building.Building;
import org.citygml4j.core.model.construction.GroundSurface;
import org.citygml4j.core.model.construction.Height;
import org.citygml4j.core.model.construction.HeightProperty;
import org.citygml4j.core.model.construction.RoofSurface;
import org.citygml4j.core.model.construction.WallSurface;
import org.citygml4j.core.model.core.AbstractSpaceBoundaryProperty;
import org.citygml4j.core.model.core.AbstractThematicSurface;
import org.citygml4j.core.util.geometry.GeometryFactory;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.CityGMLContextException;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.PrecisionModel;
import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurfaceProperty;
import org.xmlobjects.gml.model.geometry.primitives.Polygon;
import org.xmlobjects.gml.model.geometry.primitives.Shell;
import org.xmlobjects.gml.model.geometry.primitives.Solid;
import org.xmlobjects.gml.model.geometry.primitives.SolidProperty;
import org.xmlobjects.gml.model.geometry.primitives.SurfaceProperty;
import org.xmlobjects.gml.model.measures.Length;
import org.xmlobjects.gml.util.id.DefaultIdCreator;
import org.xmlobjects.gml.util.id.IdCreator;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.feature.IPopulation;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPositionList;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IEnvelope;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.ILineString;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IRing;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.geometrie.Vecteur;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.sig3d.calculation.BooleanOperators;
import fr.ign.cogit.geoxygene.sig3d.equation.ApproximatedPlanEquation;
import fr.ign.cogit.geoxygene.sig3d.equation.PlanEquation;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPositionList;
import fr.ign.cogit.geoxygene.spatial.coordgeom.GM_LineString;
import fr.ign.cogit.geoxygene.spatial.coordgeom.GM_Polygon;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiSurface;
import fr.ign.cogit.geoxygene.spatial.geomprim.GM_Ring;
import fr.ign.cogit.geoxygene.spatial.geomprim.GM_Solid;
import fr.ign.cogit.geoxygene.util.algo.JtsAlgorithms;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.geoxygene.util.conversion.JtsGeOxygene;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;

public class ExportAsCityGML {
    private static IdCreator idCreator;
        private static GeometryFactory factory;
    
        private static Envelope getEnvelope(IEnvelope inputEnvelope, double minHeight, double maxHeight) {
            Envelope envelope = new Envelope();
            DirectPosition lowerCorner = new DirectPosition();
            lowerCorner.setValue(Arrays.asList(inputEnvelope.minX(), inputEnvelope.minY(), minHeight));
            envelope.setLowerCorner(lowerCorner);
            DirectPosition upperCorner = new DirectPosition();
            upperCorner.setValue(Arrays.asList(inputEnvelope.maxX(), inputEnvelope.maxY(), maxHeight));
            envelope.setUpperCorner(upperCorner);
            envelope.setSrsDimension(3);
            envelope.setSrsName("EPSG:25832");
            return envelope;
        }
    
        public static ILineString longestEdge(IDirectPositionList dpl) {
            int nbPoints = dpl.size();
            dpl.remove(nbPoints - 1);
            List<fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition> lC = dpl.stream()
                    .map(p -> new fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition(p.getX(), p.getY(), 0)).toList();
            return IntStream.range(0, lC.size())
                    .mapToObj(i -> new GM_LineString(lC.get(i), lC.get((i + 1) % lC.size())))
                    .max((l1, l2) -> Double.compare(l1.length(), l2.length())).get();
        }
    
        public static List<Edge> fromDPLToEdges(IDirectPositionList dpl) {
            int nbPoints = dpl.size();
            dpl.remove(nbPoints - 1);
            List<Corner> lC = dpl.stream().map(p -> new Corner(p.getX(), p.getY(), 0)).toList();
            return IntStream.range(0, lC.size())
                    .mapToObj(i -> new Edge(lC.get(i), lC.get((i + 1) % lC.size())))// , (i % 2 == 0) ? 0 : Math.PI / 4))
                    .toList();
        }
    
        private static IDirectPositionList convertLoopCorner(Loop<Point3d> lC) {
            return new DirectPositionList(
                    new ArrayList<>(lC.stream()
                            .map(c -> (IDirectPosition) new fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition(c.x,
                                    c.y, c.z))
                            .toList()));
        }
    
        private static LineSegment edgeToLineSegment(Edge e) {
            return new LineSegment(new Coordinate(e.start.x, e.start.y), new Coordinate(e.end.x, e.end.y));
        }
    
        private static LineSegment lineStringToLineSegment(ILineString l) {
            return new LineSegment(
                    new Coordinate(l.getControlPoint(0).getX(), l.getControlPoint(0).getY()),
                    new Coordinate(l.getControlPoint(l.sizeControlPoint() - 1).getX(),
                            l.getControlPoint(l.sizeControlPoint() - 1).getY()));
        }
    
        private static boolean perpendicular(double a) {
            // System.out.println("angle in " + a);
            // make sure the angle is between -PI and PI
            double angle = (a < -Math.PI) ? a + 2 * Math.PI : (a > Math.PI) ? a - 2 * Math.PI : a;
            // System.out.println("angle norm " + angle);
            // perpendicular enough if angle is between PI/4 and 3PI/4
            return Math.abs(angle) > Math.PI / 4 && Math.abs(angle) < 3 * Math.PI / 4;
        }
    
        private static List<IOrientableSurface> getGableRoof(IOrientableSurface surface, ILineString edge) {
            LoopL<Edge> input = new LoopL<Edge>();
            IRing rExt = ((IPolygon) surface).getExterior();
            IDirectPositionList points = rExt.coord();
            PlanEquation pe = new ApproximatedPlanEquation(points);
            Vecteur normal = pe.getNormale().getNormalised();
            if (normal.getZ() < 0) {
                System.err.println("reverse roof");
                points = points.reverse();
            }
            double height = rExt.coord().get(0).getZ();
            List<Edge> lEExt = fromDPLToEdges(points);
            LineSegment segment = lineStringToLineSegment(edge);
            // determine wich edges are perpendicular to the longest edges
            // TODO check if they are end edges too? (perpendicular to previous and next
            // edge)
            lEExt.forEach(e -> e.setAngle(perpendicular(edgeToLineSegment(e).angle() - segment.angle()) ? 0 : Math.PI / 4));
            // lEExt.forEach(e->System.err.println("machineangle " + e.getAngle()));
            Loop<Edge> loop = new Loop<Edge>(lEExt);
            lEExt.forEach(e -> e.machine = new Machine(e.getAngle()));
            input.add(loop);
            Skeleton s = new Skeleton(input, true);
            s.skeleton();
            return s.output.faces.values().stream().map(f -> {
                System.err.println("face = " + f.pointCount() + " with height " + height);
                IPolygon poly = new GM_Polygon();
                for (Loop<Point3d> lP : f.points) {
                    IDirectPositionList dpl = convertLoopCorner(lP);
                    // System.err.println("dpl 1 =\n" + dpl);
                    // add height
                    dpl.stream().forEach(p -> p.setZ(p.getZ() + height));
                    // make sure the face is closed
                    dpl.add(dpl.get(0));
                    dpl.reverse();//???
                    // System.err.println("dpl 2 =\n" + dpl);
                    if (poly.getExterior() == null) {
                        poly.setExterior(new GM_Ring(new GM_LineString(dpl)));
                    } else {
                        poly.addInterior(new GM_Ring(new GM_LineString(dpl)));
                    }
                }
                // System.err.println("polygon = " + poly);
                return (IOrientableSurface) poly;
            }).toList();
        }
    
        private static double averageHeight(GM_Polygon p) {
            List<IDirectPosition> list = p.getExterior().coord().getList();
            PlanEquation pe = new ApproximatedPlanEquation(p);
            Vecteur normal = pe.getNormale().getNormalised();
            System.err.println(normal.getX() + ", " + normal.getY() + ", " + normal.getZ());
            list.remove(list.size() - 1);
            return list.stream().map(c -> c.getZ()).collect(Collectors.summingDouble(Double::doubleValue)) / list.size();
        }
    
        record BuildingFace(IOrientableSurface surface, double height) {
        };
    
        private static BuildingFace getRoofFromCuboid(List<IOrientableSurface> list) {
            return list.stream().map(f -> new BuildingFace(f, averageHeight((GM_Polygon) f)))
                    .max((o1, o2) -> Double.compare(o1.height, o2.height)).get();
        }
    
        private static Double roundUp(Double v) {
            return Long.valueOf(Math.round(v * 10)).doubleValue() / 10;
        }
    
        private static GM_Polygon roundPolygon(GM_Polygon p) {
            List<IDirectPosition> coords = p.getExterior().coord().stream().map(
                    c -> new fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition(c.getX(), c.getY(), roundUp(c.getZ())))
                    .collect(Collectors.toList());
            return new GM_Polygon(new GM_LineString(coords));
        }

        private static List<IOrientableSurface> getRoofSurfaces(List<IOrientableSurface> list) {
            return list.stream().flatMap(p -> {
                PlanEquation pe = new ApproximatedPlanEquation(p);
                Vecteur normal = pe.getNormale().getNormalised();
                return (normal.getZ() > 0.1) ? Stream.of(p) : Stream.empty();
            }).toList();
        }

        private static List<IOrientableSurface> getAggregatedRoofs(List<IOrientableSurface> list) {
            Map<Double, List<IOrientableSurface>> grouped = list.stream().collect(Collectors.groupingBy(p -> p.coord().get(0).getZ()));
            return grouped.values().stream().flatMap(l -> unionToStream(l)).toList();
        }
    
        @SuppressWarnings("unchecked")
        private static Stream<IOrientableSurface> unionToStream(List<IOrientableSurface> l) {
            IGeometry union = JtsAlgorithms.union(l);
            if (IOrientableSurface.class.isInstance(union)) {
                return Stream.of((IOrientableSurface) union);
            }
            if (IMultiSurface.class.isInstance(union)) {
                return ((IMultiSurface<IOrientableSurface>) union).stream();
            }
            return Stream.empty();
        }
    
        public static void main(String[] args) throws CityGMLWriteException, JAXBException, CityGMLContextException {
            String outputFolder = "./output_roof/";
            // String outputFolder = "./output/";
            IPopulation<IFeature> exported = ShapefileReader.read(outputFolder + "out.shp");
            System.out.println("ALL DONE! " + (outputFolder + "out.shp"));
            File folder = new File(App.class.getClassLoader().getResource("munich_full/").getPath());
            IFeatureCollection<IFeature> fensters = Loader.readShapefile(new File(folder, "fenster.shp"));
    
            CityGMLContext context = CityGMLContext.newInstance();
    
            CityGMLVersion version = CityGMLVersion.v2_0;
            CityGMLOutputFactory out = context.createCityGMLOutputFactory(version);
            // CityGMLOutputFactory out =
            // context.createCityGMLBuilder().createCityGMLOutputFactory(version);
            // CityModel city = new CityModel();
            idCreator = DefaultIdCreator.getInstance();
        factory = GeometryFactory.newInstance().withIdCreator(idCreator);
        List<Building> outBuildings = new ArrayList<>();
        // MyGeometryVisitor visitor = new MyGeometryVisitor();
        double maxBuildingHeight = 0.0;
        // we group buildings by parcel and window
        Map<String, List<IFeature>> grouped = exported.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAttribute("parcelId").toString() + "/" + f.getAttribute("windowId").toString()));
        for (Entry<String, List<IFeature>> entry : grouped.entrySet()) {
            String windowId = entry.getKey().split("/")[1];
            System.out.println("windowId = " + windowId);
            String roofShape = fensters.stream().filter(f -> f.getAttribute("OBJECTID").toString().equals(windowId))
                    .findFirst().get().getAttribute("DACHFORM").toString();
            IGeometry union = null;
            Collection<? extends IFeature> buildings = entry.getValue();
            double longestEdgeLength = 0;
            ILineString longestEdge = null;
            for (IFeature building : buildings) {
                // System.err.println(building.getGeom());
                @SuppressWarnings("unchecked")
                GM_MultiSurface<? extends IOrientableSurface> buildingGeometry = (GM_MultiSurface<? extends IOrientableSurface>) building
                        .getGeom();
                // buildingGeometry.getList().forEach(g -> System.err.println("H1 = " +
                // averageHeight((GM_Polygon) g)));
                List<IOrientableSurface> roundedList = buildingGeometry.getList().stream()
                        .map(o -> (IOrientableSurface) roundPolygon((GM_Polygon) o)).toList();
                // roundedList.forEach(g -> System.err.println("H2 = " +
                // averageHeight((GM_Polygon) g)));
                List<IOrientableSurface> list = roundedList.stream()
                        .map(s -> (IOrientableSurface) new JtsAlgorithms().translate(s, 0.0, 0.0, 508.0)).toList();
                // list.forEach(g -> System.err.println("H3 = " + averageHeight((GM_Polygon)
                // g)));
                BuildingFace roof = getRoofFromCuboid(list);
                // FIXME maybe we should compute the longest edge for the entire window?
                ILineString edge = longestEdge(((IPolygon) roof.surface).getExterior().coord());
                if (edge.length() > longestEdgeLength) {
                    longestEdgeLength = edge.length();
                    longestEdge = edge;
                }
                // System.err.println("Roof = " + roof);
                List<IOrientableSurface> newList = new ArrayList<>(list);
                newList.remove(roof.surface);
                // put the roof in the proper orientation
                newList.add(new GM_Polygon(new GM_LineString(roof.surface.coord().reverse())));
                // newList.addAll(getGableRoof(roof.surface));
                // System.err.println("NewList = " + newList);
                IGeometry geom = new GM_Solid(newList);
                // System.err.println("Solid = " + geom);
                // System.err.println(geom);
                if (union == null) {
                    union = geom;
                } else {
                    union = BooleanOperators.compute(new DefaultFeature(union), new DefaultFeature(geom),
                            BooleanOperators.UNION);
                }
            }
            if (union != null) {
                Building building = new Building();
                List<IOrientableSurface> list = ((GM_Solid) union).getFacesList();
                final List<IOrientableSurface> newList = new ArrayList<>(list);;
                if (roofShape.equals("SD")) {
                    // gable roof
                    System.err.println("longestEdge = " + longestEdge);
                    List<IOrientableSurface> roofSurfaces = getRoofSurfaces(list);
                    System.err.println("list1 = " + newList.size());
                    newList.forEach(p -> System.err.println("list1 = (" + newList.indexOf(p) + ") = " + p));
                    System.err.println("roof = " + roofSurfaces.size());
                    roofSurfaces.forEach(p -> System.err.println("roof = (" + newList.indexOf(p) + ") = " + p));
                    // newList.removeAll(roofSurfaces);
                    newList.forEach(p -> System.err.println("test1 = (" + new JtsAlgorithms().equals(p, roofSurfaces.get(0)) + ") = " + p));

                    newList.forEach(p -> {
                        try {
                            System.err.println("test2 = (" + JtsGeOxygene.makeJtsGeom(p));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    org.locationtech.jts.geom.GeometryFactory fact = new org.locationtech.jts.geom.GeometryFactory(new PrecisionModel(), newList.get(0).getCRS());
                    newList.forEach(p -> {
                        try {
                            System.err.println("test3 = (" + AdapterFactory.toGeometry(fact, p, false));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    newList.forEach(p -> {
                        try {
                            System.err.println("test4 = (" + fact.createPolygon(
                                (org.locationtech.jts.geom.LinearRing) AdapterFactory.toGeometry(fact,
                                    ((IPolygon) p).getExterior(), false),
                                AdapterFactory.toLinearRingArray(fact,
                                    ((IPolygon) p).getInterior(), false)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    System.err.println("test5");
                    newList.forEach(p -> {
                        ((IPolygon) p).getExterior().coord().forEach(c->System.err.println(AdapterFactory.toCoordinate(c)));
                    });
                    System.err.println("test6");
                    newList.forEach(p -> {
                        CoordinateSequence sequence = AdapterFactory.toCoordinateSequence(fact, ((IPolygon) p).getExterior().coord(), false);
                        for (int i = 0 ; i < sequence.size() ; i++) {
                            System.err.println(sequence.getCoordinate(i));
                        }
                        System.err.println(fact.createPolygon(sequence));
                    });

                    roofSurfaces.forEach(roof->newList.remove(roof));//FIXME: floor is removed instead !???
                    System.err.println("list2 = " + newList.size());
                    newList.forEach(p -> System.err.println("list2 = (" + newList.indexOf(p) + ") = " + p));
                    final ILineString edge = longestEdge;
                    // create the gable roofs
                    List<IOrientableSurface> aggregatedRoofSurfaces = getAggregatedRoofs(roofSurfaces);
                    List<IOrientableSurface> newRoofSurfaces = aggregatedRoofSurfaces.stream()
                            .flatMap(r -> getGableRoof(r, edge).stream()).toList();
                    newList.addAll(newRoofSurfaces);
                    // project cuboids to parcel/window
                    // merge close points from cuboids
                }
                GM_Solid solid = new GM_Solid(newList);// (GM_Solid) union;
                double min = solid.coord().stream().map(c -> c.getZ()).mapToDouble(Double::doubleValue).min()
                        .getAsDouble();
                double max = solid.coord().stream().map(c -> c.getZ()).mapToDouble(Double::doubleValue).max()
                        .getAsDouble();
                double height = max - min;
                maxBuildingHeight = Math.max(maxBuildingHeight, height);
                System.out.println(solid);
                // building.setLod2Solid(convert(solid));
                convert(building, solid);
                System.err.println(building.getLod2Solid());
                // ((Solid) building.getLod2Solid().getObject()).getExterior().getObject().accept(visitor);
                // Envelope envelope = getEnvelope(union.envelope(), min, max);
                // building.setBoundedBy(new BoundingShape(envelope));
                Envelope envelope = building.computeEnvelope();
                building.setBoundedBy(new BoundingShape(envelope));
                Length l = new Length();
                l.setUom("m");
                l.setValue(height);
                // building.setMeasuredHeight(l);
                building.getHeights().add(new HeightProperty(Height.ofMeasuredHeight(l)));
                outBuildings.add(building);
                // CityObjectMember object = new CityObjectMember();
                // object.setCityObject(building);
                // city.addCityObjectMember(object);
            }
        }
        System.err.println("MAX Building Height = " + maxBuildingHeight);
        Envelope envelope = getEnvelope(exported.getEnvelope(), 508.0, 508.0 + maxBuildingHeight);
        // city.setBoundedBy(new BoundingShape(envelope));
        // Appearance appearance = new Appearance();
        // X3DMaterial m = new X3DMaterial();
        // m.setDiffuseColor(new Color(1.0, 0.0, 0.0));
        // m.setTarget(visitor.getIds());
        // SurfaceDataProperty property = new SurfaceDataProperty(m);
        // appearance.addSurfaceDataMember(property);
        // city.setAppearanceMember(Arrays.asList(new AppearanceMember(appearance)));

        // File output = new File(p.get("result").toString() + "city_union_out_3d.gml");
        File output = new File(outputFolder + "city_union_out_3d.gml");
        // System.out.println("Writing the feature as CityGML " + version + " file " +
        // output);
        // CityGMLWriter writer = out.createCityGMLWriter(output,
        // StandardCharsets.UTF_8.name());
        // writer.setIndentString(" ");
        // writer.write(city);
        // writer.close();

        try (CityGMLChunkWriter writer = out.createCityGMLChunkWriter(output, StandardCharsets.UTF_8.name())) {
            writer.withIndent("  ")
                    .withDefaultSchemaLocations()
                    .withDefaultPrefixes()
                    .withDefaultNamespace(CoreModule.of(version).getNamespaceURI())
                    .withHeaderComment("File created with citygml4j");

            System.out.println("Setting metadata on the CityModel of the output file");
            writer.getCityModelInfo().setBoundedBy(new BoundingShape(envelope));

            System.out.println("Writing the building object to the file");
            outBuildings.forEach(building -> {
                try {
                    writer.writeMember(building);
                } catch (CityGMLWriteException e) {
                    e.printStackTrace();
                }
            });
        }

        System.out.println("ALL DONE! city_union_out_3d.gml");
    }

    private static void convert(Building building, GM_Solid solid) {
        List<Polygon> polygons = new ArrayList<>();
        solid.getFacesList().forEach(f->{
            IPolygon polygon = (IPolygon) f;
            PlanEquation pe = new ApproximatedPlanEquation(polygon.getExterior().coord());
            Vecteur normal = pe.getNormale().getNormalised();
            Polygon p = factory.createPolygon(polygon.getExterior().coord().toArray3D(),3);
            if (normal.getZ() < 0) {
                building.addBoundary(processBoundarySurface(new GroundSurface(), p));
            } else if (normal.getZ() > 0) {
                building.addBoundary(processBoundarySurface(new RoofSurface(), p));
            } else {
                building.addBoundary(processBoundarySurface(new WallSurface(), p));
            }
            polygons.add(p);
        });
        Shell shell = new Shell();
        polygons.stream().map(p->new SurfaceProperty("#" + p.getId())).forEach(shell.getSurfaceMembers()::add);
        building.setLod2Solid(new SolidProperty(new Solid(shell)));
    }
    
    private static AbstractSpaceBoundaryProperty processBoundarySurface(AbstractThematicSurface thematicSurface, Polygon... polygons) {
        thematicSurface.setId(idCreator.createId());
        thematicSurface.setLod2MultiSurface(new MultiSurfaceProperty(factory.createMultiSurface(polygons)));
        return new AbstractSpaceBoundaryProperty(thematicSurface);
    }
}
