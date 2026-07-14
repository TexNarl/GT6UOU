/*
 * River edge geometry follows TerraFirmaCraft's EUPL-1.2 RiverEdge and
 * MidpointFractal implementations. TerraFirmaCraft is credited in NOTICE.md.
 */
package gregtech.worldgen.earth.region;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * One directed, block-queryable segment of a regional river graph.
 * Coordinates are stored in TFC grid units (128 blocks); widths are blocks.
 */
public final class EarthRiverEdge {
    public static final int MIN_WIDTH = 8;
    public static final int MAX_WIDTH = 24;
    private static final int MAX_AFFECTING_GRID_DISTANCE = 6;

    private final Vertex source;
    private final Vertex drain;
    private final MidpointFractal fractal;
    private final int minPartX;
    private final int minPartZ;
    private final int maxPartX;
    private final int maxPartZ;

    private int width = MIN_WIDTH;
    private boolean hasSourceEdge;
    private @Nullable EarthRiverEdge drainEdge;

    public EarthRiverEdge(Vertex source, Vertex drain, RandomSource random) {
        this.source = source;
        this.drain = drain;
        fractal = new MidpointFractal(random, 4, source.x(), source.z(), drain.x(), drain.z());

        int centerGridX = (int) Math.round(0.5 * (source.x() + drain.x()));
        int centerGridZ = (int) Math.round(0.5 * (source.z() + drain.z()));
        minPartX = EarthUnits.gridToPartition(centerGridX - MAX_AFFECTING_GRID_DISTANCE);
        minPartZ = EarthUnits.gridToPartition(centerGridZ - MAX_AFFECTING_GRID_DISTANCE);
        maxPartX = EarthUnits.gridToPartition(centerGridX + MAX_AFFECTING_GRID_DISTANCE);
        maxPartZ = EarthUnits.gridToPartition(centerGridZ + MAX_AFFECTING_GRID_DISTANCE);
    }

    public Vertex source() {
        return source;
    }

    public Vertex drain() {
        return drain;
    }

    public MidpointFractal fractal() {
        return fractal;
    }

    public int minPartX() {
        return minPartX;
    }

    public int minPartZ() {
        return minPartZ;
    }

    public int maxPartX() {
        return maxPartX;
    }

    public int maxPartZ() {
        return maxPartZ;
    }

    public int width() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    /** True when another edge enters this edge's source vertex. */
    public boolean hasSourceEdge() {
        return hasSourceEdge;
    }

    public @Nullable EarthRiverEdge drainEdge() {
        return drainEdge;
    }

    public void linkToDrain(@Nullable EarthRiverEdge edge) {
        drainEdge = edge;
        if (edge != null) {
            edge.hasSourceEdge = true;
        }
    }

    public double widthSq(double exactGridX, double exactGridZ) {
        double delta = projectAlongLine(
                source.x(), source.z(), drain.x(), drain.z(), exactGridX, exactGridZ
        );
        int drainWidth = drainEdge == null ? width : drainEdge.width;
        double interpolated = width + delta * (drainWidth - width);
        return interpolated * interpolated;
    }

    public record Vertex(double x, double z, double angle, double length, int distance) {
    }

    /** Four-bisection TFC midpoint fractal used for natural block-scale bends. */
    public static final class MidpointFractal {
        private static final double JITTER_MAX = 0.2;
        private static final double JITTER_MIN = 0.05;
        private static final int MAX_BISECTIONS = 10;
        private static final double[] ENCOMPASSING_RANGES = new double[MAX_BISECTIONS];

        static {
            double sqrtTwo = Math.sqrt(2.0);
            double delta = 0.0;
            double midpointDelta = 0.0;
            double norm = 1.0;
            for (int index = 0; index < MAX_BISECTIONS; index++) {
                ENCOMPASSING_RANGES[index] = Math.max(delta, midpointDelta);
                double previousMidpoint = midpointDelta;
                midpointDelta = 0.5 * (delta + midpointDelta) + sqrtTwo * JITTER_MAX * norm;
                delta = Math.max(previousMidpoint, delta);
                norm *= 0.5 + JITTER_MAX;
            }
        }

        private final double[] segments;
        private final double encompassingRange;

        private MidpointFractal(
                RandomSource random,
                int bisections,
                double sourceX,
                double sourceZ,
                double drainX,
                double drainZ
        ) {
            if (bisections < 0 || bisections >= MAX_BISECTIONS) {
                throw new IllegalArgumentException("Bisections must be in [0, " + MAX_BISECTIONS + ")");
            }
            segments = bisect(random, bisections, new double[]{sourceX, sourceZ, drainX, drainZ});
            encompassingRange = ENCOMPASSING_RANGES[bisections]
                    * normInf(sourceX - drainX, sourceZ - drainZ);
        }

        public double[] segments() {
            return segments;
        }

        public boolean maybeIntersect(double x, double z, double distance) {
            double distanceSq = distancePointToLineSq(
                    segments[0], segments[1],
                    segments[segments.length - 2], segments[segments.length - 1],
                    x, z
            );
            double bound = distance + encompassingRange;
            return distanceSq <= bound * bound;
        }

        public double intersectDistance(double x, double z) {
            double minimum = Double.MAX_VALUE;
            for (int index = 0; index < segments.length - 2; index += 2) {
                minimum = Math.min(minimum, distancePointToLineSq(
                        segments[index], segments[index + 1],
                        segments[index + 2], segments[index + 3],
                        x, z
                ));
            }
            return minimum;
        }

        private static double[] bisect(RandomSource random, int bisections, double[] input) {
            double[] segments = input;
            for (int iteration = 0; iteration < bisections; iteration++) {
                double[] split = new double[(segments.length << 1) - 2];
                split[0] = segments[0];
                split[1] = segments[1];
                int target = 2;
                for (int index = 0; index < segments.length - 2; index += 2) {
                    double sourceX = segments[index];
                    double sourceZ = segments[index + 1];
                    double drainX = segments[index + 2];
                    double drainZ = segments[index + 3];
                    double norm = normInf(sourceX - drainX, sourceZ - drainZ);
                    split[target] = randomJitter(random) * norm + 0.5 * (sourceX + drainX);
                    split[target + 1] = randomJitter(random) * norm + 0.5 * (sourceZ + drainZ);
                    split[target + 2] = drainX;
                    split[target + 3] = drainZ;
                    target += 4;
                }
                segments = split;
            }
            return segments;
        }

        private static double randomJitter(RandomSource random) {
            double value = 2.0 * random.nextDouble() - 1.0;
            return (JITTER_MAX - JITTER_MIN) * value + (value < 0.0 ? -JITTER_MIN : JITTER_MIN);
        }
    }

    public static double distancePointToLineSq(
            double sourceX,
            double sourceZ,
            double drainX,
            double drainZ,
            double pointX,
            double pointZ
    ) {
        double delta = projectAlongLine(sourceX, sourceZ, drainX, drainZ, pointX, pointZ);
        double nearestX = sourceX + delta * (drainX - sourceX);
        double nearestZ = sourceZ + delta * (drainZ - sourceZ);
        return norm2(nearestX - pointX, nearestZ - pointZ);
    }

    public static double projectAlongLine(
            double sourceX,
            double sourceZ,
            double drainX,
            double drainZ,
            double pointX,
            double pointZ
    ) {
        double lengthSq = norm2(sourceX - drainX, sourceZ - drainZ);
        if (lengthSq == 0.0) return 0.0;
        return Math.clamp(
                ((pointX - sourceX) * (drainX - sourceX)
                        + (pointZ - sourceZ) * (drainZ - sourceZ)) / lengthSq,
                0.0,
                1.0
        );
    }

    private static double norm2(double x, double z) {
        return x * x + z * z;
    }

    private static double normInf(double x, double z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }
}
