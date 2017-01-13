package es.uma.lcc.uma;

import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.singleobjective.differentialevolution.DifferentialEvolutionBuilder;
import org.uma.jmetal.operator.impl.crossover.DifferentialEvolutionCrossover;
import org.uma.jmetal.operator.impl.selection.DifferentialEvolutionSelection;
import org.uma.jmetal.problem.DoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.util.AlgorithmRunner;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.MultithreadedSolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

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

import static org.uma.jmetal.runner.AbstractAlgorithmRunner.printFinalSolutionSet;

public class DESolver {
	private static final int DEFAULT_NUMBER_OF_CORES = 1;
	private static final int MAX_EVALUATIONS = 500 ; //250000;
	private static final int POP_SIZE = 5;
	
	private static final String OUTPUT_FILE_RANGE = "tile-comp-results.csv";
	private static final String OUTPUT_FILE_ROUTE = "routing-results.csv";

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

	public static void main(String[] args) throws IOException {
		Log.setLogLevel(LogLevel.INFO);
		List<Pair<Float, Float>> pairs = loadSpots("spots-malaga.txt");
		List<Pair<GeoStreetInterface, GeoStreetInterface>> origDests = getOrigDests(pairs);
		BufferedWriter routeWriter = null;
		BufferedWriter rangeWriter = null;
		
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
			routeWriter.write("latRange;lonRange;totaltime;searchtime;fitnesstime;found\n");
			routeWriter.flush();

		} catch (IOException e) {
			Log.error("Something happen with file " + OUTPUT_FILE_RANGE);
			e.printStackTrace();
		}

		// 5.0d ~ 5,000 tiles for whole world
		// 0.001 ~ 68,719,476,736 tiles for whole world
		DoubleProblem problem = new TDDSProblem(2, 0.01d, 5.0d, origDests, routeWriter, rangeWriter);
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
				.setSolutionListEvaluator(evaluator).setMaxEvaluations(MAX_EVALUATIONS).setPopulationSize(POP_SIZE).build();

		AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor(algorithm).execute();

		DoubleSolution solution = algorithm.getResult();
		long computingTime = algorithmRunner.getComputingTime();

		List<DoubleSolution> population = new ArrayList<DoubleSolution>(1);
		population.add(solution);
		printFinalSolutionSet(population);

		Log.debug("Time: " + computingTime);

		evaluator.shutdown();
		
		try {
			routeWriter.close();
			rangeWriter.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		System.exit(0);
	}
}
