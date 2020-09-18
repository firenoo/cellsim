package firenoo.sim.env;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import firenoo.lib.misc.BiIntFunction;
import firenoo.lib.structs.MinPriorityQueue;
import firenoo.lib.data.SaveHelper;

import firenoo.sim.log.Logger;

public class Environment implements IEnvironment {

    public static final Logger LOGGER;
    static {
        File file = new File("log.txt");
        OutputStream fileOut = System.out;
        try {
            file.createNewFile();
            fileOut = new FileOutputStream(file, true);
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            LOGGER = new Logger(fileOut);
        }
    }
    //update rate in updates per second
    public static final int UPS = 1;

    public static final int MAJOR_VERSION = 0;
    public static final int MINOR_VERSION = 1;

    private int globalTime;
    private int ups;
    private ITile[][] tiles;

    private int width, height;
    private MinPriorityQueue<Runnable> process;
    private CellMoveHandler moveHandler;

    public Environment(int width, int height) {
        this(width, height, (x, y) -> {
            if(x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                return 1;
            } else {
                return 2;
            }
        });
    }
    
    public Environment(int width, int height, BiIntFunction func) {
        this.globalTime = 0;
        this.width = width;
        this.height = height;
        //Row-major order
        this.tiles = new ITile[height][width];
        this.moveHandler = new CellMoveHandler();
        this.process = new MinPriorityQueue<>((width * height - (2 * (width + height - 2))) * 2);
		init(func);
    }
	
	private void init(BiIntFunction func) {
		for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                int t = func.apply(j, i);
                if(t == 1) {
                    this.tiles[i][j] = Tile.createBlockTile(j, i);
                } else if(t == 2) {
                    this.tiles[i][j] = new Tile(0, j, i);
                }
            }
        }
    }

    @Override
    public void beginLoop() {
        Thread thread = new Thread(() -> {
            while(true) {
                try {
                    onCycleUpdate(globalTime++);
                    System.out.println(this);
                    Thread.sleep(1000 / UPS);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
    
        }, "game_thread");
        thread.start();
    }


    @Override
    public ITile[][] getTiles() {
        return tiles;
    }

    @Override
    public ITile getTile(int x, int y) {
        if(x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        } else {
            return tiles[y][x];
        }
    }

    @Override
    public ITile getTile(int x, int y, int neighbor) {
        //nswe
        switch(neighbor) {
            case 0:
                return getTile(x, y - 1);
            case 1:
                return getTile(x - 1, y);
            case 2:
                return getTile(x, y - 1);
            case 3:
                return getTile(x + 1, y);
        }
        return null;
    }

    @Override
    public ITile[] getNeighbors(int x, int y) {
        return new ITile[]{
            getTile(x, y, 0),
            getTile(x, y, 1),
            getTile(x, y, 2),
            getTile(x, y, 3)
        };
    }

    /**
     * Every cycle, this is called.
     * @param globalTime
     */
    @Override
    public void onCycleUpdate(int globalTime) {
        for(int i = 0; i < tiles.length; i++) {
            for(int j = 0; j < tiles[i].length; j++) {
                ITile tile = tiles[i][j];
                if(tile.getCell() != null) {
                    if(tile.getCell().getBehavior() != null) {
                        ITile[][] vision = getTilesInRange(j, i, tile.getCell().getBehavior().getVisionRange());
                        process.enqueue(() -> tile.getCell().getBehavior().eatEvent(vision, globalTime), tile.getX() + tile.getY() * width);
                        process.enqueue(() -> tile.getCell().getBehavior().moveEvent(vision, moveHandler, globalTime), (width * height) +  (tile.getX() + tile.getY() * width));
                    }
    
                }
            }
        }
        while(!process.isEmpty()) {
            process.dequeue().run();
        }
        moveHandler.resolveAll(tiles);
    }
    
    @Override
    public int getGlobalTime() {
        return globalTime;
    }

    
    @Override
    public ITile[][] getTilesInRange(int x, int y, int r) {
        if(r > width) r = width;
        if(r > height) r = height;
        int diameter = r * 2 + 1;
        ITile[][] result = new ITile[diameter][diameter];
        for(int i = 0; i < diameter; i++) {
            for(int j = 0; j < diameter; j++) {
                if(IEnvironment.taxicabDist(x - r + j, y - r + i, x, y) <= r) {
                    result[i][j] = getTile(x - r + j, y - r + i);
                }
            }
        }
        

        return result;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(super.toString()).append('\n');
        for(int i = 0; i < tiles.length; i++) {
            for(int j = 0; j < tiles[i].length; j++) {
                b.append(tiles[i][j].toString());
            }
            b.append('\n');
        }
        return b.toString();
    }

    @Override
    public void serialize(String fileName) {
        try(FileOutputStream stream = new FileOutputStream(fileName)) {
            LOGGER.logf("Serializing instance to %s", fileName);
            SaveHelper.writeInt(MAJOR_VERSION, stream);
            SaveHelper.writeInt(MINOR_VERSION, stream);
            SaveHelper.writeInt(width, stream);
            SaveHelper.writeInt(height, stream);
            SaveHelper.writeInt(ups, stream);
            SaveHelper.writeInt(globalTime, stream);
            for(ITile[] row : tiles) {
                for(ITile tile : row) {
                    tile.serialize(stream);
                }
            }
            SaveHelper.writeInt(process.elementCt(), stream);
            while(!process.isEmpty()) {
                int currentTileIndex = process.peekPriority();
                int processPhase = currentTileIndex >= width * height ? 1 : 0;
                int x = currentTileIndex / width;
                int y = currentTileIndex % width;
                if(processPhase == 0) {
                    SaveHelper.writeByte((byte) 1, stream);
                } else {
                    SaveHelper.writeByte((byte) 2, stream);
                }
                SaveHelper.writeInt(x, stream);
                SaveHelper.writeInt(y, stream);
                process.dequeue();                
            }
                
            
            LOGGER.logf("Serialization successful.");
        } catch(IOException e) {
            LOGGER.errorf("Serialization failed, error printed below");
            LOGGER.errorf(e.getLocalizedMessage());
            for(StackTraceElement ste : e.getStackTrace()) {
                LOGGER.errorf(ste.toString());
            }
        }
    }

    public static Environment deserialize(String fileName) throws IOException {
        FileInputStream stream = new FileInputStream(fileName);
        int majVer = SaveHelper.readInt(stream);
        int minVer = SaveHelper.readInt(stream);

        int width = SaveHelper.readInt(stream);
        int height = SaveHelper.readInt(stream);
        Environment env = new Environment(width, height);
        LOGGER.logf("Deserializing from %s", fileName);
        if(majVer != MAJOR_VERSION) {
            //warning for wrong version
            LOGGER.warn("Major version is not the same, may cause unexpected behavior.");
        }
        if(minVer != MINOR_VERSION) {
            //warning for minor version
            LOGGER.warn("Minor version is not the same, may cause unexpected behavior.");
        }
        env.ups = SaveHelper.readInt(stream);
        env.globalTime = SaveHelper.readInt(stream);        
        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                env.tiles[i][j] = Tile.deserialize(j, i, stream);
                //env.logger.logf("Loaded position %d, %d", j, i);
            }
        }
        int nextUpdate = SaveHelper.readInt(stream);
        for(int i = 0; i < nextUpdate; i++) {
            int type = stream.read();
            int x = SaveHelper.readInt(stream);
            int y = SaveHelper.readInt(stream);
            if(type == 1) {
                ITile tile = env.getTile(x, y);
                ITile[][] vision = env.getTilesInRange(x, y, tile.getCell().getBehavior().getVisionRange());
                env.process.enqueue(() -> tile.getCell().getBehavior().eatEvent(vision, env.globalTime), x + (y * width));
            } else if(type == 2) {
                ITile tile = env.getTile(x, y);
                ITile[][] vision = env.getTilesInRange(x, y, tile.getCell().getBehavior().getVisionRange());
                env.process.enqueue(() -> tile.getCell().getBehavior().moveEvent(vision, env.moveHandler, env.globalTime), x + (y * width) + (width * height));
            } else {
                LOGGER.errorf("Move process %d: Type cannot be discerned.", i);
                throw new IOException("Type cannot be discerned.");
            }
        }
        LOGGER.logf("Deserialization successful.");
        return env;
    }

}