import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

public class PropertyNameMatcher {

    // Map για aliases
    private static final Map<String, String> aliases = new HashMap<>();

    static {
        // Παράδειγμα:
        // aliases.put(normalize("blue harmony 3br heart of athens by mrs"),
        //             "Blue Harmony 3BR • Heart of Athens by MPS");
        // Συμπληρώνεις δικά σου αν θες.
    }

    /** Normalize text: remove accents, lowercase, remove punctuation */
    public static String normalize(String input) {
        if (input == null) return "";

        String text = input;

        // Ενοποίηση non-breaking spaces κλπ σε space
        text = text.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ');

        text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9α-ω ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return text;
    }

    /** Fuzzy match similarity (Levenshtein distance) */
    public static int similarity(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[s1.length()][s2.length()];
    }

    public static String resolveAlias(String original) {
        String norm = normalize(original);
        String mapped = aliases.get(norm);
        return mapped != null ? mapped : original;
    }
}
