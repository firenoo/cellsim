package firenoo.sim.cell;

public abstract class ACellBehavior implements ICellBehavior {
    
    protected ICell cell;
    /**
     * Indicates the current (food) state of the cell. This is a bit field.
     */
    protected int state;
    protected int visionRange;
    protected int cycle;

    public ACellBehavior() {
        this.state = ICellBehavior.STABLE;
    }

    @Override
    public void setCell(ICell cell, int cycle) {
        this.cell = cell;
        this.visionRange = cell.ribosome().getVisionRange();
    }

    @Override
    public ICell getCell() {
        return cell;
    }

    @Override
    public boolean isSplitReady() {
        return cell.level() >= ICell.MAX_LEVEL;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public int getVisionRange() {
        return visionRange;
    }

}