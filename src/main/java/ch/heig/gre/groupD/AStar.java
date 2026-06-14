package ch.heig.gre.groupD;

import ch.heig.gre.Keys;
import ch.heig.gre.graph.GridGraph2D;
import ch.heig.gre.graph.PositiveWeightFunction;
import ch.heig.gre.graph.VertexLabelling;
import ch.heig.gre.maze.MazeSolver;
import ch.heig.gre.maze.Metadata;
import com.sun.security.jgss.GSSUtil;
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
    return result;
  }

  /**
   * Get the delta of x and y cord between the source vertex and the target vertex
   * @param source source vertex
   * @param target target vertex
   * @param gridWidth grid width
   * @return [x delta, y delta]
   */
  public static int[] getCordDelta(int source, int target,int gridWidth){
    int[] sourceCords = AStar.getCord(source,gridWidth);
    int[] targetCords = AStar.getCord(target,gridWidth);
    targetCords[0]=Math.abs(targetCords[0]-sourceCords[0]);
    targetCords[1]=Math.abs(targetCords[1]-sourceCords[1]);
    return targetCords;
  }

  public enum Heuristic {
    DIJKSTRA{
      @Override
      int calc(final int source, final int target, final GridGraph2D grid, int minWeigh, double k) {
        return 0;
      }
    },
    INFINITY_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid, int minWeigh, double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return minWeigh * Math.max(
                Math.abs(cordDelta[0]),Math.abs(cordDelta[1])
        );
      }
    },
    EUCLIDEAN_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid, int minWeigh, double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return minWeigh * (int)Math.round(
            Math.sqrt(
                    Math.pow(cordDelta[0],2) +
                            Math.pow(cordDelta[1],2)
            )
        );
      }
    },
    MANHATTAN{
      @Override
      int calc(int source, int target, GridGraph2D grid, int minWeigh, double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,grid.width());
        return minWeigh *  (cordDelta[0]) + (cordDelta[1]);
      }
    },
    K_MANHATTAN{
      @Override
      int calc(int source, int target, GridGraph2D grid, int minWeigh, double k) {
        return (int)Math.round(MANHATTAN.calc(source,target,grid, minWeigh,k)*k);
      }
    };

    /**
     *
     * @param source source vertex
     * @param target target vertex
     * @param grid Grid
     * @param minWeigh min weigh of edge
     * @param k k scale (use only of Manhattan k)
     * @return heristic
     */
    abstract int calc(final int source, final int target,final GridGraph2D grid,int minWeigh,double k);
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

    boolean[] v_inQueue= new boolean[grid.nbVertices()];    // track if a given vertex is in the queue or not

    int nVertexCompute=0;

    PriorityQueue<Integer> priorityQueue = new PriorityQueue<Integer>(
            grid.nbVertices(),
            Comparator.comparingInt(a -> (v_distance[a] + v_heristic[a]))
    );


    // init
    int actualVertex = source;
    for (int i = 0; i < grid.nbVertices(); i++) {
      v_distance[i]=Integer.MAX_VALUE;
      v_heristic[i]=Integer.MAX_VALUE;
    }


    // add source into the priority queue
    v_heristic[actualVertex]=this.heuristic.calc(actualVertex, destination, grid,weights.minWeight(), this.kManhattan);
    v_distance[actualVertex]=0;
    priorityQueue.add(actualVertex);
    v_inQueue[actualVertex]=true;

    while (!priorityQueue.isEmpty()){

      // get actual vertex, since we can have multiple version of the same vertex
      // we just need to check if  vertex is supposed to be in the queue
      // priority can only been reduced, so we only care if they poll a vertex, and it's as been all ready
      // consume, in that case we jus skip, the cool side reside in the complexity of polling, which
      // is O(1), there for, theses extra n polling we dose to clean the heap are pretty cheap
      // the number of wrong pulling depend on the number of updated vertex making about 1-3 wrong pulling
      // pare pull in average. All things conciliar, we fought it was a good solution
      do{
        actualVertex = priorityQueue.poll();
      }while (!v_inQueue[actualVertex] && !priorityQueue.isEmpty());

      if(!v_inQueue[actualVertex])continue;

      v_inQueue[actualVertex]=false;

      distances.setLabel(actualVertex, 1);

      nVertexCompute++;

      // if destination -> end
      if(actualVertex==destination){
        break;
      }

      for (int other : grid.neighbors(actualVertex)) {
        int c = weights.get(other,actualVertex);

        // compute new distance of the other vertex
        int newDistance = (v_distance[actualVertex] + c);

        // if new distance shorter than the actual update
        if (v_distance[other] > newDistance) {

          // if the distance was unset compute the heristic
          if (v_distance[other] == Integer.MAX_VALUE) {
            // compute heristic
            v_heristic[other] = this.heuristic.calc(other, destination, grid, weights.minWeight(),this.kManhattan);
          }
          v_distance[other] = newDistance;
          v_pred[other] = actualVertex;

          // add into priot queue / update, since we track if a vertex is in the queue or not, we can simple add the vertex without update it
          // to keep the addtion in the O(Log N)
          priorityQueue.add(other);
          v_inQueue[other]=true;
        }
      }
    }

    // compute path
    List<Integer> path = new ArrayList<>(grid.nbVertices());
    int v = destination;
    int pathLength = 0;
    while (v!=source){
      pathLength+=weights.get(v,v_pred[v]);
      path.add(v);
      v=v_pred[v];
    }

    // meta data
    Metadata metadata = new Metadata();
    metadata.put(Keys.LENGTH, pathLength);
    metadata.put(Keys.NB_PROCESSED_VERTICES, nVertexCompute);

    // reverse to get the path in the correct order
    return new Result(path.reversed(), metadata);
  }
}