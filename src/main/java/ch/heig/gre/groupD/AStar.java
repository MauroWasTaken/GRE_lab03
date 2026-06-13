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

  public static int[] getCordDelta(int source, int target, int minweigth,int gridWidth){
    int[] sourceCords = AStar.getCord(source,gridWidth);
    int[] targetCords = AStar.getCord(target,gridWidth);
    targetCords[0]=Math.abs(targetCords[0]-sourceCords[0]) * minweigth;
    targetCords[1]=Math.abs(targetCords[1]-sourceCords[1])* minweigth;
    return targetCords;
  }

  public enum Heuristic {
    DIJKSTRA{
      @Override
      int calc(final int source, final int target,final GridGraph2D grid,int minWeigth,double k) {
        return 0;
      }
    },
    INFINITY_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid,int minWeigth,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,minWeigth,grid.width());
        return Math.max(
                Math.abs(cordDelta[0]),Math.abs(cordDelta[1])
        );
      }
    },
    EUCLIDEAN_NORM{
      @Override
      int calc(int source, int target, GridGraph2D grid,int minWeigth,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,minWeigth,grid.width());
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
      int calc(int source, int target, GridGraph2D grid,int minWeigth,double k) {
        int[] cordDelta = AStar.getCordDelta(source,target,minWeigth,grid.width());
        return (cordDelta[0]) + (cordDelta[1]);
      }
    },
    K_MANHATTAN{
      @Override
      int calc(int source, int target, GridGraph2D grid,int minWeigth,double k) {
        return (int)Math.round(MANHATTAN.calc(source,target,grid,minWeigth,k)*k);
      }
    };

    abstract int calc(final int source, final int target,final GridGraph2D grid,int minWeigth,double k);

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
    int[] watingLists=new int[grid.nbVertices()];
    int priorityQueueLength=0;

    for (int i = 0; i < grid.nbVertices(); i++) {
      v_distance[i]=Integer.MAX_VALUE;
      v_heristic[i]=Integer.MAX_VALUE;
    }


    v_inQueue[actualVertex]=true;
    v_heristic[actualVertex]=this.heuristic.calc(actualVertex, destination, grid,weights.minWeight(), this.kManhattan);;
    v_distance[actualVertex]=0;
    watingLists[priorityQueueLength]=source;
    v_inQueue[priorityQueueLength]=true;
    priorityQueueLength++;

    while (priorityQueueLength>0){

      // get next vertex by priority (the smallest distance + heristic)
      int minVertexindex = 0;
      for (int i = 0; i < priorityQueueLength; i++) {
          int a = watingLists[minVertexindex];
          int b = watingLists[i];

          // compare distance + heristic
          if ((v_distance[b] + v_heristic[b]) < (v_distance[a] + v_heristic[a])) {
              minVertexindex=i;
          }
      }

      // remove vertex from the waiting lists
      actualVertex=watingLists[minVertexindex];
      priorityQueueLength--;
      // earse trick, since our array dosn't need to be sorted we can do that
      watingLists[minVertexindex]=watingLists[priorityQueueLength];
      v_inQueue[actualVertex]=false;

      distances.setLabel(actualVertex, v_distance[actualVertex]);

      if(actualVertex==destination){
        break;
      }

      for (int other : grid.neighbors(actualVertex)) {
          int c = weights.get(other,actualVertex);

          // is distance
          if (v_distance[other] > (v_distance[actualVertex] + c)) {
              if (v_distance[other] == Integer.MAX_VALUE) {
                  // cal heristic
                  v_heristic[other] = this.heuristic.calc(other, destination, grid, weights.minWeight(),this.kManhattan);
              }
              v_distance[other] = v_distance[actualVertex] + c;
              v_pred[other] = actualVertex;

              // add to prio queu if isn't
              if (!v_inQueue[other]) {
                  watingLists[priorityQueueLength] = other;
                  v_inQueue[other] = true;
                  priorityQueueLength++;
              }
          }
      }
    }

    List<Integer> path = new ArrayList<>();
    int v = destination;
    while (v!=source){
      path.add(v);
      v=v_pred[v];
    }

    // TODO META DATA
    Metadata metadata = new Metadata();
    metadata.put(Keys.LENGTH, path.size());
    metadata.put(Keys.NB_PROCESSED_VERTICES, 0);

    return new Result(path.reversed(), metadata);
  }
}