package com.instantwaste;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;

public class ItemMatcher {
    private static final int MAX_LEVENSHTEIN_DISTANCE = 3; // Allow up to 3 char differences

    private List<String> completedWasteItems;
    private List<String> rawWasteItems;
    private Map<String, String> normalizedToOriginal; // For fast lookups

    public ItemMatcher() {
        loadMasterList();
        buildNormalizedMap();
    }

    @SuppressWarnings("unchecked")
    private void loadMasterList() {
        try {
            InputStream is = getClass().getResourceAsStream("/master-items.json");
            if (is == null) {
                throw new RuntimeException("master-items.json not found in resources");
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<String>> data = mapper.readValue(is, Map.class);

            completedWasteItems = data.getOrDefault("completedWaste", new ArrayList<>());
            rawWasteItems = data.getOrDefault("rawWaste", new ArrayList<>());

            System.out.println("✓ Loaded master item list: " +
                    completedWasteItems.size() + " completed waste items, " +
                    rawWasteItems.size() + " raw waste items");

        } catch (Exception e) {
            System.err.println("⚠️ Failed to load master items list: " + e.getMessage());
            e.printStackTrace();
            completedWasteItems = new ArrayList<>();
            rawWasteItems = new ArrayList<>();
        }
    }

    private void buildNormalizedMap() {
        normalizedToOriginal = new HashMap<>();

        for (String item : completedWasteItems) {
            normalizedToOriginal.put(normalize(item), item);
        }

        for (String item : rawWasteItems) {
            normalizedToOriginal.put(normalize(item), item);
        }
    }

    /**
     * Matches OCR text against master item list.
     * Returns the canonical item name from master list, or NULL if no match.
     *
     * STRICT MATCHING RULES:
     * - Short strings (<=4 chars) require exact match only
     * - Length-based distance thresholds prevent bad fuzzy matches
     * - Prefix checking prevents completely different words from matching
     */
    public String matchItem(String ocrText, boolean isCompletedWaste) {

        if (ocrText == null || ocrText.trim().isEmpty()) {
            return null;
        }

        List<String> masterList = isCompletedWaste ? completedWasteItems : rawWasteItems;
        String cleaned = cleanOcrText(ocrText);
        String normalized = normalize(cleaned);

        // 1. Try exact normalized match (fastest)
        if (normalizedToOriginal.containsKey(normalized)) {
            String match = normalizedToOriginal.get(normalized);
            if (!match.equals(cleaned)) {
                System.out.println("  ✓ Exact match: '" + cleaned + "' → '" + match + "'");
            }
            return match;
        }

        // 2. Very short strings (<=3 chars) need exact match
        if (cleaned.length() <= 3) {
            System.out.println("  ⚠️ No match found for: '" + cleaned + "' (too short for fuzzy matching)");
            return null;
        }

        // 3. SUBSTRING MATCHING: Check if OCR text is contained in any master item
        // This handles "Ketchup" → "Bulk Ketchup", "Sauce" → "Mac Sauce"
        for (String masterItem : masterList) {
            String masterNormalized = normalize(masterItem);

            // If OCR text is a significant part of the master item
            if (normalized.length() >= 5 && masterNormalized.contains(normalized)) {
                System.out.println("  🔍 Substring match: '" + cleaned + "' → '" + masterItem + "'");
                return masterItem;
            }

            // Or if master item is contained in OCR text (less common)
            if (masterNormalized.length() >= 5 && normalized.contains(masterNormalized)) {
                System.out.println("  🔍 Substring match: '" + cleaned + "' → '" + masterItem + "'");
                return masterItem;
            }
        }

        // 4. FUZZY MATCHING with balanced thresholds
        int maxDistance;
        if (cleaned.length() <= 6) {
            maxDistance = 1;  // Strict for short (4-6 chars)
        } else if (cleaned.length() <= 10) {
            maxDistance = 2;  // Medium (7-10 chars)
        } else if (cleaned.length() <= 15) {
            maxDistance = 3;  // Lenient (11-15 chars)
        } else {
            maxDistance = 4;  // Very lenient (16+ chars)
        }

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String masterItem : masterList) {
            String masterNormalized = normalize(masterItem);
            int distance = levenshteinDistance(normalized, masterNormalized);

            if (distance < bestDistance && distance <= maxDistance) {

                // Prefix check ONLY for short strings (<=8 chars)
                if (distance > 0 && cleaned.length() <= 8) {
                    int prefixLen = Math.min(3, Math.min(cleaned.length(), masterItem.length()));
                    String cleanedPrefix = normalized.substring(0, prefixLen);
                    String masterPrefix = masterNormalized.substring(0, prefixLen);

                    int prefixDistance = levenshteinDistance(cleanedPrefix, masterPrefix);
                    if (prefixDistance >= 2) {
                        continue; // Skip if prefixes very different
                    }
                }

                bestDistance = distance;
                bestMatch = masterItem;
            }
        }

        if (bestMatch != null) {
            System.out.println("  🔍 Fuzzy match: '" + cleaned + "' → '" + bestMatch + "' (distance: " + bestDistance + ")");
            return bestMatch;
        }

        System.out.println("  ⚠️ No match found for: '" + cleaned + "' (no valid fuzzy match)");
        return null;
    }

    /**
     * Normalize text for matching: lowercase, remove punctuation, collapse spaces
     */
    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // Remove punctuation
                .replaceAll("\\s+", " ")        // Collapse whitespace
                .trim();
    }

    /**
     * Clean OCR artifacts from text
     */
    private String cleanOcrText(String text) {
        // Remove non-Latin scripts that are OCR errors
        text = text.replaceAll("[०-९]", "");     // Devanagari digits
        text = text.replaceAll("[༠-༩]", "");     // Tibetan digits
        text = text.replaceAll("[가-힣]", "");    // Korean
        text = text.replaceAll("[\\u0900-\\u097F]", ""); // All Devanagari
        text = text.replaceAll("\\s+", " ");     // Normalize whitespace
        return text.trim();
    }

    /**
     * Simple Levenshtein distance implementation (no external dependency needed)
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Get all items for a specific waste type (for frontend dropdowns)
     */
    public List<String> getAllItems(boolean isCompletedWaste) {
        return new ArrayList<>(isCompletedWaste ? completedWasteItems : rawWasteItems);
    }

    /**
     * Export master list for frontend use
     */
    public Map<String, List<String>> exportForFrontend() {
        Map<String, List<String>> export = new HashMap<>();
        export.put("completedWaste", new ArrayList<>(completedWasteItems));
        export.put("rawWaste", new ArrayList<>(rawWasteItems));
        return export;
    }
}