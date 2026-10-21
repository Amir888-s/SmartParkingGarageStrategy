package parking.pricing;

/** Mutable simulated sensor for repeatable demonstrations. */
public final class SimulatedOccupancySource implements OccupancySource {
    private double value;
    public SimulatedOccupancySource(double value) { setOccupancy(value); }
    public void setOccupancy(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Occupancy must be between zero and one");
        this.value = value;
    }
    @Override public double occupancy() { return value; }
}
