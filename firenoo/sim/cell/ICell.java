package firenoo.sim.cell;

import java.io.OutputStream;
import java.util.Random;
import java.io.IOException;

import firenoo.dna.IDna;

import firenoo.lib.buffer.IBoundedBuffer;

import firenoo.sim.env.ITile;
/**
 * Represents a cell on a grid. Stores the following:
 * <ul>
 *  <li>Age</li>
 *  <li>Level</li>
 *  <li>DNA</li>
 *  <li>Food</li>
 * </ul>
 * It will grow and try to split when it reaches maximum level.
 * It will die if it runs out of food and starves.
 * It will attack other cells if necessary (for food).
 */
public interface ICell {

    /**
     * @return Age of the cell
     */
    int age();

    /**
     * @return Level of the cell
     */
    int level();

    /**
     * @return Amount of food the cell has internally.
     */
    IBoundedBuffer<Double> food();

    /**
     * @return Growth Progress to the next level
     */
    IBoundedBuffer<Integer> growthProgress();

    /**
     * @return Dna sequence of the cell
     */
    IDna dna();

    /**
     * @return Ribosome of the cell.
     */
    IRibosome ribosome();

    /**
     * Changes the Ribosome of the cell.
     */
    void setRibosome(IRibosome rna);
 
    /**
     * Move the cell to the specified tile. Called to update references.
     */
    void moveTo(ITile tile);



    /**
     * This is a method that is called before any eatEvents, moveEvents,
     * and splitEvents. (Don't put eating, moving, and splitting here....)
     * @param globalTime
     * @return
     */
    int onCycleUpdate(int globalTime);

    ICellBehavior getBehavior();

    ITile getTile();

    void setStarveCounter(int value);

    int getStarveCounter();

    void serialize(OutputStream stream) throws IOException;

    Random getRandom();

    static int MAX_LEVEL = 10;

}