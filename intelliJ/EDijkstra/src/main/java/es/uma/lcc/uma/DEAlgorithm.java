package es.uma.lcc.uma;

import org.uma.jmetal.problem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.DoubleSolution;
import org.uma.jmetal.solution.impl.DefaultDoubleSolution;

import java.util.ArrayList;
import java.util.List;

class DEAlgorithm  extends AbstractDoubleProblem {
    private int numberOfVariables;
    private double lower;
    private double upper;

    DEAlgorithm(int numberOfVariables, double lower, double upper) {
        this.numberOfVariables = numberOfVariables;
        this.lower = lower;
        this.upper = upper;

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
            // Llamar a la simulación de N rutas con marcos de tamaño x[0] + x[1]
            // y obtener como resultado el tiempo medio que tarda en realizarse la
            // asignación de rutas

            // 'fx' debe almacenar el tiempo promedio
            fx += x[var] * x[var]; // <-- Cambiar por llamada al simulador
        }

        // Asigno el valor de la función de fitness
        solution.setObjective(0, fx);
    }

    // Esta función es utilizada por el algoritmo para crear la población inicial
    public DoubleSolution createSolution() {
        return new DefaultDoubleSolution(this);
    }
}
