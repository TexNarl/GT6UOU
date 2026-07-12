package gregapi.lang;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Runtime localization helper for generated GT-style content.
 *
 * <p>This intentionally does not write or read asset lang files. It is for
 * names that can be built from API data, for example material + form:
 * {@code Iron + dust -> Iron dust}.</p>
 */
public final class GTLocalization {
    private GTLocalization() {
    }

    public static Component literal(String englishName) {
        return Component.literal(requireText(englishName));
    }

    public static Component materialFormComponent(String materialName, String formName) {
        return literal(materialForm(materialName, formName));
    }

    public static String materialForm(String materialName, String formName) {
        return sentenceCase(join(materialName, formName));
    }

    public static String join(String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
    }

    public static String sentenceCase(String value) {
        String text = requireText(value).toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Localized text cannot be blank");
        }
        return value.trim();
    }
}
