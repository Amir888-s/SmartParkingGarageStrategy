package parking.pricing;

/** Replaceable sensor boundary; occupancy is a fraction from zero to one. */
@FunctionalInterface
public interface OccupancySource { double occupancy(); }
