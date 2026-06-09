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

    private static final double[] K_MANHATTANS = {0, 0.5, 1, 2, 4, 6, 8};

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
        ArrayList<AStar> aStars = new ArrayList<>(AStar.Heuristic.values().length);
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
            aStars.add(new AStar(AStar.Heuristic.values()[i]));
        }
        for (double mult : K_MANHATTANS) { // adds different versions of k-manhattan
            aStars.add(new AStar(AStar.Heuristic.K_MANHATTAN, mult));
        }
        //storage for results might print right away maybe?
        ArrayList<ArrayList<Double>> allAvgLengths = new ArrayList<>(EXPERIMENTS.length);
        ArrayList<ArrayList<Double>> allAvgProcessed = new ArrayList<>(EXPERIMENTS.length);
        ArrayList<ArrayList<Double>> allImprovementRates = new ArrayList<>(EXPERIMENTS.length);
        for (Params experiment : EXPERIMENTS) {
            ArrayList<Double> avgLength = new ArrayList<>(aStars.size());
            ArrayList<Double> avgProcessed = new ArrayList<>(aStars.size());
            ArrayList<Double> improvementRate = new ArrayList<>(aStars.size());
            for (int i = 0; i < aStars.size(); i++) {
                avgLength.add(0.);
                avgProcessed.add(0.);
            }
            for (int i = 0; i < N; i++) {
                GenerationResult generation = generateGrid(DFS, experiment, rng);
                for (int j = 0; j < aStars.size(); j++) {
                    MazeSolver.Result result = aStars.get(j).solve(generation.maze, generation.weights, SRC, DST, mockView);
                    avgLength.set(j, avgLength.get(j) + result.metadata().get(Keys.LENGTH) * 1. / N);
                    avgProcessed.set(j, avgProcessed.get(j) + result.metadata().get(Keys.NB_PROCESSED_VERTICES) * 1. / N);
                    if (j == 0) {
                        improvementRate.add(1.);
                    } else {
                        improvementRate.add(avgLength.get(0) / avgLength.get(j));
                    }
                }
            }
            allAvgLengths.add(avgLength);
            allAvgProcessed.add(avgProcessed);
            allImprovementRates.add(improvementRate);
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
