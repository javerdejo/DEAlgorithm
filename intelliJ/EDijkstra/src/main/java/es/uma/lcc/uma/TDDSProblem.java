package es.uma.lcc.uma;

import org.uma.jmetal.problem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.solution.impl.DefaultDoubleSolution;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.iblue.model.GeoStreetInterface;
import com.iblue.model.IntersectionInterface;
import com.iblue.model.db.service.TileService;
import com.iblue.path.AlgorithmInterface;
import com.iblue.path.Dijkstra;
import com.iblue.path.GraphInterface;
import com.iblue.utils.Log;
import com.iblue.utils.Pair;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class TDDSProblem extends AbstractDoubleProblem {

	private static final long serialVersionUID = -6718380720330242163L;
	private static final long PENALTY = 20000;
	private int numberOfVariables;
	private double lower;
	private double upper;
	// private TileService tileService;
	private List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests;
	private BufferedWriter routeWriter;
	private BufferedWriter rangeWriter;
	private BufferedWriter fitnessWriter;
	private Table<BigDecimal, BigDecimal, Double> fits;
	private boolean cache = true;

	public TDDSProblem(int numberOfVariables, double lower, double upper,
			List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests, BufferedWriter routeWriter,
			BufferedWriter rangeWriter, BufferedWriter fitnessWriter) {
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
		this.fits = HashBasedTable.create();

		// Asigno los limites superior e inferior a cada variable
		for (int i = 0; i < getNumberOfVariables(); i++) {
			lowerLimit.add(lower);
			upperLimit.add(upper);
		}

		setLowerLimit(lowerLimit);
		setUpperLimit(upperLimit);

		// tileService = new TileService();

		this.rangeWriter = rangeWriter;
		this.routeWriter = routeWriter;
		this.fitnessWriter = fitnessWriter;
	}

	public void evaluate(DoubleSolution solution) {
		// Almacena de forma local los valores para cada una de las variables
		// del problema "individuo"
		double[] x = new double[getNumberOfVariables()];

		// Almacena el valor de la función de fitness
		double fx = 0d;

		// Guardo los valores de las variables del individuo en un arreglo local
		for (int i = 0; i < solution.getNumberOfVariables(); i++) {
			x[i] = solution.getVariableValue(i);
		}

		TileService tileService = new TileService();
		// generate tiles using x[0] and x[1]
		BigDecimal latRange = new BigDecimal(x[0]).setScale(7, BigDecimal.ROUND_HALF_DOWN);
		BigDecimal lonRange = new BigDecimal(x[1]).setScale(7, BigDecimal.ROUND_HALF_DOWN);
		Log.info("Evaluate solution LatRange=" + latRange + " \tLonRange=" + lonRange);
		if (fits.contains(latRange, lonRange)) {
			// due to rounding, solution has been already evaluated
			fx = fits.get(latRange, lonRange);
			Log.info("Solution already computed");
		} else {
			// new solution
			long beginComputingTiles = System.currentTimeMillis();
			String resp = tileService.computeMap(latRange, lonRange);
			long time = System.currentTimeMillis() - beginComputingTiles;
			Log.info("Time for computing tiles " + time + "(" + resp + ")");
			Pair<List<BigDecimal>, List<BigDecimal>> ranges = tileService.getRanges();
			Pair<BigDecimal, BigDecimal> range = new Pair<BigDecimal, BigDecimal>(ranges.getFirst().get(0),
					ranges.getSecond().get(0));
			try {
				rangeWriter.write(range.getFirst() + ";" + range.getSecond() + ";" + time + "\n");
				rangeWriter.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// calculate fx as mean time (for resolving n routes)
			fx = routing(range.getFirst() + ";" + range.getSecond());
		}
		// Asigno el valor de la función de fitness
		solution.setObjective(0, fx);
		if (cache) {
			fits.put(latRange, lonRange, fx);
		}

		Log.info("Solution (" + latRange + ", " + lonRange + ") fitness=" + fx);
		try {
			fitnessWriter.write(latRange + ";" + lonRange + ";" + fx + "\n");
			fitnessWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
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

	private double routing(String range) {
		long aggFitnessTime = 0l;
		// long aggTotalTime = 0l;
		// long aggSearchTime = 0l;
		TileService tileService = new TileService();
		for (int i = 0; i < origDests.size(); i++) {
			Pair<GeoStreetInterface, GeoStreetInterface> p = origDests.get(i);
			Log.debug("Setting graph " + System.currentTimeMillis());
			long begin = System.currentTimeMillis();
			GraphInterface graph = tileService.getTile(p.getFirst().getLatitude1(), p.getFirst().getLongitude1(),
					p.getSecond().getLatitude1(), p.getSecond().getLongitude1());
			AlgorithmInterface alg = new Dijkstra();
			alg.setGraph(graph);

			long beginSearch = System.currentTimeMillis();
			LinkedList<IntersectionInterface> path = alg.getPath(p.getFirst().getFromIntersection(),
					p.getSecond().getFromIntersection());
			long end = System.currentTimeMillis();
			long time = end - begin;
			long searchTime = end - beginSearch;
			long fitnessTime = time;

			boolean found = !path.isEmpty();
			if (!found) {
				fitnessTime += PENALTY;
			}
			aggFitnessTime += fitnessTime;
			// aggTotalTime += time;
			// aggSearchTime += searchTime;

			Log.debug("Route " + i + " time " + time + " found " + found);
			try {
				routeWriter.write(
						range + ";" + i + ";" + time + ";" + searchTime + ";" + fitnessTime + ";" + found + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			routeWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (double) (aggFitnessTime / origDests.size());
	}

	public void setCache(boolean cache) {
		this.cache = cache;
	}

	public void addCache(Table<BigDecimal, BigDecimal, Double> cache) {
		this.cache = true;
		this.fits.putAll(cache);
		Log.info("Cache added (actual size=" + fits.size() + ")");
	}

	public Table<BigDecimal, BigDecimal, Double> getCache() {
		return fits;
	}
}
