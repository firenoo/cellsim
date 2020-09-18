package firenoo.sim.env;

import firenoo.lib.structs.Vec2i;
/**
 * Represents the environment in which the cells live in.
 */
public interface IEnvironment {

    /**
     * Gets all tiles contained in the environment. None of the objects
     * should be null in the array.
     */
    ITile[][] getTiles();

    /**
     * Gets the neighbors of the tile at the specified position.
     * The array contains 4 tiles in the following order: North, West,
     * South, East. If the position is on a corner/border, the invalid
     * tiles are null instead.
     */
    ITile[] getNeighbors(int x, int y);

    /**
     * Gets the tile at the specified position.
     * @param pos
     * @return
     */
    ITile getTile(int x, int y);

    /**
     * Gets the tile to the north, west, south, or east of the indicated
     * position.
     * @param x         x coordinate in [0, width)
     * @param y         y coordinate in [0, height)
     * @param neighbor  0 - North, 1 - West, 2 - South, 3 - East
     * @return The tile, or NULL if the request is out of bounds.
     */
    ITile getTile(int x, int y, int neighbor);

    /**
     * Creates a ((r * 2) + 1) ^ 2 sized array that contains the
     * tiles around the (x, y) coordinates. NOTE: If r > width or r > height,
     * r is clamped appropriately. Thus, the result array cannot have a size greater than
     * the source array size. Elements in this array are nonnull iff the 
     * taxicab distance between a tile and the parameter coordinates are 
     * less than or equal to r, and the position is in bounds. The array is stored as
     * ITile[y][x].
     * The returned array can be considered a "view" into the source tile array;
     * positions are preserved and changes to the tiles are reflected in the source
     * array.
     * @param x
     * @param y
     * @param r
     * @return
     */
    ITile[][] getTilesInRange(int x, int y, int r);

    /**
     * The number of cycles since the environment's initialization.
     */
    int getGlobalTime();
 
    static int taxicabDist(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }
    
    static int taxicabDist(Vec2i posA, Vec2i posB) {
        return taxicabDist(posA.x, posA.y, posB.x, posB.y);
    }

    void beginLoop();

    void onCycleUpdate(int globalTime);

    void serialize(String fileName);
}