package com.mooncycle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Difficulty;

public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    /**
     * Parses a string into an Adventure Component.
     * Supports both MiniMessage tags and legacy '&' color codes.
     * If both appear in the same message, legacy codes are translated to MiniMessage tags
     * so gradients and advanced styling render properly.
     */
    public static Component render(String input) {
        if (input == null || input.isBlank()) return Component.empty();

        // If no MiniMessage tags exist, fast path to legacy serializer
        if (!input.contains("<") || !input.contains(">")) {
            return LEGACY.deserialize(input);
        }

        // If legacy codes are mixed with MiniMessage tags, convert '&' codes first
        if (input.indexOf('&') != -1) {
            return MINI_MESSAGE.deserialize(translateLegacy(input));
        }

        return MINI_MESSAGE.deserialize(input);
    }

    private static String translateLegacy(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 16);
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                String tag = toMiniTag(chars[i + 1]);
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    private static String toMiniTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default  -> null;
        };
    }

    public static String formatDifficulty(Difficulty diff) {
        if (diff == null) return "Normal";
        return switch (diff) {
            case PEACEFUL -> "Peaceful";
            case EASY     -> "Easy";
            case NORMAL   -> "Normal";
            case HARD     -> "Hard";
        };
    }
}
