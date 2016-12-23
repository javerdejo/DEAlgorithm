package es.uma.lcc.uma;

import org.uma.jmetal.problem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.solution.impl.DefaultDoubleSolution;

import com.iblue.model.GeoStreetInterface;
import com.iblue.model.db.service.TileService;
import com.iblue.path.AlgorithmInterface;
import com.iblue.path.Dijkstra;
import com.iblue.path.GraphInterface;
import com.iblue.utils.Log;
import com.iblue.utils.Pair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

class TDDSProblem extends AbstractDoubleProblem {

	private static final long serialVersionUID = -6718380720330242163L;
	private int numberOfVariables;
	private double lower;
	private double upper;
	private TileService tileService;
	private List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests;

	TDDSProblem(int numberOfVariables, double lower, double upper,
			List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests) {
		this.numberOfVariables = numberOfVariables;
		this.setLower(lower);
		this.upper = upper;
		this.origDests = origDests;

		setNumberOfVariables(numberOfVariables); // número de variables
													// (dimensiones)
		setNumberOfObjectives(1); // Problema monoobjetivo
		setName("TDDSP"); // Nombre del problema

		List<Double> lowerLimit = new ArrayList<Double>();
		List<Double> upperLimit = new ArrayList<Double>();

		// Asigno los limites superior e inferior a cada variable
		for (int i = 0; i < getNumberOfVariables(); i++) {
			lowerLimit.add(lower);
			upperLimit.add(upper);
		}

		setLowerLimit(lowerLimit);
		setUpperLimit(upperLimit);

		tileService = new TileService();
	}

	public void evaluate(DoubleSolution solution) {
		// Almacena de forma local los valores para cada una de las variables
		// del problema "individuo"
		double[] x = new double[getNumberOfVariables()];

		// Almacena el valor de la función de fitness
		double fx = 0;

		// Guardo los valores de las variables del individuo en un arreglo local
		for (int i = 0; i < solution.getNumberOfVariables(); i++) {
			x[i] = solution.getVariableValue(i);
		}

		// generate tiles using x[0] and x[1]
		BigDecimal latRange = new BigDecimal(x[0]);
		BigDecimal lonRange = new BigDecimal(x[1]);
		long beginComputingTiles = System.currentTimeMillis();
		//tileService.computeMapWithNewTileDef(latRange, lonRange);
		Log.debug("Time for computing tiles " + (System.currentTimeMillis() - beginComputingTiles));

		// calculate fx as mean time (for resolving n routes)
		// TODO save times!
		routing();
		// Asigno el valor de la función de fitness
		solution.setObjective(0, fx);
	}

	// Esta función es utilizada por el algoritmo para crear la población
	// inicial
	public DoubleSolution createSolution() {
		return new DefaultDoubleSolution(this);
	}

	public double getLower() {
		return lower;
	}

	public void setLower(double lower) {
		this.lower = lower;
	}

	public int getNumberOfVariables() {
		return numberOfVariables;
	}

	public void setNumberOfVariables(int numberOfVariables) {
		this.numberOfVariables = numberOfVariables;
	}

	public double getUpper() {
		return upper;
	}

	public void setUpper(double upper) {
		this.upper = upper;
	}

	private double routing() {
		long aggTime = 0l;
		for (int i = 0; i < origDests.size(); i++) {
			Pair<GeoStreetInterface, GeoStreetInterface> p = origDests.get(i);
			System.out.println("Setting graph " + System.currentTimeMillis());
			GraphInterface graph = tileService.getTile(p.getFirst().getLatitude1(), p.getFirst().getLongitude1(),
					p.getSecond().getLatitude1(), p.getSecond().getLongitude1());
			AlgorithmInterface alg = new Dijkstra();
			alg.setGraph(graph);

			long begin = System.currentTimeMillis();
			// LinkedList<IntersectionInterface> path =
			alg.getPath(p.getFirst().getFromIntersection(), p.getSecond().getFromIntersection());
			long end = System.currentTimeMillis();
			Log.debug("Route " + i + " time " + (end - begin));
			aggTime += end - begin;
		}

		return (double) (aggTime / origDests.size());
	}
}
