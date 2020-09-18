package firenoo.sim.cell;

import firenoo.sim.env.CellMoveHandler;
import firenoo.sim.env.ITile;

public interface ICellBehavior {


    /**
     * Indicates that the cell currently has no abnormal food situations.
     */
    static final int STABLE = 0;
    /**
     * Indicates that the cell is wandering.
     */
    static final int WANDERING = 1;
    /**
     * Indicates that the cell currently has no food on its tile.
     */
    static final int HUNGRY = 2;
    /**
     * Indicates that the cell currently has no food in its internal buffer.
     */
    static final int STARVING = 4;

    void setCell(ICell cell, int cycle);

    ICell getCell();

    /**
     * Called during the eat phase (phase 2), which allows behaviors to
     * simulate absorption of food from the environment. Cells are responsible
     * for subtracting the appropriate amount of food from the tile.
     * @param vision
     */
    void eatEvent(ITile[][] vision, int cycle);

    /**
     * Called during the digest phase (phase 3). Cells should use the food in
     * their buffer to grow during this event. If the cell cannot grow, the cell
     * should add one to their starvation counter.
     * @param cycle the current update cycle
     * @return the amount of food that was digested.
     */
    void digestEvent(int cycle);

    /**
     * Called during the move phase (phase 1), which allows behaviors to 
     * make move requests (simulate moving).
     * Moves and splits cannot occur in the same turn.
     * @param vision the tiles that the cell can "see". This is represented
     *               by a 2-D array. A null field represents tiles outside
     *               the cell's vision. A special BlockTile is used for the walls.
     */
    void moveEvent(ITile[][] vision, CellMoveHandler bus, int cycle);

    /**
     * Called during the split phase (phase 4), which allows cells to create
     * offspring.
     * Moves and splits cannot occur in the same turn.
     * @param vision the tiles that the cell can "see".
     */
    void splitEvent(ITile[][] vision, CellMoveHandler bus, int cycle);

    /**
     * Get the current behavior state. One of
     * <p> 0 = STABLE (no movement) <p/>
     * <p> 1 = WANDERING (random movement) <p/>
     * <p> 2 = HUNGRY (dedicated movement) <p/>
     */
    int getState();

    /**
     * Helper method for getting the vision range.
     */
    int getVisionRange();

    boolean isSplitReady();

}