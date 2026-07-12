package gregapi.data;

import gregapi.code.BlockTypeDefinitions;

import java.util.List;

import static gregapi.code.BlockTypeDefinitions.clay;
import static gregapi.code.BlockTypeDefinitions.other;
import static gregapi.code.BlockTypeDefinitions.rock;
import static gregapi.code.BlockTypeDefinitions.sand;

/**
 * API-level lists of GT6UOU natural block types.
 *
 * <p>This file is intentionally only data: if you want to add/remove a rock,
 * clay, sand or miscellaneous terrain block, edit the lists here. The meaning
 * of each type lives in {@link BlockTypeDefinitions}.</p>
 */
public final class RocksList {
    /**
     * Names use sentence-style capitalization: only the first letter is
     * uppercase, for example {@code Black granite}, not {@code Black Granite}.
     */
    public static final List<BlockTypeDefinitions.RockType> ROCKS = List.of(
            rock("stone", "Stone"),
            rock("basalt", "Basalt"),
            rock("granite", "Granite"),
            rock("diorite", "Diorite"),
            rock("andesite", "Andesite"),
            rock("tuff", "Tuff"),

            rock("black_granite", "Black granite", 0x2C2C2C),
            rock("red_granite", "Red granite", 0x9D3F2E),
            rock("marble", "Marble"),
            rock("limestone", "Limestone"),
            rock("komatiite", "Komatiite"),
            rock("green_schist", "Green schist"),
            rock("blue_schist", "Blue schist"),
            rock("kimberlite", "Kimberlite"),
            rock("quartzite", "Quartzite"),
            rock("slate", "Slate"),
            rock("shale", "Shale"),
            rock("gabbro", "Gabbro"),
            rock("claystone", "Claystone"),
            rock("conglomerate", "Conglomerate"),
            rock("dolomite", "Dolomite"),
            rock("chert", "Chert"),
            rock("chalk", "Chalk"),
            rock("rhyolite", "Rhyolite"),
            rock("dacite", "Dacite"),
            rock("phyllite", "Phyllite"),
            rock("gneiss", "Gneiss")
    );

    public static final List<BlockTypeDefinitions.ClayType> CLAYS = List.of(
            clay("brown_clay", "Brown clay", 0xE68C4B),
            clay("yellow_clay", "Yellow clay", 0xEAF20A),
            clay("red_clay", "Red clay", 0xF20202),
            clay("blue_clay", "Blue clay", 0x405DED),
            clay("white_clay", "White clay", 0xFFFFFF)
    );

    public static final List<BlockTypeDefinitions.SandType> SANDS = List.of(
            sand("sand", "Sand", 0xDBD3A0),
            sand("red_sand", "Red sand", 0xA95824),

            sand("oilsands", "Oilsands", 0x3C3026),

            sand("black_sand", "Black sand", 0x1F1F1F),
            sand("basaltic_black_sand", "Basaltic black sand", 0x283028),
            sand("granitic_black_sand", "Granitic black sand", 0x302A2A),

            sand("basaltic_mineral_sand", "Basaltic mineral sand", 0x2A2926),
            sand("granitic_mineral_sand", "Granitic mineral sand", 0x51413B),
            sand("cassiterite_sand", "Cassiterite sand", 0x6F675C),
            sand("garnet_sand", "Garnet sand", 0x8E3A2F),
            sand("glauconite_sand", "Glauconite sand", 0x5C8A5C)
    );

    public static final List<BlockTypeDefinitions.OtherType> OTHERS = List.of(
            other("turf", "Turf", 0x4A3424)
    );

    private RocksList() {
    }
}
