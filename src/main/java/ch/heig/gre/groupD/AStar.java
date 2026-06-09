package ch.heig.gre.groupD;

import ch.heig.gre.Keys;
import ch.heig.gre.graph.GridGraph2D;
import ch.heig.gre.graph.PositiveWeightFunction;
import ch.heig.gre.graph.VertexLabelling;
import ch.heig.gre.maze.MazeSolver;
import ch.heig.gre.maze.Metadata;
import com.sun.tools.jconsole.JConsoleContext;

import java.util.*;



public final class AStar implements MazeSolver {

  /**
   * Get a vertex x and y cord in a grid
   * @param source vertex
   * @param gridWidth grid width
   * @return [x pose, y pose]
   */
  public static int[] getCord(int source, int gridWidth){
    int[] result = new int[2];
    result[0] = source%gridWidth;
    result[1] = (int)((source) / gridWidth);
    System.out.println(result[0]+", "+result[1]);
    return result;
  }

  public static int[] getCordDelta(int source, int target, int gridWidth){
    int[] sourceCords = AStar.getCord(source,gridWidth);
    int[] targetCords = AStar.getCord(target,gridWidth);
    targetCords[0]-=sourceCords[0];
    targetCords[1]-=sourceCords[1];
    return targetCords;
  }

  public enum Heuristic {
    DIJKSTRA{
      @Override
      int calc(final int source, final int target,final GridGraph2D grid,double k) {
        return 0;
      }
    },
    INFINITY_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return Math.max(
                Math.abs(cordDelta[0]),Math.abs(cordDelta[1])
        );
      }
    },
    EUCLIDEAN_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return (int)Math.round(
            Math.sqrt(
                    Math.pow(cordDelta[0],2) +
                            Math.pow(cordDelta[1],2)
            )
        );
      }
    },
    MANHATTAN{
      @Override
      int calc(int source, int target, GridGraph2D grid,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return (cordDelta[0]) + (cordDelta[1]);
      }
    },
    K_MANHATTAN{
      @Override
      int calc(int source, int target, GridGraph2D grid,double k) {
        return (int)Math.round(MANHATTAN.calc(source,target,grid,k)*k);
      }
    };

    abstract int calc(final int source, final int target,final GridGraph2D grid,double k);

  }

  /** Heuristique utilisée pour l'algorithme A*. */
  private final Heuristic heuristic;

  /** Facteur multiplicatif de la distance de Manhattan utilisé par l'heuristique K-Manhattan. */
  private final double kManhattan;

  public AStar(Heuristic heuristic) {
    this(heuristic, 1);
  }

  public AStar(Heuristic heuristic, double kManhattan) {
    this.heuristic = heuristic;
    this.kManhattan = kManhattan;
  }

  @Override
  public Result solve(final GridGraph2D grid,
                      final PositiveWeightFunction weights,
                      final int source,
                      final int destination,
                      final VertexLabelling<Integer> distances) {
    if (source < 0 || source >= grid.nbVertices()
        || destination < 0 || destination >= grid.nbVertices()) {
      throw new IllegalArgumentException("Source or destination vertex id is out of bounds");
    }

    int[] v_distance= new int[grid.nbVertices()];
    int[] v_pred= new int[grid.nbVertices()];
    int[] v_heristic= new int[grid.nbVertices()];
    boolean[] v_inQueue= new boolean[grid.nbVertices()];


    int actualVertex = source;

    distances.setLabel(actualVertex, 0);



    // TODO : check if it's the good chose or not :[
    Queue<Integer> priorityQueue = new PriorityQueue<>(Comparator.comparingInt(a -> (v_distance[a]+v_heristic[a])));

    for (int i = 0; i < grid.nbVertices(); i++) {
      v_distance[i]=Integer.MAX_VALUE;
      v_heristic[i]=Integer.MAX_VALUE;
    }


    v_inQueue[actualVertex]=true;
    v_heristic[actualVertex]=0;
    v_distance[actualVertex]=0;
    priorityQueue.add(actualVertex);

    while (!priorityQueue.isEmpty()){
      // get next vertex
      actualVertex = priorityQueue.poll();
      v_inQueue[actualVertex]=false;

      if(actualVertex==destination){
        break;
      }

      int[] neightbore = grid.neighbors(actualVertex);
      for (int i = 0; i < neightbore.length; i++) {
        int other = neightbore[i];
        int c = weights.get(actualVertex,other);

        // is distance
        if(v_distance[other] > (v_distance[actualVertex] + c)){
          if(v_distance[other]==Integer.MAX_VALUE){
            // cal heristic
            v_heristic[other]=this.heuristic.calc(other,destination,grid,this.kManhattan);
          }
          v_distance[other] = v_distance[actualVertex] + c;
          v_pred[other] = actualVertex;

          distances.setLabel(other, v_distance[other]);

          // update prio
          if(v_inQueue[other]){
            priorityQueue.remove(other);
          }
          v_inQueue[other]=true;
          priorityQueue.add(other);
          if(priorityQueue.size()==6){
            System.out.println("test");
          }
        }
      }
    }

    System.out.println("dkskdlas");
    // TODO
    //throw new UnsupportedOperationException("Not yet implemented");
    Metadata metadata = new Metadata();
    metadata.put(Keys.LENGTH, 0);
    metadata.put(Keys.NB_PROCESSED_VERTICES, 0);

    return new Result(new ArrayList<>(), metadata);
  }
}