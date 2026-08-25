package com.dreamdawn.waypointguide;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 颜色解析工具，支持颜色名、#RRGGBB、0xAARRGGBB 格式
 */
public final class ColorParser {
    private static final Map<String, Integer> COLOR_NAMES = Map.ofEntries(
            Map.entry("red", 0xFFFF0000),
            Map.entry("green", 0xFF00FF00),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("cyan", 0xFF00FFFF),
            Map.entry("magenta", 0xFFFF00FF),
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("black", 0xFF000000),
            Map.entry("orange", 0xFFFFA500),
            Map.entry("pink", 0xFFFFC0CB),
            Map.entry("gray", 0xFF808080),
            Map.entry("purple", 0xFF800080)
    );

    private ColorParser() {}

    public static Integer parse(String input) {
        if (input == null || input.isBlank()) return null;

        String s = input.trim().toLowerCase(Locale.ROOT);

        Integer named = COLOR_NAMES.get(s);
        if (named != null) return named;

        if (s.startsWith("0x") || s.startsWith("0X")) {
            try {
                long val = Long.parseLong(s.substring(2), 16);
                return (int) val;
            } catch (NumberFormatException ignored) {}
        }

        if (s.startsWith("#")) {
            try {
                long val = Long.parseLong(s.substring(1), 16);
                return 0xFF000000 | (int) val;
            } catch (NumberFormatException ignored) {}
        }

        try {
            long val = Long.parseLong(s, 16);
            return (int) val;
        } catch (NumberFormatException ignored) {}

        return null;
    }

    public static Set<String> getColorNames() {
        return COLOR_NAMES.keySet();
    }
}
