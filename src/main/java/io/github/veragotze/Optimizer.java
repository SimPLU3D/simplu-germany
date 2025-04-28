package io.github.veragotze;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.GeometryFactory;

import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.loader.LoaderCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.optimizer.cuboid.BasicCuboidOptimizer;
import fr.ign.cogit.simplu3d.rjmcmc.generic.visitor.CountVisitor;
import fr.ign.cogit.simplu3d.rjmcmc.generic.visitor.PrepareVisitors;
import fr.ign.cogit.simplu3d.util.SimpluParameters;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.random.Random;
import fr.ign.rjmcmc.configuration.ConfigurationModificationPredicate;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.SimulatedAnnealing;
import fr.ign.simulatedannealing.endtest.EndTest;
import fr.ign.simulatedannealing.schedule.Schedule;
import fr.ign.simulatedannealing.temperature.SimpleTemperature;
import fr.ign.simulatedannealing.visitor.CompositeVisitor;
import fr.ign.simulatedannealing.visitor.Visitor;

/**
 * 
 * This software is released under the licence CeCILL
 * 
 * see LICENSE.TXT
 * 
 * see http://www.cecill.info/
 * 
 * 
 * 
 * copyright IGN
 * 
 * @author Brasebin Mickaël
 * 
 * @version 1.0
 **/
public class Optimizer extends BasicCuboidOptimizer<Cuboid> {

// 	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, SimpluParameters p, Environnement env, int id,
// 			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred) {
// 		return this.process(bpu, p, env, id, pred,
// 				new ArrayList<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>>());
// 	}
 

//    public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, SimpluParameters p, Environnement env, int id,
//            ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred, 
//            GraphConfiguration<Cuboid> conf) {
//   // Géométrie de l'unité foncière sur laquelle porte la génération
//      IGeometry geom = bpu.generateGeom().buffer(1);
//        return this.process(bpu,geom, p, env, id, pred,
//                new ArrayList<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>>(), conf);
//    }

	
	
	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, IGeometry geom, SimpluParameters p,
			Environnement env, int id,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred) {
		return this.process(bpu, geom, p, env, id, pred,
				new ArrayList<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>>());
	}

	// public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, SimpluParameters p, Environnement env, int id,
	// 		ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
	// 		List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors) {
	// 	// Géométrie de l'unité foncière sur laquelle porte la génération
	// 	IGeometry geom = bpu.generateGeom().buffer(1);
	// 	return this.process(bpu, geom, p, env, id, pred, lSupplementaryVisitors);

	// }
	
	
	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, IGeometry geom, SimpluParameters p,
        Environnement env, int id,
        ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
        List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors){
	  return this.process(bpu,geom,p,env,id,pred,lSupplementaryVisitors,null);
	}
	
	/**
	 * Process the generation of the optimization
	 * @param bpu                    Basic property unit
	 * @param geom                   The geometry in which the centroid of the
	 *                               cuboids will be generated
	 * @param p                      the parameters
	 * @param env                    the environement
	 * @param id                     the id of the experiments
	 * @param pred                   the rules to check
	 * @param lSupplementaryVisitors some extra visitors
	 * @param conf                   empty configuration (for custom energy functions) 
	 * @return a set of cuboid as a graph
	 */
	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, IGeometry geom, SimpluParameters p,
			Environnement env, int id,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
			List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors,
			GraphConfiguration<Cuboid> conf) {

		// Sampler creation
		Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> samp = create_sampler(Random.random(), p,
				bpu, pred, geom);
		
		if(conf == null) {
		try {
			conf = create_configuration(p, AdapterFactory.toGeometry(new GeometryFactory(), bpu.getGeom()), bpu);
		} catch (Exception e) {
			e.printStackTrace();
		}
		}
		
		// Temperature initialization
		Schedule<SimpleTemperature> sch = create_schedule(p);
	
		//We may initialize the initial configuration with existing cuboids
		int loadExistingConfig = p.getInteger("load_existing_config");
		if (loadExistingConfig == 1) {
			String configPath = p.get("config_shape_file").toString();
			List<Cuboid> lCuboid = LoaderCuboid.loadFromShapeFile(configPath);
			BirthDeathModification<Cuboid> m = conf.newModification();
			for (Cuboid c : lCuboid) {
				m.insertBirth(c);
			}
			conf.deltaEnergy(m);
			// conf.apply(m);
			m.apply(conf);
			System.out.println("First update OK");
		}
		
		//The end test condition
		end = create_end_test(p);

		//The visitors initialisation
		PrepareVisitors<Cuboid> pv = new PrepareVisitors<>(env, lSupplementaryVisitors);
		CompositeVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> mVisitor = pv.prepare(p, id);
		
		
		countV = pv.getCountV();
		
		//Optimization process
		SimulatedAnnealing.optimize(Random.random(), conf, samp, sch, end, mVisitor);
		return conf;
	}

	private EndTest end;

	public EndTest getEndTest() {
		return end;
	}

	public int getCount() {
		return countV.getCount();
	}

	protected CountVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> countV = null;

}
