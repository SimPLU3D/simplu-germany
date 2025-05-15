package io.github.veragotze;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.CadastralParcel;
import fr.ign.cogit.simplu3d.model.ParcelBoundary;
import fr.ign.cogit.simplu3d.rjmcmc.cuboid.geometry.impl.AbstractSimpleBuilding;
import fr.ign.cogit.simplu3d.rjmcmc.generic.object.ISimPLU3DPrimitive;
import fr.ign.cogit.simplu3d.util.merge.SDPCalc;
import fr.ign.mpp.configuration.AbstractBirthDeathModification;
import fr.ign.mpp.configuration.AbstractGraphConfiguration;
import fr.ign.rjmcmc.configuration.ConfigurationModificationPredicate;

public class Predicate<O extends ISimPLU3DPrimitive, C extends AbstractGraphConfiguration<O, C, M>, M extends AbstractBirthDeathModification<O, C, M>>
		implements ConfigurationModificationPredicate<C, M> {

	private Map<CadastralParcel, Double> maximalFloorAreaRatios;

	// BasicPropertyUnit utilisée
	private BasicPropertyUnit currentBPU;

	private List<Window<Geometry>> windows;

	private double roofAngle;
	private double heightThreshold = 2.3; // federal German rule

	/**
	 * 
	 * @param currentBPU            The current basic property unit on which the
	 *                              built
	 *                              configuration will be generated (generally a
	 *                              parcel)
	 * @param maximalFloorAreaRatios maximal floor area ratio (FloorArea/ParcelArea)
	 * @throws Exception an exception
	 */
	public Predicate(BasicPropertyUnit currentBPU, Map<CadastralParcel, Double> maximalFloorAreaRatios, List<Window<Geometry>> windows, double roofAngle)
			throws Exception {
		// On appelle l'autre connstructeur qui renseigne un certain nombre de
		// géométries
		this(currentBPU);
		this.maximalFloorAreaRatios = maximalFloorAreaRatios;
		this.windows = windows;
		this.roofAngle = roofAngle;
	}

	Geometry surface = null;

	/**
	 * Ce constructeur initialise les géométries curveLimiteFondParcel,
	 * curveLimiteFrontParcel & curveLimiteLatParcel car elles seront utilisées pour
	 * exprimer certaines contraintes
	 * 
	 * @param bPU
	 * @throws Exception
	 */
	private Predicate(BasicPropertyUnit bPU) throws Exception {
		super();
		this.currentBPU = bPU;

		// On parcourt les parcelles du BasicPropertyUnit (un propriétaire peut
		// avoir plusieurs parcelles)
		for (CadastralParcel cP : bPU.getCadastralParcels()) {

			// On parcourt les limites séparaticves
			for (ParcelBoundary sCB : cP.getBoundaries()) {

				// En fonction du type on ajoute à telle ou telle géométrie
				IGeometry geom = sCB.getGeom();

				if (geom == null || geom.isEmpty() || geom.length() < 0.01) {
					continue;
				}
			}
		}
		GeometryFactory gf = new GeometryFactory();
		this.surface = AdapterFactory.toGeometry(gf, bPU.getGeom());
	}

	/**
	 * This method is applied each time the system try a new modification
	 * proposition
	 * 
	 * @param c contains the current configuration (basically a collection of
	 *          cuboids)
	 * @param m the modification that the sytem will try to apply. Normally, there
	 *          is 0 or 1 birth and 0 or 1 death
	 * @return Indicate if the rules are respected or not
	 */
	@Override
	public boolean check(C c, M m) {
		List<O> lO = m.getBirth();
		for (CadastralParcel currentParcel : currentBPU.getCadastralParcels()) {
			try {
				Geometry parcelGeometry = AdapterFactory.toGeometry(new GeometryFactory(), currentParcel.getGeom());
				for (O cuboid : lO) {
					if (parcelGeometry.contains(cuboid.toGeometry().getCentroid())) {
						if (!parcelGeometry.contains(cuboid.toGeometry())) {
							return false;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		for (O cuboid : lO) {
			// FIXME We might have multiple windows for watch for it
			if (!windows.stream().map(w -> w.geometry).anyMatch(w -> w.contains(cuboid.toGeometry()))) {
				return false;
			}
			// if (!windows.contains(cuboid.toGeometry())) {
			// return false;
			// }
		}
		// Check maximalFloorAreaRatio
		if (!respectMaximalFloorAreaRatio(c, m)) {
			return false;
		}
		// On a réussi tous les tests, on renvoie vrai
		return true;
	}

	/**
	 * maximalFloorAreaRatio.
	 * 
	 * @param c
	 * @param m
	 * @return
	 */
	private boolean respectMaximalFloorAreaRatio(C c, M m) {
		// On fait la liste de tous les objets après modification
		List<O> lCuboid = new ArrayList<>();

		// On ajoute tous les nouveaux objets
		lCuboid.addAll(m.getBirth());

		// On récupère la boîte (si elle existe) que l'on supprime lors de la
		// modification
		O cuboidDead = null;

		if (!m.getDeath().isEmpty()) {
			cuboidDead = m.getDeath().get(0);
		}

		// On parcourt les objets existants moins celui qu'on supprime
		Iterator<O> iTBat = c.iterator();
		while (iTBat.hasNext()) {

			O cuboidTemp = iTBat.next();

			// Si c'est une boîte qui est amenée à disparaître après
			// modification,
			// elle n'entre pas en jeu dans les vérifications
			if (cuboidTemp == cuboidDead) {
				continue;
			}

			lCuboid.add(cuboidTemp);

		}

		// C'est vide la règle est respectée
		if (lCuboid.isEmpty()) {
			return true;
		}

		for (CadastralParcel currentParcel : currentBPU.getCadastralParcels()) {
			try {
				Geometry parcelGeometry = AdapterFactory.toGeometry(new GeometryFactory(), currentParcel.getGeom());
				List<AbstractSimpleBuilding> buildings = new ArrayList<>();
				for (O cuboid : lCuboid) {
					if (parcelGeometry.contains(cuboid.toGeometry().getCentroid())) {
						buildings.add((AbstractSimpleBuilding) cuboid);
					}
				}
				if (!respectMaximalFloorAreaRatioForParcel(buildings, parcelGeometry.getArea(), maximalFloorAreaRatios.get(currentParcel))) {
					return false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	private boolean respectMaximalFloorAreaRatioForParcel(List<AbstractSimpleBuilding> buildings, double area, double maximalFloorAreaRatio) {
		SDPCalc computation = new SDPCalc(2.5);
		double totalRoofSurface = buildings.stream().mapToDouble(b->roofSurface(b, windows.stream().filter(w->w.geometry.contains(b.toGeometry())).findAny().get().roofType)).sum();
		return (computation.process(buildings) + totalRoofSurface) / area <= maximalFloorAreaRatio;
	}
	private double roofSurface(AbstractSimpleBuilding building, String roofType) {
		switch (roofType) {
			case "FD":
				return 0;
			case "SD":
				double roofHeight = Math.tan(roofAngle) * Math.min(building.width, building.length) / 2;
				return (roofHeight > heightThreshold) ? building.getArea() : 0.0;
			default:
				break;
		}
		return 0;
	}
}