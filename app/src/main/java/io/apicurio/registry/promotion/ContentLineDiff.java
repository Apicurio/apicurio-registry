package io.apicurio.registry.promotion;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal line-based diff used for promotion compare reports.
 */
public final class ContentLineDiff {

    public record Line(String op, String text) {
    }

    private ContentLineDiff() {
    }

    public static List<Line> diff(String previous, String next) {
        String[] left = split(previous);
        String[] right = split(next);
        int[][] lengths = new int[left.length + 1][right.length + 1];
        for (int i = left.length - 1; i >= 0; i--) {
            for (int j = right.length - 1; j >= 0; j--) {
                if (left[i].equals(right[j])) {
                    lengths[i][j] = lengths[i + 1][j + 1] + 1;
                } else {
                    lengths[i][j] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
                }
            }
        }
        List<Line> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.length && j < right.length) {
            if (left[i].equals(right[j])) {
                i++;
                j++;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                lines.add(new Line("removed", left[i]));
                i++;
            } else {
                lines.add(new Line("added", right[j]));
                j++;
            }
        }
        while (i < left.length) {
            lines.add(new Line("removed", left[i++]));
        }
        while (j < right.length) {
            lines.add(new Line("added", right[j++]));
        }
        return lines;
    }

    private static String[] split(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        return value.split("\n", -1);
    }
}
