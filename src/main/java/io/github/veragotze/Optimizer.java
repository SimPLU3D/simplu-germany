package io.github.veragotze;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.noding.SegmentStringUtil;
import org.locationtech.jts.noding.snap.SnappingNoder;
import org.locationtech.jts.operation.linemerge.LineMerger;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IEnvelope;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.geoxygene.util.conversion.JtsGeOxygene;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.CadastralParcel;
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
import fr.ign.simulatedannealing.ParallelTempering;
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
 **/
public class Optimizer extends BasicCuboidOptimizer<Cuboid> {

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
            Environnement env,
            ConfigurationModificationPredicate<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> pred,
            List<Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> lSupplementaryVisitors) {

        // Sampler creation
        Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> samp = create_sampler(
                new MersenneTwister(42), p,
                bpu, pred, windows);

        GraphConfiguration<Cuboid> conf = null;
        try {
            conf = create_configuration(p, JtsGeOxygene.makeJtsGeom(bpu.getGeom()), bpu);
            // TODO Add a unary energy to penalise the configurations that are askew with regards to borders?
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Temperature initialization
        Schedule<SimpleTemperature> sch = create_schedule(p);

        // The end test condition
        end = create_end_test(p);

        // The visitors initialisation
        PrepareVisitors<Cuboid> pv = new PrepareVisitors<>(env, lSupplementaryVisitors);
        CompositeVisitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> mVisitor = pv.prepare(p, 0);

        countV = pv.getCountV();

        // Optimization process
        SimulatedAnnealing.optimize(Random.random(), conf, samp, sch, end, mVisitor);
        // TODO Test with ParallelTemporing?
        return conf;
    }

    record ClassBuilderPair<U extends Cuboid>(Class<U> theClass, ObjectBuilder<Cuboid> builder) {
    }

    @SuppressWarnings("unchecked")
    static <U extends Cuboid> ClassBuilderPair<U> createClass(String fullName,
            MultiObjectUniformBirth<Cuboid> objectSampler, RandomGenerator rng, double area, double minX, double minY,
            double minLength, double minWidth, double minHeight, double maxX, double maxY, double maxLength, double maxWidth, double maxHeight)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
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
        Class<U> clazz = (Class<U>) subClass.toClass(Optimizer.class);
        Constructor<U> constr = (Constructor<U>) clazz.getConstructors()[0];
        U min = constr.newInstance(minX, minY, minLength, minWidth, minHeight, 0);
        U max = constr.newInstance(maxX, maxY, maxLength, maxWidth, maxHeight, Math.PI);
        ObjectBuilder<Cuboid> builder = new ObjectBuilder<Cuboid>() {
            @Override
            public Cuboid build(double[] c) {
                try {
                    return (Cuboid) constr.newInstance(c[0], c[1], c[2], c[3], c[4], c[5]);
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
        return new ClassBuilderPair<U>(clazz, builder);
    }

    @SuppressWarnings("unchecked")
    static <U extends Cuboid> ClassBuilderPair<U> createClassOneSidedAttached(String fullName,
            MultiObjectUniformBirth<Cuboid> objectSampler, RandomGenerator rng, double area, LineString referenceLine,
            double minX, double minY, double minWidth, double minHeight, double maxX, double maxY, double maxWidth,
            double maxHeight)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        ClassPool pool = ClassPool.getDefault();
        // Create the class.
        CtClass subClass = pool.makeClass(fullName);
        final CtClass superClass = pool.get(OneSidedAttachedCuboid.class.getName());
        subClass.setSuperclass(superClass);
        subClass.setModifiers(Modifier.PUBLIC);
        // Add a constructor which will call super( ... );
        CtClass[] params = new CtClass[] { CtClass.doubleType, CtClass.doubleType, CtClass.doubleType,
                CtClass.doubleType, pool.get(LineString.class.getName()) };
        final CtConstructor ctor = CtNewConstructor.make(params, null, CtNewConstructor.PASS_PARAMS, null, null,
                subClass);
        subClass.addConstructor(ctor);
        Class<U> clazz = (Class<U>) subClass.toClass(Optimizer.class);
        Constructor<U> constr = (Constructor<U>) clazz.getConstructors()[0];
        U min = constr.newInstance(minX, minY, minWidth, minHeight, referenceLine);
        U max = constr.newInstance(maxX, maxY, maxWidth, maxHeight, referenceLine);
        ObjectBuilder<Cuboid> builder = new ObjectBuilder<Cuboid>() {
            @Override
            public Cuboid build(double[] coordinates) {
                try {
                    return (Cuboid) constr.newInstance(coordinates[0], coordinates[1], coordinates[2], coordinates[3],
                            referenceLine);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public int size() {
                return 4;
            }

            @Override
            public void setCoordinates(Cuboid t, double[] coordinates) {
                OneSidedAttachedCuboid o = (OneSidedAttachedCuboid) t;
                coordinates[0] = o.px;
                coordinates[1] = o.py;
                coordinates[2] = t.width;
                coordinates[3] = t.height;
            }
        };
        objectSampler.add(rng, clazz, area, min, max, builder);
        return new ClassBuilderPair<U>(clazz, builder);
    }

    @SuppressWarnings("unchecked")
    static <U extends Cuboid> ClassBuilderPair<U> createClassTwoSidedAttached(String fullName,
            MultiObjectUniformBirth<Cuboid> objectSampler, RandomGenerator rng, double area, LineString referenceLine1,
            LineString referenceLine2, double minWidth, double minHeight, double maxWidth, double maxHeight)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        ClassPool pool = ClassPool.getDefault();
        // Create the class.
        CtClass subClass = pool.makeClass(fullName);
        final CtClass superClass = pool.get(TwoSidedAttachedCuboid.class.getName());
        subClass.setSuperclass(superClass);
        subClass.setModifiers(Modifier.PUBLIC);
        // Add a constructor which will call super( ... );
        CtClass[] params = new CtClass[] { CtClass.doubleType, CtClass.doubleType, CtClass.doubleType,
                pool.get(LineString.class.getName()), pool.get(LineString.class.getName()) };
        final CtConstructor ctor = CtNewConstructor.make(params, null, CtNewConstructor.PASS_PARAMS, null, null,
                subClass);
        subClass.addConstructor(ctor);
        Class<U> clazz = (Class<U>) subClass.toClass(Optimizer.class);
        Constructor<U> constr = (Constructor<U>) clazz.getConstructors()[0];
        U min = constr.newInstance(0, minWidth, minHeight, referenceLine1, referenceLine2);
        U max = constr.newInstance(1, maxWidth, maxHeight, referenceLine1, referenceLine2);
        ObjectBuilder<Cuboid> builder = new ObjectBuilder<Cuboid>() {
            @Override
            public Cuboid build(double[] coordinates) {
                try {
                    return (Cuboid) constr.newInstance(coordinates[0], coordinates[1], coordinates[2], referenceLine1,
                            referenceLine2);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public int size() {
                return 3;
            }

            @Override
            public void setCoordinates(Cuboid t, double[] coordinates) {
                TwoSidedAttachedCuboid c = (TwoSidedAttachedCuboid) t;
                coordinates[0] = c.distance;
                coordinates[1] = c.width;
                coordinates[2] = c.height;
            }
        };
        objectSampler.add(rng, clazz, area, min, max, builder);
        return new ClassBuilderPair<U>(clazz, builder);
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
    @SuppressWarnings({ "unchecked" })
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
        Map<Class<? extends Cuboid>, Double> windowArea = new HashMap<>();
        int windowIndex = 0;
        for (Window<IGeometry> window : windows) {
            record ParcelWindowIntersection(CadastralParcel parcel, IPolygon polygon) {
            }
            List<ParcelWindowIntersection> intersections = bpU.getCadastralParcels().stream().flatMap(parcel -> {
                if (parcel.getGeom().intersects(window.geometry))
                    return Stream.of(new ParcelWindowIntersection(parcel,
                            (IPolygon) parcel.getGeom().intersection(window.geometry)));
                return Stream.empty();
            }).toList();
            for (ParcelWindowIntersection intersection : intersections) {
                // System.err
                //         .println("Create window " + windowIndex + " with " + window.minHeight + " - " + window.maxHeight
                //                 + " type " + window.buildingStyle);
                IEnvelope env = intersection.polygon.envelope();
                String name = "io.github.veragotze.B" + windowIndex;
                ClassBuilderPair<? extends Cuboid> classBuilderPair = null;
                double subWindowArea = intersection.polygon.area();
                try {
                    switch (window.buildingStyle) {
                        case "g":
                            Polygon parcelPolygon = (Polygon) JtsGeOxygene.makeJtsGeom(intersection.parcel.getGeom());
                            Polygon subWindowPolygon = (Polygon) JtsGeOxygene.makeJtsGeom(intersection.polygon);
                            SnappingNoder noder = new SnappingNoder(0.5);
                            List<NodedSegmentString> segs = SegmentStringUtil.extractNodedSegmentStrings(parcelPolygon);
                            segs.addAll(SegmentStringUtil.extractNodedSegmentStrings(subWindowPolygon));
                            noder.computeNodes(segs);
                            List<NodedSegmentString> res = (List<NodedSegmentString>) noder.getNodedSubstrings();
                            Geometry nodedPolygon = SegmentStringUtil.toGeometry(res.stream().filter(n->n.getData() == parcelPolygon).toList(), parcelPolygon.getFactory());
                            Geometry nodedSubWindow = SegmentStringUtil.toGeometry(res.stream().filter(n->n.getData() == subWindowPolygon).toList(), parcelPolygon.getFactory());
                            Geometry referenceLines = nodedPolygon.intersection(nodedSubWindow);
                            LineMerger merger = new LineMerger();
                            merger.add(referenceLines);
                            List<LineString> merged = (List<LineString>) merger.getMergedLineStrings();
                            if (merged.size() == 1) {
                                LineString line = (LineString) merged.get(0);
                                System.err.println(name + " " + intersection.parcel.getCode() + " " + subWindowArea + " " + window.minHeight + " " + window.maxHeight + " " + line);
                                System.err.println(intersection.polygon);
                                System.err.println(referenceLines.getFactory().createPoint(new Coordinate(env.minX(), env.minY())));
                                System.err.println(referenceLines.getFactory().createPoint(new Coordinate(env.maxX(), env.maxY())));
                                classBuilderPair = createClassOneSidedAttached(name, objectSampler, rng, subWindowArea, line, 
                                env.minX(), env.minY(), minwid, window.minHeight, 
                                env.maxX(), env.maxY(), maxwid, window.maxHeight);
                                break;
                            } else if (merged.size() == 2) {
                                LineString line1 = (LineString) merged.get(0);
                                LineString line2 = (LineString) merged.get(1);
                                System.err.println(name + " " + intersection.parcel.getCode() + " " + subWindowArea + " " + window.minHeight + " " + window.maxHeight + " " + line1 + " " + line2);
                                classBuilderPair = createClassTwoSidedAttached(name, objectSampler,
                                        rng, subWindowArea, line1, line2, minwid,
                                        window.minHeight, maxwid, window.maxHeight);
                                break;
                            }
                            System.err.println("REVERTING TO STANDARD!!! " + merged.size());
                        default:
                            System.err.println(name + " " + intersection.parcel.getCode() + " " + subWindowArea + " " + window.minHeight + " " + window.maxHeight);
                            classBuilderPair = createClass(name, objectSampler,
                                    rng, subWindowArea, env.minX(), env.minY(), minlen, minwid,
                                    window.minHeight, env.maxX(), env.maxY(), maxlen, maxwid, window.maxHeight);
                            break;
                    }
                    classes.add(classBuilderPair.theClass);
                    builders.put(classBuilderPair.theClass, classBuilderPair.builder);
                    windowArea.put(classBuilderPair.theClass, subWindowArea);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                windowIndex++;
            }
            ;
        }

        // Step 2 : Listing the modification kernel
        // List of kernel for modification during the process
        List<Kernel<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>>> kernels = new ArrayList<>();
        // A factory to create proper kernels
        KernelFactory<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> factory = new KernelFactory<>();
        // Adding the birth/death kernel
        double aMove = p.getDouble("amplitudeMove");
        double aRotate = p.getDouble("amplitudeRotate") * Math.PI / 180;
        double aMaxDim = p.getDouble("amplitudeMaxDim");
        double aHeight = p.getDouble("amplitudeHeight");
        double aDistance = p.getDouble("amplitudeDistance");
        for (Class<? extends Cuboid> c : classes) {
            // System.err.println("CLASS " + c);
            // System.err.println(builders.get(c));
            // System.out.println(objectSampler.getBirth(c));
            ObjectBuilder<Cuboid> builder = builders.get(c);
            double area = windowArea.get(c);
            double pbirth = p.getDouble("pbirth") * area;
            double pmove = 0.2 * area;
            kernels.add(
                    factory.make_uniform_typed_birth_death_kernel(rng, c, builder, objectSampler.getBirth(c),
                            pbirth, 1.0, c.getName()));
            if (TwoSidedAttachedCuboid.class.isAssignableFrom(c)) {
                // System.err.println("TwoSidedAttachedCuboid");
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new OneParameterChange(aDistance, 0, 4),
                        pmove, 1.0, 1, 1, "MoveDistance"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new OneParameterChange(aMaxDim, 1, 4),
                        pmove, 1.0, 1, 1, "WidthTwoSided"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new OneParameterChange(aMaxDim, 2, 4),
                        pmove, 1.0, 1, 1, "HeightTwoSided"));
            } else if (OneSidedAttachedCuboid.class.isAssignableFrom(c)) {
                // System.err.println("OneSidedAttachedCuboid");
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder, new MoveOneSided(aMove),
                        pmove, 1.0, 1, 1, "MoveOneSided"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new OneParameterChange(aMaxDim, 2, 5),
                        pmove, 1.0, 1, 1, "WidthOneSided"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new OneParameterChange(aMaxDim, 3, 5),
                        pmove, 1.0, 1, 1, "HeightOneSided"));
            } else {
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder, new MoveCuboid(aMove),
                        pmove, 1.0, 1, 1, "Move"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new RotateCuboid(aRotate), pmove, 1.0, 1, 1,
                        "Rotate"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new ChangeWidth(aMaxDim), pmove, 1.0, 1, 1,
                        "ChgWidth"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new ChangeLength(aMaxDim), pmove, 1.0, 1, 1,
                        "ChgLength"));
                kernels.add(factory.make_uniform_typed_modification_kernel(rng, c, builder,
                        new ChangeHeight(aHeight), pmove, 1.0, 1, 1,
                        "ChgHeight"));
            }
        }

        // Step 3 : Creation of the sampler for the brith/death of cuboid

        // This distribution create a biais to make the system tends around a certain
        // number of boxes
        PoissonDistribution distribution = new PoissonDistribution(rng, p.getDouble("poisson"));
        // Creation of the sampler with the modification in itself
        DirectSampler<Cuboid, GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> ds = new DirectRejectionSampler<>(
                distribution, objectSampler, pred);

        // Step 4 : Creation of the GreenSampler that will be used during the optimization process
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
