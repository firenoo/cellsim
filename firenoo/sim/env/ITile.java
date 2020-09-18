package firenoo.sim.env;

import java.io.OutputStream;
import java.io.IOException;

import firenoo.sim.cell.ICell;
import firenoo.lib.buffer.IBoundedBuffer;


/**
 * Represents a tile in the environmental grid.
 */
public interface ITile {

    /**
     * Called every cycle by the Environment.
     * @param globalTime The number of cycles since the start of the 
     *                   simulation environment.
     * @return Should always return 0.
     */
    int onCycleUpdate(int globalTime);

    /**
     * Get the food on the tile. Always 0 or greater. Food values on block
     * tiles are always treated as 0.
     */
    IBoundedBuffer<Double> food();

    /**
     * Gets the cell on this tile.
     * @return The cell that exists on this tile or null if no cell exists.
     */
	ICell getCell();
    
    /**
     * Puts the cell on this tile. Overrides the existing cell.
     * @param cell The cell to put on this tile.
     * @return the cell that was replaced, or null if no cell existed prior.
     */
    ICell putCell(ICell cell);

    /**
     * Whether or not this tile is an empty block. If this method returns true,
     * calling onCycleUpdate(int) does nothing, food() always returns an
     * immutable buffer with value 0, and getCell() always returns null.
     * @return
     */
    boolean isBlock();

    int getX();
    
    int getY();

    void serialize(OutputStream stream) throws IOException;

}