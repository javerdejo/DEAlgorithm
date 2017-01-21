package es.uma.lcc.uma;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.singleobjective.differentialevolution.DifferentialEvolutionBuilder;
import org.uma.jmetal.operator.impl.crossover.DifferentialEvolutionCrossover;
import org.uma.jmetal.operator.impl.selection.DifferentialEvolutionSelection;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.solution.impl.DefaultDoubleSolution;
import org.uma.jmetal.util.AlgorithmRunner;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.MultithreadedSolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.iblue.model.GeoStreetInterface;
import com.iblue.model.ParkingAllocInterface;
import com.iblue.model.StreetDAOInterface;
import com.iblue.model.db.Spot;
import com.iblue.model.db.dao.GeoStreetDAO;
import com.iblue.model.db.service.ParkingAlloc;
import com.iblue.utils.Log;
import com.iblue.utils.Log.LogLevel;
import com.iblue.utils.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.uma.jmetal.runner.AbstractAlgorithmRunner.printFinalSolutionSet;

public class DESolver {
	private static final int DEFAULT_NUMBER_OF_CORES = 1;
	private static final int MAX_EVALUATIONS = 1000; // 250000;
	private static final int POP_SIZE = 5;

	private static final String OUTPUT_FILE_RANGE = "tile-comp-results.csv";
	private static final String OUTPUT_FILE_ROUTE = "routing-results.csv";
	private static final String OUTPUT_FILE_FITNESS = "fitness-results.csv";
	private static final String CACHE_FILE = "cache.csv";

	private static List<Pair<GeoStreetInterface, GeoStreetInterface>> getOrigDests(List<Pair<Float, Float>> pairsSpot) {
		List<GeoStreetInterface> spots = new ArrayList<GeoStreetInterface>();
		ParkingAllocInterface park = new ParkingAlloc();
		StreetDAOInterface stDao = new GeoStreetDAO();

		for (Pair<Float, Float> p : pairsSpot) {
			Spot spot = new Spot();
			spot.setLatLong(new BigDecimal(p.getFirst()), new BigDecimal(p.getSecond()));
			// System.out.println("Spot added " + spot.toString());
			long stId = park.getNearestStreetId(spot);
			GeoStreetInterface st = (GeoStreetInterface) stDao.getStreet(stId);
			// System.out.println("Street added " + st.getId());
			spots.add(st);
		}

		List<Pair<GeoStreetInterface, GeoStreetInterface>> pairs = new ArrayList<Pair<GeoStreetInterface, GeoStreetInterface>>();
		for (int i = 0; i < spots.size(); i++) {
			for (int j = 0; j < spots.size(); j++) {
				if (i != j) {
					pairs.add(new Pair<GeoStreetInterface, GeoStreetInterface>(spots.get(i), spots.get(j)));
				}
			}
		}

		Log.debug(pairs.size() + " routes added");

		return pairs;
	}

	private static List<Pair<Float, Float>> loadSpots(String filePath) throws IOException {
		FileReader fr = new FileReader(filePath);
		BufferedReader br = new BufferedReader(fr);
		String line;
		List<Pair<Float, Float>> pairs = new ArrayList<Pair<Float, Float>>();
		while ((line = br.readLine()) != null) {
			String[] nums = line.split(",");
			Pair<Float, Float> p = new Pair<Float, Float>(Float.parseFloat(nums[0]), Float.parseFloat(nums[1]));
			pairs.add(p);
		}
		br.close();
		return pairs;
	}

	private static void tdds(List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests, BufferedWriter routeWriter,
			BufferedWriter rangeWriter, String[] args, BufferedWriter fitnessWriter) {

		// 5.0d ~ 5,000 tiles for whole world
		// 0.001 ~ 68,719,476,736 tiles for whole world
		TDDSProblem problem = new TDDSProblem(2, 0.01d, 5.0d, origDests, routeWriter, rangeWriter, fitnessWriter);
		problem.addCache(readCache());

		Algorithm<DoubleSolution> algorithm;
		DifferentialEvolutionSelection selection;
		DifferentialEvolutionCrossover crossover;
		SolutionListEvaluator<DoubleSolution> evaluator;

		int numberOfCores;
		if (args.length == 1) {
			numberOfCores = Integer.valueOf(args[0]);
		} else {
			numberOfCores = DEFAULT_NUMBER_OF_CORES;
		}

		if (numberOfCores == 1) {
			evaluator = new SequentialSolutionListEvaluator<DoubleSolution>();
		} else {
			evaluator = new MultithreadedSolutionListEvaluator<DoubleSolution>(numberOfCores, problem);
		}

		crossover = new DifferentialEvolutionCrossover(0.5, 0.5, "rand/1/bin");
		selection = new DifferentialEvolutionSelection();

		algorithm = new DifferentialEvolutionBuilder(problem).setCrossover(crossover).setSelection(selection)
				.setSolutionListEvaluator(evaluator).setMaxEvaluations(MAX_EVALUATIONS).setPopulationSize(POP_SIZE)
				.build();

		AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor(algorithm).execute();

		DoubleSolution solution = algorithm.getResult();
		long computingTime = algorithmRunner.getComputingTime();

		List<DoubleSolution> population = new ArrayList<DoubleSolution>(1);
		population.add(solution);
		printFinalSolutionSet(population);

		Log.debug("Time: " + computingTime);

		writeCache(problem.getCache());

		evaluator.shutdown();

	}

	private static void sdev(List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests, BufferedWriter routeWriter,
			BufferedWriter rangeWriter, String[] args, BufferedWriter fitnessWriter) {
		// Min and max values won't be used
		TDDSProblem problem = new TDDSProblem(2, 0.01d, 5.0d, origDests, routeWriter, rangeWriter, fitnessWriter);
		problem.setCache(false);

		List<DoubleSolution> sols = new ArrayList<DoubleSolution>();
		DoubleSolution tmp = new DefaultDoubleSolution(problem);
		// 2.4362724	0.0355379	295
		tmp.setVariableValue(0, 2.4362724d);
		tmp.setVariableValue(1, 0.0355379d);
		sols.add(tmp);
		
		// 1.4609874	0.0270191	293
		tmp = new DefaultDoubleSolution(problem);
		tmp.setVariableValue(0, 1.4609874d);
		tmp.setVariableValue(1, 0.0270191d);
		sols.add(tmp);
		
		// 0.2372263	0.0289512	286
		tmp = new DefaultDoubleSolution(problem);
		tmp.setVariableValue(0, 0.2372263d);
		tmp.setVariableValue(1, 0.0289512d);
		sols.add(tmp);
		
		for (DoubleSolution solution : sols) {
			for (int i = 0; i < 30; i++) {
				problem.evaluate(solution);
			}
		}
	}

	private static void rand(List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests, BufferedWriter routeWriter,
			BufferedWriter rangeWriter, String[] args, BufferedWriter fitnessWriter) {

		Random generator = new Random(1791823123l);

		TDDSProblem problem = new TDDSProblem(2, 0.01d, 5.0d, origDests, routeWriter, rangeWriter, fitnessWriter);
		problem.addCache(readCache());

		for (int i = 0; i < 30; i++) {
			DoubleSolution solution = new DefaultDoubleSolution(problem);
			double x = generator.nextDouble() * 4.9d + 0.01d;
			double y = generator.nextDouble() * 4.9d + 0.01d;
			solution.setVariableValue(0, x);
			solution.setVariableValue(1, y);
			problem.evaluate(solution);
		}

		writeCache(problem.getCache());
	}

	private static void manual(List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests, BufferedWriter routeWriter,
			BufferedWriter rangeWriter, String[] args, BufferedWriter fitnessWriter) {

		TDDSProblem problem = new TDDSProblem(2, 0.01d, 5.0d, origDests, routeWriter, rangeWriter, fitnessWriter);
		problem.addCache(readCache());

		List<Double> sizes = new ArrayList<Double>();
		sizes.add(0.01d);
		sizes.add(0.1d);
		sizes.add(0.2d);
		sizes.add(0.5d);
		sizes.add(1.0d);
		sizes.add(10.0d);

		List<DoubleSolution> sols = new ArrayList<DoubleSolution>();
		for (Double d1 : sizes) {
			for (Double d2 : sizes) {
				DoubleSolution solution = new DefaultDoubleSolution(problem);
				solution.setVariableValue(0, d1);
				solution.setVariableValue(1, d2);
				sols.add(solution);
				Log.debug("Solution added to list: " + d1 + " " + d2);
			}
		}

		for (DoubleSolution solution : sols) {
			problem.evaluate(solution);
		}

		writeCache(problem.getCache());
	}

	private static Table<BigDecimal, BigDecimal, Double> readCache() {
		Table<BigDecimal, BigDecimal, Double> cache = HashBasedTable.create();
		try {
			File file = new File(CACHE_FILE);
			if (file.exists()) {
				FileReader fr = new FileReader(file);
				BufferedReader br = new BufferedReader(fr);
				String line;
				br.readLine();
				while ((line = br.readLine()) != null) {
					Log.debug(line);
					String[] prts = line.split(";");
					BigDecimal latRange = new BigDecimal(prts[0]).setScale(7, BigDecimal.ROUND_HALF_DOWN);
					BigDecimal lonRange = new BigDecimal(prts[1]).setScale(7, BigDecimal.ROUND_HALF_DOWN);
					Double fitness = new Double(prts[2]);
					cache.put(latRange, lonRange, fitness);
					Log.debug("Added to cache: " + latRange + ";" + lonRange + ";" + fitness);
				}
				br.close();
			}

		} catch (IOException e) {
			Log.error("Something happen with file " + OUTPUT_FILE_RANGE);
			e.printStackTrace();
		}

		return cache;
	}

	private static void writeCache(Table<BigDecimal, BigDecimal, Double> cache) {

		try {
			File file = new File(CACHE_FILE);
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file, false);
			BufferedWriter cw = new BufferedWriter(fw);
			cw.write("latRange;lonRange;fitness\n");
			for (Cell<BigDecimal, BigDecimal, Double> cell : cache.cellSet()) {
				cw.write(cell.getRowKey() + ";" + cell.getColumnKey() + ";" + cell.getValue() + "\n");
			}
			cw.flush();
			cw.close();

		} catch (IOException e) {
			Log.error("Something happen with file " + OUTPUT_FILE_RANGE);
			e.printStackTrace();
		}

	}

	private static final int DIFF = 1;
	private static final int SDEV = 2;
	private static final int RAND = 3;
	private static final int MANU = 4;

	public static void main(String[] args) throws IOException {
		int selection = 2;

		Log.setLogLevel(LogLevel.INFO);

		List<Pair<Float, Float>> pairs = loadSpots("spots-malaga.txt");
		List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests = getOrigDests(pairs);
		BufferedWriter routeWriter = null;
		BufferedWriter rangeWriter = null;
		BufferedWriter fitnessWriter = null;

		try {
			File file = new File(OUTPUT_FILE_RANGE);
			if (!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file, false);
			rangeWriter = new BufferedWriter(fw);
			rangeWriter.write("latRange;lonRange;buildtime\n");
			rangeWriter.flush();

			file = new File(OUTPUT_FILE_ROUTE);
			if (!file.exists()) {
				file.createNewFile();
			}
			fw = new FileWriter(file, false);
			routeWriter = new BufferedWriter(fw);
			routeWriter.write("latRange;lonRange;route;totaltime;searchtime;fitnesstime;found\n");
			routeWriter.flush();

			file = new File(OUTPUT_FILE_FITNESS);
			if (!file.exists()) {
				file.createNewFile();
			}
			fw = new FileWriter(file, false);
			fitnessWriter = new BufferedWriter(fw);
			fitnessWriter.write("latRange;lonRange;fitness\n");
			fitnessWriter.flush();

		} catch (IOException e) {
			Log.error("Something happen with file " + OUTPUT_FILE_RANGE);
			e.printStackTrace();
		}

		switch (selection) {
		case DIFF:
			tdds(origDests, routeWriter, rangeWriter, args, fitnessWriter);
			break;
		case SDEV:
			sdev(origDests, routeWriter, rangeWriter, args, fitnessWriter);
			break;
		case RAND:
			rand(origDests, routeWriter, rangeWriter, args, fitnessWriter);
			break;
		case MANU:
			manual(origDests, routeWriter, rangeWriter, args, fitnessWriter);
			break;
		}

		try {
			routeWriter.close();
			rangeWriter.close();
			fitnessWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.exit(0);
	}
}
