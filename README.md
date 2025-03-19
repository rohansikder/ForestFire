# Parallel Forest Fire Simulation

## Description
This project simulates the spread and recovery of forest fires using parallel computing techniques. It demonstrates the use of data parallelism, task parallelism and synchronization mechanisms for optimizing simulation performance on multi-core processors. The implementation uses Java's ForkJoin framework to efficiently manage parallel tasks and analyze performance metrics.

## Features
- **Data Parallelism**: The forest grid is partitioned among multiple threads, allowing simultaneous updates and reducing dependencies.
- **Task Parallelism**: Independent simulation tasks, such as updating cell states and state logging, are executed concurrently.
- **ForkJoin Framework**: Efficiently manages task distribution and load balancing through recursive subdivision of tasks.
- **Visualization**: Visualization of the forest state at each simulation step.
- **Performance Metrics**: Measures execution time, speedup, efficiency, and scalability using Amdahl's Law, Gustafson's Law, and the Karp-Flatt metric.
- **Configurable Simulation**: Easily adjust grid size, simulation steps and thread count to test performance across different scenarios.

## Technical Details
- **Language**: Java
- **Concurrency**: ForkJoinPool, RecursiveAction
- **Randomization**: Thread-local random generators for reducing contention

## Usage

### Prerequisites
- Java Development Kit (JDK) 17 or newer

### Configuration
The simulation can be customized by modifying the following parameters in `ForestFireSimulation.java`:
- `gridSizes`: Array of grid sizes to test (e.g., `[100, 500, 1000, 2000]`).
- `stepCounts`: Number of simulation steps (default is `500`).
- `threadCounts`: Array specifying different thread counts to test parallel performance (e.g., `[1, 2, 4, 8]`).
- `growthProbability`: Probability that an empty cell will grow a new tree (default is `0.01`).
- `burnProbability`: Probability that a tree will catch fire from a burning neighbor (default is `0.5`).
- `visualize`: Set to `true` to display simulation steps.
- `trackStates`: Set to `true` to log state data to CSV.

### Output
Simulation results and performance metrics are stored in:
- `performance_metrics.csv`: Contains data such as execution time, speedup, efficiency, and theoretical scalability.
- `state_tracking.csv`: Tracks the number of tree, burning, and empty cells at each simulation step.

## Performance Analysis
The simulation was evaluated on various grid sizes and thread counts, demonstrating performance improvements through parallelization, but also highlighting the importance of considering overhead and synchronization costs. For detailed analysis, refer to the generated `performance_metrics.csv`.

## Author
**Rohan Sikder**  
**Student ID**: 24165816
