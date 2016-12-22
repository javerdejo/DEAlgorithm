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

import java.util.ArrayList;
import java.util.List;

import static org.uma.jmetal.runner.AbstractAlgorithmRunner.printFinalSolutionSet;

public class Main {
    private static final int DEFAULT_NUMBER_OF_CORES = 1 ;

    public static void main(String[] args) {
        DoubleProblem problem = new DEAlgorithm(2);
        Algorithm<DoubleSolution> algorithm;
        DifferentialEvolutionSelection selection;
        DifferentialEvolutionCrossover crossover;
        SolutionListEvaluator<DoubleSolution> evaluator ;

        int numberOfCores;
        if (args.length == 1) {
            numberOfCores = Integer.valueOf(args[0]) ;
        } else {
            numberOfCores = DEFAULT_NUMBER_OF_CORES ;
        }

        if (numberOfCores == 1) {
            evaluator = new SequentialSolutionListEvaluator<DoubleSolution>() ;
        } else {
            evaluator = new MultithreadedSolutionListEvaluator<DoubleSolution>(numberOfCores, problem) ;
        }

        crossover = new DifferentialEvolutionCrossover(0.5, 0.5, "rand/1/bin") ;
        selection = new DifferentialEvolutionSelection();

        algorithm = new DifferentialEvolutionBuilder(problem)
                .setCrossover(crossover)
                .setSelection(selection)
                .setSolutionListEvaluator(evaluator)
                .setMaxEvaluations(250000)
                .setPopulationSize(100)
                .build() ;

        AlgorithmRunner algorithmRunner = new AlgorithmRunner.Executor(algorithm)
                .execute() ;

        DoubleSolution solution = algorithm.getResult() ;
        long computingTime = algorithmRunner.getComputingTime() ;

        List<DoubleSolution> population = new ArrayList<DoubleSolution>(1) ;
        population.add(solution) ;
        printFinalSolutionSet(population);

        System.out.println("Time: " + computingTime);

        evaluator.shutdown();
    }
}
