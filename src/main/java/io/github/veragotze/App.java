package io.github.veragotze;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
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

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws Exception {
        // Step 0 ; Defining an output existing folder
        String outputFolder = "/tmp/";
        // Step 1 : Creating the geographic environnement using the repository that
        // contains the data
        // Load default environment (data are in resource directory)
        AttribNames.setATT_CODE_PARC("OID_1");
        File folder = new File(App.class.getClassLoader().getResource("munich_overlap/").getPath());
        Environnement env = Loader.load(folder, null);
        // Select a parcel on which generation is proceeded
        BasicPropertyUnit propertyUnit = new BasicPropertyUnit();
        propertyUnit.setId(0);
        // quick trick
        for (BasicPropertyUnit bPU : env.getBpU()) {
            // System.out.println("BasicPropertyUnit : " + bPU.getId() + " "+
            // bPU.getGeom());
            for (CadastralParcel p : bPU.getCadastralParcels()) {
                // System.out.println(p.getCode());
                propertyUnit.getCadastralParcels().add(p);
            }
        }
        IFeatureCollection<IFeature> fensters = Loader.readShapefile(new File(folder, "fenster.shp"));
        List<Window<Geometry>> windows = new ArrayList<>();
        // List<Geometry> windows = new ArrayList<>();
        // List<Double> windowsMinHeight = new ArrayList<>();
        // List<Double> windowsMaxHeight = new ArrayList<>();
        for (IFeature fenster : fensters) {
            Geometry fensterGeom = AdapterFactory.toGeometry(new GeometryFactory(), fenster.getGeom());
            int fensterFloors = Integer.parseInt(fenster.getAttribute("GESCH").toString());
            double windowMinHeight = fensterFloors * 2.5;
            double windowMaxHeight = fensterFloors * 3.5;
            // windows.add(fensterGeom);
            // windowsMinHeight.add(windowMinHeight);
            // windowsMaxHeight.add(windowMaxHeight);
            windows.add(new Window<>(fensterGeom, windowMinHeight, windowMaxHeight));
        }
        // IFeature fenster = fensters.get(0);
        // IGeometry fensterGeom = fenster.getGeom();
        // int fensterFloors =
        // Integer.parseInt(fenster.getAttribute("GESCH").toString());
        // Geometry window = new UnaryUnionOp(windows).union();
        String fileName = "building_parameters.json";
        String folderName = App.class.getClassLoader().getResource("scenario/").getPath();
        SimpluParameters p = new SimpluParametersJSON(new File(folderName + fileName));

        // double minHeight =
        // windowsMinHeight.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
        // double maxHeight =
        // windowsMaxHeight.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
        // p.set("minheight", minHeight);
        // p.set("maxheight", maxHeight);

        // BasicPropertyUnit bPU = env.getBpU().get(0);
        // IGeometry window = fensterGeom;//bPU.getGeom().intersection(fensterGeom);
        // System.out.println("window="+window);
        // Maximal floor space ratio
        double maximalCOS = 0.7;
        // Instanciation of a predicate class
        Predicate<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred = new Predicate<>(
                propertyUnit, maximalCOS, windows);
        // Step 3 : Defining the regulation that will be applied during the simulation
        // Instantiation of the sampler
        // OptimisedBuildingsCuboidFinalDirectRejection oCB = new
        // OptimisedBuildingsCuboidFinalDirectRejection();
        Optimizer oCB = new Optimizer();
        // Loading the parameters for the building shape generation
        // Run of the optimisation on a parcel with the predicate
        System.out.println("START");
        try {
            List<Window<IGeometry>> windows2 = windows.stream().map(w -> {
                try {
                    return new Window<IGeometry>(AdapterFactory.toGM_Object(w.geometry), w.minHeight, w.maxHeight);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());
            GraphConfiguration<Cuboid> cc = oCB.process(propertyUnit, windows2, p, env, 1, pred);
            // 4 - Writting the output
            ExportAsFeatureCollection exporter = new ExportAsFeatureCollection(cc, propertyUnit.getId());
            ShapefileWriter.write(exporter.getFeatureCollection(), outputFolder + "out.shp", CRS.decode("EPSG:25832"));
            System.out.println("ALL DONE! " + (outputFolder + "out.shp"));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
