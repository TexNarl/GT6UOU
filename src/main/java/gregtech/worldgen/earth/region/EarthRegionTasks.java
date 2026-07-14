/*
 * Task order and algorithms in this file are derived from TerraFirmaGreg's
 * 1.20.1 region pipeline and TerraFirmaCraft 3.2.22 region tasks.
 * Both sources and the EUPL-1.2 license are credited in NOTICE.md.
 */
package gregtech.worldgen.earth.region;

import gregapi.worldgen.GeologicalRules.RegionType;
import gregtech.worldgen.earth.region.EarthRegion.TerrainProfile;
import gregtech.worldgen.earth.river.EarthRiverGraph;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TFG-ordered generation of the coarse 128-block Earth semantic map.
 *
 * <p>This stage does not create block terrain. It labels the irregular region
 * grid with continents, coasts, inlandness, mountain ranges, altitude,
 * geological province and terrain profile. Later zoom/height stages consume
 * this single map.</p>
 */
public final class EarthRegionTasks {
    public static final int BIOME_ALTITUDE_WIDTH = 4;

    private static final int SMALL_OCEAN_FILL_THRESHOLD = 180;
    private static final int ISLAND_SEED_DEPTH = 3;

    private static final TerrainProfile[] LOW_PROFILES = {
            TerrainProfile.PLAINS,
            TerrainProfile.PLAINS,
            TerrainProfile.HILLS,
            TerrainProfile.HILLS,
            TerrainProfile.ROLLING_HILLS
    };
    private static final TerrainProfile[] MID_PROFILES = {
            TerrainProfile.PLAINS,
            TerrainProfile.HILLS,
            TerrainProfile.ROLLING_HILLS,
            TerrainProfile.ROLLING_HILLS,
            TerrainProfile.HIGHLANDS
    };
    private static final TerrainProfile[] HIGH_PROFILES = {
            TerrainProfile.HIGHLANDS,
            TerrainProfile.HIGHLANDS,
            TerrainProfile.HIGHLANDS,
            TerrainProfile.ROLLING_HILLS
    };

    private EarthRegionTasks() {
    }

    static EarthRegion generate(EarthRegionGenerator generator, EarthRegionGenerator.Cell owner) {
        EarthRegion region = new EarthRegion(owner);
        RandomSource random = generator.createRegionRandom(owner);

        initializeOwnedArea(generator, region);
        addContinents(generator, region);
        annotateDistanceToCellEdge(region);
        fillSmallOceans(region);
        addIslands(region, random);
        addHotSpots(generator, region);
        annotateDistanceToOcean(region);
        annotateBaseLandHeight(region, random);
        annotateDistanceToWestCoast(region);
        addMountains(region, random);
        annotateTerrainAltitude(region, random);
        annotateClimate(generator, region);
        chooseGeologicalProvinces(region);
        chooseTerrainProfiles(generator, region);
        region.setRivers(EarthRiverGraph.generate(region, random));
        annotateLakes(region, random);
        return region;
    }

    /** TFG's threshold + outward flood-fill hotspot-chain task. */
    private static void addHotSpots(EarthRegionGenerator generator, EarthRegion region) {
        final double threshold = 0.65;
        final double expansionThreshold = 0.15;
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (EarthRegion.Point point : region.points()) {
            EarthRegionGenerator.Cell plate = generator.sampleCell(point.gridX(), point.gridZ());
            double edgeDistance = Math.abs(plate.f1() - plate.f2());
            double intensity = generator.hotSpotIntensity(point.gridX() + 0.5, point.gridZ() + 0.5);
            if (intensity > threshold && edgeDistance > 0.05) {
                byte age = generator.hotSpotAge(point.gridX() + 0.5, point.gridZ() + 0.5);
                point.setHotSpot(age, (float) intensity);
                if (age != 4) point.setLand();
                queue.addLast(point.index());
            }
        }

        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            EarthRegion.Point source = region.atIndex(index);
            byte age = source.hotSpotAge();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point next = region.atOffset(index, dx, dz);
                    if (next == null || next.hotSpotAge() != 0) continue;
                    double intensity = generator.hotSpotIntensity(next.gridX() + 0.5, next.gridZ() + 0.5);
                    if (intensity > expansionThreshold) {
                        next.setHotSpot(age, (float) intensity);
                        if (age != 4) next.setLand();
                        queue.addLast(next.index());
                    } else if (!next.land()) {
                        double behind = generator.hotSpotIntensity(
                                next.gridX() + 0.5 - dx,
                                next.gridZ() + 0.5 - dz
                        );
                        if (behind > expansionThreshold) {
                            next.setHotSpot(age, (float) behind);
                            if (age != 4) next.setLand();
                        }
                    }
                }
            }
        }
    }

    /** TFG source-river lake annotation, after river linking and biome choice. */
    private static void annotateLakes(EarthRegion region, RandomSource random) {
        for (EarthRiverEdge edge : region.rivers()) {
            if (!edge.hasSourceEdge() && random.nextInt(3) == 0) {
                placeLakeNear(region, edge, 1, 1);
                placeLakeNear(region, edge, -1, 1);
                placeLakeNear(region, edge, 1, -1);
                placeLakeNear(region, edge, -1, -1);
            }
        }
    }

    private static void placeLakeNear(EarthRegion region, EarthRiverEdge edge, int offsetX, int offsetZ) {
        int gridX = (int) (edge.source().x() + 0.3 * offsetX);
        int gridZ = (int) (edge.source().z() + 0.3 * offsetZ);
        EarthRegion.Point point = region.at(gridX, gridZ);
        if (point != null && point.land() && !point.lake()
                && point.distanceToOcean() >= 2 && point.distanceToEdge() >= 2
                && point.terrainProfile() != TerrainProfile.MOUNTAINS
                && point.terrainProfile() != TerrainProfile.COASTAL_MOUNTAINS) {
            point.setLake();
        }
    }

    private static void initializeOwnedArea(EarthRegionGenerator generator, EarthRegion region) {
        int initialMinX = region.minX();
        int initialMinZ = region.minZ();
        int initialWidth = region.sizeX();
        BitSet owned = new BitSet(region.boundingSize());

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int dx = 0; dx < initialWidth; dx++) {
            for (int dz = 0; dz < initialWidth; dz++) {
                int gridX = initialMinX + dx;
                int gridZ = initialMinZ + dz;
                EarthRegionGenerator.Cell sampled = generator.sampleCell(gridX, gridZ);
                if (sampled.ownerX() == region.owner().ownerX()
                        && sampled.ownerZ() == region.owner().ownerZ()) {
                    owned.set(dx + initialWidth * dz);
                    minX = Math.min(minX, gridX);
                    minZ = Math.min(minZ, gridZ);
                    maxX = Math.max(maxX, gridX);
                    maxZ = Math.max(maxZ, gridZ);
                }
            }
        }

        if (minX == Integer.MAX_VALUE) {
            throw new IllegalStateException("Cell owns no grid points: " + region.owner());
        }

        int offsetX = minX - initialMinX;
        int offsetZ = minZ - initialMinZ;
        int width = 1 + maxX - minX;
        int depth = 1 + maxZ - minZ;
        region.setOwnedArea(minX, minZ, maxX, maxZ);

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (owned.get((offsetX + dx) + initialWidth * (offsetZ + dz))) {
                    region.initializePoint(minX + dx, minZ + dz);
                }
            }
        }
    }

    private static void addContinents(EarthRegionGenerator generator, EarthRegion region) {
        for (EarthRegion.Point point : region.points()) {
            double continent = generator.rawContinent(point.gridX(), point.gridZ());
            point.setRawContinent(continent, EarthRegionGenerator.LAND_THRESHOLD);
        }
    }

    /** Exact TFC/TFG edge-distance BFS, including null owner-cell entries. */
    private static void annotateDistanceToCellEdge(EarthRegion region) {
        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int dx = 0; dx < region.sizeX(); dx++) {
            for (int dz = 0; dz < region.sizeZ(); dz++) {
                int index = dx + region.sizeX() * dz;
                EarthRegion.Point point = region.atIndex(index);
                if (point == null || dx == 0 || dz == 0
                        || dx == region.sizeX() - 1 || dz == region.sizeZ() - 1) {
                    explored.set(index);
                    queue.addLast(index);
                    if (point != null) {
                        point.setDistanceToEdge(-1);
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            int nextDistance = lastPoint == null ? 0 : lastPoint.distanceToEdge() + 1;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.distanceToEdge() == 0) {
                        if (!explored.get(point.index())) {
                            point.setDistanceToEdge(nextDistance);
                            explored.set(point.index());
                            queue.addLast(point.index());
                        }
                        explored.set(point.index());
                    }
                }
            }
        }
    }

    /** Fills only bounded ocean holes smaller than the TFG threshold. */
    private static void fillSmallOceans(EarthRegion region) {
        BitSet explored = new BitSet(region.boundingSize());

        for (EarthRegion.Point point : region.points()) {
            if (!point.land() && !explored.get(point.index())) {
                fillOceanComponent(region, explored, point.index());
            }
        }
    }

    private static void fillOceanComponent(EarthRegion region, BitSet explored, int originIndex) {
        Set<Integer> values = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.addLast(originIndex);
        values.add(originIndex);
        explored.set(originIndex);
        boolean unbounded = false;

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point == null) {
                        unbounded = true;
                        continue;
                    }
                    if (point.land() || explored.get(point.index())) {
                        continue;
                    }

                    explored.set(point.index());
                    queue.addLast(point.index());
                    values.add(point.index());
                }
            }
        }

        if (values.size() < SMALL_OCEAN_FILL_THRESHOLD && !unbounded) {
            for (int index : values) {
                region.atIndex(index).setLand();
            }
        }
    }

    /** Exact TFG island attempt counts and original-centered chain offsets. */
    private static void addIslands(EarthRegion region, RandomSource random) {
        for (int attempt = 0, placed = 0; attempt < 130 && placed < 15; attempt++) {
            EarthRegion.Point point = region.randomPoint(random);
            if (point == null) {
                continue;
            }

            if (!point.land() && !point.shore() && point.distanceToEdge() > 2) {
                int originX = point.gridX();
                int originZ = point.gridZ();
                for (int island = 0; island < 12; island++) {
                    point.setLand();
                    point.setIsland();
                    point = region.at(
                            originX + random.nextInt(4) - random.nextInt(4),
                            originZ + random.nextInt(4) - random.nextInt(4)
                    );
                    if (point == null || (point.land() && !point.island()) || point.distanceToEdge() <= 2) {
                        break;
                    }
                }
                placed++;
            }
        }
    }

    private static void annotateDistanceToOcean(EarthRegion region) {
        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (EarthRegion.Point point : region.points()) {
            if (!point.land()) {
                point.setDistanceToOcean(-1);
                queue.addLast(point.index());
                explored.set(point.index());
            }
        }

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            int nextDistance = lastPoint.distanceToOcean() + 1;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.land() && point.distanceToOcean() == 0) {
                        if (!lastPoint.land() && !point.island()) {
                            lastPoint.setShore();
                        }
                        if (!explored.get(point.index())) {
                            point.setDistanceToOcean(nextDistance);
                            queue.addLast(point.index());
                        }
                        explored.set(point.index());
                    }
                }
            }
        }
    }

    private static void annotateBaseLandHeight(EarthRegion region, RandomSource random) {
        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<Integer> islandQueue = new ArrayList<>();

        for (EarthRegion.Point point : region.points()) {
            if (point.land()) {
                int baseLandHeight = point.distanceToOcean();
                if (baseLandHeight > point.distanceToEdge()) {
                    baseLandHeight = (int) (0.3F * baseLandHeight + 0.7F * point.distanceToEdge());
                }
                point.setBaseLandHeight(baseLandHeight);
                explored.set(point.index());

                if (point.island()) {
                    point.setDistanceToLand(ISLAND_SEED_DEPTH);
                    islandQueue.add(point.index());
                } else {
                    point.setDistanceToLand(0);
                    queue.addLast(point.index());
                }
            }
        }

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            int nextDepth = lastPoint.distanceToLand() + 1;

            if (nextDepth == ISLAND_SEED_DEPTH && !islandQueue.isEmpty()) {
                queue.addAll(islandQueue);
                islandQueue.clear();
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point != null && !point.land() && point.distanceToLand() == 0) {
                        if (!explored.get(point.index())) {
                            if (random.nextInt(15) == 0) {
                                point.setDistanceToLand(lastPoint.distanceToLand());
                                queue.addFirst(point.index());
                            } else {
                                point.setDistanceToLand(nextDepth);
                                queue.addLast(point.index());
                            }
                        }
                        explored.set(point.index());
                    }
                }
            }
        }
    }

    /**
     * Direct port of TFGAnnotateDistanceToWestCoast. The field is directional
     * by design: prevailing moisture enters from the west and accumulates a
     * continental rain-shadow distance while crossing land.
     */
    private static void annotateDistanceToWestCoast(EarthRegion region) {
        for (int dx = 0; dx < region.sizeX(); dx++) {
            for (int dz = 0; dz < region.sizeZ(); dz++) {
                int index = dx + region.sizeX() * dz;
                EarthRegion.Point point = region.atIndex(index);
                if (point == null) {
                    continue;
                }
                if (dx == 0) {
                    point.setDistanceToWestCoast(0);
                    continue;
                }

                EarthRegion.Point west = region.atIndex(index - 1);
                if (west == null) {
                    point.setDistanceToWestCoast(point.land() ? 25 + point.distanceToOcean() : 0);
                } else if (!point.land()) {
                    point.setDistanceToWestCoast(Math.max(west.distanceToWestCoast() - 2, 0));
                } else {
                    double frequency = EarthUnits.GRID_WIDTH_IN_BLOCK
                            / (2.0 * EarthRegionGenerator.TEMPERATURE_SCALE_BLOCKS);
                    double latitude = Math.abs(4.0 * frequency * point.gridZ()
                            - 4.0 * Math.floor(frequency * point.gridZ() + 0.75)) - 1.0;
                    int start = -2 + (latitude > 0.1 ? 1 : 0);
                    int end = 2 - (latitude < -0.1 ? 1 : 0);
                    int sum = 0;
                    for (int offsetZ = start; offsetZ <= end; offsetZ++) {
                        EarthRegion.Point neighbor = region.atOffset(index, -1, offsetZ);
                        sum += neighbor == null
                                ? west.distanceToWestCoast()
                                : neighbor.distanceToWestCoast();
                    }
                    point.setDistanceToWestCoast((int) Math.ceil(sum / (1.0 + end - start)) + 1);
                }
            }
        }

        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (EarthRegion.Point point : region.points()) {
            if (point.land()) {
                explored.set(point.index());
                queue.addLast(point.index());
            }
        }

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            if (lastPoint == null) {
                continue;
            }
            int lastDistance = lastPoint.distanceToWestCoast();
            int nextDistance = lastDistance + (lastDistance > 40 ? -1 : 1);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.distanceToWestCoast() == 0
                            && !explored.get(point.index())) {
                        point.setDistanceToWestCoast(nextDistance);
                        explored.set(point.index());
                        queue.addLast(point.index());
                    }
                }
            }
        }
    }

    /** TFG's regional climate annotation, before any biome selection. */
    private static void annotateClimate(EarthRegionGenerator generator, EarthRegion region) {
        for (EarthRegion.Point point : region.points()) {
            int x = point.gridX();
            int z = point.gridZ();
            float temperature = (float) generator.baseTemperature(x, z);
            float rainfall = (float) generator.baseRainfall(x, z);

            float bias = 0.0F;
            if (point.land()) {
                float potentialBias = clampedMap(point.distanceToEdge(), 2.0F, 6.0F, 0.0F, 1.0F);
                float oceanProximityBias = clampedMap(point.distanceToOcean(), 2.0F, 6.0F, 0.0F, 1.0F);
                bias = Math.min(potentialBias, oceanProximityBias);
            }

            float targetTemperature = lerp(bias, 5.0F, temperature);
            float targetRainfall = lerp(bias, Math.min(rainfall + 350.0F, 500.0F), rainfall);
            float temperatureDelta = clampedMap(
                    (float) generator.oceanicInfluence(x, z),
                    -0.8F, 0.9F, -0.07F, 0.23F
            );
            float oldTemperature = temperature;
            temperature = lerp(temperatureDelta, oldTemperature, targetTemperature);

            float rainfallDelta = clampedMap(
                    temperature - oldTemperature,
                    -2.0F, 2.0F, 0.0F, 0.25F
            );
            rainfall = Math.clamp(lerp(rainfallDelta, rainfall, targetRainfall), 0.0F, 500.0F);
            point.setClimate(temperature, rainfall);
        }
    }

    private static float clampedMap(float value, float oldMin, float oldMax, float newMin, float newMax) {
        float normalized = Math.clamp((value - oldMin) / (oldMax - oldMin), 0.0F, 1.0F);
        return lerp(normalized, newMin, newMax);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** Exact TFC 3.2.22 / TFG contour-following mountain algorithm. */
    private static void addMountains(EarthRegion region, RandomSource random) {
        for (int attempt = 0, placed = 0; attempt < 40 && placed < 3; attempt++) {
            EarthRegion.Point origin = region.randomPoint(random);
            if (origin == null || !origin.land()) {
                continue;
            }

            int height = origin.baseLandHeight();
            if (height <= 1 || height >= 4 && height <= 11) {
                Set<Integer> range = placeMountainRange(region, random, origin.index());
                if (range.size() > 45) {
                    for (int index : range) {
                        EarthRegion.Point point = region.atIndex(index);
                        point.setMountain();
                        if (origin.baseLandHeight() <= 2) {
                            point.setCoastalMountain();
                        }
                    }
                    placed++;
                }
            }
        }
    }

    private static Set<Integer> placeMountainRange(EarthRegion region, RandomSource random, int originIndex) {
        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Set<Integer> range = new HashSet<>();

        queue.addLast(originIndex);
        explored.set(originIndex);
        range.add(originIndex);

        int originBaseLandHeight = Math.max(1, region.atIndex(originIndex).baseLandHeight());
        int maxSize = 70 + random.nextInt(40);

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            if (range.size() > maxSize) {
                break;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point == null) {
                        continue;
                    }

                    if (point.land()
                            && point.baseLandHeight() >= originBaseLandHeight - 1
                            && point.baseLandHeight() <= originBaseLandHeight + 1
                            && (point.baseLandHeight() > 2 || point.distanceToOcean() < 3)
                            && !explored.get(point.index())) {
                        if (lastPoint.baseLandHeight() != point.baseLandHeight()) {
                            queue.addLast(point.index());
                        } else {
                            queue.addFirst(point.index());
                        }
                        range.add(point.index());
                    }
                    explored.set(point.index());
                }
            }
        }
        return range;
    }

    private static void annotateTerrainAltitude(EarthRegion region, RandomSource random) {
        BitSet explored = new BitSet(region.boundingSize());
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (EarthRegion.Point point : region.points()) {
            if (point.land() && point.mountain()) {
                point.setBiomeAltitude(3 * BIOME_ALTITUDE_WIDTH);
                queue.addLast(point.index());
                explored.set(point.index());
            }
        }

        while (!queue.isEmpty()) {
            int last = queue.removeFirst();
            EarthRegion.Point lastPoint = region.atIndex(last);
            int nextAltitude = lastPoint.biomeAltitude() - 1;
            if (nextAltitude < 0) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EarthRegion.Point point = region.atOffset(last, dx, dz);
                    if (point != null && point.land() && point.biomeAltitude() == 0
                            && !explored.get(point.index())) {
                        if (random.nextInt(13) == 0
                                && lastPoint.biomeAltitude() != 3 * BIOME_ALTITUDE_WIDTH) {
                            point.setBiomeAltitude(lastPoint.biomeAltitude());
                            queue.addFirst(point.index());
                        } else {
                            point.setBiomeAltitude(nextAltitude);
                            queue.addLast(point.index());
                        }
                        explored.set(point.index());
                    }
                }
            }
        }

        for (EarthRegion.Point point : region.points()) {
            if (point.land() && point.discreteBiomeAltitude() == 0 && point.baseLandHeight() >= 4) {
                point.setBiomeAltitude(BIOME_ALTITUDE_WIDTH);
                if (point.discreteBiomeAltitude() == 1 && point.baseLandHeight() >= 11) {
                    point.setBiomeAltitude(2 * BIOME_ALTITUDE_WIDTH);
                }
            }
        }
    }

    /** Maps TFG's ocean/land/volcanic/uplift rock supertypes to our API. */
    private static void chooseGeologicalProvinces(EarthRegion region) {
        for (EarthRegion.Point center : region.points()) {
            RegionType type = center.hotSpotAge() > 0
                    ? RegionType.VOLCANIC
                    : center.land() ? RegionType.CONTINENTAL_SEDIMENTARY : RegionType.MAFIC_DEEP;
            int minDistance = Integer.MAX_VALUE;

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    EarthRegion.Point point = region.atOffset(center.index(), dx, dz);
                    int distance = Math.abs(dx) + Math.abs(dz);
                    if (point != null && distance < minDistance) {
                        if (point.island() && distance < 4) {
                            type = RegionType.VOLCANIC;
                            minDistance = distance;
                        } else if ((point.mountain() || point.coastalMountain()) && distance < 3) {
                            type = RegionType.UPLIFT;
                            minDistance = distance;
                        }
                    }
                }
            }
            center.setGeologicalProvince(type);
        }
    }

    /**
     * Climate-neutral terrain table for the first Earth port. It preserves
     * TFG's altitude-driven profile selection without introducing new biomes.
     */
    private static void chooseTerrainProfiles(EarthRegionGenerator generator, EarthRegion region) {
        for (EarthRegion.Point point : region.points()) {
            TerrainProfile profile;
            if (point.hotSpotAge() > 0 && point.hotSpotAge() < 4) {
                profile = TerrainProfile.VOLCANO;
            } else if (point.island()) {
                profile = TerrainProfile.ISLAND;
            } else if (point.mountain()) {
                profile = point.coastalMountain()
                        ? TerrainProfile.COASTAL_MOUNTAINS
                        : TerrainProfile.MOUNTAINS;
            } else if (!point.land()) {
                profile = point.distanceToLand() >= 5
                        ? TerrainProfile.DEEP_OCEAN
                        : TerrainProfile.OCEAN;
            } else {
                TerrainProfile[] choices = switch (Math.clamp(point.discreteBiomeAltitude(), 0, 2)) {
                    case 1 -> MID_PROFILES;
                    case 2 -> HIGH_PROFILES;
                    default -> LOW_PROFILES;
                };
                int index = Math.floorMod(generator.pointHash(
                        point.gridX(), point.gridZ(), 0x4F1BBCDCBFA54001L), choices.length);
                profile = choices[index];
            }
            point.setTerrainProfile(profile);
        }
    }
}
