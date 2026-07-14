package gregtech.worldgen.resources;

import gregapi.data.BedrockFluidList;
import gregapi.worldgen.BedrockFluidRules.BedrockFluidDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class BedrockFluidWorldData extends SavedData {
    public static final int VEIN_CHUNK_SIZE = 8;
    public static final int MAXIMUM_VEIN_OPERATIONS = 100_000;
    public static final double DEPLETED_YIELD_MODIFIER = 0.125D;
    private static final int DATA_VERSION = 2;
    private static final int REGION_CHUNK_SIZE = 12;
    private static final int REGION_CENTER_JITTER = 6;
    private static final String DATA_NAME = "gt6uou_bedrock_fluid";
    private static final long SEED_SALT = 0x424544524F434B46L; // "BEDROCKF"

    private final ServerLevel level;
    private final Map<ChunkPos, BedrockFluidEntry> entries = new HashMap<>();

    public static BedrockFluidWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new BedrockFluidWorldData(level),
                        (tag, provider) -> new BedrockFluidWorldData(level, tag),
                        null
                ),
                DATA_NAME
        );
    }

    public BedrockFluidWorldData(ServerLevel level) {
        this.level = level;
    }

    public BedrockFluidWorldData(ServerLevel level, CompoundTag tag) {
        this(level);
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ChunkPos chunkPos = new ChunkPos(entryTag.getLong("pos"));
            entries.put(chunkPos, BedrockFluidEntry.read(entryTag.getCompound("data")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (Map.Entry<ChunkPos, BedrockFluidEntry> entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.getKey().toLong());
            entryTag.put("data", entry.getValue().write());
            list.add(entryTag);
        }

        tag.put("entries", list);
        return tag;
    }

    public BedrockFluidEntry getEntry(int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        BedrockFluidEntry entry = entries.get(chunkPos);

        if (entry == null) {
            entry = createEntry(chunkX, chunkZ);
            entries.put(chunkPos, entry);
            setDirty();
        } else if (entry.dataVersion() < DATA_VERSION) {
            entry = createEntry(chunkX, chunkZ);
            entries.put(chunkPos, entry);
            setDirty();
        }

        return entry;
    }

    public void deplete(int chunkX, int chunkZ) {
        BedrockFluidEntry entry = getEntry(chunkX, chunkZ);
        BedrockFluidDefinition definition = entry.definition();

        if (definition == null || definition.depletionChance() <= 0) {
            return;
        }

        if (definition.depletionChance() >= 100
                || Math.floorMod(stableHash(level.getSeed() ^ entry.operationsRemaining(), chunkX, chunkZ), 100) < definition.depletionChance()) {
            entry.decreaseOperations(definition.depletionAmount());
            setDirty();
        }
    }

    private BedrockFluidEntry createEntry(int chunkX, int chunkZ) {
        BedrockFluidDefinition definition = selectDefinition(chunkX, chunkZ);
        int fluidYield = 0;

        if (definition != null) {
            int range = definition.maximumYield() - definition.minimumYield();
            if (range <= 0) {
                fluidYield = definition.minimumYield();
            } else {
                fluidYield = definition.minimumYield()
                        + Math.floorMod(stableHash(level.getSeed() ^ SEED_SALT ^ definition.id().hashCode(), chunkX, chunkZ), range + 1);
            }
        }

        return new BedrockFluidEntry(DATA_VERSION, definition == null ? null : definition.id(), fluidYield, MAXIMUM_VEIN_OPERATIONS);
    }

    @Nullable
    private BedrockFluidDefinition selectDefinition(int chunkX, int chunkZ) {
        FluidRegion region = nearestFluidRegion(chunkX, chunkZ);
        Holder<Biome> biome = level.getBiome(new BlockPos(region.centerChunkX() << 4, level.getSeaLevel(), region.centerChunkZ() << 4));
        int totalWeight = 0;

        for (BedrockFluidDefinition definition : BedrockFluidList.OVERWORLD) {
            if (definition.isAllowedIn(level.dimension())) {
                totalWeight += definition.weightFor(biome);
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = Math.floorMod(stableHash(level.getSeed() ^ SEED_SALT, region.regionX(), region.regionZ()), totalWeight);

        for (BedrockFluidDefinition definition : BedrockFluidList.OVERWORLD) {
            if (!definition.isAllowedIn(level.dimension())) {
                continue;
            }

            roll -= definition.weightFor(biome);
            if (roll < 0) {
                return definition;
            }
        }

        return null;
    }

    private FluidRegion nearestFluidRegion(int chunkX, int chunkZ) {
        int baseRegionX = Math.floorDiv(chunkX, REGION_CHUNK_SIZE);
        int baseRegionZ = Math.floorDiv(chunkZ, REGION_CHUNK_SIZE);
        FluidRegion nearest = null;
        long nearestDistance = Long.MAX_VALUE;

        for (int regionX = baseRegionX - 1; regionX <= baseRegionX + 1; regionX++) {
            for (int regionZ = baseRegionZ - 1; regionZ <= baseRegionZ + 1; regionZ++) {
                FluidRegion candidate = fluidRegion(regionX, regionZ);
                long dx = chunkX - candidate.centerChunkX();
                long dz = chunkZ - candidate.centerChunkZ();
                long distance = dx * dx + dz * dz;

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
        }

        return nearest == null ? fluidRegion(baseRegionX, baseRegionZ) : nearest;
    }

    private FluidRegion fluidRegion(int regionX, int regionZ) {
        int centerChunkX = regionX * REGION_CHUNK_SIZE + REGION_CHUNK_SIZE / 2
                + randomBetween(level.getSeed() ^ SEED_SALT ^ 0x9E3779B97F4A7C15L, regionX, regionZ,
                -REGION_CENTER_JITTER, REGION_CENTER_JITTER);
        int centerChunkZ = regionZ * REGION_CHUNK_SIZE + REGION_CHUNK_SIZE / 2
                + randomBetween(level.getSeed() ^ SEED_SALT ^ 0xC2B2AE3D27D4EB4FL, regionX, regionZ,
                -REGION_CENTER_JITTER, REGION_CENTER_JITTER);

        return new FluidRegion(regionX, regionZ, centerChunkX, centerChunkZ);
    }

    private static int randomBetween(long seed, int x, int z, int minInclusive, int maxInclusive) {
        int bound = maxInclusive - minInclusive + 1;
        return minInclusive + Math.floorMod(stableHash(seed, x, z), bound);
    }

    private static int stableHash(long seed, int x, int z) {
        long value = seed;
        value ^= x * 0x9E37_79B9_7F4A_7C15L;
        value ^= z * 0xC2B2_AE3D_27D4_EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51_AFD7_ED55_8CCDL;
        value ^= value >>> 33;
        value *= 0xC4CE_B9FE_1A85_EC53L;
        value ^= value >>> 33;

        return (int) value;
    }

    public static final class BedrockFluidEntry {
        private final int dataVersion;
        @Nullable
        private final String definitionId;
        private final int fluidYield;
        private int operationsRemaining;

        private BedrockFluidEntry(int dataVersion, @Nullable String definitionId, int fluidYield, int operationsRemaining) {
            this.dataVersion = dataVersion;
            this.definitionId = definitionId;
            this.fluidYield = fluidYield;
            this.operationsRemaining = operationsRemaining;
        }

        public int dataVersion() {
            return dataVersion;
        }

        @Nullable
        public String definitionId() {
            return definitionId;
        }

        @Nullable
        public BedrockFluidDefinition definition() {
            return definitionId == null ? null : BedrockFluidList.byId(definitionId);
        }

        @Nullable
        public Fluid fluid() {
            BedrockFluidDefinition definition = definition();
            return definition == null ? null : BuiltInRegistries.FLUID.get(definition.fluidId());
        }

        public int fluidYield() {
            return fluidYield;
        }

        public int operationsRemaining() {
            return operationsRemaining;
        }

        public double remainingPercent() {
            return operationsRemaining * 100.0D / MAXIMUM_VEIN_OPERATIONS;
        }

        public double yieldModifier() {
            double remaining = Math.max(0.0D, Math.min(1.0D, operationsRemaining / (double) MAXIMUM_VEIN_OPERATIONS));
            return DEPLETED_YIELD_MODIFIER + (1.0D - DEPLETED_YIELD_MODIFIER) * remaining;
        }

        public int currentYield() {
            return Math.max(0, (int) Math.round(fluidYield * yieldModifier()));
        }

        private void decreaseOperations(int amount) {
            operationsRemaining = Math.max(0, operationsRemaining - Math.max(0, amount));
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", dataVersion);
            if (definitionId != null) {
                tag.putString("definition", definitionId);
            }
            tag.putInt("fluidYield", fluidYield);
            tag.putInt("operationsRemaining", operationsRemaining);
            return tag;
        }

        private static BedrockFluidEntry read(CompoundTag tag) {
            String definition = tag.contains("definition") ? tag.getString("definition") : null;
            int version = tag.contains("version") ? tag.getInt("version") : 0;
            return new BedrockFluidEntry(version, definition, tag.getInt("fluidYield"), tag.getInt("operationsRemaining"));
        }
    }

    private record FluidRegion(int regionX, int regionZ, int centerChunkX, int centerChunkZ) {
    }
}
