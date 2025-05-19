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
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.AbstractGenericAttributeProperty;
import org.citygml4j.core.model.core.AbstractSpaceBoundaryProperty;
import org.citygml4j.core.model.core.AbstractThematicSurface;
import org.citygml4j.core.model.generics.StringAttribute;
import org.citygml4j.core.util.geometry.GeometryFactory;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.CityGMLContextException;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.xmlobjects.gml.model.basictypes.Code;
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
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;

public class ExportAsCityGML {
    private static IdCreator idCreator;
    private static GeometryFactory factory;
    private static double roofAngle = Math.PI / 4;

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

    private static boolean perpendicular(double a, double tolerance) {
        // make sure the angle is between -PI and PI
        double angle = (a < -Math.PI) ? a + 2 * Math.PI : (a > Math.PI) ? a - 2 * Math.PI : a;
        // perpendicular enough if angle is between PI/4 and 3PI/4
        return Math.abs(angle) > Math.PI / 2 - tolerance && Math.abs(angle) < Math.PI / 2 + tolerance;
    }

    private static List<IOrientableSurface> getGableRoof(IOrientableSurface surface, ILineString edge) {
        LoopL<Edge> input = new LoopL<Edge>();
        IRing rExt = ((IPolygon) surface).getExterior();
        IDirectPositionList points = rExt.coord();
        PlanEquation pe = new ApproximatedPlanEquation(points);
        Vecteur normal = pe.getNormale().getNormalised();
        if (normal.getZ() < 0) {
            // we reverse the orientation if the polygon faces down
            points = points.reverse();
        }
        double height = rExt.coord().get(0).getZ();
        List<Edge> lEExt = fromDPLToEdges(points);
        LineSegment segment = lineStringToLineSegment(edge);
        // determine wich edges are perpendicular to the longest edges
        IntStream.range(0, lEExt.size()).forEach(index -> {
            Edge e = lEExt.get(index);
            // Edge previous = lEExt.get((index == 0) ? lEExt.size() - 1 : index - 1);
            // Edge next = lEExt.get((index + 1) % lEExt.size());
            // double angle = edgeToLineSegment(previous).angle() - edgeToLineSegment(next).angle();
            // check if they are end edges too(perpendicular to previous and next edge): tested it and it's not better
            // boolean isEnd = angle > Math.PI - Math.PI / 10 || angle < -Math.PI + Math.PI / 10;
            boolean isPerpendicularToLongestEdge = perpendicular(edgeToLineSegment(e).angle() - segment.angle(),
                    Math.PI / 4);
            e.setAngle(isPerpendicularToLongestEdge /* && isEnd */ ? 0 : roofAngle);
        });
        Loop<Edge> loop = new Loop<Edge>(lEExt);
        lEExt.forEach(e -> e.machine = new Machine(e.getAngle()));
        input.add(loop);
        Skeleton s = new Skeleton(input, true);
        s.skeleton();
        return s.output.faces.values().stream().flatMap(f -> {
            // filter out empty polygons
            if (f.pointCount() > 0) {
                IPolygon poly = new GM_Polygon();
                for (Loop<Point3d> lP : f.points) {
                    IDirectPositionList dpl = convertLoopCorner(lP);
                    // add height
                    dpl.stream().forEach(p -> p.setZ(p.getZ() + height));
                    // make sure the face is closed
                    dpl.add(dpl.get(0));
                    // TODO we might want to check the orientation before reversing?
                    dpl.reverse();// ???
                    if (poly.getExterior() == null) {
                        poly.setExterior(new GM_Ring(new GM_LineString(dpl)));
                    } else {
                        poly.addInterior(new GM_Ring(new GM_LineString(dpl)));
                    }
                }
                return Stream.of((IOrientableSurface) poly);
            } else {
                return Stream.empty();
            }
        }).toList();
    }

    private static double averageHeight(GM_Polygon p) {
        List<IDirectPosition> list = p.getExterior().coord().getList();
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
        Map<Double, List<IOrientableSurface>> grouped = list.stream()
                .collect(Collectors.groupingBy(p -> p.coord().get(0).getZ()));
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
        String outputFolder = "./output/";
        String outputFileName = "city_union_out_3d.gml";
        double roofAngleInDegrees = 40.0;
        // we have to do the 90° - angle trick because of camp skeleton
        ExportAsCityGML.roofAngle = (90.0 - roofAngleInDegrees) * Math.PI / 180.0;
        IPopulation<IFeature> exported = ShapefileReader.read(outputFolder + "out.shp");
        File folder = new File(App.class.getClassLoader().getResource("munich_full/").getPath());
        IFeatureCollection<IFeature> fensters = Loader.readShapefile(new File(folder, "fenster.shp"));
        CityGMLContext context = CityGMLContext.newInstance();
        CityGMLVersion version = CityGMLVersion.v2_0;
        CityGMLOutputFactory out = context.createCityGMLOutputFactory(version);
        idCreator = DefaultIdCreator.getInstance();
        factory = GeometryFactory.newInstance().withIdCreator(idCreator);
        List<Building> outBuildings = new ArrayList<>();
        double maxBuildingHeight = 0.0;
        // we group buildings by parcel and window
        Map<String, List<IFeature>> grouped = exported.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getAttribute("parcelId").toString() + "/" + f.getAttribute("windowId").toString()));
        for (Entry<String, List<IFeature>> entry : grouped.entrySet()) {
            String parcelId = entry.getKey().split("/")[0];
            String windowId = entry.getKey().split("/")[1];
            System.err.println("windowId = " + windowId + " parcelId = " + parcelId);
            String roofShape = fensters.stream().filter(f -> f.getAttribute("OBJECTID").toString().equals(windowId))
                    .findFirst().get().getAttribute("DACHFORM").toString();
            IGeometry union = null;
            Collection<? extends IFeature> buildings = entry.getValue();
            double longestEdgeLength = 0;
            ILineString longestEdge = null;
            for (IFeature building : buildings) {
                @SuppressWarnings("unchecked")
                GM_MultiSurface<? extends IOrientableSurface> buildingGeometry = (GM_MultiSurface<? extends IOrientableSurface>) building
                        .getGeom();
                List<IOrientableSurface> roundedList = buildingGeometry.getList().stream()
                        .map(o -> (IOrientableSurface) roundPolygon((GM_Polygon) o)).toList();
                List<IOrientableSurface> list = roundedList.stream()
                        .map(s -> (IOrientableSurface) new JtsAlgorithms().translate(s, 0.0, 0.0, 508.0)).toList();
                BuildingFace roof = getRoofFromCuboid(list);
                ILineString edge = longestEdge(((IPolygon) roof.surface).getExterior().coord());
                if (edge.length() > longestEdgeLength) {
                    longestEdgeLength = edge.length();
                    longestEdge = edge;
                }
                List<IOrientableSurface> newList = new ArrayList<>(list);
                newList.remove(roof.surface);
                // put the roof in the proper orientation
                newList.add(new GM_Polygon(new GM_LineString(roof.surface.coord().reverse())));
                IGeometry geom = new GM_Solid(newList);
                union = (union == null) ? geom
                        : BooleanOperators.compute(new DefaultFeature(union), new DefaultFeature(geom),
                                BooleanOperators.UNION);
            }
            if (union != null) {
                Building building = new Building();
                building.setId(idCreator.createId());
                List<IOrientableSurface> list = ((GM_Solid) union).getFacesList();
                final List<IOrientableSurface> newList = new ArrayList<>(list);
                if (roofShape.equals("SD")) {
                    // gable roof
                    List<IOrientableSurface> roofSurfaces = getRoofSurfaces(list);
                    newList.removeAll(roofSurfaces);
                    final ILineString edge = longestEdge;
                    // create the gable roofs
                    List<IOrientableSurface> aggregatedRoofSurfaces = getAggregatedRoofs(roofSurfaces);
                    List<IOrientableSurface> newRoofSurfaces = aggregatedRoofSurfaces.stream()
                            .flatMap(r -> getGableRoof(r, edge).stream()).toList();
                    newList.addAll(newRoofSurfaces);
                    // TODO project cuboids to parcel/window
                    // TODO merge close points from cuboids
                }
                GM_Solid solid = new GM_Solid(newList);
                double min = solid.coord().stream().map(c -> c.getZ()).mapToDouble(Double::doubleValue).min()
                        .getAsDouble();
                double max = solid.coord().stream().map(c -> c.getZ()).mapToDouble(Double::doubleValue).max()
                        .getAsDouble();
                double height = max - min;
                maxBuildingHeight = Math.max(maxBuildingHeight, height);
                convert(building, solid);
                Envelope envelope = building.computeEnvelope();
                envelope.setSrsName("EPSG:25832");
                building.setBoundedBy(new BoundingShape(envelope));
                Length l = new Length();
                l.setUom("m");
                l.setValue(height);
                building.getHeights().add(new HeightProperty(Height.ofMeasuredHeight(l)));
                building.getGenericAttributes()
                        .add(new AbstractGenericAttributeProperty(new StringAttribute("RoofShape", roofShape)));
                outBuildings.add(building);
            }
        }
        Envelope envelope = getEnvelope(exported.getEnvelope(), 508.0, 508.0 + maxBuildingHeight);
        File output = new File(outputFolder + outputFileName);
        try (CityGMLChunkWriter writer = out.createCityGMLChunkWriter(output, StandardCharsets.UTF_8.name())) {
            writer.withIndent("  ")
                    .withDefaultSchemaLocations()
                    .withDefaultPrefixes()
                    .withDefaultNamespace(CoreModule.of(version).getNamespaceURI())
                    .withHeaderComment("File created with citygml4j");

            writer.getCityModelInfo().setBoundedBy(new BoundingShape(envelope));
            outBuildings.forEach(building -> {
                try {
                    writer.writeMember(building);
                } catch (CityGMLWriteException e) {
                    e.printStackTrace();
                }
            });
        }
        System.out.println("ALL DONE!");
    }

    private static List<Code> groundNames = Arrays.asList(new Code("Ground"));
    private static List<Code> wallNames = Arrays.asList(new Code("Wall"));
    private static List<Code> roofNames = Arrays.asList(new Code("Roof"));

    private static void convert(Building building, GM_Solid solid) {
        List<Polygon> polygons = new ArrayList<>();
        solid.getFacesList().forEach(f -> {
            IPolygon polygon = (IPolygon) f;
            PlanEquation pe = new ApproximatedPlanEquation(polygon.getExterior().coord());
            Vecteur normal = pe.getNormale().getNormalised();
            Polygon p = factory.createPolygon(polygon.getExterior().coord().toArray3D(), 3);
            if (normal.getZ() < 0) {
                building.addBoundary(processBoundarySurface(new GroundSurface(), groundNames, p));
            } else if (normal.getZ() > 0) {
                building.addBoundary(processBoundarySurface(new RoofSurface(), roofNames, p));
            } else {
                building.addBoundary(processBoundarySurface(new WallSurface(), wallNames, p));
            }
            polygons.add(p);
        });
        Shell shell = new Shell();
        polygons.stream().map(p -> new SurfaceProperty("#" + p.getId())).forEach(shell.getSurfaceMembers()::add);
        Solid gmlSolid = new Solid(shell);
        gmlSolid.setId(idCreator.createId());
        gmlSolid.setSrsDimension(3);
        gmlSolid.setSrsName("EPSG:25832");
        building.setLod2Solid(new SolidProperty(gmlSolid));
    }

    private static AbstractSpaceBoundaryProperty processBoundarySurface(AbstractThematicSurface thematicSurface,
            List<Code> names,
            Polygon... polygons) {
        thematicSurface.setId(idCreator.createId());
        thematicSurface.setNames(names);
        thematicSurface.setLod2MultiSurface(new MultiSurfaceProperty(factory.createMultiSurface(polygons)));
        return new AbstractSpaceBoundaryProperty(thematicSurface);
    }
}
