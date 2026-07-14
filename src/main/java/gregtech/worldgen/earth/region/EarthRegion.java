/*
 * Region ownership model follows TerraFirmaCraft's EUPL-1.2 region design.
 * This implementation is written for GT6UOU and credited in NOTICE.md.
 */
package gregtech.worldgen.earth.region;

import gregapi.worldgen.GeologicalRules.RegionType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * One irregular cellular region. The backing array is only a bounding box;
 * entries outside the owning Voronoi cell remain {@code null}.
 */
public final class EarthRegion {
    private final EarthRegionGenerator.Cell owner;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private int sizeX;
    private int sizeZ;
    private Point[] points;
    private List<EarthRiverEdge> rivers = List.of();

    EarthRegion(EarthRegionGenerator.Cell owner) {
        this.owner = owner;
        int centerX = fastRound(owner.centerX());
        int centerZ = fastRound(owner.centerZ());
        minX = centerX - EarthUnits.REGION_RADIUS_IN_GRID;
        minZ = centerZ - EarthUnits.REGION_RADIUS_IN_GRID;
        maxX = centerX + EarthUnits.REGION_RADIUS_IN_GRID;
        maxZ = centerZ + EarthUnits.REGION_RADIUS_IN_GRID;
        sizeX = 1 + maxX - minX;
        sizeZ = 1 + maxZ - minZ;
        points = new Point[0];
    }

    public EarthRegionGenerator.Cell owner() {
        return owner;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int boundingSize() {
        return sizeX * sizeZ;
    }

    public Iterable<Point> points() {
        return () -> new Iterator<>() {
            private int cursor;
            private Point next = advance();

            private Point advance() {
                while (cursor < points.length) {
                    Point point = points[cursor++];
                    if (point != null) {
                        return point;
                    }
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Point next() {
                if (next == null) {
                    throw new NoSuchElementException();
                }
                Point result = next;
                next = advance();
                return result;
            }
        };
    }

    public List<EarthRiverEdge> rivers() {
        return rivers;
    }

    void setRivers(List<EarthRiverEdge> rivers) {
        this.rivers = List.copyOf(rivers);
    }

    @Nullable
    public Point at(int gridX, int gridZ) {
        return isInBounds(gridX, gridZ) ? points[index(gridX, gridZ)] : null;
    }

    @Nullable
    Point atIndex(int index) {
        return index >= 0 && index < points.length ? points[index] : null;
    }

    @Nullable
    public Point atOffset(int index, int dx, int dz) {
        int localX = dx + index % sizeX;
        int localZ = dz + index / sizeX;
        return localX >= 0 && localX < sizeX && localZ >= 0 && localZ < sizeZ
                ? points[localX + sizeX * localZ]
                : null;
    }

    /**
     * Matches TFG's regional random selection: the bounding array is sampled,
     * so points outside the irregular owner cell may intentionally return null.
     */
    @Nullable
    Point randomPoint(RandomSource random) {
        return points[random.nextInt(points.length)];
    }

    Point atOrThrow(int gridX, int gridZ) {
        Point point = at(gridX, gridZ);
        if (point == null) {
            throw new IllegalStateException("Region " + owner.ownerX() + "," + owner.ownerZ()
                    + " does not own grid point " + gridX + "," + gridZ);
        }
        return point;
    }

    int index(int gridX, int gridZ) {
        if (!isInBounds(gridX, gridZ)) {
            throw new IndexOutOfBoundsException("Grid point outside region bounds");
        }
        return (gridX - minX) + sizeX * (gridZ - minZ);
    }

    void setOwnedArea(int newMinX, int newMinZ, int newMaxX, int newMaxZ) {
        minX = newMinX;
        minZ = newMinZ;
        maxX = newMaxX;
        maxZ = newMaxZ;
        sizeX = 1 + maxX - minX;
        sizeZ = 1 + maxZ - minZ;
        points = new Point[sizeX * sizeZ];
    }

    void initializePoint(int gridX, int gridZ) {
        int index = index(gridX, gridZ);
        points[index] = new Point(gridX, gridZ, index);
    }

    private boolean isInBounds(int gridX, int gridZ) {
        return gridX >= minX && gridX <= maxX && gridZ >= minZ && gridZ <= maxZ;
    }

    private static int fastRound(double value) {
        return value >= 0 ? (int) (value + 0.5) : (int) (value - 0.5);
    }

    public static final class Point {
        private final int gridX;
        private final int gridZ;
        private final int index;
        private double rawContinent;
        private boolean land;
        private boolean island;
        private boolean mountain;
        private boolean coastalMountain;
        private boolean lake;
        private byte hotSpotAge;
        private float hotSpotIntensity;
        private int distanceToOcean;
        private int distanceToEdge;
        private int distanceToLand;
        private int distanceToWestCoast;
        private int baseLandHeight;
        private int biomeAltitude;
        private float averageTemperature;
        private float averageRainfall;
        private RegionType geologicalProvince = RegionType.MAFIC_DEEP;
        private TerrainProfile terrainProfile = TerrainProfile.DEEP_OCEAN;

        private Point(int gridX, int gridZ, int index) {
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.index = index;
        }

        public int gridX() {
            return gridX;
        }

        public int gridZ() {
            return gridZ;
        }

        public int index() {
            return index;
        }

        public double rawContinent() {
            return rawContinent;
        }

        public boolean land() {
            return land;
        }

        public boolean island() {
            return island;
        }

        public boolean shore() {
            return distanceToOcean == -2;
        }

        public boolean mountain() {
            return mountain;
        }

        public boolean coastalMountain() {
            return coastalMountain;
        }

        public boolean lake() {
            return lake;
        }

        /** 0 = none, 1 = active, 2 = dormant, 3 = extinct, 4 = ancient. */
        public byte hotSpotAge() {
            return hotSpotAge;
        }

        public float hotSpotIntensity() {
            return hotSpotIntensity;
        }

        public int distanceToOcean() {
            return distanceToOcean;
        }

        public int distanceToEdge() {
            return distanceToEdge;
        }

        public int distanceToLand() {
            return distanceToLand;
        }

        /**
         * TFG's directional continentality field. Values grow while moving
         * east across land and are propagated back into adjacent oceans.
         */
        public int distanceToWestCoast() {
            return distanceToWestCoast;
        }

        public int baseLandHeight() {
            return baseLandHeight;
        }

        public int biomeAltitude() {
            return biomeAltitude;
        }

        /** Mean annual sea-level temperature in degrees Celsius. */
        public float averageTemperature() {
            return averageTemperature;
        }

        /** Mean annual rainfall in millimetres, clamped to TFG's 0..500 range. */
        public float averageRainfall() {
            return averageRainfall;
        }

        public int discreteBiomeAltitude() {
            return Math.floorDiv(biomeAltitude, EarthRegionTasks.BIOME_ALTITUDE_WIDTH);
        }

        public RegionType geologicalProvince() {
            return geologicalProvince;
        }

        public TerrainProfile terrainProfile() {
            return terrainProfile;
        }

        void setRawContinent(double rawContinent, double landThreshold) {
            this.rawContinent = rawContinent;
            this.land = rawContinent > landThreshold;
        }

        void setLand() {
            land = true;
        }

        void setIsland() {
            island = true;
        }

        void setShore() {
            distanceToOcean = -2;
        }

        void setMountain() {
            mountain = true;
        }

        void setCoastalMountain() {
            coastalMountain = true;
        }

        void setLake() {
            lake = true;
            terrainProfile = TerrainProfile.LAKE;
            averageRainfall += 0.09F * (500.0F - averageRainfall);
        }

        void setHotSpot(byte age, float intensity) {
            hotSpotAge = age;
            hotSpotIntensity = Math.max(hotSpotIntensity, intensity);
        }

        void setDistanceToOcean(int distanceToOcean) {
            this.distanceToOcean = distanceToOcean;
        }

        void setDistanceToEdge(int distanceToEdge) {
            this.distanceToEdge = distanceToEdge;
        }

        void setDistanceToLand(int distanceToLand) {
            this.distanceToLand = distanceToLand;
        }

        void setDistanceToWestCoast(int distanceToWestCoast) {
            this.distanceToWestCoast = distanceToWestCoast;
        }

        void setBaseLandHeight(int baseLandHeight) {
            this.baseLandHeight = baseLandHeight;
        }

        void setBiomeAltitude(int biomeAltitude) {
            this.biomeAltitude = biomeAltitude;
        }

        void setClimate(float averageTemperature, float averageRainfall) {
            this.averageTemperature = averageTemperature;
            this.averageRainfall = averageRainfall;
        }

        void setGeologicalProvince(RegionType geologicalProvince) {
            this.geologicalProvince = geologicalProvince;
        }

        void setTerrainProfile(TerrainProfile terrainProfile) {
            this.terrainProfile = terrainProfile;
        }
    }

    /**
     * Climate-neutral subset of the TFG terrain table. These are terrain
     * shapes, not biome registrations or texture requirements.
     */
    public enum TerrainProfile {
        DEEP_OCEAN,
        OCEAN,
        ISLAND,
        PLAINS,
        HILLS,
        ROLLING_HILLS,
        HIGHLANDS,
        MOUNTAINS,
        COASTAL_MOUNTAINS,
        SHORE,
        TIDAL_FLATS,
        COASTAL_DUNES,
        EMBAYMENTS,
        ROCKY_SHORES,
        LAKE,
        VOLCANO
    }
}
