package io.github.veragotze;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.List;

import org.checkerframework.checker.units.qual.C;
import org.geolatte.geom.M;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.CadastralParcel;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.AbstractSimpleBuilding;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.Cuboid;
import fr.ign.cogit.simplu3d.util.merge.SDPCalc;
import fr.ign.mpp.configuration.BirthDeathModification;
import fr.ign.mpp.configuration.GraphConfiguration;
import fr.ign.mpp.configuration.ListConfiguration;
import fr.ign.rjmcmc.configuration.Configuration;
import fr.ign.rjmcmc.configuration.Modification;
import fr.ign.rjmcmc.sampler.Sampler;
import fr.ign.simulatedannealing.temperature.Temperature;
import fr.ign.simulatedannealing.visitor.Visitor;

public class SimpleVisitor implements Visitor<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> {
    private PrintStream stream;
    private int dump;
    private int iter;
    private long clock_begin;
    Formatter formatter;
    String formatString = "| %1$-12s ";
    String formatInt = "| %1$-12d ";
    String formatStringSmall = "| %1$-5s ";
    List<CadastralParcel> parcels;

    public SimpleVisitor(PrintStream os, List<CadastralParcel> parcels) {
        this.stream = os;
        this.parcels = parcels;
    }

    @Override
    public void init(int dump, int save) {
        this.iter = 0;
        this.dump = dump;
    }

    @Override
    public void begin(GraphConfiguration<Cuboid> config, Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> sampler, Temperature t) {
        this.stream.format("Starting at %1$tH:%1$tM:%1$tS%n", Calendar.getInstance());
        this.formatter = new Formatter(this.stream);
        this.formatter.format(this.formatString, "Iteration");
        if (config instanceof ListConfiguration) {
            this.formatter.format(this.formatString, "Objects");
        }
        parcels.stream().forEach(p -> {
            String id = p.getCode();
            this.formatter.format(this.formatStringSmall, id.substring(id.length() - 3));
        });
        stream.println();
        stream.flush();
        clock_begin = System.currentTimeMillis();
    }

    @Override
    public void visit(GraphConfiguration<Cuboid> config, Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> sampler, Temperature t) {
        if (sampler == null)
            return;
        ++iter;
        if ((dump > 0) && (iter % dump == 0)) {
            this.formatter.format(this.formatInt, Integer.valueOf(iter));
            if (config instanceof ListConfiguration) {
                ListConfiguration<?, ?, ?> c = (ListConfiguration<?, ?, ?>) config;
                this.formatter.format(this.formatInt, Integer.valueOf(c.size()));
            }
            List<Cuboid> lCuboid = new ArrayList<>(config.getGraph().vertexSet().stream().map(v->v.getValue()).toList());
            parcels.stream().forEach(p -> {
                try {
                    Geometry parcelGeometry = AdapterFactory.toGeometry(new GeometryFactory(), p.getGeom());
                    List<AbstractSimpleBuilding> buildings = new ArrayList<>();
                    for (Cuboid cuboid : lCuboid) {
                        if (parcelGeometry.contains(cuboid.toGeometry().getCentroid())) {
                            buildings.add((AbstractSimpleBuilding) cuboid);
                        }
                    }
                    SDPCalc computation = new SDPCalc(2.5);
                    double fsi = computation.process(buildings) / parcelGeometry.getArea();
                    this.formatter.format(this.formatStringSmall, String.valueOf(fsi).substring(0, 5));
                } catch (Exception e) {

                }
            });
            stream.println();
            stream.flush();
        }
    }

    @Override
    public void end(GraphConfiguration<Cuboid> config, Sampler<GraphConfiguration<Cuboid>, BirthDeathModification<Cuboid>> sampler, Temperature t) {
        this.visit(config, sampler, t);
        long clock_end = System.currentTimeMillis();
        this.stream.format("Finished at %1$tH:%1$tM:%1$tS%n", Calendar.getInstance());
        this.stream.println("Total elapsed time (s) :  " + (clock_end - clock_begin) / 1000);
        this.stream.flush();
    }

}
