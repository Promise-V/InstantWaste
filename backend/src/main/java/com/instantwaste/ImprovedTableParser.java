package com.instantwaste;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Improved table parsing using master list validation as row anchors.
 * This eliminates fragmentation by treating validated item names as definitive rows,
 * then attaching numbers to the nearest validated row.
 *
 * FIXES APPLIED:
 * 1. ItemMatcher strict mode: Returns null for non-matches (no more "Bacon", "Ranch" noise)
 * 2. Quantity validation: OPEN/SWING/CLOSE/COUNT only accept numeric values (no more "Bulk", "Tartar" in CLOSE)
 * 3. SIZE column: Only keeps first value, no appending (no more "Bag Bag Bag")
 * 4. COUNT protection: Only assigns first number found (prevents overwriting)
 * 5. MULTI-WORD ITEM FIX: Uses generous X boundary + SIZE keyword filtering
 *    - Allows "Coffee Frappe" to cluster together (both words included)
 *    - Explicitly excludes SIZE keywords (Each, Bag, Box) from item names
 *    - No strict boundary that splits multi-word items
 */
public class ImprovedTableParser {

    /**
     * Represents a validated row anchored by a master list item
     */
    public static class ValidatedRow {
        public String itemName;           // Validated item from master list
        public int anchorY;               // Y-coordinate of the item name
        public VisionOcr.TextBlock itemBlock; // Original OCR block for item

        // Data fields
        public String size;
        public String open;
        public String swing;
        public String close;
        public String count;

        // Review flags
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
     * TWO-PASS PARSING SYSTEM
     *
     * Pass 1: Find all validated item names (row anchors)
     * Pass 2: Attach numbers and other data to nearest validated rows
     *
     * INPUT: List of OCR blocks, Table structure, ItemMatcher
     * OUTPUT: List of ValidatedRows with attached data
     */
    public static List<ValidatedRow> parseTableWithValidation(
            TableSegmenter.Table table,
            List<VisionOcr.TextBlock> blocks,
            ItemMatcher itemMatcher) {

        System.out.println("\n🔍 PARSING " + table.name + " WITH VALIDATION");

        // Determine if this is completed waste or raw waste for matching
        boolean isCompletedWaste = table.type.equals("COMPLETED_WASTE_2COL");

        // ============================================================
        // PASS 1: FIND VALIDATED ROW ANCHORS
        // ============================================================
        List<ValidatedRow> validatedRows = findValidatedRowAnchors(blocks, table, itemMatcher, isCompletedWaste);
        System.out.println("  ✓ Found " + validatedRows.size() + " validated row anchors");

        // ============================================================
        // PASS 2: ATTACH DATA TO VALIDATED ROWS
        // ============================================================
        attachDataToValidatedRows(validatedRows, blocks, table);
        System.out.println("  ✓ Attached data to validated rows");

        return validatedRows;
    }

    /**
     * PASS 1: Find all blocks that match the master list
     * These become row anchors - the foundation of our table
     *
     * INPUT: All OCR blocks, table boundaries, item matcher
     * OUTPUT: List of ValidatedRows (with only item names, no data yet)
     *
     * LOGIC:
     * 1. Filter blocks to item column only (left side)
     * 2. Attempt to match each text block to master list
     * 3. If match found, create ValidatedRow as anchor
     * 4. Sort by Y-position (top to bottom)
     *
     * FIXED: Now properly handles null from ItemMatcher (strict validation)
     */
    private static List<ValidatedRow> findValidatedRowAnchors(
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table,
            ItemMatcher itemMatcher,
            boolean isCompletedWaste) {

        System.out.println("\n  📍 PASS 1: Finding validated row anchors...");

        List<ValidatedRow> anchors = new ArrayList<>();

        // Get item column boundary
        TableSegmenter.ColumnBoundary itemBoundary = table.columnBoundaries.get("ITEM");
        TableSegmenter.ColumnBoundary sizeBoundary = table.columnBoundaries.get("SIZE");

        // IMPROVED: Use generous boundary but filter SIZE keywords
        int itemColumnEnd;
        if (sizeBoundary != null) {
            // Multi-column: generous overlap into SIZE
            itemColumnEnd = sizeBoundary.xStart + 100;
        } else {
            // 2-column: Use midpoint between ITEM and COUNT columns
            TableSegmenter.ColumnBoundary countBoundary = table.columnBoundaries.get("COUNT");
            if (countBoundary != null) {
                int midpoint = (itemBoundary.xEnd + countBoundary.xStart) / 2;
                itemColumnEnd = midpoint + 50; // Midpoint + buffer
            } else {
                // Fallback: Use ITEM column end + generous buffer
                itemColumnEnd = itemBoundary.xEnd + 150;
            }
        }
        // Group blocks that might be part of same item name (horizontal proximity)
        // SIZE keywords will be filtered out by clusterItemNameBlocks
        List<ItemNameCluster> itemClusters = clusterItemNameBlocks(blocks, table, itemColumnEnd);

        System.out.println("    Found " + itemClusters.size() + " item name clusters");

        // Try to match each cluster to master list
        for (ItemNameCluster cluster : itemClusters) {
            String combinedText = cluster.getCombinedText();

            // Attempt validation against master list
            // ItemMatcher now returns NULL if no match found (strict mode)
            String matchedItem = itemMatcher.matchItem(combinedText, isCompletedWaste);

            // Only create row anchor if ItemMatcher found a real match
            if (matchedItem == null) {
                System.out.println("    ❌ No match, skipping: '" + combinedText + "'");
                continue;
            }

            // Additional safety check for empty strings
            if (matchedItem.trim().isEmpty()) {
                System.out.println("    ❌ Empty match, skipping: '" + combinedText + "'");
                continue;
            }

            // Create validated row anchor
            ValidatedRow anchor = new ValidatedRow(matchedItem, cluster.averageY, cluster.primaryBlock);
            anchors.add(anchor);

            System.out.println("    ✓ Validated: '" + combinedText + "' → '" + matchedItem + "' at Y=" + cluster.averageY);
        }

        // Sort anchors by Y position (top to bottom)
        anchors.sort(Comparator.comparingInt(a -> a.anchorY));

        return anchors;
    }

    /**
     * Clusters text blocks in item column that are horizontally close
     * (same line but split by OCR)
     *
     * INPUT: All blocks, table boundaries, item column end X
     * OUTPUT: List of ItemNameClusters (grouped blocks that form one item name)
     *
     * LOGIC:
     * 1. Filter to blocks near ITEM column (generous X boundary)
     * 2. EXPLICITLY EXCLUDE SIZE KEYWORDS (Each, Bag, Box, etc.)
     * 3. Group blocks with similar Y-coordinates (within 25px)
     * 4. Sort each group by X (left to right)
     * 5. Combine into single text string per cluster
     *
     * This allows "Coffee Frappe" to cluster together while excluding "Each", "Bag"
     */
    private static List<ItemNameCluster> clusterItemNameBlocks(
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table,
            int itemColumnEnd) {

        // SIZE keywords that should never be part of item names
        Set<String> sizeKeywords = Set.of(
                "EACH", "BAG", "BOX", "TUBE", "JUG", "BOTTLE", "INNER",
                "CAN", "STICK", "TUB", "MACHINE"
        );

        // Filter to item column, non-numeric blocks, excluding SIZE keywords
        List<VisionOcr.TextBlock> itemBlocks = blocks.stream()
                .filter(b -> b.x >= table.xStart && b.x < itemColumnEnd)
                .filter(b -> b.y > table.yStart + 50) // Below headers
                .filter(b -> !b.text.trim().matches("\\d+")) // Not pure numbers
                .filter(b -> !isSubHeaderText(b.text)) // Not category labels
                .filter(b -> !isSizeKeyword(b.text)) // Not SIZE keywords // Not SIZE keywords
                .sorted(Comparator.comparingInt(b -> b.y))
                .collect(Collectors.toList());

        System.out.println("    Filtered to " + itemBlocks.size() + " item name blocks (excluded SIZE keywords)");

        List<ItemNameCluster> clusters = new ArrayList<>();

        if (itemBlocks.isEmpty()) return clusters;

        // Start first cluster
        ItemNameCluster currentCluster = new ItemNameCluster();
        currentCluster.addBlock(itemBlocks.get(0));

        // Group blocks by Y proximity (range-based comparison)
        for (int i = 1; i < itemBlocks.size(); i++) {
            VisionOcr.TextBlock block = itemBlocks.get(i);

            // Check if this block is part of current cluster (similar Y)
            if (Math.abs(block.y - currentCluster.averageY) <= 25) {
                currentCluster.addBlock(block);
            } else {
                // Start new cluster
                clusters.add(currentCluster);
                currentCluster = new ItemNameCluster();
                currentCluster.addBlock(block);
            }
        }

        // Don't forget last cluster
        if (!currentCluster.blocks.isEmpty()) {
            clusters.add(currentCluster);
        }

        return clusters;
    }

    /**
     * Check if text is a SIZE keyword that should be excluded from item names
     *
     * INPUT: Text string from OCR block
     * OUTPUT: true if it's a SIZE keyword, false otherwise
     */
    private static boolean isSizeKeyword(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        // SIZE keywords that should never be part of item names
        Set<String> sizeKeywords = Set.of(
                "EACH", "BAG", "BOX", "TUBE", "JUG", "BOTTLE", "INNER",
                "CAN", "STICK", "TUB", "MACHINE"
        );

        String upper = text.toUpperCase().trim();

        // Exact match to SIZE keywords
        if (sizeKeywords.contains(upper)) {
            return true;
        }

        // Also check if text is ONLY a size keyword (no other content)
        // This catches cases like "Each " or " Bag" with extra whitespace
        String cleaned = upper.replaceAll("[^A-Z]", "");
        return sizeKeywords.contains(cleaned);
    }

    /**
     * Helper class to group text blocks that form one item name
     */
    private static class ItemNameCluster {
        List<VisionOcr.TextBlock> blocks = new ArrayList<>();
        int averageY = 0;
        VisionOcr.TextBlock primaryBlock; // First/main block

        void addBlock(VisionOcr.TextBlock block) {
            blocks.add(block);
            if (primaryBlock == null) primaryBlock = block;

            // Recalculate average Y
            int totalY = blocks.stream().mapToInt(b -> b.y).sum();
            averageY = totalY / blocks.size();
        }

        String getCombinedText() {
            return blocks.stream()
                    .sorted(Comparator.comparingInt(b -> b.x)) // Left to right
                    .map(b -> b.text)
                    .collect(Collectors.joining(" "));
        }
    }

    /**
     * PASS 2: Attach numbers and data to validated rows
     *
     * INPUT: List of validated row anchors, all OCR blocks, table structure
     * OUTPUT: Validated rows now have data fields filled
     *
     * LOGIC:
     * For each block that's not already used as an item name:
     * 1. Determine which column it belongs to (by X-coordinate)
     * 2. Find nearest validated row (by Y-distance)
     * 3. If within acceptable range (<80px), attach to that row
     * 4. If too far, flag for review or discard
     */
    private static void attachDataToValidatedRows(
            List<ValidatedRow> validatedRows,
            List<VisionOcr.TextBlock> blocks,
            TableSegmenter.Table table) {

        System.out.println("\n  📎 PASS 2: Attaching data to validated rows...");

        // Get item column boundary to know where data columns start
        TableSegmenter.ColumnBoundary itemBoundary = table.columnBoundaries.get("ITEM");
        int itemColumnEnd = (itemBoundary != null) ? itemBoundary.xEnd : table.xStart + 300;

        // Collect all blocks that are NOT item names (data blocks)
        Set<VisionOcr.TextBlock> usedItemBlocks = validatedRows.stream()
                .map(r -> r.itemBlock)
                .collect(Collectors.toSet());

        List<VisionOcr.TextBlock> dataBlocks = blocks.stream()
                .filter(b -> !usedItemBlocks.contains(b))
                .filter(b -> b.y > table.yStart + 50) // Below headers
                .filter(b -> !isSubHeaderText(b.text)) // Not category labels
                .collect(Collectors.toList());

        System.out.println("    Processing " + dataBlocks.size() + " data blocks");

        // Process each data block
        for (VisionOcr.TextBlock block : dataBlocks) {
            // Skip if outside table boundaries
            if (block.x < table.xStart || block.x > table.xEnd) continue;

            // Determine column based on X position
            String columnName = determineColumn(block, table, itemColumnEnd);

            // For 2-column tables, special handling
            if (table.type.equals("COMPLETED_WASTE_2COL")) {
                attachToCompletedWasteRow(block, validatedRows, table);
            } else {
                // For raw waste tables (3-col and 5-col)
                attachToRawWasteRow(block, validatedRows, columnName);
            }
        }
    }

    /**
     * Determines which column a block belongs to based on X-coordinate
     *
     * INPUT: Block, table structure, item column end
     * OUTPUT: Column name (ITEM, SIZE, OPEN, SWING, CLOSE, COUNT)
     */
    private static String determineColumn(
            VisionOcr.TextBlock block,
            TableSegmenter.Table table,
            int itemColumnEnd) {

        int blockCenterX = block.x + (block.width / 2);

        // Check if in item column
        if (blockCenterX < itemColumnEnd) {
            return "ITEM";
        }

        // Check each defined column boundary
        for (Map.Entry<String, TableSegmenter.ColumnBoundary> entry : table.columnBoundaries.entrySet()) {
            TableSegmenter.ColumnBoundary boundary = entry.getValue();
            if (blockCenterX >= boundary.xStart && blockCenterX < boundary.xEnd) {
                return boundary.name;
            }
        }

        // Default fallback
        return "UNKNOWN";
    }

    /**
     * Attaches data block to nearest validated row (for 2-column completed waste)
     *
     * SPECIAL HANDLING: Only numbers in right half are counts
     *
     * INPUT: Block, list of validated rows, table
     * OUTPUT: Block data attached to appropriate ValidatedRow field
     *
     * FIXED: COUNT now only accepts numeric values
     */
    private static void attachToCompletedWasteRow(
            VisionOcr.TextBlock block,
            List<ValidatedRow> validatedRows,
            TableSegmenter.Table table) {

        int tableMidpoint = (table.xStart + table.xEnd) / 2;
        boolean isRightSide = block.x > tableMidpoint;
        String text = block.text.trim();

        // VALIDATION: COUNT must be numeric only
        boolean isNumber = text.matches("\\d+");

        // Only process numbers on right side as counts
        if (!isRightSide || !isNumber) {
            return; // Ignore - might be part of item name that wasn't matched
        }

        // Find nearest validated row
        ValidatedRow nearestRow = findNearestRow(block, validatedRows);

        if (nearestRow == null) {
            System.out.println("    ⚠️ No nearby row for COUNT '" + text + "' at Y=" + block.y);
            return;
        }

        int distance = Math.abs(block.y - nearestRow.anchorY);

        // Assign count only if not already set (Problem 4 fix)
        if (nearestRow.count == null || nearestRow.count.isEmpty()) {
            nearestRow.count = text;

            // Flag for review if distance is suspicious
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
     * Attaches data block to nearest validated row (for raw waste 3-col and 5-col)
     *
     * INPUT: Block, list of validated rows, column name
     * OUTPUT: Block data attached to appropriate ValidatedRow field
     *
     * FIXED:
     * - Quantity columns now only accept numbers (no sub-headers)
     * - SIZE column only keeps first value (no appending)
     */
    private static void attachToRawWasteRow(
            VisionOcr.TextBlock block,
            List<ValidatedRow> validatedRows,
            String columnName) {

        String text = block.text.trim();

        // VALIDATION: Quantity columns must be numeric only
        if (columnName.matches("OPEN|SWING|CLOSE|COUNT")) {
            if (!text.matches("\\d+")) {
                System.out.println("    ❌ Rejected non-numeric " + columnName + " '" + text + "'");
                return;
            }
        }

// Find nearest validated row
        ValidatedRow nearestRow = findNearestRow(block, validatedRows);

        if (nearestRow == null) {
            System.out.println("    ⚠️ No nearby row for " + columnName + " '" + text + "' at Y=" + block.y);
            return;
        }

        int distance = Math.abs(block.y - nearestRow.anchorY);

// Reject if too far (likely OCR noise)
        if (distance > 110) {  // ✅ RAISED: Was 80, now 110
            System.out.println("    ❌ Rejected " + columnName + " '" + text + "' - too far (" + distance + "px)");
            return;
        }

        boolean needsReview = distance > 65;

        // Assign to appropriate field based on column
        switch (columnName) {
            case "SIZE":
                // SIZE: Only keep first value (pre-printed on form, shouldn't have multiple values)
                if (nearestRow.size == null || nearestRow.size.isEmpty()) {
                    nearestRow.size = text;
                    System.out.println("    ✓ SIZE '" + text + "' → '" + nearestRow.itemName + "'");
                }
                // Don't append - SIZE is pre-printed and should only have one value
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
     * Finds the validated row closest to a given block (by Y-distance)
     *
     * INPUT: Data block, list of validated rows
     * OUTPUT: Nearest ValidatedRow, or null if none within range
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
     * Checks if text is a sub-header/category label that should be ignored
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
     * TEST MAIN - Can be run independently to test the logic
     */
    public static void main(String[] args) {
        System.out.println("========== IMPROVED TABLE PARSER TEST ==========\n");

        String imagePath = "images/main_filled_form.JPG";

        // Initialize components
        ItemMatcher itemMatcher = new ItemMatcher();

        System.out.println("Step 1: Performing OCR...");
        List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);
        System.out.println("  Found " + blocks.size() + " text blocks\n");

        System.out.println("Step 2: Segmenting tables...");
        List<TableSegmenter.Table> tables = TableSegmenter.segmentTables(blocks);
        System.out.println("  Created " + tables.size() + " tables\n");

        System.out.println("Step 3: Parsing tables with validation...");

        for (TableSegmenter.Table table : tables) {
            List<ValidatedRow> rows = parseTableWithValidation(table, table.data, itemMatcher);

            System.out.println("\n========== " + table.name + " RESULTS ==========");
            System.out.println("Validated rows: " + rows.size());

            for (ValidatedRow row : rows) {
                System.out.println(row);

                // Show review flags
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

            System.out.println(); // Blank line between tables
        }

        System.out.println("\n========== TEST COMPLETE ==========");
    }
}