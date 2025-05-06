package io.github.veragotze;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.random.RandomGenerator;
import org.locationtech.jts.geom.GeometryFactory;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IEnvelope;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.Environnement;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.optimizer.cuboid.BasicCuboidOptimizer;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeHeight;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeLength;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.ChangeWidth;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.MoveCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.transformation.RotateCuboid;
import fr.ign.cogit.simplu3d.rjmcmc.generic.sampler.GreenSamplerBlockTemperature;
import fr.ign.cogit.simplu3d.rjmcmc.generic.visitor.CountVisitor;
import fr.ign.cogit.simplu3d.rjmcmc.generic.visitor.PrepareVisitors;
import fr.ign.cogit.simplu3d.util.SimpluParameters;
import fr.ign.mpp.DirectRejectionSampler;
import fr.ign.mpp.DirectSampler;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.mpp.kernel.KernelFactory;
import fr.ign.mpp.kernel.MultiObjectUniformBirth;
import fr.ign.mpp.kernel.ObjectBuilder;
import fr.ign.random.Random;
import fr.ign.rjmcmc.acceptance.MetropolisAcceptance;
import fr.ign.rjmcmc.configuration.ConfigurationModificationPredicate;
import fr.ign.rjmcmc.distribution.PoissonDistribution;
import fr.ign.rjmcmc.kernel.Kernel;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.SimulatedAnnealing;
import fr.ign.simulatedannealing.endtest.EndTest;
import fr.ign.simulatedannealing.schedule.Schedule;
import fr.ign.simulatedannealing.temperature.SimpleTemperature;
import fr.ign.simulatedannealing.visitor.CompositeVisitor;
import fr.ign.simulatedannealing.visitor.Visitor;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.NotFoundException;

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

	// public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu,
	// SimpluParameters p, Environnement env, int id,
	// ConfigurationModificationPredicate<GraphConfiguration<Cuboid>,
	// BirthDeathModification<Cuboid>> pred) {
	// return this.process(bpu, p, env, id, pred,
	// new ArrayList<Visitor<GraphConfiguration<Cuboid>,
	// BirthDeathModification<Cuboid>>>());
	// }

	// public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu,
	// SimpluParameters p, Environnement env, int id,
	// ConfigurationModificationPredicate<GraphConfiguration<Cuboid>,
	// BirthDeathModification<Cuboid>> pred,
	// GraphConfiguration<Cuboid> conf) {
	// // Géométrie de l'unité foncière sur laquelle porte la génération
	// IGeometry geom = bpu.generateGeom().buffer(1);
	// return this.process(bpu,geom, p, env, id, pred,
	// new ArrayList<Visitor<GraphConfiguration<Cuboid>,
	// BirthDeathModification<Cuboid>>>(), conf);
	// }

	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, List<Window<IGeometry>> windows,
			SimpluParameters p,
			Environnement env, int id,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred) {
		return this.process(bpu, windows, p, env, id, pred,
				new ArrayList<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>>());
	}

	// public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu,
	// SimpluParameters p, Environnement env, int id,
	// ConfigurationModificationPredicate<GraphConfiguration<Cuboid>,
	// BirthDeathModification<Cuboid>> pred,
	// List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>>
	// lSupplementaryVisitors) {
	// // Géométrie de l'unité foncière sur laquelle porte la génération
	// IGeometry geom = bpu.generateGeom().buffer(1);
	// return this.process(bpu, geom, p, env, id, pred, lSupplementaryVisitors);

	// }

	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, List<Window<IGeometry>> windows,
			SimpluParameters p,
			Environnement env, int id,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
			List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors) {
		return this.process(bpu, windows, p, env, id, pred, lSupplementaryVisitors, null);
	}

	/**
	 * Process the generation of the optimization
	 * 
	 * @param bpu                    Basic property unit
	 * @param geom                   The geometry in which the centroid of the
	 *                               cuboids will be generated
	 * @param p                      the parameters
	 * @param env                    the environement
	 * @param id                     the id of the experiments
	 * @param pred                   the rules to check
	 * @param lSupplementaryVisitors some extra visitors
	 * @param conf                   empty configuration (for custom energy
	 *                               functions)
	 * @return a set of cuboid as a graph
	 */
	public GraphConfiguration<Cuboid> process(BasicPropertyUnit bpu, List<Window<IGeometry>> windows,
			SimpluParameters p,
			Environnement env, int id,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
			List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors,
			GraphConfiguration<Cuboid> conf) {

		// Sampler creation
		Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> samp = create_sampler(Random.random(), p,
				bpu, pred, windows);

		if (conf == null) {
			try {
				conf = create_configuration(p, AdapterFactory.toGeometry(new GeometryFactory(), bpu.getGeom()), bpu);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Temperature initialization
		Schedule<SimpleTemperature> sch = create_schedule(p);

		// We may initialize the initial configuration with existing cuboids
		// int loadExistingConfig = p.getInteger("load_existing_config");
		// if (loadExistingConfig == 1) {
		// String configPath = p.get("config_shape_file").toString();
		// List<Cuboid> lCuboid = LoaderCuboid.loadFromShapeFile(configPath);
		// BirthDeathModification<Cuboid> m = conf.newModification();
		// for (Cuboid c : lCuboid) {
		// m.insertBirth(c);
		// }
		// conf.deltaEnergy(m);
		// // conf.apply(m);
		// m.apply(conf);
		// System.out.println("First update OK");
		// }

		// The end test condition
		end = create_end_test(p);

		// The visitors initialisation
		PrepareVisitors<Cuboid> pv = new PrepareVisitors<>(env, lSupplementaryVisitors);
		CompositeVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> mVisitor = pv.prepare(p, id);

		countV = pv.getCountV();

		// Optimization process
		SimulatedAnnealing.optimize(Random.random(), conf, samp, sch, end, mVisitor);
		return conf;
	}

	@SuppressWarnings("unchecked")
	static Class<? extends Cuboid> createClass(String fullName) throws NotFoundException, CannotCompileException {
		// System.err.println("Create class " +fullName);
		ClassPool pool = ClassPool.getDefault();
		// Create the class.
		CtClass subClass = pool.makeClass(fullName);
		final CtClass superClass = pool.get(Cuboid.class.getName());
		subClass.setSuperclass(superClass);
		subClass.setModifiers(Modifier.PUBLIC);
		// Add a constructor which will call super( ... );
		CtClass[] params = new CtClass[] { CtClass.doubleType, CtClass.doubleType, CtClass.doubleType,
				CtClass.doubleType, CtClass.doubleType, CtClass.doubleType };
		final CtConstructor ctor = CtNewConstructor.make(params, null, CtNewConstructor.PASS_PARAMS, null, null,
				subClass);
		subClass.addConstructor(ctor);
		return (Class<? extends Cuboid>) subClass.toClass(Optimizer.class);
	}

	private static <U extends Cuboid> ObjectBuilder<Cuboid> add(RandomGenerator rng, Class<U> clazz, double area,
			MultiObjectUniformBirth<Cuboid> objectSampler,
			double a, double b, double c, double d, double e, double f, double g, double h, double i, double j)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<U> constr = (Constructor<U>) clazz.getConstructors()[0];
		U min = constr.newInstance(a, b, c, d, e, 0);
		U max = constr.newInstance(f, g, h, i, j, Math.PI);
		ObjectBuilder<Cuboid> builder = new ObjectBuilder<Cuboid>() {
			@Override
			public Cuboid build(double[] coordinates) {
				try {
					return (Cuboid) constr.newInstance(coordinates[0], coordinates[1], coordinates[2], coordinates[3],
							coordinates[4], coordinates[5]);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}

			@Override
			public int size() {
				return 6;
			}

			@Override
			public void setCoordinates(Cuboid t, double[] coordinates) {
				coordinates[0] = t.centerx;
				coordinates[1] = t.centery;
				coordinates[2] = t.length;
				coordinates[3] = t.width;
				coordinates[4] = t.height;
				coordinates[5] = t.orientation;
			}
		};

		objectSampler.add(rng, clazz, area, min, max, builder);
		return builder;
	}

	/**
	 * Creation of the sampler
	 * 
	 * @param rng  a random generator
	 * @param p    the parameters loaded from the json file
	 * @param bpU  the basic property unit on which the simulation will be proceeded
	 * @param pred a predicate that will check the respect of the rules
	 * @param geom a geometry that will contains all the cuboid
	 * @return a sampler that will be used during the simulation process
	 */
	public Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> create_sampler(RandomGenerator rng,
			SimpluParameters p, BasicPropertyUnit bpU,
			ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
			List<Window<IGeometry>> windows) {

		// Getting minimal and maximal dimension from the parameter file
		double minlen = Double.isNaN(this.minLengthBox) ? p.getDouble("minlen") : this.minLengthBox;
		double maxlen = Double.isNaN(this.maxLengthBox) ? p.getDouble("maxlen") : this.maxLengthBox;

		double minwid = Double.isNaN(this.minWidthBox) ? p.getDouble("minwid") : this.minWidthBox;
		double maxwid = Double.isNaN(this.maxWidthBox) ? p.getDouble("maxwid") : this.maxWidthBox;

		// Step 1 : Creation of the object that will control the birth and death of
		// cuboid
		MultiObjectUniformBirth<Cuboid> objectSampler = new MultiObjectUniformBirth<>();
		List<Class<? extends Cuboid>> classes = new ArrayList<>();
		Map<Class<? extends Cuboid>, ObjectBuilder<Cuboid>> builders = new HashMap<>();
		int windowIndex = 0;
		for (Window<IGeometry> window : windows) {
			IEnvelope env = window.geometry.envelope();
			Class<? extends Cuboid> c;
			try {
				c = createClass("io.github.veragotze.Cuboid_" + windowIndex);
				System.err.println("Create window " +windowIndex + " with " + window.minHeight + " - " + window.maxHeight);
				ObjectBuilder<Cuboid> builder;
				try {
					builder = Optimizer.add(rng, c, window.geometry.area(), objectSampler, env.minX(),
							env.minY(), minlen, minwid, window.minHeight, env.maxX(), env.maxY(), maxlen, maxwid,
							window.maxHeight);
					classes.add(c);
					builders.put(c, builder);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Constructor<? extends Cuboid> constr = (Constructor<? extends Cuboid>)
			// c.getConstructors()[0];
			// Cuboid min = constr.newInstance(env.minX(), env.minY(), minlen, minwid,
			// window.minHeight, 0);
			// Cuboid max = constr.newInstance(env.maxX(), env.maxY(), maxlen, maxwid,
			// window.maxHeight, Math.PI);
			// objectSampler.add(rng, c, window.geometry.area(), min, max, null);
			windowIndex++;
		}

		// double minheight = p.getDouble("minheight");
		// double maxheight = p.getDouble("maxheight");

		// Builder class of the object
		// ObjectBuilder<Cuboid> builder = new CuboidBuilder();

		// The geometry in which the sampler will be instanciated
		// if (geom != null) {
		// samplingSurface = geom;
		// }

		// if (samplingSurface == null) {
		// samplingSurface = bpU.getGeom();
		// }
		// IEnvelope env = samplingSurface.getEnvelope();

		// Instanciation of the object dedicated for the creation of new cuboid during
		// the process
		// Passing the building, the class (TransformToSurface) that will make
		// the transformation between random numbers and coordinates inside the
		// samplingSurface
		// UniformBirth<Cuboid> birth = new UniformBirth<Cuboid>(rng,
		// new Cuboid(env.minX(), env.minY(), minlen, minwid, minheight, 0),
		// new Cuboid(env.maxX(), env.maxY(), maxlen, maxwid, maxheight, Math.PI),
		// builder,
		// TransformToSurface.class, samplingSurface);

		// Step 2 : Listing the modification kernel

		// List of kernel for modification during the process
		List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> kernels = new ArrayList<>();

		// A factory to create proper kernels
		KernelFactory<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> factory = new KernelFactory<>();
		// Adding the birth/death kernel
		double amplitudeMove = p.getDouble("amplitudeMove");
		double amplitudeRotate = p.getDouble("amplitudeRotate") * Math.PI / 180;
		double amplitudeMaxDim = p.getDouble("amplitudeMaxDim");
		double amplitudeHeight = p.getDouble("amplitudeHeight");
		for (Class<? extends Cuboid> c : classes) {
			System.err.println("CLASS " + c);
			System.err.println(builders.get(c));
			System.out.println(objectSampler.getBirth(c));
			kernels.add(
					factory.make_uniform_typed_birth_death_kernel(rng, c, builders.get(c), objectSampler.getBirth(c),
							p.getDouble("pbirth"), 1.0, c.getName()));
			kernels.add(
					factory.make_uniform_typed_modification_kernel(rng, c, builders.get(c), new MoveCuboid(amplitudeMove), 0.2, 1.0, 1, 1, "Move"));
			kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builders.get(c), new RotateCuboid(amplitudeRotate), 0.2, 1.0, 1, 1,
					"Rotate"));
			kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builders.get(c), new ChangeWidth(amplitudeMaxDim), 0.2, 1.0, 1, 1,
					"ChgWidth"));
			kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builders.get(c), new ChangeLength(amplitudeMaxDim), 0.2, 1.0, 1, 1,
					"ChgLength"));
			kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builders.get(c), new ChangeHeight(amplitudeHeight), 0.2, 1.0, 1, 1,
					"ChgHeight"));

		}
		// kernels.add(
		// factory.make_uniform_birth_death_kernel(rng, builder, birth,
		// p.getDouble("pbirth"), 1.0, "BirthDeath"));
		// Adding the other modification kernel
		// kernels.add(factory.make_uniform_modification_kernel(rng, builder, new
		// MoveCuboid(amplitudeMove), 0.2, "Move"));
		// kernels.add(factory.make_uniform_modification_kernel(rng, builder, new
		// RotateCuboid(amplitudeRotate), 0.2,
		// "Rotate"));
		// kernels.add(factory.make_uniform_modification_kernel(rng, builder, new
		// ChangeWidth(amplitudeMaxDim), 0.2,
		// "ChgWidth"));
		// kernels.add(factory.make_uniform_modification_kernel(rng, builder, new
		// ChangeLength(amplitudeMaxDim), 0.2,
		// "ChgLength"));
		// kernels.add(factory.make_uniform_modification_kernel(rng, builder, new
		// ChangeHeight(amplitudeHeight), 0.2,
		// "ChgHeight"));

		// Step 3 : Creation of the sampler for the brith/death of cuboid

		// This distribution create a biais to make the system tends around a certain
		// number of boxes
		PoissonDistribution distribution = new PoissonDistribution(rng, p.getDouble("poisson"));
		// Creation of the sampler with the modification in itself
		DirectSampler<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> ds = new DirectRejectionSampler<>(
				distribution, objectSampler, pred);

		// Step 4 : Creation of the GreenSampler that will be used during the
		// optimization process
		// It notably control the acception ratio and that the created objects and that
		// the proposed configurations area generated
		// According to the uniformbirth
		Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> s = new GreenSamplerBlockTemperature<>(rng,
				ds, new MetropolisAcceptance<SimpleTemperature>(), kernels);
		return s;
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
