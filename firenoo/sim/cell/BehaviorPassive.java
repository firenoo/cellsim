package firenoo.sim.cell;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

import firenoo.sim.cell.memory.CellMapMemory;
import firenoo.sim.env.CellMove;
import firenoo.sim.env.CellMoveHandler;
import firenoo.sim.env.IEnvironment;
import firenoo.sim.env.ITile;
import firenoo.lib.data.BitUtils;
import firenoo.lib.structs.MinPriorityQueue;
import firenoo.lib.structs.Queue;
import firenoo.lib.structs.Vec2i;

/**
 * This AI is powered primarily by the A* algorithm. This AI
 * will be "dumb" in that it doesn't have a memory and makes
 * basic decisions about its environment; there isn't anything
 * too complex about it.
*/
public class BehaviorPassive extends ACellBehavior {
    
    private Queue<NodeVertex> movePath;
    //Food positions are kept in increasing distnace from the current cell.
    private MinPriorityQueue<NodeVertex> foodPositions;
    private Random random;

    private CellMapMemory memory = null;

    private static final double MIN_FOOD = 0.1;

    public BehaviorPassive(int cycle) {
        super();
        this.cycle = cycle;
    }

    @Override
    public void setCell(ICell cell, int cycle) {
        super.setCell(cell, cycle);
        this.movePath = new Queue<>(this.visionRange * this.visionRange / 2);
        this.foodPositions = new MinPriorityQueue<>(movePath.capacity());
        this.random = new Random((cell.hashCode() + 1) << cycle);
        this.memory = new CellMapMemory(cell, cycle);
    }

    @Override
    public void eatEvent(ITile[][] vision, int cycle) {
        final ITile tile = cell.getTile();
        double maxFoodAbsorbed = cell.ribosome().getFoodAbsorption();    
        if(tile.food().value() >= MIN_FOOD) {
            //Absorb food from the environment into the cell.
            cell.food().transfer(tile.food(), maxFoodAbsorbed);
            this.state &= ~HUNGRY;
        } else {
            this.state |= HUNGRY;
        }
    }
    
    @Override
    public void digestEvent(int cycle) {
        if(cell.food().atMin()) {
            this.state |= STARVING;
        } else {
            int rationing = cell.ribosome().isRationing();
            double rationLimit = 0.2 + 0.8 * (rationing / 256.0); //max rationing is 20%.
            int ge = cell.ribosome().getGrowthEfficiency();
            int gf = cell.ribosome().getGrowthFactor();
            gf = BitUtils.hammingDist(gf, 0, 8) - 4;
            double fd = cell.ribosome().getFoodDigestion();
            fd = cell.getTile().food().max();
            double maxAbsorption;
            if(rationing == 256.0) {
                maxAbsorption = (gf * 0.1 + 1) * fd;
            } else {
                maxAbsorption = rationLimit * fd;
            }
            double amount = Math.max(cell.food().value(), maxAbsorption);
            double growth = amount * ((ge + 128.0) / 256.0f);
            cell.food().transfer(cell.growthProgress(), (int)(growth * 25));
            this.state &= ~STARVING;
        }
    }

    /**
     * The AI will only move if the tile it occupies has little/no food.
     */
    @Override
    public void moveEvent(ITile[][] vision, CellMoveHandler handler, int cycle) {
        final int x = this.cell.getTile().getX();
        final int y = this.cell.getTile().getY();
        //TODO: OPTIMIZE THIS AI CODE.
        if(this.state == HUNGRY) {
            //Check if the food positions have any 
            if(foodPositions.isEmpty()) {
                NodeVertex pos = findClosestFood(vision);
                //Proposition: movePath will always be empty here. The condition for
                //movePath to have contents is to have a target food tile, and to have
                //a target food tile means foodPositions is not empty.
                if(pos == null) {
                    wander(handler, cycle, x, y, vision);
                    return;
                }
                //Find a path to the located food, if it exists.
                SearchResult result = pathTo(pos.pos, vision, 1);
                if(result == null) {
                    wander(handler, cycle, x, y, vision);
                    return;
                }
                //Add the next move to the queue.
                handler.queueEvent(new CellMove(cycle, x, y, result.nextMove.pos.x, result.nextMove.pos.y, 4));
                //Fill our move path
                NodeVertex iter = result.nextMove;
                while(iter != null) {
                    iter = iter.target;
                    movePath.enqueue(iter);
                }
                //Add these moves at a lower priority to the move handler.
                SearchResult backups;
                for(int i = 1; i < 4; i++) {
                    backups = pathTo(pos.pos, vision, i + 1);
                    if(backups.nextMove.pos.equals(result.nextMove.pos)) {
                        break;
                    }
                    handler.queueEvent(new CellMove(cycle, x, y, backups.nextMove.pos.x, backups.nextMove.pos.y, 4 - i));
                }
                //Add the food to the food positions.
                foodPositions.enqueue(pos, result.dist);
                
            } else {
                while(!foodPositions.isEmpty()){
                    NodeVertex target = foodPositions.dequeue();
                    //This updates the path!
                    SearchResult result = pathTo(target.pos, vision, 1);
                    if(result != null) {
                        handler.queueEvent(new CellMove(cycle, x, y, result.nextMove.pos.x, result.nextMove.pos.y, 4));
                        SearchResult backups;
                        for(int i = 1; i < 4; i++) {
                            backups = pathTo(target.pos, vision, i + 1);
                            
                            if(backups.nextMove.pos.equals(result.nextMove.pos)) {
                                break;
                            }
                            handler.queueEvent(new CellMove(cycle, x, y, backups.nextMove.pos.x, backups.nextMove.pos.y, 4 - i));
                        }
                    }
                }
                if(!movePath.isEmpty()) {
                    NodeVertex next = movePath.dequeue();
                    handler.queueEvent(new CellMove(cycle, x, y, next.pos.x, next.pos.y, 4));
                } else {
                    wander(handler, cycle, x, y, vision);
                }
                
                
            }

        }
    }

    @Override
    public void splitEvent(ITile[][] vision, CellMoveHandler handler, int cycle) {
    }

    /**
     * Finds the closest unoccupied tile with food in range. BFS Search.
     * @param vision
     * @return
     */
    private NodeVertex findClosestFood(ITile[][] vision) {
        int center = this.visionRange;
        Map<ITile, NodeVertex> finished = new HashMap<>();
        Queue<NodeVertex> bfs = new Queue<>(vision.length * vision.length / 2);
        bfs.enqueue(new NodeVertex(vision[center][center], center, center, null));
        NodeVertex coords = null;
        while(!bfs.isEmpty()) {
            NodeVertex node = bfs.dequeue();
            finished.put(node.tile, node);
            // System.out.println(node.tile);
            //Found
            if(node.tile.food().value() > 0 && node.tile.getCell() == null) {
                coords = node;
                while(node != null) {
                    //change from relative coordinates -> tile coordinates
                    node.pos = new Vec2i(node.tile.getX(), node.tile.getY());
                    node = node.source;
                }
                break;
            }
            NodeVertex[] neighbors = neighbors(vision, node, finished);
            for(NodeVertex n : neighbors) {
                if(n == null) continue;
                if(!finished.containsKey(n.tile)) {
                    bfs.enqueue(n);
                }
            }
        }
        return coords;
    }

    /**
     * Finds the path distance to the position. This algorithm avoids tiles with
     * other cells, which increases path cost by {@code cellCost}. Uses A*
     * @return the path distance (cost) or -1 if no path was found
     */
    public SearchResult pathTo(Vec2i pos, ITile[][] vision, int cellCost) {
        MinPriorityQueue<NodeVertex> openSet = new MinPriorityQueue<>(vision.length * vision.length);
        int center = vision.length / 2;
        Vec2i cellPos = new Vec2i(this.cell.getTile().getX(), this.cell.getTile().getY());
        //If it is out of range...
        if(IEnvironment.taxicabDist(cellPos, pos) > visionRange) {
            return null;
        }
        openSet.enqueue(new NodeVertex(vision[center][center], center, center, 0, null), 0);
        HashMap<ITile, NodeVertex> completed = new HashMap<>();
        while(!openSet.isEmpty()) {
            NodeVertex current = openSet.dequeue();
            // System.out.printf("At %d, %d%n", current.tile.getX(), current.tile.getY());
            Vec2i currentPos = new Vec2i(current.tile.getX(), current.tile.getY());
            completed.put(current.tile, current);
            if(currentPos.equals(pos)) {
                //done, reconstruct path
                // System.out.println("Finished");
                int dist = -1;
                while(current.source.tile != vision[center][center]) {
                    // System.out.println(current.tile.getX() + ", " + current.tile.getY());
                    current = current.source;
                    current.source.target = current;
                    dist++;
                }
                current.pos = new Vec2i(current.tile.getX(), current.tile.getY());
                return new SearchResult(dist, current);
            }
            for(NodeVertex n : neighbors(vision, current, completed)) {
                if(n == null || n.tile.isBlock()) continue;
                int g = current.g + (n.tile.getCell() == null ? 1 : cellCost);
                // System.out.printf("Considering %d, %d%n", n.tile.getX(), n.tile.getY());
                //-1 denotes infinity.
                if(g < n.g || n.g == -1) {
                    n.source = current;
                    n.g = g;
                    if(!openSet.contains(n)) {
                        // System.out.println("Adding " + n.tile.getX() + ", " + n.tile.getY());
                        openSet.enqueue(n, n.g + IEnvironment.taxicabDist(n.tile.getX(), n.tile.getY(), pos.x, pos.y));
                    
                    }
                }
            }
        }
        return null;

    }

    private void wander(CellMoveHandler handler, int cycle, int x, int y, ITile[][] tiles) {
        //wander AI
        int dir;
        Set<Integer> dupe = new HashSet<>();
        int priority = 4;
        do {
            dir = random.nextInt(4);
            if(!dupe.contains(dir)) {
                if(addByDirection(handler, cycle, priority, dir, x, y, tiles)) {
                    priority--;
                }
                dupe.add(dir);
            }
        } while(dupe.size() < 4);
        movePath.clear();
        foodPositions.clear();
    }

    private boolean addByDirection(CellMoveHandler handler, int cycle, int priority, int direction, int x, int y, ITile[][] vision) {
        //nswe
        ITile tile;
        switch(direction) {
            case 0: 
                //North
                tile = vision[visionRange - 1][visionRange];
                if(tile != null && !tile.isBlock()) {
                    handler.queueEvent(new CellMove(cycle, x, y, x, y - 1, priority));
                    return true;
                }
                break;
            case 1:
                //South
                tile = vision[visionRange + 1][visionRange];
                if(tile != null && !tile.isBlock()) {
                    handler.queueEvent(new CellMove(cycle, x, y, x, y + 1, priority));
                    return true;
                }
                break;
            case 2:
                //West
                tile = vision[visionRange][visionRange - 1];
                if(tile != null && !tile.isBlock()) {
                    handler.queueEvent(new CellMove(cycle, x, y, x - 1, y, priority));
                    return true;
                }
                break;
            case 3:
                //East
                tile = vision[visionRange][visionRange + 1];
                if(tile != null && !tile.isBlock()) {
                    handler.queueEvent(new CellMove(cycle, x, y,  x + 1, y, priority));
                    return true;
                }
                break;
            default:
                throw new IllegalArgumentException("Direction must be an integer from 0 to 3!");
        }
        return false;
    }
    
    private NodeVertex[] neighbors(ITile[][] tiles, NodeVertex pos, Map<ITile, NodeVertex> set) {
        ITile north = null;
        ITile west = null;
        ITile south = null;
        ITile east = null;
        int x = pos.x();
        int y = pos.y();
        if(pos.y() > 0) {
            north = tiles[y - 1][x];
            
        }
        if(y < tiles.length) {
            south = tiles[y + 1][x];
        }
        if(pos.x() > 0) {
            west = tiles[y][x - 1];
        }
        if(pos.x() < tiles[y].length) {
            east = tiles[y][x + 1];
        }
        return new NodeVertex[] {
            set.getOrDefault(north, NodeVertex.from(north, x, y-1, -1, pos)),
            set.getOrDefault(south, NodeVertex.from(south, x, y+1, -1, pos)),
            set.getOrDefault(west, NodeVertex.from(west, x-1, y, -1, pos)),
            set.getOrDefault(east, NodeVertex.from(east, x+1, y, -1, pos))       
        };
    }
    
    private static class NodeVertex {
        ITile tile;
        Vec2i pos;
        NodeVertex source, target;
        //current score
        int g;

        private NodeVertex(ITile tile, int x, int y, NodeVertex source) {
            this(tile, x, y, -1, source);
        }

        private NodeVertex(ITile tile, int x, int y, int g, NodeVertex source) {
            this.tile = tile;
            this.pos = new Vec2i(x, y);
            this.source = source;
            this.g = g;
        }

        private static NodeVertex from(ITile tile, int x, int y, int g, NodeVertex source) {
            if(tile != null) {
                return new NodeVertex(tile, x, y, g, source);
            } else {
                return null;
            }
        }
        
        int x() {
            return pos.x;
        }

        int y() {
            return pos.y;
        }
    }

    static class SearchResult {
        int dist;
        NodeVertex nextMove;

        SearchResult(int dist, NodeVertex nextMove) {
            this.dist = dist;
            this.nextMove = nextMove;
        }
    }
}