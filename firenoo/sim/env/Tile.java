package firenoo.sim.env;

import firenoo.sim.cell.ICell;
import firenoo.sim.cell.Cell;

import firenoo.lib.buffer.DoubleBoundBuffer;
import firenoo.lib.buffer.IBoundedBuffer;
import firenoo.lib.buffer.ImmutableDoubleBuffer;
import firenoo.lib.data.SaveHelper;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.EOFException;
import java.io.IOException;

public class Tile implements ITile {
    
    //This is a constant, but can be edited manually by serialization
    public final double MAX_FOOD_ON_TILE;

    boolean isBlock;

    private int x, y;
        
    IBoundedBuffer<Double> food;

    private ICell cell;

    /**
     * Creates a non-block tile.
     * @param env  Must be nonnull. An instance of the environment this tile
     *             will be a part of.
     * @param food The amount of food to initialize this tile with. Will be
     *             clamped to appropriate min/max values.
     */
    public Tile(double food, int x, int y) {
        this(x, y, 20, food);

    }

    private Tile(int x, int y) {
        this(0d, x, y);
    }

    private Tile(int x, int y, double maxFood, double food) {
        this.MAX_FOOD_ON_TILE = maxFood;
        this.food = new DoubleBoundBuffer(0, MAX_FOOD_ON_TILE);
        this.food.set(food);        
        this.isBlock = false;
        this.x = x;
        this.y = y;
    }

    @Override
    public int onCycleUpdate(int globalTime) {
        
        return 0;
    }

    @Override
    public IBoundedBuffer<Double> food() {
        return food;
    }

    @Override
    public ICell getCell() {
        return cell;
    }

    @Override
    public ICell putCell(ICell cell) {
        ICell ret = this.cell;
        this.cell = cell;
        this.cell.moveTo(this);
        return ret;
    }

    @Override
    public boolean isBlock() {
        return isBlock;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public static Tile createBlockTile(int x, int y) {
        return new BlockTile(x, y);
    }

    public String toString() {
        if(cell == null) {
            return "T";
        } else {
            return "C";
        }
    }

    @Override
    public void serialize(OutputStream stream) throws IOException {
        if(isBlock()) {
            SaveHelper.writeByte((byte) 0, stream);
        } else {
            SaveHelper.writeByte((byte) 1, stream);
            SaveHelper.writeDouble(MAX_FOOD_ON_TILE, stream);
            SaveHelper.writeDouble(food.value(), stream);
            if(cell != null) {
                SaveHelper.writeByte((byte) 1, stream);
                cell.serialize(stream);
            } else {
                SaveHelper.writeByte((byte) 0, stream);
            }
        }
    }

    public static ITile deserialize(int x, int y, InputStream stream) throws IOException {
        int block = stream.read();
        if(block == 0) {
            return Tile.createBlockTile(x, y);
        } else if(block == 1) {
            double maxFood = SaveHelper.readDouble(stream);
            double food = SaveHelper.readDouble(stream);
            int hasCell = stream.read();
            ITile tile = new Tile(x, y, maxFood, food);
            if(hasCell == 1) {
                ICell cell = Cell.deserialize(tile, stream);
                tile.putCell(cell);
            } else if(hasCell == -1) {
                throw new EOFException(String.format("Cannot read cell. (Position: %d, %d)", x, y));
            }
            return tile;
        } else {
            throw new IOException(String.format("Cannot read cell. (Position: %d, %d)", x, y));
        }
    }

    public static class BlockTile extends Tile {

        private IBoundedBuffer<Double> food;

        private BlockTile(int x, int y) {
            super(x, y);
            this.isBlock = true;
            this.food = new ImmutableDoubleBuffer();
        }

        @Override
        public IBoundedBuffer<Double> food() {
            return this.food;
        }

        @Override
        public final int onCycleUpdate(int globalTime) {
            return 0;
        }

        @Override
        public boolean isBlock() {
            return true;
        }

        @Override
        public ICell getCell() {
            return null;
        }

        @Override
        public ICell putCell(ICell cell) {
            return null;
        }
        
        public String toString() {
            return "B";
        }

    } 

}