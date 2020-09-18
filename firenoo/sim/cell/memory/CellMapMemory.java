package firenoo.sim.cell.memory;

import firenoo.sim.env.IEnvironment;
import firenoo.sim.env.ITile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import firenoo.sim.cell.ICell;
import firenoo.lib.structs.MinPriorityQueue;
import firenoo.lib.structs.Queue;
import firenoo.lib.structs.Vec2i;

public class CellMapMemory {
    
    private static final int CHUNK_SIZE = 3;

    private ICell cell;
        
    private int replacementPolicy;

    private int memSize;
 
    private int blocks = 0;

    private final int initX, initY;

    //Mainly for quickly finding a block.
    private Map<Vec2i, Block> keyAccess;

    public CellMapMemory(ICell cell, int cycle) {
        this.cell = cell;
        this.memSize = cell.ribosome().getMemSize();
        this.replacementPolicy = cell.ribosome().getForgetOrder();
        this.keyAccess = new HashMap<>((int)(memSize * 4.0 / 3));
        this.initX = cell.getTile().getX();
        this.initY = cell.getTile().getY();
        keyAccess.put(new Vec2i(0, 0), new Block(0, 0, CHUNK_SIZE, cycle));
    }

    /**
     * Update the memory map in batch
     * @param cycle
     */
    public void updateAll(int cycle, ITile... tiles) {
        Map<Vec2i, List<ITile>> batches = new HashMap<>((int)(tiles.length * 4.0 / 3));
        Vec2i[] transforms;
        for(ITile tile : tiles) {
            transforms = transformW2B(tile.getX(), tile.getY());
            if(batches.containsKey(transforms[0])) {
                batches.get(transforms[0]).add(tile);
            } else {
                List<ITile> list = new ArrayList<>(tiles.length);
                list.add(tile);
                batches.put(transforms[0].deepCopy(), list);
            }
        }
        Block block;
        for(Map.Entry<Vec2i, List<ITile>> batch : batches.entrySet()) {
            Vec2i bPos = batch.getKey();
            block = keyAccess.get(bPos);
            List<ITile> toUpdate = batch.getValue();
            int size = toUpdate.size();
            int[] coordXs = new int[size];
            int[] coordYs = new int[size];
            double[] foods = new double[size];
            boolean[] hasCells = new boolean[size];
            if(block == null) {
                block = allocBlock(bPos.x, bPos.y, cycle);
            }
            for(int i = 0; i < size; i++) {
                ITile t = toUpdate.get(i);
                coordXs[i] = t.getX();
                coordYs[i] = t.getY();
                foods[i] = t.food().value();
                hasCells[i] = t.getCell() != null;
            }
            block.update(coordXs, coordYs, foods, hasCells, cycle);
        }
    }

    /**
     * Update the memory map
     */
    public void update(ITile tile, int cycle) {
        Vec2i[] transforms = transformW2B(tile.getX(), tile.getY());
        Block block = keyAccess.getOrDefault(transforms[0], allocBlock(transforms[0].x, transforms[0].y, cycle));
        block.update(transforms[1].x, transforms[1].y, tile.food().value(), tile.getCell() != null, cycle);
    }

    public double queryF(int x, int y, int cycle) {
        Vec2i[] transforms = transformW2B(x, y);
        return keyAccess.get(transforms[0]).getF(x, y, cycle);
    }

    public boolean queryC(int x, int y, int cycle) {
        Vec2i[] transforms = transformW2B(x, y);
        return keyAccess.get(transforms[0]).getC(x, y, cycle);
    }

    /**
     * Gets the food amount and whether a cell was spotted on the specified tile
     * @param x the absolute coordinate x (use from the world map)
     * @param y the absolute coordinate y (use from the world map)
     */
    public Object[] query2(int x, int y, int cycle) {
        Vec2i[] transforms = transformW2B(x, y);
        return keyAccess.get(transforms[0]).get(transforms[1].x, transforms[1].y, cycle);
    }

    /**
     * Transforms specified world coordinates to block coordinates, with the offset.
     * @return index 0 - block coords.
     *         index 1 - offset.
     */
    private Vec2i[] transformW2B(int x, int y) {
        x -= initX;
        y -= initY;
        Vec2i blockPos = new Vec2i(0, 0);
        Vec2i offset = new Vec2i(0, 0);
        if(x > 0) {
            blockPos.x = (x + 1) / CHUNK_SIZE;
            offset.x = x - (blockPos.x * CHUNK_SIZE);
        } else if(x < 0) {
            blockPos.x = (x - 1) / CHUNK_SIZE;
            offset.x = x + (blockPos.x * CHUNK_SIZE);
        }
        if(y > 0) {
            blockPos.y = (y + 1) / CHUNK_SIZE;
            offset.y = x - (blockPos.x * CHUNK_SIZE);
        } else if(y < 0) {
            blockPos.y = (y - 1) / CHUNK_SIZE;
            offset.y = y - (blockPos.y * CHUNK_SIZE);
        }
        return new Vec2i[] {blockPos, offset};
    }

    /**
     * Transforms block coords to absolute coords.
     * @param bx block x
     * @param by block y
     * @param ox offset x
     * @param oy offset y
     */
    private Vec2i transformB2W(int bx, int by, int ox, int oy) {
        return new Vec2i(bx * CHUNK_SIZE + ox + initX, by * CHUNK_SIZE + oy + initY);
    }

    /**
     * Queries if there is a block that stores the specified world coordinates. 
     * @param x the absolute coordinate x (use from world map)
     * @param y the absolute coordinate y (use from world map)
     * @return True iff there is a block in memory with the coordinates in storage
     */
    public boolean containsBlock(int x, int y) {
        return keyAccess.containsKey(transformW2B(x, y)[0]);
    }

    /**
     * Finds the tile position in memory that has {@code food >= minFood}, and is
     * closest to the specified coordinates. This method does not affect the memory
     * blocks.
     * "Closest" means the smallest distance, in taxicab distance.
     * @param originX absolute coordinates x (use from world map)
     * @param originY absolute coordinates y (use from world map)
     * @param minFood the minimum amount of food for a tile to be valid.
     * @param hasCell whether the tile is allowed to have a cell or not. If false, the tile
     *                must not contain a cell on it to be considered.
     * @return the absolute coordinates of the found result, or null if no result could be found.
     */
    public Vec2i findClosest(int originX, int originY, double minFood, boolean hasCell, int cycle) {
        Vec2i[] transforms = transformW2B(originX, originY);
        MinPriorityQueue<Vec2i> bPositions = new MinPriorityQueue<>(memSize);
        for(Vec2i pos : keyAccess.keySet()) {
            bPositions.enqueue(pos, IEnvironment.taxicabDist(pos, transforms[0]));
        }
        Queue<Vec2i> bfs = new Queue<>(CHUNK_SIZE * CHUNK_SIZE / 2);
        Vec2i result = null;
        Block block;
        MinPriorityQueue<Vec2i> bestFoodPos = new MinPriorityQueue<>(memSize);
        while(!bPositions.isEmpty()){
            int o_x, o_y; //init block pos
            Vec2i b = bPositions.dequeue();
            block = keyAccess.get(b);
            if(block == null) continue;
            if(transforms[0].x > b.x) {
                o_x = 1;
            } else if(transforms[0].x < b.x) {
                o_x = -1;
            } else {
                o_x = transforms[1].x;
            }

            if(transforms[0].y > b.y) {
                o_y = 1;
            } else if(transforms[0].y < b.y) {
                o_y = -1;
            } else {
                o_y = transforms[1].y;
            }
            bfs.enqueue(new Vec2i(o_x, o_y));
            while(!bfs.isEmpty()) {
                Vec2i offset = bfs.dequeue();
                Object[] toTest = block.get(offset.x, offset.y, cycle);
                if((double) toTest[0] > minFood) {
                    Vec2i realCoords = transformB2W(b.x, b.y, offset.x, offset.y);
                    bestFoodPos.enqueue(realCoords, IEnvironment.taxicabDist(originX, originY, realCoords.x, realCoords.y));
                }
            }

            if(bestFoodPos.isEmpty()) {
                
            } else {
                
            }
            
        }

        return result;
    }

    /**
     * Allocates a block to memory.
     * @param x the block coordinate x (convert first from the world map)
     * @param y the block coordinate y (convert first from the world map)
     * @return the block that was allocated
     */
    private Block allocBlock(int x, int y, int cycle) {
        if(blocks < memSize) {
            blocks++;
        } else {
            switch(replacementPolicy) {
                case 1:
                    //Latest first
                    keyAccess.entrySet().stream().max(Comparator.comparingInt(a -> a.getValue().lastUsed)).ifPresent(v -> {
                        keyAccess.remove(v.getKey());
                        Block toRemove = v.getValue();
                        if(toRemove.prevBlock[0] != null) {
                            toRemove.prevBlock[0].nextBlock[0] = null;
                        }
                        if(toRemove.prevBlock[1] != null) {
                            toRemove.prevBlock[1].nextBlock[1] = null;
                        }
                        if(toRemove.nextBlock[0] != null) {
                            toRemove.nextBlock[0].prevBlock[0] = null;
                        }
                        if(toRemove.nextBlock[1] != null) {
                            toRemove.nextBlock[1].prevBlock[1] = null;
                        }
                    });
                    break;
                case 2:
                    //Delete at random
                    Random random = cell.getRandom();
                    Optional.ofNullable(keyAccess.entrySet().stream().collect(() -> new MinPriorityQueue<Map.Entry<Vec2i, Block>>(keyAccess.size()),(queue, entry) -> {
                        queue.enqueue(entry, random.nextInt());
                    }, (queue1, queue2) -> {
                        int priority = queue2.peekPriority();
                        while(!queue2.isEmpty() && priority <  queue1.peekPriority()) {
                            queue1.enqueue(queue1.dequeue(), priority);
                            priority = queue2.peekPriority();
                        }
                    }).dequeue()).ifPresent(v -> {
                        keyAccess.remove(v.getKey());
                        Block toRemove = v.getValue();
                        if(toRemove.prevBlock[0] != null) {
                            toRemove.prevBlock[0].nextBlock[0] = null;
                        }
                        if(toRemove.prevBlock[1] != null) {
                            toRemove.prevBlock[1].nextBlock[1] = null;
                        }
                        if(toRemove.nextBlock[0] != null) {
                            toRemove.nextBlock[0].prevBlock[0] = null;
                        }
                        if(toRemove.nextBlock[1] != null) {
                            toRemove.nextBlock[1].prevBlock[1] = null;
                        }
                    });
                    break;
                default:
                    //Earliest first
                    keyAccess.entrySet().stream().min(Comparator.comparingInt(a -> a.getValue().lastUsed)).ifPresent(v -> {
                        keyAccess.remove(v.getKey());
                        Block toRemove = v.getValue();
                        if(toRemove.prevBlock[0] != null) {
                            toRemove.prevBlock[0].nextBlock[0] = null;
                        }
                        if(toRemove.prevBlock[1] != null) {
                            toRemove.prevBlock[1].nextBlock[1] = null;
                        }
                        if(toRemove.nextBlock[0] != null) {
                            toRemove.nextBlock[0].prevBlock[0] = null;
                        }
                        if(toRemove.nextBlock[1] != null) {
                            toRemove.nextBlock[1].prevBlock[1] = null;
                        }
                    });
                    break;            
            }
        }
        Vec2i temp = new Vec2i(x - 1, y);
        Block prevX = keyAccess.get(temp);
        temp.set(x, y - 1);
        Block prevY = keyAccess.get(temp);
        
        Block block = new Block(x, y, CHUNK_SIZE, cycle, new Block[]{prevX, prevY});
        keyAccess.put(new Vec2i(x, y), block);
        return block;
    }

    private static class Block {

        private double[][] memFood;
        private boolean[][] memCell;
        private int x, y;
        //2-pair
        //0 - X+
        //1 - Y+
        private Block[] prevBlock;
        //2-pair
        //0 - X-
        //1 - Y-
        private Block[] nextBlock;
        private int lastUsed;
        
        private Block(int blockX, int blockY, int chunkSize, int cycle) {
            this(blockX, blockY, chunkSize, cycle, null);
        }

        private Block(int blockX, int blockY, int chunkSize, int cycle, Block[] prev) {
            this.memFood = new double[chunkSize][chunkSize];
            this.memCell = new boolean[chunkSize][chunkSize];
            for(int i = 0; i < chunkSize; i++) {
                for(int j = 0; j < chunkSize; j++) {
                    memFood[i][j] = -1;
                    memCell[i][j] = false;
                }
            }
            this.x = blockX;
            this.y = blockY;
            if(prev == null) {
                this.prevBlock = new Block[2];
            } else {
                if(prev[0] != null) {
                    //0 - X-
                    prev[0].nextBlock[0] = this;
                }
                if(prev[1] != null) {
                    //1 - Y-
                    prev[1].nextBlock[1] = this;
                }
                this.prevBlock = prev.clone();                
            }
            this.lastUsed = cycle;
        }

        /**
         * Updates all the tiles in the block with the specified offsets. All
         * parameters should have the same number of elements. The n'th element
         * in each array corresponds with each other.
         * @param x offset x's
         * @param y offset y's
         * @param food food values
         * @param hasCell cell values
         */
        void update(int[] x, int[] y, double[] food, boolean[] hasCell, int cycle) {
            for(int i = 0; i < x.length; i++) {
                memFood[y[i]][x[i]] = food[i]; 
                memCell[y[i]][x[i]] = hasCell[i];
            }
            this.lastUsed = cycle;
        }

        /**
         * Updates the specified tile's data.
         * @param x offset x
         * @param y offset y
         */
        void update(int x, int y, double food, boolean hasCell, int cycle) {
            memFood[y][x] = food;
            memCell[y][x] = hasCell;
            this.lastUsed = cycle;
        }

        /**
         * Gets the data at the offset
         * @return index 0 - double, food.
         *         index 1 - boolean, hasCell
         */
        Object[] get(int x, int y, int cycle) {
            lastUsed = cycle;
            return new Object[] {memFood[y][x], memCell[y][x]};
        }

        double getF(int x, int y, int cycle) {
            lastUsed = cycle;
            return memFood[y][x];
        }

        boolean getC(int x, int y, int cycle) {
            lastUsed = cycle;
            return memCell[y][x];
        }

    }

}