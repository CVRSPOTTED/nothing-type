package dev.nothingtype.swipe;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Experimental decoder for traces already converted to nearest key labels. */
public final class SwipeDecoder {
    private record Candidate(String word, int distance) {}

    private SwipeDecoder() {}

    public static List<String> decode(String trace, List<String> dictionary, int limit) {
        String path = collapse(trace);
        if (path.isEmpty() || limit <= 0) return List.of();
        int threshold = Math.max(1, path.length() / 3);
        return dictionary.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .filter(word -> !word.isEmpty() && word.chars().allMatch(Character::isLetter))
                .distinct()
                .map(word -> new Candidate(word, distance(path, collapse(word))))
                .filter(candidate -> candidate.distance() <= threshold)
                .sorted(Comparator.comparingInt(Candidate::distance).thenComparing(Candidate::word))
                .limit(limit)
                .map(Candidate::word)
                .toList();
    }

    private static String collapse(String input) {
        StringBuilder result = new StringBuilder();
        for (char letter : input.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetter(letter) &&
                    (result.isEmpty() || result.charAt(result.length() - 1) != letter)) {
                result.append(letter);
            }
        }
        return result.toString();
    }

    private static int distance(String path, String word) {
        int[] row = new int[word.length() + 1];
        for (int j = 0; j < row.length; j++) row[j] = j;
        for (int i = 1; i <= path.length(); i++) {
            int diagonal = row[0];
            row[0] = i;
            for (int j = 1; j < row.length; j++) {
                int above = row[j];
                int substitution = path.charAt(i - 1) == word.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(above, row[j - 1]) + 1, diagonal + substitution);
                diagonal = above;
            }
        }
        return row[word.length()];
    }
}