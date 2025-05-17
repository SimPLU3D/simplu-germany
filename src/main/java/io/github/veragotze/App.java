package io.github.veragotze;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.algo.JtsAlgorithms;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.simplu3d.io.feature.AttribNames;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.CadastralParcel;
import fr.ign.cogit.simplu3d.model.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.util.SimpluParameters;
import fr.ign.cogit.simplu3d.util.SimpluParametersJSON;
import fr.ign.cogit.simplu3d.util.convert.ExportAsFeatureCollection;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.simulatedannealing.visitor.Visitor;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws Exception {
        // Step 0 ; Defining an output existing folder
        String outputFolder = "./output_roof/";
        double roofAngleInDegrees = 40.0;
        double roofAngle = roofAngleInDegrees * Math.PI / 180.0;
        // Step 1 : Creating the geographic environnement using the repository that
        // contains the data
        // Load default environment (data are in resource directory)
        AttribNames.setATT_CODE_PARC("OID_1");
        File folder = new File(App.class.getClassLoader().getResource("munich_full/").getPath());
        Environnement env = Loader.load(folder, null);
        IFeatureCollection<IFeature> fensters = Loader.readShapefile(new File(folder, "fenster.shp"));
        Map<String, Double> plan2FSI = new HashMap<>();
        Loader.readShapefile(new File(folder, "plan.shp")).stream().forEach(f -> plan2FSI
                .put(f.getAttribute("NR_PLAN").toString()+f.getAttribute("TEILGEBIET").toString(), Double.parseDouble(f.getAttribute("FSI").toString())));
        plan2FSI.forEach((k,v)->System.out.println(k + " with fsi = " + v));
        // Select a parcel on which generation is proceeded
        BasicPropertyUnit propertyUnit = new BasicPropertyUnit();
        propertyUnit.setId(0);
        Map<CadastralParcel, Double> parcel2FSI = new HashMap<>();
        // quick trick
        for (BasicPropertyUnit bPU : env.getBpU()) {
            for (CadastralParcel p : bPU.getCadastralParcels()) {
                propertyUnit.getCadastralParcels().add(p);
                // FIXME here we assume that all fensters have the same zoning plan: we might
                // want to sort them by descending intersection area with the parcel?
                Set<Double> fsis = fensters.select(p.getGeom()).stream()
                        .map(f -> plan2FSI.get(f.getAttribute("NR_PLAN").toString()+f.getAttribute("TEILGEBIET").toString())).collect(Collectors.toSet());
                parcel2FSI.put(p, fsis.iterator().next());
                System.out.println("PARCEL " + p.getCode().substring(p.getCode().length()-3) + " with FSI = " + fsis);
            }
        }
        propertyUnit.setGeom(JtsAlgorithms.union(propertyUnit.getCadastralParcels().stream().map(p->p.getGeom()).toList()));
        List<Window<Geometry>> windows = new ArrayList<>();
        for (IFeature fenster : fensters) {
            Geometry fensterGeom = AdapterFactory.toGeometry(new GeometryFactory(), fenster.getGeom());
            int fensterFloors = Integer.parseInt(fenster.getAttribute("GESCH").toString());
            double windowMinHeight = fensterFloors * 2.5;
            double windowMaxHeight = fensterFloors * 3.5;
            windows.add(new Window<>(fensterGeom, windowMinHeight, windowMaxHeight, fenster.getAttribute("DACHFORM").toString(), fenster.getAttribute("BAUWEISE").toString()));
        }
        String fileName = "building_parameters.json";
        String folderName = App.class.getClassLoader().getResource("scenario/").getPath();
        SimpluParameters p = new SimpluParametersJSON(new File(folderName + fileName));
        // Instanciation of a predicate class
        Predicate<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred = new Predicate<>(
                propertyUnit, parcel2FSI, windows, roofAngle);
        // Step 3 : Defining the regulation that will be applied during the simulation
        // Instantiation of the sampler
        Optimizer oCB = new Optimizer();
        // Loading the parameters for the building shape generation
        // Run of the optimisation on a parcel with the predicate
        System.out.println("START");
        try {
            List<Window<IGeometry>> windows2 = windows.stream().map(w -> {
                try {
                    return new Window<IGeometry>(AdapterFactory.toGM_Object(w.geometry), w.minHeight, w.maxHeight, w.roofType, w.buildingStyle);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());
            // add visitors
            List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> visitors = Arrays.asList(new SimpleVisitor(System.out,propertyUnit.getCadastralParcels(),pred));
            GraphConfiguration<Cuboid> cc = oCB.process(propertyUnit, windows2, p, env, pred, visitors);
            // 4 - Writting the output
            ExportAsFeatureCollection exporter = new ExportAsFeatureCollection(cc);
            IFeatureCollection<? extends IFeature> exported = exporter.getFeatureCollection();
            for (CadastralParcel parcel : propertyUnit.getCadastralParcels()) {
                Collection<? extends IFeature> buildings = exported.select(parcel.getGeom().buffer(-0.5));
                buildings.forEach(b -> {
                    AttributeManager.addAttribute(b, "parcelId", parcel.getCode(), "String");
                    AttributeManager.addAttribute(b, "windowId",
                            fensters.stream().filter(f -> f.getGeom().contains(b.getGeom().centroid().toGM_Point())).findAny().get()
                                    .getAttribute("OBJECTID").toString(),
                            "String");
                });
            }
            ShapefileWriter.write(exported, outputFolder + "out.shp", CRS.decode("EPSG:25832"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
