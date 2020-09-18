package firenoo.sim.env;

public class CellMove {
    
    public static final int CELL_MOVE = 1;
    public static final int CELL_SPLIT = 2;

    private int time;
    private int x, y;
    private int targetX, targetY;
    private int priority;

    public CellMove(int time, int x, int y, int targetX, int targetY, int priority) {
        this.time = time;
        this.x = x;
        this.y = y;
        this.targetX = targetX;
        this.targetY = targetY;
        this.priority = priority;
    }

    public int timestamp() {
        return time;
    }

    public int getPosX() {
        return x;
    }

    public int getPosY() {
        return y;
    }

    public int getTargetPosX() {
        return targetX;
    }
    
    public int getTargetPosY() {
        return targetY;
    }
    
    public int getPriority() {
        return priority;
    }
}