/*
 * Regional graph construction follows TerraFirmaCraft's EUPL-1.2 River and
 * AddRiversAndLakes implementations. TerraFirmaCraft is credited in NOTICE.md.
 */
package gregtech.worldgen.earth.river;

import gregtech.worldgen.earth.region.EarthRegion;
import gregtech.worldgen.earth.region.EarthRiverEdge;
import gregtech.worldgen.earth.region.EarthRiverEdge.Vertex;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/** Builds TFC/TFG-style directed river trees from regional shore points. */
public final class EarthRiverGraph {
    public static final double RIVER_LENGTH = 2.7;
    public static final int RIVER_DEPTH = 17;
    public static final double RIVER_FEATHER = 0.8;

    private static final double MIN_BRANCH_ANGLE = 0.4;
    private static final int MIN_BRANCH_DISTANCE = 2;
    private static final int MIN_RIVER_EDGE_COUNT = 6;

    private EarthRiverGraph() {
    }

    public static List<EarthRiverEdge> generate(EarthRegion region, RandomSource random) {
        ParallelBuilder rivers = new ParallelBuilder(region);
        for (EarthRegion.Point point : region.points()) {
            if (!point.shore()) continue;
            double angle = findBestStartingAngle(region, random, point.index());
            if (!Double.isNaN(angle)) {
                rivers.add(new Builder(
                        new XoroshiroRandomSource(random.nextLong()),
                        point.gridX() + 0.5,
                        point.gridZ() + 0.5,
                        angle,
                        RIVER_LENGTH,
                        RIVER_DEPTH,
                        RIVER_FEATHER
                ));
            }
        }

        List<GraphEdge> graphEdges = rivers.build();
        List<EarthRiverEdge> edges = new ArrayList<>(graphEdges.size());
        Map<Vertex, EarthRiverEdge> sourceToEdge = new HashMap<>();
        for (GraphEdge graphEdge : graphEdges) {
            EarthRiverEdge edge = new EarthRiverEdge(graphEdge.source(), graphEdge.drain(), random);
            edges.add(edge);
            sourceToEdge.put(edge.source(), edge);
        }
        for (EarthRiverEdge edge : edges) {
            edge.linkToDrain(sourceToEdge.get(edge.drain()));
        }
        for (EarthRiverEdge edge : edges) {
            if (!edge.hasSourceEdge()) {
                int width = EarthRiverEdge.MIN_WIDTH;
                EarthRiverEdge downstream = edge;
                while (downstream != null) {
                    downstream.setWidth(Math.max(downstream.width(), width));
                    downstream = downstream.drainEdge();
                    width = Math.min(width + 2, EarthRiverEdge.MAX_WIDTH);
                }
            }
        }
        return List.copyOf(edges);
    }

    private static double findBestStartingAngle(
            EarthRegion region,
            RandomSource random,
            int pointIndex
    ) {
        double bestMetric = Float.MIN_VALUE;
        int bestCount = 0;
        double bestAngle = Double.NaN;
        for (int directionX = -1; directionX <= 1; directionX++) {
            for (int directionZ = -1; directionZ <= 1; directionZ++) {
                if (directionX == 0 && directionZ == 0) continue;
                EarthRegion.Point inland = region.atOffset(
                        pointIndex,
                        4 * directionX,
                        4 * directionZ
                );
                if (inland == null || !inland.land()) continue;

                double metric = inland.distanceToOcean()
                        - Math.abs(directionX) - Math.abs(directionZ);
                if (metric > bestMetric
                        || metric == bestMetric && random.nextInt(1 + bestCount) == 0) {
                    if (metric > bestMetric) {
                        bestMetric = metric;
                        bestCount = 0;
                    }
                    bestCount++;
                    bestAngle = Math.atan2(directionZ, directionX);
                }
            }
        }
        return Double.isNaN(bestAngle)
                ? Double.NaN
                : bestAngle + random.nextDouble() * 0.2 - 0.1;
    }

    private record GraphEdge(Vertex source, Vertex drain) {
    }

    private interface Context {
        boolean intersects(GraphEdge edge);
    }

    private static final class Builder implements Context {
        private final Queue<GraphEdge> branchQueue = new LinkedList<>();
        private final RandomSource random;
        private final List<GraphEdge> edges = new ArrayList<>();
        private final Vertex root;
        private final List<GraphEdge> branch = new ArrayList<>();
        private final int depth;
        private final double featherSq;

        private Builder(
                RandomSource random,
                double drainX,
                double drainZ,
                double angle,
                double length,
                int depth,
                double feather
        ) {
            this.random = random;
            root = new Vertex(drainX, drainZ, angle, length, 0);
            this.depth = depth;
            featherSq = feather * feather;
        }

        private boolean buildInitialBranch(Context context) {
            Vertex previous = root;
            int length = depth + random.nextInt(1 + (int) (depth * 0.3));
            for (int index = 0; index < length; index++) {
                Vertex next = computeNext(previous, previous.length(), previous.distance());
                GraphEdge edge = new GraphEdge(next, previous);
                if (context.intersects(edge)) {
                    pruneIfTooShort();
                    return true;
                }
                edges.add(edge);
                previous = next;
                if (previous.distance() < depth && previous.distance() >= MIN_BRANCH_DISTANCE) {
                    branchQueue.offer(edge);
                }
            }
            return false;
        }

        private boolean buildBranch(Context context) {
            if (branchQueue.isEmpty()) {
                pruneIfTooShort();
                return true;
            }
            GraphEdge previousEdge = branchQueue.poll();
            Vertex previous = previousEdge.drain();
            int previousDistance = previous.distance() + random.nextInt(3);
            Vertex firstVertex = computeNext(previous, previous.length(), previousDistance);
            double angleDelta = Math.abs(previousEdge.source().angle() - firstVertex.angle());
            if (angleDelta < MIN_BRANCH_ANGLE || 2 * Math.PI - angleDelta < MIN_BRANCH_ANGLE) {
                return false;
            }

            branch.clear();
            GraphEdge first = new GraphEdge(firstVertex, previous);
            if (context.intersects(first)) return false;
            branch.add(first);
            previous = firstVertex;

            int attempts = depth - previous.distance()
                    + random.nextInt(1 + (int) (depth * 0.3));
            for (int index = 0; index < attempts; index++) {
                Vertex next = computeNext(previous, previous.length(), previousDistance);
                GraphEdge edge = new GraphEdge(next, previous);
                if (context.intersects(edge)) break;
                branch.add(edge);
                previous = next;
                previousDistance = next.distance();
                if (previousDistance < depth && previousDistance >= MIN_BRANCH_DISTANCE) {
                    branchQueue.offer(edge);
                }
            }
            edges.addAll(branch);
            return false;
        }

        private Vertex computeNext(Vertex previous, double length, int distance) {
            double angle = distance == 0
                    ? previous.angle()
                    : previous.angle() + (random.nextDouble() * 0.5 + 0.2)
                    * (random.nextBoolean() ? 1 : -1);
            double nextLength = length * (random.nextDouble() * 0.08 + 0.92);
            return new Vertex(
                    previous.x() + Math.cos(angle) * nextLength,
                    previous.z() + Math.sin(angle) * nextLength,
                    angle,
                    nextLength,
                    distance + 1
            );
        }

        private void pruneIfTooShort() {
            if (edges.size() < MIN_RIVER_EDGE_COUNT) {
                edges.clear();
                branchQueue.clear();
            }
        }

        @Override
        public boolean intersects(GraphEdge edge) {
            for (GraphEdge existing : edges) {
                if (existing.source() != edge.drain()
                        && existing.drain() != edge.drain()
                        && (distance(existing, edge.source()) < featherSq
                        || linesIntersect(
                        existing.source(), existing.drain(), edge.source(), edge.drain()))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ParallelBuilder implements Context {
        private final EarthRegion region;
        private final List<Builder> builders = new ArrayList<>();

        private ParallelBuilder(EarthRegion region) {
            this.region = region;
        }

        private void add(Builder builder) {
            builders.add(builder);
        }

        private List<GraphEdge> build() {
            PriorityQueue<Builder> working = new PriorityQueue<>(Comparator.comparingInt(
                    builder -> -builder.edges.size() - 10 * builder.branchQueue.size()
            ));
            for (Builder builder : builders) {
                if (builder.buildInitialBranch(this)) working.offer(builder);
            }
            while (!working.isEmpty()) {
                Builder builder = working.poll();
                if (!builder.buildBranch(this)) working.offer(builder);
            }
            return builders.stream().flatMap(builder -> builder.edges.stream()).toList();
        }

        @Override
        public boolean intersects(GraphEdge edge) {
            if (!isLegal(edge.drain(), edge.source())) return true;
            for (Builder builder : builders) {
                if (builder.intersects(edge)) return true;
            }
            return false;
        }

        private boolean isLegal(Vertex previous, Vertex vertex) {
            EarthRegion.Point previousPoint = point(previous);
            EarthRegion.Point nextPoint = point(vertex);
            return previousPoint != null
                    && nextPoint != null
                    && nextPoint.land()
                    && nextPoint.distanceToOcean() >= previousPoint.distanceToOcean()
                    && nextPoint.distanceToOcean() >= Math.min(3, previous.distance() / 2);
        }

        private @Nullable EarthRegion.Point point(Vertex vertex) {
            return region.at((int) Math.round(vertex.x()), (int) Math.round(vertex.z()));
        }
    }

    private static double distance(GraphEdge edge, Vertex point) {
        return EarthRiverEdge.distancePointToLineSq(
                edge.source().x(), edge.source().z(),
                edge.drain().x(), edge.drain().z(),
                point.x(), point.z()
        );
    }

    private static boolean linesIntersect(Vertex first, Vertex second, Vertex third, Vertex fourth) {
        int firstOrientation = orientation(first, second, third);
        int secondOrientation = orientation(first, second, fourth);
        int thirdOrientation = orientation(third, fourth, first);
        int fourthOrientation = orientation(third, fourth, second);
        return firstOrientation != secondOrientation && thirdOrientation != fourthOrientation
                || firstOrientation == 0 && collinear(first, third, second)
                || secondOrientation == 0 && collinear(first, fourth, second)
                || thirdOrientation == 0 && collinear(third, first, fourth)
                || fourthOrientation == 0 && collinear(third, second, fourth);
    }

    private static boolean collinear(Vertex first, Vertex middle, Vertex last) {
        return middle.x() <= Math.max(first.x(), last.x())
                && middle.x() >= Math.min(first.x(), last.x())
                && middle.z() <= Math.max(first.z(), last.z())
                && middle.z() >= Math.min(first.z(), last.z());
    }

    private static int orientation(Vertex first, Vertex second, Vertex third) {
        double value = (second.z() - first.z()) * (third.x() - second.x())
                - (second.x() - first.x()) * (third.z() - second.z());
        if (value == 0.0) return 0;
        return value > 0.0 ? 1 : 2;
    }
}
