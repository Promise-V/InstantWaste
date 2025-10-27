package com.instantwaste;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Header-Proximity-Based Table Parser
 *
 * CORE PRINCIPLE: No arbitrary boundaries. Column assignment based on proximity to headers.
 *
 * For any text block at position X, determine which column it belongs to by:
 * 1. Calculate distance to each column header
 * 2. Assign to the column with the SMALLEST distance
 *
 * This handles:
 * - Multi-word items that extend across boundaries ("Coffee Frappe")
 * - Varying handwriting spacing
 * - Different column widths across tables
 * - No tolerance values needed
 */
public class HeaderProximityParser {

    /**
     * Represents a validated row anchored by a master list item
     */
    public static class ValidatedRow {
        public String itemName;
        public int anchorY;
        public VisionOcr.TextBlock itemBlock;

        public String size;
        public String open;
        public String swing;
        public String close;
        public String count;

        public boolean openNeedsReview = false;
        public boolean swingNeedsReview = false;
        public boolean closeNeedsReview = false;
        public boolean countNeedsReview = false;

        public String openIssue;
        public String swingIssue;
        public String closeIssue;
        public String countIssue;

        public ValidatedRow(String itemName, int anchorY, VisionOcr.TextBlock itemBlock) {
            this.itemName = itemName;
            this.anchorY = anchorY;
            this.itemBlock = itemBlock;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Item: ").append(itemName);
            if (size != null) sb.append(" | Size: ").append(size);
            if (open != null) sb.append(" | OPEN: ").append(open);
            if (swing != null) sb.append(" | SWING: ").append(swing);
            if (close != null) sb.append(" | CLOSE: ").append(close);
            if (count != null) sb.append(" | COUNT: ").append(count);
            return sb.toString();
        }
    }

    /**
     * Main parsing entry point
     */
    public static List<ValidatedRow> parseTableWithValidation(
            TableSegmenter.Table table,
            List<VisionOcr.TextBlock> blocks,
            ItemMatcher itemMatcher) {

        System.out.println("\n🔍 PARSING " + table.name + " WITH VALIDATION (Header-Proximity Method)");

        boolean isCompletedWaste = table.type.equals("COMPLETED_WASTE_2COL");

        // PASS 1: Find validated row anchors using header proximity
        List<ValidatedRow> validatedRows = findValidatedRowAnchors(blocks, table, itemMatcher, isCompletedWaste);
        System.out.println("  ✓ Found " + validatedRows.size() + " validated row anchors");

        // PASS 2: Attach data to validated rows using header proximity
        attachDataToValidatedRows(validatedRows, blocks, table);
        System.out.println("  ✓ Attached data to validated rows");

        return validatedRows;
    }

    /**
     * PASS 1: Find validated row anchors
     * Uses header proximity to determine which blocks belong in ITEM column
     */
    private static List<ValidatedRow> findValidatedRowAnchors(
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table,
            ItemMatcher itemMatcher,
            boolean isCompletedWaste) {

        System.out.println("\n  📍 PASS 1: Finding validated row anchors (header-proximity method)...");

        List<ValidatedRow> anchors = new ArrayList<>();

        // Get ITEM header position
        TableSegmenter.ColumnBoundary itemBoundary = table.columnBoundaries.get("ITEM");
        if (itemBoundary == null) {
            System.out.println("    ❌ No ITEM column boundary found!");
            return anchors;
        }
        int itemHeaderX = itemBoundary.headerX;
        System.out.println("    ITEM header at X=" + itemHeaderX);

        // Group blocks into item name clusters using header proximity
        List<ItemNameCluster> itemClusters = clusterItemNamesByHeaderProximity(
                blocks, table, itemHeaderX
        );

        System.out.println("    Found " + itemClusters.size() + " item name clusters");

        // Validate each cluster against master list
        for (ItemNameCluster cluster : itemClusters) {
            String combinedText = cluster.getCombinedText();

            String matchedItem = itemMatcher.matchItem(combinedText, isCompletedWaste);

            if (matchedItem == null) {
                System.out.println("    ❌ No match, skipping: '" + combinedText + "'");
                continue;
            }

            if (matchedItem.trim().isEmpty()) {
                System.out.println("    ❌ Empty match, skipping: '" + combinedText + "'");
                continue;
            }

            ValidatedRow anchor = new ValidatedRow(matchedItem, cluster.averageY, cluster.primaryBlock);
            anchors.add(anchor);

            System.out.println("    ✓ Validated: '" + combinedText + "' → '" + matchedItem + "' at Y=" + cluster.averageY);
        }

        anchors.sort(Comparator.comparingInt(a -> a.anchorY));

        return anchors;
    }

    /**
     * Clusters blocks that belong to ITEM column using header proximity
     *
     * KEY DIFFERENCE: Instead of boundary cutoff, checks if block is closest to ITEM header
     */
    private static List<ItemNameCluster> clusterItemNamesByHeaderProximity(
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table,
            int itemHeaderX) {

        // Filter to blocks that are CLOSEST to ITEM header
        List<VisionOcr.TextBlock> itemBlocks = blocks.stream()
                .filter(b -> b.y > table.yStart + 50) // Below headers
                .filter(b -> !b.text.trim().matches("\\d+")) // Not pure numbers
                .filter(b -> !isSubHeaderText(b.text)) // Not category labels
                .filter(b -> isClosestToItemHeader(b, table, itemHeaderX)) // PROXIMITY CHECK
                .sorted(Comparator.comparingInt(b -> b.y))
                .collect(Collectors.toList());

        System.out.println("    Filtered to " + itemBlocks.size() + " blocks closest to ITEM header");

        List<ItemNameCluster> clusters = new ArrayList<>();

        if (itemBlocks.isEmpty()) return clusters;

        // Group by Y proximity (same row)
        ItemNameCluster currentCluster = new ItemNameCluster();
        currentCluster.addBlock(itemBlocks.get(0));

        for (int i = 1; i < itemBlocks.size(); i++) {
            VisionOcr.TextBlock block = itemBlocks.get(i);

            // Same row if Y is close
            if (Math.abs(block.y - currentCluster.averageY) <= 25) {
                currentCluster.addBlock(block);
            } else {
                clusters.add(currentCluster);
                currentCluster = new ItemNameCluster();
                currentCluster.addBlock(block);
            }
        }

        if (!currentCluster.blocks.isEmpty()) {
            clusters.add(currentCluster);
        }

        return clusters;
    }

    /**
     * CORE METHOD: Determines if block is closest to ITEM header
     *
     * Calculates distance to ALL column headers and picks the closest one.
     * Returns true if ITEM header is closest.
     */
    private static boolean isClosestToItemHeader(
            VisionOcr.TextBlock block,
            TableSegmenter.Table table,
            int itemHeaderX) {

        int blockX = block.x + (block.width / 2); // Use center of block

        // Calculate distance to ITEM header
        int distanceToItem = Math.abs(blockX - itemHeaderX);

        // Calculate distance to all other headers
        int minDistanceToOtherHeaders = Integer.MAX_VALUE;

        for (Map.Entry<String, TableSegmenter.ColumnBoundary> entry : table.columnBoundaries.entrySet()) {
            String columnName = entry.getKey();
            if (columnName.equals("ITEM")) continue; // Skip ITEM itself

            TableSegmenter.ColumnBoundary boundary = entry.getValue();
            int distance = Math.abs(blockX - boundary.headerX);

            if (distance < minDistanceToOtherHeaders) {
                minDistanceToOtherHeaders = distance;
            }
        }

        // Block belongs to ITEM if it's closer to ITEM header than any other header
        return distanceToItem < minDistanceToOtherHeaders;
    }

    /**
     * Helper class for grouping item name blocks
     */
    private static class ItemNameCluster {
        List<VisionOcr.TextBlock> blocks = new ArrayList<>();
        int averageY = 0;
        VisionOcr.TextBlock primaryBlock;

        void addBlock(VisionOcr.TextBlock block) {
            blocks.add(block);
            if (primaryBlock == null) primaryBlock = block;

            int totalY = blocks.stream().mapToInt(b -> b.y).sum();
            averageY = totalY / blocks.size();
        }

        String getCombinedText() {
            return blocks.stream()
                    .sorted(Comparator.comparingInt(b -> b.x))
                    .map(b -> b.text)
                    .collect(Collectors.joining(" "));
        }
    }

    /**
     * PASS 2: Attach data to validated rows
     */
    private static void attachDataToValidatedRows(
            List<ValidatedRow> validatedRows,
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table) {

        System.out.println("\n  📎 PASS 2: Attaching data using header proximity...");

        // Collect blocks already used as item names
        Set<VisionOcr.TextBlock> usedItemBlocks = validatedRows.stream()
                .map(r -> r.itemBlock)
                .collect(Collectors.toSet());

        List<VisionOcr.TextBlock> dataBlocks = blocks.stream()
                .filter(b -> !usedItemBlocks.contains(b))
                .filter(b -> b.y > table.yStart + 50)
                .filter(b -> !isSubHeaderText(b.text))
                .collect(Collectors.toList());

        System.out.println("    Processing " + dataBlocks.size() + " data blocks");

        for (VisionOcr.TextBlock block : dataBlocks) {
            if (block.x < table.xStart || block.x > table.xEnd) continue;

            // Determine column by header proximity
            String columnName = determineColumnByHeaderProximity(block, table);

            if (columnName.equals("UNKNOWN")) {
                continue; // Skip blocks that don't clearly belong to any column
            }

            // Special handling for 2-column completed waste
            if (table.type.equals("COMPLETED_WASTE_2COL")) {
                attachToCompletedWasteRow(block, validatedRows, columnName);
            } else {
                attachToRawWasteRow(block, validatedRows, columnName);
            }
        }
    }

    /**
     * CORE METHOD: Determines column by finding closest header
     *
     * INPUT: Text block
     * OUTPUT: Column name (ITEM, SIZE, OPEN, SWING, CLOSE, COUNT, or UNKNOWN)
     *
     * LOGIC: Calculate distance to each column header, return the closest one
     */
    private static String determineColumnByHeaderProximity(
            VisionOcr.TextBlock block,
            TableSegmenter.Table table) {

        int blockX = block.x + (block.width / 2);

        String closestColumn = "UNKNOWN";
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<String, TableSegmenter.ColumnBoundary> entry : table.columnBoundaries.entrySet()) {
            String columnName = entry.getKey();
            TableSegmenter.ColumnBoundary boundary = entry.getValue();

            int distance = Math.abs(blockX - boundary.headerX);

            if (distance < minDistance) {
                minDistance = distance;
                closestColumn = columnName;
            }
        }

        // Sanity check: if distance is too large (>200px), treat as unknown
        if (minDistance > 200) {
            return "UNKNOWN";
        }

        return closestColumn;
    }

    /**
     * Attach data for 2-column completed waste tables
     */
    private static void attachToCompletedWasteRow(
            VisionOcr.TextBlock block,
            List<ValidatedRow> validatedRows,
            String columnName) {

        String text = block.text.trim();

        // COUNT must be numeric
        if (columnName.equals("COUNT") && !text.matches("\\d+")) {
            System.out.println("    ❌ Rejected non-numeric COUNT '" + text + "'");
            return;
        }

        // Only process COUNT column for completed waste
        if (!columnName.equals("COUNT")) {
            return;
        }

        ValidatedRow nearestRow = findNearestRow(block, validatedRows);

        if (nearestRow == null) {
            System.out.println("    ⚠️ No nearby row for COUNT '" + text + "' at Y=" + block.y);
            return;
        }

        int distance = Math.abs(block.y - nearestRow.anchorY);

        if (nearestRow.count == null || nearestRow.count.isEmpty()) {
            nearestRow.count = text;

            if (distance > 40) {
                nearestRow.countNeedsReview = true;
                nearestRow.countIssue = "Distance from item: " + distance + "px";
                System.out.println("    ⚠️ COUNT '" + text + "' → '" + nearestRow.itemName + "' (distance: " + distance + "px) [REVIEW]");
            } else {
                System.out.println("    ✓ COUNT '" + text + "' → '" + nearestRow.itemName + "'");
            }
        }
    }

    /**
     * Attach data for raw waste tables (3-col and 5-col)
     */
    private static void attachToRawWasteRow(
            VisionOcr.TextBlock block,
            List<ValidatedRow> validatedRows,
            String columnName) {

        String text = block.text.trim();

        // Quantity columns must be numeric
        if (columnName.matches("OPEN|SWING|CLOSE|COUNT")) {
            if (!text.matches("\\d+")) {
                System.out.println("    ❌ Rejected non-numeric " + columnName + " '" + text + "'");
                return;
            }
        }

        ValidatedRow nearestRow = findNearestRow(block, validatedRows);

        if (nearestRow == null) {
            System.out.println("    ⚠️ No nearby row for " + columnName + " '" + text + "' at Y=" + block.y);
            return;
        }

        int distance = Math.abs(block.y - nearestRow.anchorY);

        if (distance > 80) {
            System.out.println("    ❌ Rejected " + columnName + " '" + text + "' - too far (" + distance + "px)");
            return;
        }

        boolean needsReview = distance > 50;

        switch (columnName) {
            case "SIZE":
                if (nearestRow.size == null || nearestRow.size.isEmpty()) {
                    nearestRow.size = text;
                    System.out.println("    ✓ SIZE '" + text + "' → '" + nearestRow.itemName + "'");
                }
                break;

            case "OPEN":
                if (nearestRow.open == null || nearestRow.open.isEmpty()) {
                    nearestRow.open = text;
                    if (needsReview) {
                        nearestRow.openNeedsReview = true;
                        nearestRow.openIssue = "Distance: " + distance + "px";
                    }
                    System.out.println("    ✓ OPEN '" + text + "' → '" + nearestRow.itemName + "'" +
                            (needsReview ? " [REVIEW]" : ""));
                }
                break;

            case "SWING":
                if (nearestRow.swing == null || nearestRow.swing.isEmpty()) {
                    nearestRow.swing = text;
                    if (needsReview) {
                        nearestRow.swingNeedsReview = true;
                        nearestRow.swingIssue = "Distance: " + distance + "px";
                    }
                    System.out.println("    ✓ SWING '" + text + "' → '" + nearestRow.itemName + "'" +
                            (needsReview ? " [REVIEW]" : ""));
                }
                break;

            case "CLOSE":
                if (nearestRow.close == null || nearestRow.close.isEmpty()) {
                    nearestRow.close = text;
                    if (needsReview) {
                        nearestRow.closeNeedsReview = true;
                        nearestRow.closeIssue = "Distance: " + distance + "px";
                    }
                    System.out.println("    ✓ CLOSE '" + text + "' → '" + nearestRow.itemName + "'" +
                            (needsReview ? " [REVIEW]" : ""));
                }
                break;

            case "COUNT":
                if (nearestRow.count == null || nearestRow.count.isEmpty()) {
                    nearestRow.count = text;
                    if (needsReview) {
                        nearestRow.countNeedsReview = true;
                        nearestRow.countIssue = "Distance: " + distance + "px";
                    }
                    System.out.println("    ✓ COUNT '" + text + "' → '" + nearestRow.itemName + "'" +
                            (needsReview ? " [REVIEW]" : ""));
                }
                break;
        }
    }

    /**
     * Find nearest validated row by Y-distance
     */
    private static ValidatedRow findNearestRow(
            VisionOcr.TextBlock block,
            List<ValidatedRow> validatedRows) {

        ValidatedRow nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (ValidatedRow row : validatedRows) {
            int distance = Math.abs(block.y - row.anchorY);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = row;
            }
        }

        return nearest;
    }

    /**
     * Check if text is a sub-header
     */
    private static boolean isSubHeaderText(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        String upper = text.toUpperCase().trim();

        Set<String> subHeaders = Set.of(
                "BUNS", "SAUCES", "BREAKFAST BREAD", "MEAT AND CHICKEN",
                "SALAD AND TOPPINGS", "PREP TABLE", "POTATO PRODUCT",
                "EGGS", "CHEESES", "SEASONINGS", "BREAKFAST MEAT",
                "SHAKE AND SUNDAE", "MISCELLANEOUS", "SMOOTHIE MACHINE",
                "BREAKFAST SAUCES", "MCCAFE AND COFFEE", "POP",
                "POTATO", "MEAT", "BREAKFAST", "BREAD"
        );

        return subHeaders.contains(upper);
    }

    /**
     * TEST MAIN
     */
    public static void main(String[] args) {
        System.out.println("========== HEADER-PROXIMITY PARSER TEST ==========\n");

        String imagePath = "images/main_filled_form.JPG";

        ItemMatcher itemMatcher = new ItemMatcher();

        System.out.println("Step 1: Performing OCR...");
        List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);
        System.out.println("  Found " + blocks.size() + " text blocks\n");

        System.out.println("Step 2: Segmenting tables...");
        List<TableSegmenter.Table> tables = TableSegmenter.segmentTables(blocks);
        System.out.println("  Created " + tables.size() + " tables\n");

        System.out.println("Step 3: Parsing tables with header-proximity validation...");

        for (TableSegmenter.Table table : tables) {
            List<ValidatedRow> rows = parseTableWithValidation(table, table.data, itemMatcher);

            System.out.println("\n========== " + table.name + " RESULTS ==========");
            System.out.println("Validated rows: " + rows.size());

            for (ValidatedRow row : rows) {
                System.out.println(row);

                if (row.openNeedsReview) {
                    System.out.println("    ⚠️ OPEN needs review: " + row.openIssue);
                }
                if (row.swingNeedsReview) {
                    System.out.println("    ⚠️ SWING needs review: " + row.swingIssue);
                }
                if (row.closeNeedsReview) {
                    System.out.println("    ⚠️ CLOSE needs review: " + row.closeIssue);
                }
                if (row.countNeedsReview) {
                    System.out.println("    ⚠️ COUNT needs review: " + row.countIssue);
                }
            }

            System.out.println();
        }

        System.out.println("\n========== TEST COMPLETE ==========");
    }
}