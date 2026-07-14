/*
 * Coordinate hierarchy follows TerraFirmaCraft's region system.
 * TerraFirmaCraft-derived design is credited under EUPL-1.2 in NOTICE.md.
 */
package gregtech.worldgen.earth.region;

/**
 * Coordinate conversions used by the Earth regional generator.
 *
 * <ul>
 *     <li>1 quart = 4 blocks</li>
 *     <li>1 chunk = 16 blocks</li>
 *     <li>1 grid point = 128 blocks</li>
 *     <li>1 river partition = 384 blocks</li>
 *     <li>1 region cell = 12,288 blocks</li>
 * </ul>
 */
public final class EarthUnits {
    public static final int PARTITION_BITS = 5;
    public static final int PARTITION_BIT_MASK = (1 << PARTITION_BITS) - 1;
    public static final int PARTITION_WIDTH_IN_GRID = 3;
    public static final int CELL_WIDTH_IN_PARTITION = 1 << PARTITION_BITS;
    public static final int CELL_WIDTH_IN_GRID = CELL_WIDTH_IN_PARTITION * PARTITION_WIDTH_IN_GRID;

    public static final int REGION_RADIUS_IN_GRID = CELL_WIDTH_IN_GRID + 5;
    public static final int REGION_WIDTH_IN_GRID = 1 + 2 * REGION_RADIUS_IN_GRID;

    public static final int QUART_BITS = 2;
    public static final int GRID_BITS = 7;
    public static final int GRID_WIDTH_IN_BLOCK = 1 << GRID_BITS;
    public static final int GRID_WIDTH_IN_QUART = 1 << (GRID_BITS - QUART_BITS);

    private EarthUnits() {
    }

    public static int gridToCell(int grid) {
        return Math.floorDiv(grid, CELL_WIDTH_IN_GRID);
    }

    public static int cellToGrid(int cell) {
        return cell * CELL_WIDTH_IN_GRID;
    }

    public static int gridToPartition(int grid) {
        return Math.floorDiv(grid, PARTITION_WIDTH_IN_GRID);
    }

    public static int partitionToCell(int partition) {
        return Math.floorDiv(partition, CELL_WIDTH_IN_PARTITION);
    }

    public static int cellToPartition(int cell) {
        return cell << PARTITION_BITS;
    }

    public static int partitionToGrid(int partition) {
        return partition * PARTITION_WIDTH_IN_GRID;
    }

    public static int quartToGrid(int quart) {
        return quart >> (GRID_BITS - QUART_BITS);
    }

    public static int gridToQuart(int grid) {
        return grid << (GRID_BITS - QUART_BITS);
    }

    public static int blockToGrid(int block) {
        return block >> GRID_BITS;
    }

    public static double blockToGridExact(double block) {
        return block / GRID_WIDTH_IN_BLOCK;
    }

    public static int gridToBlock(int grid) {
        return grid << GRID_BITS;
    }
}
