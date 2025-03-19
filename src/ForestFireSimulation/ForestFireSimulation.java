// Name: Rohan Sikder
// Student ID: 24165816
// Assignment 2: Parallelising the Simulation of Forest Fire Spread and Recovery

package ForestFireSimulation;

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ForkJoinPool;
import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;

public class ForestFireSimulation {
    // Represent the state of a cell in the forest
    static final int EMPTY = 0;
    static final int TREE = 1;
    static final int BURNING = 2;

    // Parameters for the grid, simulation steps and probabilities
    static int gridHeight = 100;
    static int gridWidth = 100;
    static int timeSteps = 500;

    static double growthProbability = 0.01; // Chance of tree growth in an empty cell
    static double burnProbability = 0.5;   // Chance a tree catches fire from a burning neighbor

    // The forest grid and its next state
    static int[][] forest;
    static int[][] nextForest;

    // Thread local random number generators for parallel tasks
    static Random[] threadLocalRandoms;

    // Flags for visualization and state tracking
    static boolean visualize = true; // Whether to show the grid at each step
    static boolean trackStates = true; // Whether to log the states of the forest

    public static void main(String[] args) {
        // Different configurations to test the simulation
        int[] gridSizes = {100, 500, 1000, 2000};
        int[] stepCounts = {500};
        int[] threadCounts = {1, 2, 4, 8};

        // Output files for performance metrics and state tracking
        String csvFileName = "performance_metrics.csv";
        String stateTrackingFileName = "state_tracking.csv";

        try (
            // File writers for output data
            FileWriter csvWriter = new FileWriter(csvFileName);
            FileWriter stateWriter = new FileWriter(stateTrackingFileName)
        ) {
            // Write CSV headers
            csvWriter.append("GridSize,Steps,Threads,Time,Speedup,Efficiency,AmdahlSpeedup,GustafsonSpeedup,KarpFlattMetric\n");
            if (trackStates) {
                stateWriter.append("TimeStep,Trees,Empty,Burning\n");
            }

            // Iterate through all grid sizes and configurations
            for (int gridSize : gridSizes) {
                for (int steps : stepCounts) {
                    // Run the serial version for a baseline performance measure
                    double serialTime = runConfiguration(gridSize, steps, 1, csvWriter, stateWriter);

                    // Test with multiple threads for parallelization
                    for (int threads : threadCounts) {
                        if (threads == 1) continue; // Skip 1-thread as it's already the serial case
                        runConfigurationWithBaseline(gridSize, steps, threads, serialTime, csvWriter, stateWriter);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double runConfiguration(int gridSize, int steps, int threads, FileWriter csvWriter, FileWriter stateWriter) throws IOException {
        System.out.printf("Testing with Grid: %dx%d, Steps: %d, Threads: %d%n", gridSize, gridSize, steps, threads);
        
        gridHeight = gridSize;
        gridWidth = gridSize;
        timeSteps = steps;
        
        // Initialize the forest grid
        initializeForest(threads);
        
        // Measure the time for the simulation
        long startTime = System.nanoTime();
        runSimulation(threads, stateWriter);
        long endTime = System.nanoTime();
        
        // Calculate elapsed time
        double elapsedTime = (endTime - startTime) / 1e9;

        // Write the performance metrics
        writePerformanceMetrics(threads, elapsedTime, elapsedTime, csvWriter);
        
        return elapsedTime; // Return the serial execution time
    }

    private static void runConfigurationWithBaseline(int gridSize, int steps, int threads, double serialTime, FileWriter csvWriter, FileWriter stateWriter) throws IOException {
        System.out.printf("Testing with Grid: %dx%d, Steps: %d, Threads: %d%n", gridSize, gridSize, steps, threads);
        
        // Set up the forest dimensions and steps
        gridHeight = gridSize;
        gridWidth = gridSize;
        timeSteps = steps;
        
        // Initialize the forest grid
        initializeForest(threads);
        
        // Measure the time for the parallel simulation
        long startTime = System.nanoTime();
        runSimulation(threads, stateWriter);
        long endTime = System.nanoTime();
        
        // Calculate elapsed time
        double elapsedTime = (endTime - startTime) / 1e9;

        // Write the performance metrics comparing to the serial baseline
        writePerformanceMetrics(threads, elapsedTime, serialTime, csvWriter);
    }

    static void initializeForest(int threads) {
        // Create the grids for the forest and the next state
        forest = new int[gridHeight][gridWidth];
        nextForest = new int[gridHeight][gridWidth];
        threadLocalRandoms = new Random[threads]; // One random generator per thread
        
        Random initRandom = new Random();
        double initialBurningProbability = 0.02; // Chance of a tree starting on fire

        // Prepare thread-local random number generators
        for (int i = 0; i < threads; i++) {
            threadLocalRandoms[i] = new Random(initRandom.nextLong());
        }

        // Populate the forest grid with trees and some burning cells
        for (int i = 0; i < gridHeight; i++) {
            for (int j = 0; j < gridWidth; j++) {
                if (initRandom.nextDouble() < 0.5) { // 50% chance to have a tree
                    forest[i][j] = TREE;
                    if (initRandom.nextDouble() < initialBurningProbability) {
                        forest[i][j] = BURNING; // Some trees start burning
                    }
                }
            }
        }
    }

    static class ParallelSimulation extends RecursiveAction {
        private final UpdateTask[] tasks;
        
        ParallelSimulation(UpdateTask[] tasks) {
            this.tasks = tasks; // Group of tasks to run in parallel
        }
        
        @Override
        protected void compute() {
            invokeAll(tasks); // Run all tasks
        }
    }

    static class UpdateTask extends RecursiveAction {
        private final int startRow, endRow, threadId;
        private static final int THRESHOLD = 200; // Split tasks if they exceed this row range

        UpdateTask(int startRow, int endRow, int threadId) {
            this.startRow = startRow;
            this.endRow = endRow;
            this.threadId = threadId;
        }

        @Override
        protected void compute() {
            if (endRow - startRow <= THRESHOLD) {
                processRows(); // Process rows if the task is small enough
            } else {
                int midRow = (startRow + endRow) >>> 1; // Split the task
                invokeAll(
                    new UpdateTask(startRow, midRow, threadId),
                    new UpdateTask(midRow, endRow, threadId)
                );
            }
        }

        private void processRows() {
            Random random = threadLocalRandoms[threadId]; // Get the thread-local random generator
            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < gridWidth; j++) {
                    updateCell(i, j, random); // Update each cell in the grid
                }
            }
        }

        private void updateCell(int i, int j, Random random) {
            // Update the state of a single cell based on its neighbors
            int currentState = forest[i][j];
            
            if (currentState == BURNING) {
                nextForest[i][j] = EMPTY; // Burning cells become empty
            } else if (currentState == TREE) {
                nextForest[i][j] = (isNeighborBurning(i, j) && random.nextDouble() < burnProbability) ? BURNING : TREE;
            } else { // EMPTY
                nextForest[i][j] = (random.nextDouble() < growthProbability) ? TREE : EMPTY;
            }
        }

        private boolean isNeighborBurning(int i, int j) {
            // Check if any neighbor of the cell is burning
            return (checkCell(i-1, j-1) || checkCell(i-1, j) || checkCell(i-1, j+1) ||
                    checkCell(i, j-1) || checkCell(i, j+1) ||
                    checkCell(i+1, j-1) || checkCell(i+1, j) || checkCell(i+1, j+1));
        }

        private boolean checkCell(int i, int j) {
            // Wrap around the grid edges 
            i = (i + gridHeight) % gridHeight;
            j = (j + gridWidth) % gridWidth;
            return forest[i][j] == BURNING;
        }
    }

    static void runSimulation(int threads, FileWriter stateWriter) throws IOException {
        // Use a ForkJoinPool to manage parallel tasks
        ForkJoinPool pool = new ForkJoinPool(threads);
        
        try {
            for (int step = 0; step < timeSteps; step++) {
                if (visualize) {
                    System.out.printf("Time Step: %d%n", step);
                    displayForest(); // Show the forest grid
                }

                if (trackStates) {
                    recordState(step, stateWriter); // Log the current state
                }

                // Divide the grid into tasks for the threads
                int rowsPerThread = Math.max(1, gridHeight / threads);
                UpdateTask[] tasks = new UpdateTask[threads];
                
                for (int t = 0; t < threads; t++) {
                    int startRow = t * rowsPerThread;
                    int endRow = (t == threads - 1) ? gridHeight : (t + 1) * rowsPerThread;
                    tasks[t] = new UpdateTask(startRow, endRow, t);
                }
                
                // Run the tasks using the ForkJoin framework
                pool.invoke(new ParallelSimulation(tasks));
                
                // Swap the current and next grid for the next iteration
                int[][] temp = forest;
                forest = nextForest;
                nextForest = temp;
            }
        } finally {
            pool.shutdown(); 
        }
    }

    static void displayForest() {
        if (!visualize) return; // Skip if visualization is disabled
        
        // Display the grid as a text based map
        StringBuilder sb = new StringBuilder(gridHeight * (gridWidth * 2 + 1));
        for (int i = 0; i < gridHeight; i++) {
            for (int j = 0; j < gridWidth; j++) {
                sb.append(switch (forest[i][j]) {
                	case TREE -> "T"; 
                    case BURNING -> "*"; 
                    default -> " ";
                });
            }
            sb.append('\n');
        }
        System.out.println(sb);
    }

    static void recordState(int timeStep, FileWriter stateWriter) throws IOException {
        if (!trackStates) return;

        // Count the number of cells in each state
        int trees = 0, empty = 0, burning = 0;
        for (int[] row : forest) {
            for (int cell : row) {
                switch (cell) {
                    case TREE -> trees++;
                    case EMPTY -> empty++;
                    case BURNING -> burning++;
                }
            }
        }
        // Log the state counts to the file
        stateWriter.append(String.format("%d,%d,%d,%d%n", timeStep, trees, empty, burning));
    }

    static void writePerformanceMetrics(int threads, double elapsedTime, double serialTime, FileWriter csvWriter) throws IOException {
        // Calculate speedup, efficiency, and other parallel performance metrics
        double speedup = serialTime / elapsedTime;
        double efficiency = speedup / threads;
        double parallelFraction = 0.90; // Estimate of parallelizable portion
        double amdahlSpeedup = 1 / ((1 - parallelFraction) + (parallelFraction / threads));
        double gustafsonSpeedup = (1 - parallelFraction) + (parallelFraction * threads);
        double karpFlattMetric = (threads > 1) ? (1 / amdahlSpeedup - 1 / threads) / (1 - 1 / threads) : 0;

        // Write the metrics to the CSV file
        csvWriter.append(String.format("%dx%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f%n",
                gridHeight, gridWidth, timeSteps, threads, elapsedTime, speedup, efficiency * 100,
                amdahlSpeedup, gustafsonSpeedup, karpFlattMetric));
    }
}
