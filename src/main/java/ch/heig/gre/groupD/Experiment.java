package ch.heig.gre.groupD;

import ch.heig.gre.Keys;
import ch.heig.gre.graph.GridGraph;
import ch.heig.gre.graph.GridGraph2D;
import ch.heig.gre.graph.PositiveWeightFunction;
import ch.heig.gre.graph.VertexLabelling;
import ch.heig.gre.maze.MazeBuilder;
import ch.heig.gre.maze.MazeGenerator;
import ch.heig.gre.maze.MazeSolver;
import ch.heig.gre.maze.impl.GridMazeBuilder;
import ch.heig.gre.maze.impl.MazeTuner;
import ch.heig.gre.maze.impl.ShenaniganWeightFunction;

import java.util.ArrayList;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class Experiment {
    /**
     * Dimension de la grille (carrée)
     */
    private static final int SIDE = 1100;

    /**
     * Sommets source et destination pour les expériences
     */
    private static final int SRC = 550500;
    private static final int DST = 660600;

    /**
     * Nombre de grilles à générer pour chaque expérience
     */
    private static final int N = 100;

    /**
     * Topologie de la grille
     */
    private static final GridGraph2D TOPOLOGY;

    private static final double[] K_MANHATTANS = {0.5, 2, 4, 6, 8};

    /**
     * Expériences à réaliser
     */
    private static final Params[] EXPERIMENTS = {
            new Params(
                    "Relief très peu dense, labyrinthe très ouvert",
                    0, 0.15, 20, 1, 20),
            new Params(
                    "Relief très peu dense, labyrinthe assez ouvert",
                    0, 0.1, 20, 1, 20),
            new Params(
                    "Relief très peu dense, labyrinthe peu ouvert",
                    0, 0.01, 20, 1, 20),
            new Params(
                    "Relief dense, labyrinthe moyennement ouvert",
                    0.25, 0.05, 25, 5, 20),
            new Params(
                    "Relief très dense, labyrinthe moyennement ouvert",
                    0.5, 0.05, 25, 5, 20),
            new Params(
                    "Relief très dense et fortement pondéré, labyrinthe moyennement ouvert",
                    0.5, 0.05, 25, 5, 100)
    };

    /**
     * <p>Paramètres d'une expérience, avec une description approximative de leurs effets sur la génération.</p>
     *
     * <p>À passer en paramètre de la méthode {@link #generateGrid} pour générer un labyrinthe.</p>
     *
     * @param description            Description de l'expérience
     * @param reliefDensityFactor    Facteur de densité du relief
     * @param wallRemovalProbability Probabilité de suppression d'un mur lors de la génération du relief
     * @param reliefRadiusRatio      Ratio du rayon de la zone de relief par rapport à la taille du labyrinthe
     * @param reliefSummitsPerRange  Nombre de sommets de relief générés par chaîne de montagnes
     * @param reliefMaxSummitWeight  Poids maximal d'un sommet de relief
     */
    record Params(String description,
                  double reliefDensityFactor,
                  double wallRemovalProbability,
                  double reliefRadiusRatio,
                  int reliefSummitsPerRange,
                  int reliefMaxSummitWeight
    ) {
    }

    static {
        var g = new GridGraph(SIDE);
        GridGraph.bindAll(g);
        TOPOLOGY = g;
    }

    public static void main(String[] args) {
        // TODO
        MazeGenerator DFS = new DfsGenerator();
        RandomGenerator rng = new Random(1234); // seeded so that tests are deterministic
        ArrayList<AStar> aStarsAdmissible = new ArrayList<>(AStar.Heuristic.values().length);
        ArrayList<AStar> aStarsKManhattans = new ArrayList<>(K_MANHATTANS.length);
        VertexLabelling<Integer> mockView = new VertexLabelling<Integer>() {
            @Override
            public Integer getLabel(int v) {
                return 0;
            }

            @Override
            public void setLabel(int v, Integer label) {
            }
        };
        for (int i = 0; i < 4; i++) { // adds the first 4 heuristics
            aStarsAdmissible.add(new AStar(AStar.Heuristic.values()[i]));
        }
        for (double mult : K_MANHATTANS) { // adds different versions of k-manhattan
            aStarsKManhattans.add(new AStar(AStar.Heuristic.K_MANHATTAN, mult));
        }
        for (Params experiment : EXPERIMENTS) {
            System.out.println("\n\nExperiment - " + experiment.description);
            //admissible setup
            ArrayList<Double> avgLength = new ArrayList<>(aStarsAdmissible.size());
            ArrayList<Double> avgProcessed = new ArrayList<>(aStarsAdmissible.size());
            ArrayList<Double> improvementRate = new ArrayList<>(aStarsAdmissible.size());
            ArrayList<Double> avgExpansionRate = new ArrayList<>(aStarsAdmissible.size());
            for (int i = 0; i < aStarsAdmissible.size(); i++) {
                avgLength.add(0.);
                avgProcessed.add(0.);
                improvementRate.add(0.);
                avgExpansionRate.add(0.);
            }
            // non admissible setup
            ArrayList<Integer> solutionsFound = new ArrayList<>(aStarsKManhattans.size());
            ArrayList<Double> avgProcessedK = new ArrayList<>(aStarsKManhattans.size());
            ArrayList<Double> avgLengthK = new ArrayList<>(aStarsKManhattans.size());
            ArrayList<ArrayList<Double>> errors = new ArrayList<>(aStarsKManhattans.size());
            for (int i = 0; i < aStarsKManhattans.size(); i++) {
                solutionsFound.add(0);
                avgProcessedK.add(0.);
                avgLengthK.add(0.);
                errors.add(new ArrayList<>(N));
            }

            for (int i = 0; i < N; i++) { // run N times experiment
                GenerationResult generation = generateGrid(DFS, experiment, rng);
                int optimalLength = -1;
                int processedH0 = -1;
                for (int j = 0; j < aStarsAdmissible.size(); j++) { // get admissible values
                    MazeSolver.Result result = aStarsAdmissible.get(j).solve(generation.maze, generation.weights, SRC, DST, mockView);
                    optimalLength = result.metadata().get(Keys.LENGTH);
                    int processed = result.metadata().get(Keys.NB_PROCESSED_VERTICES);
                    if (j == 0) {
                        processedH0 = processed;
                    }
                    avgLength.set(j, avgLength.get(j) + optimalLength * 1. / N);
                    avgProcessed.set(j, avgProcessed.get(j) + processed * 1. / N);
                    avgExpansionRate.set(j, avgExpansionRate.get(j) + (optimalLength * 1. / processed) / N);
                    double reductionPourcentage = (processedH0 - processed) * 100.0 / processedH0;
                    improvementRate.set(j, improvementRate.get(j) + reductionPourcentage / N);
                }
                for (int j = 0; j < aStarsKManhattans.size(); j++) { //get kmanhattans values
                    MazeSolver.Result result = aStarsKManhattans.get(j).solve(generation.maze, generation.weights, SRC, DST, mockView);
                    if (result.metadata().get(Keys.LENGTH) == optimalLength) {
                        solutionsFound.set(j, solutionsFound.get(j) + 1);
                    }
                    avgLengthK.set(j, avgLengthK.get(j) + result.metadata().get(Keys.LENGTH) * 1. / N);
                    errors.get(j).add((double) (result.metadata().get(Keys.LENGTH) - optimalLength));
                    avgProcessedK.set(j, avgProcessedK.get(j) + result.metadata().get(Keys.NB_PROCESSED_VERTICES) * 1. / N);
                }
            }
            for (int j = 0; j < aStarsAdmissible.size(); j++) { // print admissible results
                System.out.println("\nHeuristic " + j + ":");
                System.out.println("Average length: " + avgLength.get(j));
                System.out.println("Average processed vertices: " + avgProcessed.get(j));
                System.out.println("Improvement percentage (compared to H0): " + improvementRate.get(j));
                System.out.println("Average expansion rate: " + avgExpansionRate.get(j));
            }
            for (int j = 0; j < aStarsKManhattans.size(); j++) { // print kmanattans results
                System.out.println("\nHeuristic " + (j + aStarsAdmissible.size()) + ":");
                System.out.println("Average length: " + avgLengthK.get(j));
                System.out.println("Average processed vertices: " + avgProcessedK.get(j));
                System.out.println("Minimal Error:" + errors.get(j).stream().min(Double::compareTo).orElseThrow()); //merci poa <3
                System.out.println("Average Error:" + errors.get(j).stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                System.out.println("Maximal Error:" + errors.get(j).stream().max(Double::compareTo).orElseThrow());
                double reduction = avgProcessed.get(3) - avgProcessedK.get(j);
                System.out.println("Average processed vertices reduction (compared to H3): " + reduction);
                System.out.println("Average processed vertices reduction % (compared to H3): " + reduction / avgProcessed.get(3) * 100.0);
            }
        }
    }

    /**
     * Résultat de la méthode {@link #generateGrid}, fournit un labyrinthe et une fonction de pondération associée.
     *
     * @param maze    labyrinthe généré
     * @param weights Fonction de pondération associée
     */
    private record GenerationResult(GridGraph2D maze, PositiveWeightFunction weights) {
    }

    /**
     * Génère un labyrinthe en forme de grille avec un générateur donné et des réglages spécifiques pour le relief et
     * l'ouverture (i.e. densité de murs) du labyrinthe.
     *
     * @param generator Générateur de labyrinthe.
     * @param params    Paramètres de réglage du relief et de l'ouverture du labyrinthe.
     * @param rng       Générateur de nombres aléatoires.
     * @return Un {@link GenerationResult} contenant le labyrinthe et la fonction de pondération associée.
     */
    private static GenerationResult generateGrid(MazeGenerator generator, Experiment.Params params, RandomGenerator rng) {
        GridGraph maze = new GridGraph(SIDE);

        MazeBuilder builder = new GridMazeBuilder(TOPOLOGY, maze);
        generator.generate(builder, 0);

        MazeTuner tuner = new MazeTuner()
                .setRandomGenerator(rng)
                .setReliefDensityFactor(params.reliefDensityFactor())
                .setWallRemovalProbability(params.wallRemovalProbability())
                .setReliefRadiusRatio(params.reliefRadiusRatio())
                .setReliefSummitsPerRange(params.reliefSummitsPerRange())
                .setReliefMaxSummitWeight(params.reliefMaxSummitWeight());

        tuner.removeWalls(TOPOLOGY, maze);
        int[] weights = tuner.generateRelief(SIDE, SIDE);
        PositiveWeightFunction wf = new ShenaniganWeightFunction(weights, tuner.getReliefMinWeight());

        return new GenerationResult(maze, wf);
    }
}
