package gregapi.data;

import gregapi.worldgen.OreVeinRules.OreVeinShapeDefinition;
import gregapi.worldgen.OreVeinRules.VeinShapeTypes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * API-level catalog of ore vein shapes supported by the port.
 *
 * <p>This list is based on the vein families used by GTM and TFG:</p>
 *
 * <ul>
 *     <li>GTM: {@code layeredVeinGenerator}, {@code veinedVeinGenerator},
 *     {@code classicVeinGenerator}, {@code dikeVeinGenerator},
 *     {@code cuboidVeinGenerator}, {@code GeodeVeinGenerator}</li>
 *     <li>TFG/TFC data: {@code tfc:cluster_vein}, {@code tfc:disc_vein},
 *     {@code tfc:pipe_vein}</li>
 * </ul>
 */
public final class OreVeinShapeList {
    public static final OreVeinShapeDefinition MIXED_CLUSTER = shape(
            "mixed_cluster",
            VeinShapeTypes.MIXED_CLUSTER,
            false,
            false,
            "Noisy blob where ore blocks are mixed by weight, like a salad."
    );

    public static final OreVeinShapeDefinition LAYERED_HORIZONTAL = shape(
            "layered_horizontal",
            VeinShapeTypes.LAYERED,
            true,
            false,
            "Layered deposit with mostly horizontal bands."
    );

    public static final OreVeinShapeDefinition LAYERED_SLOPED = shape(
            "layered_sloped",
            VeinShapeTypes.LAYERED,
            true,
            true,
            "Layered deposit with tilted bands, useful for geologic-looking slices."
    );

    public static final OreVeinShapeDefinition CLASSIC_BANDED = shape(
            "classic_banded",
            VeinShapeTypes.CLASSIC_BANDED,
            true,
            true,
            "GT-style top/middle/bottom/between/sporadic vein, used by diamond-like deposits."
    );

    public static final OreVeinShapeDefinition DIKE = shape(
            "dike",
            VeinShapeTypes.DIKE,
            true,
            true,
            "Long narrow intrusive sheet or cut, usually steep or slanted."
    );

    public static final OreVeinShapeDefinition CUBOID = shape(
            "cuboid",
            VeinShapeTypes.CUBOID,
            true,
            false,
            "Rough box-like body with top/middle/bottom/spread ore roles."
    );

    public static final OreVeinShapeDefinition DISC = shape(
            "disc",
            VeinShapeTypes.DISC,
            true,
            true,
            "Flattened lens or sheet, close to TFG/TFC disc veins."
    );

    public static final OreVeinShapeDefinition PIPE = shape(
            "pipe",
            VeinShapeTypes.PIPE,
            false,
            true,
            "Vertical or steeply tilted pipe-like body."
    );

    public static final OreVeinShapeDefinition GEODE = shape(
            "geode",
            VeinShapeTypes.GEODE,
            true,
            false,
            "Shell/core cavity-style body for crystal or geode-like deposits."
    );

    public static final List<OreVeinShapeDefinition> ALL = List.of(
            MIXED_CLUSTER,
            LAYERED_HORIZONTAL,
            LAYERED_SLOPED,
            CLASSIC_BANDED,
            DIKE,
            CUBOID,
            DISC,
            PIPE,
            GEODE
    );

    public static final Map<String, OreVeinShapeDefinition> BY_ID;

    static {
        Map<String, OreVeinShapeDefinition> byId = new LinkedHashMap<>();
        for (OreVeinShapeDefinition shape : ALL) {
            OreVeinShapeDefinition previous = byId.putIfAbsent(shape.id(), shape);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ore vein shape id: " + shape.id());
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private OreVeinShapeList() {
    }

    public static Optional<OreVeinShapeDefinition> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    private static OreVeinShapeDefinition shape(
            String id,
            VeinShapeTypes type,
            boolean layered,
            boolean directional,
            String description
    ) {
        return new OreVeinShapeDefinition(id, type, layered, directional, description);
    }
}
