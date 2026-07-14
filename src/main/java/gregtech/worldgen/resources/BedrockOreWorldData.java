package gregtech.worldgen.resources;

import gregapi.data.BedrockOreList;
import gregapi.worldgen.BedrockOreRules.BedrockOreDefinition;
import gregapi.worldgen.BedrockOreRules.WeightedOreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockOreWorldData extends SavedData {
    public static final int VEIN_CHUNK_SIZE = 3;
    public static final int MAXIMUM_VEIN_OPERATIONS = 100_000;
    public static final double DEPLETED_YIELD_MODIFIER = 0.125D;
    private static final int DATA_VERSION = 1;
    private static final String DATA_NAME = "gt6uou_bedrock_ore";
    private static final long SEED_SALT = 0x424544524F434B4FL; // "BEDROCKO"

    private final ServerLevel level;
    private final Map<ChunkPos, BedrockOreEntry> entries = new HashMap<>();

    public static BedrockOreWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new BedrockOreWorldData(level),
                        (tag, provider) -> new BedrockOreWorldData(level, tag),
                        null
                ),
                DATA_NAME
        );
    }

    public BedrockOreWorldData(ServerLevel level) {
        this.level = level;
    }

    public BedrockOreWorldData(ServerLevel level, CompoundTag tag) {
        this(level);
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ChunkPos chunkPos = new ChunkPos(entryTag.getLong("pos"));
            entries.put(chunkPos, BedrockOreEntry.read(entryTag.getCompound("data")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (Map.Entry<ChunkPos, BedrockOreEntry> entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.getKey().toLong());
            entryTag.put("data", entry.getValue().write());
            list.add(entryTag);
        }

        tag.put("entries", list);
        return tag;
    }

    public BedrockOreEntry getEntry(int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        BedrockOreEntry entry = entries.get(chunkPos);

        if (entry == null || entry.dataVersion() < DATA_VERSION) {
            createRelevantVein(chunkX, chunkZ);
            entry = entries.get(chunkPos);

            if (entry == null) {
                entry = BedrockOreEntry.empty();
                entries.put(chunkPos, entry);
            }

            setDirty();
        }

        return entry;
    }

    public void deplete(int chunkX, int chunkZ) {
        BedrockOreEntry entry = getEntry(chunkX, chunkZ);
        BedrockOreDefinition definition = entry.definition();

        if (definition == null || definition.depletionChance() <= 0) {
            return;
        }

        if (definition.depletionChance() >= 100
                || Math.floorMod(stableHash(level.getSeed() ^ entry.operationsRemaining(), chunkX, chunkZ), 100) < definition.depletionChance()) {
            entry.decreaseOperations(definition.depletionAmount());
            setDirty();
        }
    }

    private void createRelevantVein(int chunkX, int chunkZ) {
        if (BedrockOreList.OVERWORLD.isEmpty()) {
            return;
        }

        int maxRadius = BedrockOreList.OVERWORLD.stream()
                .mapToInt(definition -> Math.max(0, definition.size() / 2))
                .max()
                .orElse(0);
        int firstOriginX = floorToVeinStep(chunkX - maxRadius);
        int firstOriginZ = floorToVeinStep(chunkZ - maxRadius);
        int lastOriginX = floorToVeinStep(chunkX + maxRadius);
        int lastOriginZ = floorToVeinStep(chunkZ + maxRadius);

        for (int originX = firstOriginX; originX <= lastOriginX; originX += VEIN_CHUNK_SIZE) {
            for (int originZ = firstOriginZ; originZ <= lastOriginZ; originZ += VEIN_CHUNK_SIZE) {
                BedrockOreDefinition definition = selectDefinition(originX, originZ);

                if (definition == null) {
                    continue;
                }

                int radius = Math.max(0, definition.size() / 2);
                if (Math.abs(chunkX - originX) <= radius && Math.abs(chunkZ - originZ) <= radius) {
                    createVein(originX, originZ, definition);
                    return;
                }
            }
        }
    }

    private void createVein(int originChunkX, int originChunkZ, BedrockOreDefinition definition) {
        int radius = Math.max(0, definition.size() / 2);

        for (int chunkX = originChunkX - radius; chunkX <= originChunkX + radius; chunkX++) {
            for (int chunkZ = originChunkZ - radius; chunkZ <= originChunkZ + radius; chunkZ++) {
                int distance = Math.abs(originChunkX - chunkX) + Math.abs(originChunkZ - chunkZ);
                int distanceDivisor = Math.max(1, distance * distance);
                int baseYield = randomYield(definition, chunkX, chunkZ);
                int adjustedYield = Math.clamp(baseYield / distanceDivisor, definition.minimumYield(), definition.maximumYield());

                entries.put(new ChunkPos(chunkX, chunkZ),
                        new BedrockOreEntry(DATA_VERSION, definition.id(), adjustedYield, MAXIMUM_VEIN_OPERATIONS));
            }
        }
    }

    @Nullable
    private BedrockOreDefinition selectDefinition(int originChunkX, int originChunkZ) {
        Holder<Biome> biome = level.getBiome(new BlockPos(originChunkX << 4, level.getSeaLevel(), originChunkZ << 4));
        int totalWeight = 0;

        for (BedrockOreDefinition definition : BedrockOreList.OVERWORLD) {
            if (definition.isAllowedIn(level.dimension())) {
                totalWeight += definition.weightFor(biome);
            }
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll = Math.floorMod(stableHash(level.getSeed() ^ SEED_SALT, originChunkX, originChunkZ), totalWeight);

        for (BedrockOreDefinition definition : BedrockOreList.OVERWORLD) {
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

    private int randomYield(BedrockOreDefinition definition, int chunkX, int chunkZ) {
        int range = definition.maximumYield() - definition.minimumYield();

        if (range <= 0) {
            return definition.minimumYield();
        }

        return definition.minimumYield()
                + Math.floorMod(stableHash(level.getSeed() ^ SEED_SALT ^ definition.id().hashCode(), chunkX, chunkZ), range + 1);
    }

    private static int floorToVeinStep(int chunkCoord) {
        return Math.floorDiv(chunkCoord, VEIN_CHUNK_SIZE) * VEIN_CHUNK_SIZE;
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

    public static final class BedrockOreEntry {
        private final int dataVersion;
        @Nullable
        private final String definitionId;
        private final int oreYield;
        private int operationsRemaining;

        private BedrockOreEntry(int dataVersion, @Nullable String definitionId, int oreYield, int operationsRemaining) {
            this.dataVersion = dataVersion;
            this.definitionId = definitionId;
            this.oreYield = oreYield;
            this.operationsRemaining = operationsRemaining;
        }

        private static BedrockOreEntry empty() {
            return new BedrockOreEntry(DATA_VERSION, null, 0, MAXIMUM_VEIN_OPERATIONS);
        }

        public int dataVersion() {
            return dataVersion;
        }

        @Nullable
        public String definitionId() {
            return definitionId;
        }

        @Nullable
        public BedrockOreDefinition definition() {
            return definitionId == null ? null : BedrockOreList.byId(definitionId);
        }

        public List<WeightedOreResource> ores() {
            BedrockOreDefinition definition = definition();
            return definition == null ? List.of() : definition.ores();
        }

        public int oreYield() {
            return oreYield;
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
            BedrockOreDefinition definition = definition();
            int depletedYield = definition == null ? 0 : definition.depletedYield();
            return Math.max(depletedYield, (int) Math.round(oreYield * yieldModifier()));
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
            tag.putInt("oreYield", oreYield);
            tag.putInt("operationsRemaining", operationsRemaining);
            return tag;
        }

        private static BedrockOreEntry read(CompoundTag tag) {
            String definition = tag.contains("definition") ? tag.getString("definition") : null;
            int version = tag.contains("version") ? tag.getInt("version") : 0;
            return new BedrockOreEntry(version, definition, tag.getInt("oreYield"), tag.getInt("operationsRemaining"));
        }
    }
}
