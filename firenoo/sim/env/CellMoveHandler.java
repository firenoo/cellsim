package firenoo.sim.env;

import firenoo.sim.cell.ICell;

import firenoo.lib.structs.DirectedWeightedGraph;
import firenoo.lib.structs.MinPriorityQueue;
import firenoo.lib.structs.Vec2i;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class CellMoveHandler {


    private boolean isIdle;

    private DirectedWeightedGraph<Vec2i> process;
    private CellMoveGraph graph;

    public static final int MAX_EDGE_WEIGHT = 4;

    public CellMoveHandler() {
        this.process = new DirectedWeightedGraph<>();
    }

    public void queueEvent(CellMove action) {
        if(IEnvironment.taxicabDist(action.getPosX(), action.getPosY(), action.getTargetPosX(), action.getTargetPosY()) == 1) {
            // System.out.printf("Adding action %d, %d -> %d, %d, %d%n", action.getPosX(), action.getPosY(), action.getTargetPosX(), action.getTargetPosY(), action.getPriority());
            
            process.addEdge(new Vec2i(action.getPosX(), action.getPosY()), new Vec2i(action.getTargetPosX(), action.getTargetPosY()), action.getPriority(), true);
        } else throw new IllegalArgumentException("Move positions are invalid - not adjacent!");
    }

    public boolean queryProcessingState() {
        return isIdle;
    }

    public void resolveAll(ITile[][] tile) {
        this.isIdle = false;
        graph = new CellMoveGraph(process);
        graph.resolve();
        System.out.println(graph);

        DirectedWeightedGraph<Vec2i> moveResults = graph.getResult().deepCopy().reverseEdges();
        DirectedWeightedGraph<Vec2i>.NodeTraverser traverser = moveResults.bfs();
        Vec2i prevPos = traverser.nextTarget();
        while(traverser.hasNext()) {
            Vec2i pos = traverser.nextTarget();
            ICell cell = tile[pos.y][pos.x].getCell();
            tile[prevPos.y][prevPos.x].putCell(cell);
            tile[pos.y][pos.x].putCell(null);
            prevPos = pos;
        }
        this.process = new DirectedWeightedGraph<>();
        this.isIdle = true;
    }
   
    /**
     * This class provides the methods for resolving cell moves. It does not figure
     * out cell splits, which has an additional constraint.
     * It finds an answer the question:
     * Given any directed weighted graph with at least one edge incident on two unique
     * vertices, is there a way to remove the edges such that only vertices of indegree
     * 0 or 1 remain?
     */
    public static class CellMoveGraph extends DirectedWeightedGraph<Vec2i> {
        
        //Mark offset - weight (Nodes, Source)
        public static final int WEIGHT_OFS = 4;
        public static final int WEIGHT_SIZE = 2;

        //Mark offset - effective weight (Edges, Source)
        public static final int EFWMIN_OFS = 6;
        public static final int EFWMIN_SIZE = 1;

        //Mark offset - In result (Edges, Source)
        public static final int INRESULT_OFS = 7;
        public static final int INRESULT_SIZE = 1;


        
        //THis will be null
        private DirectedWeightedGraph<Vec2i> lastResult;
        
        private boolean isFinished = false;

        public CellMoveGraph(DirectedWeightedGraph<Vec2i> graph) {
            this.vertices.putAll(graph.getVMap());
        }

        // Comments are not meant for other people but for myself haha - see you in 6 months
        // (6/9/2020)
        // I already forgot what most of this does
        // (8/18/2020)
        /**
         * Resolves the graph to acceptable format.
         */
        public void resolve() {
            isFinished = false;
            DirectedWeightedGraph<Vec2i> result = new DirectedWeightedGraph<>();
            MinPriorityQueue<GraphNode> process = new MinPriorityQueue<>(size());
            vertices.forEach((vert, node) -> {
                result.addVertex(vert);
                if(node.edges().elementCt() > 0) {
                    process.enqueue(node, node.edges().elementCt());
                }
            });
            GraphNode node;
            while(!process.isEmpty()) {
                node = process.dequeue();
                GraphEdge toAdd;
                //Start with the highest weight
                AtomicInteger nextWeight = new AtomicInteger(MAX_EDGE_WEIGHT);
                do {
                    //Find next lowest weight
                    try {
                        toAdd = node.edges()
                                    .stream()
                                    .filter(edge -> edge.weight() == nextWeight.get())
                                    .findAny()
                                    .get();
                    } catch(NoSuchElementException e) {
                        //Give up if no edge can be found
                        break;
                    }
                    //Add the next edge
                    if(result.getVMap().get(toAdd.getTarget().getObject()).indegree() == 0) {
                        toAdd = result.addEdge(node.getObject(), toAdd.getTarget().getObject(), toAdd.weight(), true);
                    }
                    nextWeight.decrementAndGet();
                } while(toAdd.getTarget().indegree() != 0);
            }
            lastResult = result;
            isFinished = true;
        }


        public DirectedWeightedGraph<Vec2i> getResult() {
            return lastResult;
        }

        public boolean isResultFinished() {
            return isFinished;
        }
    }
    
}