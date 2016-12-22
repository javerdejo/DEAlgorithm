package es.uma.lcc.uma;

import org.uma.jmetal.problem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.solution.impl.DefaultDoubleSolution;

import java.util.ArrayList;
import java.util.List;

class DEAlgorithm  extends AbstractDoubleProblem {
    private int numberOfVariables;
    private final double lower = -10.0;
    private final double upper = 10.0;

    public DEAlgorithm() {
        this(2);
    }

    public DEAlgorithm(int numberOfVariables) {
        this.numberOfVariables = numberOfVariables;

        setNumberOfVariables(numberOfVariables);    // número de variables (dimensiones)
        setNumberOfObjectives(1);                   // Problema monoobjetivo
        setName("Sphere Problem");                  // Nombre del problema

        List<Double> lowerLimit = new ArrayList<Double>();
        List<Double> upperLimit = new ArrayList<Double>();

        // Asigno los limites superior e inferior a cada variable
        for (int i = 0; i < getNumberOfVariables(); i++) {
            lowerLimit.add(lower);
            upperLimit.add(upper);
        }

        setLowerLimit(lowerLimit);
        setUpperLimit(upperLimit);

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

        // Calculo el valor de fitness (x^2)
        for (int var = 0; var < solution.getNumberOfVariables(); var++) {
            fx += x[var] * x[var];
        }

        // Asigno el valor de la función de fitness al primer objetivo ya que se trata
        // de un problema mono-objetico
        solution.setObjective(0, fx);
    }

    // Esta función es utilizada por el algoritmo para crear la población inicial
    public DoubleSolution createSolution() {
        return new DefaultDoubleSolution(this);
    }

}
