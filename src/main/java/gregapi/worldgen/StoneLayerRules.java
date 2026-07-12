package gregapi.worldgen;

import gregapi.worldgen.GeologicalRules.RegionType;
import gregapi.worldgen.GeologicalRules.RockGroup;

import java.util.List;
import java.util.Locale;

/**
 * API-level rules and value objects for GT-style stone layer generation.
 */
public final class StoneLayerRules {
    private StoneLayerRules() {
    }

    /**
     * API-level definition of a rock as a GT-style stone layer.
     *
     * <p>This deliberately references rocks by id instead of holding block states.
     * That keeps the API independent from Minecraft registry timing and lets
     * block registration/worldgen resolve the id later.</p>
     *
     * @param rockId id from {@code RocksList}, for example {@code granite}
     * @param group broad geological group
     */
    public record StoneLayerDefinition(String rockId, RockGroup group) {
        public StoneLayerDefinition {
            rockId = normalizeId(rockId);
            if (group == null) {
                throw new IllegalArgumentException("Stone layer rock group cannot be null: " + rockId);
            }
        }

        private static String normalizeId(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Stone layer rock id cannot be blank");
            }

            String normalized = id.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("Stone layer rock id must use only lowercase letters, numbers and underscores: " + id);
            }
            return normalized;
        }
    }

    /**
     * A complete Overworld geological layer stack.
     *
     * <p>The first rock id is the upper layer, following ids are progressively
     * deeper layers. High terrain makes the same stack thicker instead of
     * changing the order.</p>
     *
     * @param type geological province where this stack can appear
     * @param weight relative chance among stacks of the same province
     * @param rockIds ordered rock ids from upper to deeper layers
     */
    public record StoneLayerStackDefinition(RegionType type, int weight, List<String> rockIds) {
        public StoneLayerStackDefinition {
            if (type == null) {
                throw new IllegalArgumentException("Stone layer stack type must not be null");
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("Stone layer stack weight must be positive");
            }
            if (rockIds == null || rockIds.size() < 2 || rockIds.size() > 5) {
                throw new IllegalArgumentException("Stone layer stack must contain 2 to 5 rock ids");
            }

            rockIds = List.copyOf(rockIds);
        }
    }
}
