package io.github.veragotze;

import java.io.File;

import org.geotools.referencing.CRS;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
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
        File folder = new File(App.class.getClassLoader().getResource("munich_group/").getPath());
        Environnement env = Loader.load(folder, null);
        // Select a parcel on which generation is proceeded
        BasicPropertyUnit propertyUnit = new BasicPropertyUnit();
        propertyUnit.setId(0);
        // quick trick
        for (BasicPropertyUnit bPU : env.getBpU()) {
            // System.out.println("BasicPropertyUnit : " + bPU.getId() + " "+ bPU.getGeom());
            for (CadastralParcel p: bPU.getCadastralParcels()) {
                // System.out.println(p.getCode());
                propertyUnit.getCadastralParcels().add(p);
            }
        }
        IFeatureCollection<IFeature> fensters = Loader.readShapefile(new File(folder, "fenster.shp"));
        IFeature fenster = fensters.get(0);
        IGeometry fensterGeom = fenster.getGeom();
        int fensterFloors = Integer.parseInt(fenster.getAttribute("GESCH").toString());

        String fileName = "building_parameters.json";
        String folderName = App.class.getClassLoader().getResource("scenario/").getPath();
        SimpluParameters p = new SimpluParametersJSON(new File(folderName + fileName));

        p.set("minheight", fensterFloors*2.5);
		p.set("maxheight", fensterFloors*3.5);

        // BasicPropertyUnit bPU = env.getBpU().get(0);
        IGeometry window = fensterGeom;//bPU.getGeom().intersection(fensterGeom);
        System.out.println("window="+window);
        // Maximal floor space ratio
        double maximalCOS = 0.7;
        // Instanciation of a predicate class
        Predicate<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred = new Predicate<>(propertyUnit, maximalCOS, window);
        // Step 3 : Defining the regulation that will be applied during the simulation
        // Instantiation of the sampler
        // OptimisedBuildingsCuboidFinalDirectRejection oCB = new OptimisedBuildingsCuboidFinalDirectRejection();
        Optimizer oCB = new Optimizer();
        // Loading the parameters for the building shape generation
        // Run of the optimisation on a parcel with the predicate
        System.out.println("START");
        GraphConfiguration<Cuboid> cc = oCB.process(propertyUnit, window, p, env, 1, pred);
        // 4 - Writting the output
        ExportAsFeatureCollection exporter = new ExportAsFeatureCollection(cc, propertyUnit.getId());
  		ShapefileWriter.write(exporter.getFeatureCollection(), outputFolder + "out.shp",   CRS.decode("EPSG:25832"));
        System.out.println("ALL DONE! " + (outputFolder + "out.shp"));
    }
}
