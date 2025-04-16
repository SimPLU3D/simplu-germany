package io.github.veragotze;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

	private double maximalFloorAreaRatio = 0.0;

	// BasicPropertyUnit utilisée
	private BasicPropertyUnit currentBPU;

	private Geometry window;

	/**
	 * 
	 * @param currentBPU The current basic property unit on which the built
	 *                   configuration will be generated (generally a parcel)
	 * @param maximalFloorAreaRatio maximal floor area ratio (FloorArea/ParcelArea)
	 * @throws Exception an exception
	 */
	public Predicate(BasicPropertyUnit currentBPU, double maximalFloorAreaRatio, IGeometry window) throws Exception {
		// On appelle l'autre connstructeur qui renseigne un certain nombre de
		// géométries
		this(currentBPU);
		this.maximalFloorAreaRatio = maximalFloorAreaRatio;
		this.window = AdapterFactory.toGeometry(new GeometryFactory(), window);
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
		for (O cuboid : lO) {
			// FIXME We might have multiple windows for watch for it
			if (!window.contains(cuboid.toGeometry())) {
				return false;
			}
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

		// // On calcule la surface couverte par l'ensemble des cuboid
		// int nbElem = lCuboid.size();

		// Geometry geom = lCuboid.get(0).toGeometry();

		// for (int i = 1; i < nbElem; i++) {

		// 	geom = geom.union(lCuboid.get(i).toGeometry());

		// }

		List<AbstractSimpleBuilding> lBatIni = new ArrayList<>();
		for (ISimPLU3DPrimitive s : lCuboid) {
			lBatIni.add((AbstractSimpleBuilding) s);
		}

		SDPCalc computation = new SDPCalc(2.5);
		double airePAr = 0;
		for (CadastralParcel cP : currentBPU.getCadastralParcels()) {
			airePAr = airePAr + cP.getArea();
		}
		return computation.process(lBatIni) / airePAr <= maximalFloorAreaRatio;
		
		/*
		 * List<List<AbstractSimpleBuilding>> groupes =
		 * CuboidGroupCreation.createGroup(lBatIni, 0.5); if (groupes.size() > 1) {
		 * return false; }
		 */

		// On récupère la superficie de la basic propertyUnit
		// double airePAr = 0;
		// for (CadastralParcel cP : currentBPU.getCadastralParcels()) {
		// 	airePAr = airePAr + cP.getArea();
		// }

		// return ((geom.getArea() / airePAr) <= maximalCES);
	}
}