package com.instantwaste;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


public class TableSegmenter {

    private static final boolean ENABLE_PASS_3_AGGRESSIVE_MASK =true;

    public static class Table {
        public String name;
        public String type;
        public List<VisionOcr.TextBlock> headerBlocks;
        public int xStart;
        public int xEnd;
        public int yStart;
        public List<VisionOcr.TextBlock> data;
        public Map<String, ColumnBoundary> columnBoundaries;


        public Table(String name) {
            this.name = name;
            this.headerBlocks = new ArrayList<>();
            this.data = new ArrayList<>();
            this.columnBoundaries = new HashMap<>();
        }

        public List<String> getHeaderTexts() {
            return headerBlocks.stream()
                    .sorted(Comparator.comparingInt(b -> b.x))
                    .map(b -> b.text)
                    .collect(Collectors.toList());
        }
    }

    public static class ColumnBoundary {
        public String name;
        public int xStart;
        public int xEnd;
        public int headerX;

        public ColumnBoundary(String name, int headerX, int xStart, int xEnd) {
            this.name = name;
            this.headerX = headerX;
            this.xStart = xStart;
            this.xEnd = xEnd;
        }

        @Override
        public String toString() {
            return String.format("%s: header@%d, range[%d-%d]", name, headerX, xStart, xEnd);
        }
    }

    // NEW: Header clustering approach
    public static class HeaderCluster {
        public List<VisionOcr.TextBlock> headers;
        public int xMin, xMax, yPosition;

        public HeaderCluster() {
            this.headers = new ArrayList<>();
            this.xMin = Integer.MAX_VALUE;
            this.xMax = Integer.MIN_VALUE;
        }

        public void addHeader(VisionOcr.TextBlock header) {
            headers.add(header);
            xMin = Math.min(xMin, header.x);
            xMax = Math.max(xMax, header.x + header.width);
            yPosition = header.y; // Assume all headers in cluster have same Y
        }

        public List<String> getHeaderNames() {
            return headers.stream()
                    .sorted(Comparator.comparingInt(h -> h.x))
                    .map(h -> h.text)
                    .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return String.format("Cluster[%d-%d]: %s", xMin, xMax, getHeaderNames());
        }
    }

    public static List<Table> segmentTables(List<VisionOcr.TextBlock> blocks) {
        System.out.println("🔍 ADAPTIVE HEADER CLUSTERING APPROACH");

        // Step 1: Find all headers
        List<VisionOcr.TextBlock> allHeaders = blocks.stream()
                .filter(b -> isKnownHeader(b.text))
                .sorted(Comparator.comparingInt(b -> b.x))
                .collect(Collectors.toList());

        System.out.println("Found " + allHeaders.size() + " headers:");
        for (VisionOcr.TextBlock header : allHeaders) {
            System.out.printf("  '%s' at X:%d\n", header.text, header.x);
        }

        // Step 2: Cluster headers that are close together
        List<HeaderCluster> clusters = clusterHeaders(allHeaders);

        System.out.println("\nHeader clusters created:");
        for (int i = 0; i < clusters.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + clusters.get(i));
        }

        // Step 3: Validate cluster count
        if (clusters.size() != 5) {
            System.out.println("⚠️ WARNING: Expected 5 tables but got " + clusters.size() + " clusters!");
            System.out.println("Expected structure: 2 CompletedWaste + 3 RawWaste tables");
        }

        // Step 4: Create tables from clusters
        List<Table> tables = new ArrayList<>();
        VisionOcr.TextBlock rawWasteHeader = findHeaderBlock(blocks, "RAW WASTE");
        int rawWasteStartX = (rawWasteHeader != null) ? rawWasteHeader.x : 1400;

        System.out.println("Raw waste section starts at X: " + rawWasteStartX);

        for (int i = 0; i < clusters.size(); i++) {
            HeaderCluster cluster = clusters.get(i);
            Table table = new Table("Table_" + (i + 1));

            // Copy headers
            table.headerBlocks.addAll(cluster.headers);
            table.yStart = cluster.yPosition;

            // Calculate table boundaries from cluster
            table.xStart = cluster.xMin - 50; // Small buffer
            table.xEnd = cluster.xMax + 50;
            // After creating table and calculating boundaries
            calculatePreciseColumnBoundaries(table);

            // FIXED: Adjust table boundaries to match actual column spans
            if (!table.columnBoundaries.isEmpty()) {
                int maxColumnEnd = table.columnBoundaries.values().stream()
                        .mapToInt(b -> b.xEnd)
                        .max()
                        .orElse(table.xEnd);
                table.xEnd = maxColumnEnd + 20; // Add small buffer
            }
            // Adjust boundaries to avoid gaps
            if (i > 0) {
                Table prevTable = tables.get(i - 1);
                int midpoint = (prevTable.xEnd + table.xStart) / 2;
                prevTable.xEnd = midpoint;
                table.xStart = midpoint;
            }

            // Determine table type based on headers and position
            List<String> headerNames = cluster.getHeaderNames();

            // IMPROVED: Classify based on headers FIRST, then position as fallback
            boolean hasOpen = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("OPEN"));
            boolean hasSwing = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("SWING"));
            boolean hasClose = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("CLOSE"));
            boolean hasCount = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("Count"));
            boolean hasSize = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("Size"));

            String expectedType;
            if (hasOpen && hasSwing && hasClose) {
                // Has all 3 shift columns = 5-column raw waste
                table.type = "RAW_WASTE_5COL";
                table.name += "_RawWaste_5Column";
                expectedType = "RawWaste_5Col";
            } else if (hasSize && hasCount && !hasOpen && !hasSwing && !hasClose) {
                // Has Size + Count but no shift columns = 3-column raw waste
                table.type = "RAW_WASTE_3COL";
                table.name += "_RawWaste_3Column";
                expectedType = "RawWaste_3Col";
            } else if (headerNames.size() == 1 && headerNames.get(0).equalsIgnoreCase("Item")) {
                // Only has "Item" header = 2-column completed waste
                table.type = "COMPLETED_WASTE_2COL";
                table.name += "_CompletedWaste_2Column";
                expectedType = "CompletedWaste";
            } else {
                // Fallback - shouldn't happen
                table.type = "UNKNOWN";
                table.name += "_Unknown";
                expectedType = "Unknown";
                System.out.println("⚠️ WARNING: Could not determine table type for headers: " + headerNames);
            }

            calculatePreciseColumnBoundaries(table);
            tables.add(table);

            System.out.printf("Created %s (%s): X[%d-%d], Headers:%s\n",
                    table.name, expectedType, table.xStart, table.xEnd, headerNames);
        }

        // Step 5: Validate expected structure
        long completedWasteCount = tables.stream().filter(t -> t.type.equals("COMPLETED_WASTE_2COL")).count();
        long rawWaste5ColCount = tables.stream().filter(t -> t.type.equals("RAW_WASTE_5COL")).count();
        long rawWaste3ColCount = tables.stream().filter(t -> t.type.equals("RAW_WASTE_3COL")).count();

        System.out.printf("\nTable type validation:\n");
        System.out.printf("  CompletedWaste_2Col: %d (expected: 2)\n", completedWasteCount);
        System.out.printf("  RawWaste_5Col: %d (expected: 1)\n", rawWaste5ColCount);
        System.out.printf("  RawWaste_3Col: %d (expected: 2)\n", rawWaste3ColCount);

        if (completedWasteCount != 2 || rawWaste5ColCount != 1 || rawWaste3ColCount != 2) {
            System.out.println("❌ Structure validation FAILED!");
        } else {
            System.out.println("✅ Structure validation PASSED!");
        }

        // Assign data blocks to tables
        assignDataToTables(blocks, tables);

        return tables;
    }

    // FIXED: Gap-based header clustering using actual header spacing
    private static List<HeaderCluster> clusterHeaders(List<VisionOcr.TextBlock> allHeaders) {
        if (allHeaders.isEmpty()) return new ArrayList<>();

        // SIMPLE FIX: Just split at every "Item" header after the first one
        List<HeaderCluster> clusters = new ArrayList<>();
        HeaderCluster currentCluster = new HeaderCluster();

        for (int i = 0; i < allHeaders.size(); i++) {
            VisionOcr.TextBlock header = allHeaders.get(i);
            String headerText = header.text.toUpperCase();

            if (currentCluster.headers.isEmpty()) {
                currentCluster.addHeader(header);
                continue;
            }

            // ALWAYS start new table when we see "Item" and already have some headers
            boolean shouldStartNewTable = headerText.equals("ITEM") && currentCluster.headers.size() > 0;

            if (shouldStartNewTable) {
                clusters.add(currentCluster);
                currentCluster = new HeaderCluster();
                System.out.printf("  📋 New table at '%s' (X:%d)\n", header.text, header.x);
            }

            currentCluster.addHeader(header);
        }

        if (!currentCluster.headers.isEmpty()) {
            clusters.add(currentCluster);
        }

        // MANUAL FIX: If we don't have 5 clusters, force split the big ones
        if (clusters.size() < 5) {
            System.out.println("  🔨 Manual splitting to get 5 tables...");
            List<HeaderCluster> manualClusters = new ArrayList<>();

            // Your header pattern is: [Item, Item, Item, Size, OPEN, SWING, CLOSE, Item, Size, Count, Item, Size, Count]
            // We want to split into:
            // 1: [Item]
            // 2: [Item]
            // 3: [Item, Size, OPEN, SWING, CLOSE]
            // 4: [Item, Size, Count]
            // 5: [Item, Size, Count]

            for (HeaderCluster cluster : clusters) {
                List<String> headers = cluster.getHeaderNames();
                if (headers.size() <= 2) {
                    manualClusters.add(cluster);
                } else if (headers.contains("OPEN") && headers.contains("SWING") && headers.contains("CLOSE")) {
                    // This is the 5-column table - keep it as one cluster
                    manualClusters.add(cluster);
                } else {
                    // Split the big cluster into multiple ones
                    int splitIndex1 = -1;
                    int splitIndex2 = -1;

                    // Find the split points - look for "Item" headers
                    for (int i = 0; i < cluster.headers.size(); i++) {
                        if (cluster.headers.get(i).text.equalsIgnoreCase("ITEM")) {
                            if (splitIndex1 == -1) {
                                splitIndex1 = i;
                            } else if (splitIndex2 == -1) {
                                splitIndex2 = i;
                            }
                        }
                    }

                    if (splitIndex1 > 0 && splitIndex2 > splitIndex1) {
                        // First cluster
                        HeaderCluster first = new HeaderCluster();
                        for (int i = 0; i < splitIndex1; i++) {
                            first.addHeader(cluster.headers.get(i));
                        }
                        manualClusters.add(first);

                        // Second cluster
                        HeaderCluster second = new HeaderCluster();
                        for (int i = splitIndex1; i < splitIndex2; i++) {
                            second.addHeader(cluster.headers.get(i));
                        }
                        manualClusters.add(second);

                        // Third cluster
                        HeaderCluster third = new HeaderCluster();
                        for (int i = splitIndex2; i < cluster.headers.size(); i++) {
                            third.addHeader(cluster.headers.get(i));
                        }
                        manualClusters.add(third);
                    } else {
                        manualClusters.add(cluster);
                    }
                }
            }

            clusters = manualClusters;
        }

        System.out.println("\nFinal header clusters:");
        for (int i = 0; i < clusters.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + clusters.get(i));
        }

        return clusters;
    }
    // NEW: Merge clusters that were incorrectly split
    private static List<HeaderCluster> mergeRelatedClusters(List<HeaderCluster> clusters) {
        if (clusters.size() <= 1) return clusters;

        List<HeaderCluster> merged = new ArrayList<>();
        HeaderCluster current = clusters.get(0);

        for (int i = 1; i < clusters.size(); i++) {
            HeaderCluster next = clusters.get(i);
            int gap = next.xMin - current.xMax;

            // Merge if clusters are close together and form a complete table pattern
            boolean shouldMerge = gap < 300 ||
                    (current.getHeaderNames().size() + next.getHeaderNames().size() <= 5 && gap < 500);

            if (shouldMerge) {
                // Merge the clusters
                for (VisionOcr.TextBlock header : next.headers) {
                    current.addHeader(header);
                }
            } else {
                merged.add(current);
                current = next;
            }
        }

        merged.add(current);
        return merged;
    }

    private static boolean isKnownHeader(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String upper = text.toUpperCase().trim();
        return upper.equals("ITEM") ||
                upper.equals("SIZE") ||
                upper.equals("OPEN") ||
                upper.equals("SWING") ||
                upper.equals("CLOSE") ||
                upper.equals("COUNT");
    }

    private static VisionOcr.TextBlock findHeaderBlock(List<VisionOcr.TextBlock> blocks, String headerText) {
        return blocks.stream()
                .filter(block -> block.text.trim().toUpperCase().contains(headerText.toUpperCase()))
                .findFirst()
                .orElse(null);
    }

    private static void calculatePreciseColumnBoundaries(Table table) {
        List<VisionOcr.TextBlock> sortedHeaders = new ArrayList<>(table.headerBlocks);
        sortedHeaders.sort(Comparator.comparingInt(h -> h.x));

        table.columnBoundaries.clear();

        if (sortedHeaders.isEmpty()) {
            table.columnBoundaries.put("ITEM",
                    new ColumnBoundary("ITEM", table.xStart, table.xStart, table.xEnd));
            return;
        }

        // Make sure table.type is set
        if (table.type == null) {
            // Determine table type based on headers
            List<String> headerNames = table.getHeaderTexts();
            boolean hasOpen = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("OPEN"));
            boolean hasSwing = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("SWING"));
            boolean hasClose = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("CLOSE"));
            boolean hasCount = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("COUNT"));
            boolean hasSize = headerNames.stream().anyMatch(h -> h.equalsIgnoreCase("SIZE"));

            if (hasOpen && hasSwing && hasClose) {
                table.type = "RAW_WASTE_5COL";
            } else if (hasSize && hasCount && !hasOpen && !hasSwing && !hasClose) {
                table.type = "RAW_WASTE_3COL";
            } else if (headerNames.size() == 1 && headerNames.get(0).equalsIgnoreCase("ITEM")) {
                table.type = "COMPLETED_WASTE_2COL";
            } else {
                table.type = "UNKNOWN";
            }
        }

        // SPECIALIZED BOUNDARY CALCULATION FOR DIFFERENT TABLE TYPES

        if (table.type.equals("RAW_WASTE_5COL")) {
            // FIXED: Use CLOSE column to determine table end for 5-column tables
            VisionOcr.TextBlock closeHeader = null;
            for (VisionOcr.TextBlock header : sortedHeaders) {
                if (header.text.equalsIgnoreCase("CLOSE")) {
                    closeHeader = header;
                    break;
                }
            }
            if (closeHeader != null) {
                table.xEnd = closeHeader.x + closeHeader.width + 100;
            }

            for (int i = 0; i < sortedHeaders.size(); i++) {
                VisionOcr.TextBlock header = sortedHeaders.get(i);
                String columnName = header.text.toUpperCase();

                int columnStart, columnEnd;

                if (i == 0) {
                    columnStart = table.xStart;
                } else {
                    VisionOcr.TextBlock prevHeader = sortedHeaders.get(i - 1);
                    columnStart = prevHeader.x + prevHeader.width + 15;
                }

                if (i == sortedHeaders.size() - 1) {
                    columnEnd = table.xEnd;
                } else {
                    VisionOcr.TextBlock nextHeader = sortedHeaders.get(i + 1);
                    columnEnd = nextHeader.x - 15;
                }

                if (columnEnd - columnStart < 80) {
                    columnEnd = columnStart + 80;
                }

                table.columnBoundaries.put(columnName,
                        new ColumnBoundary(columnName, header.x, columnStart, columnEnd));
            }
        }
        else if (table.type.equals("RAW_WASTE_3COL")) {
            // FIXED: Use COUNT column to determine table end for 3-column tables
            VisionOcr.TextBlock countHeader = null;
            for (VisionOcr.TextBlock header : sortedHeaders) {
                if (header.text.equalsIgnoreCase("COUNT")) {
                    countHeader = header;
                    break;
                }
            }
            if (countHeader != null) {
                table.xEnd = countHeader.x + countHeader.width + 80;
            }

            for (int i = 0; i < sortedHeaders.size(); i++) {
                VisionOcr.TextBlock header = sortedHeaders.get(i);
                String columnName = header.text.toUpperCase();

                int columnStart, columnEnd;

                if (i == 0) {
                    columnStart = table.xStart;
                } else {
                    VisionOcr.TextBlock prevHeader = sortedHeaders.get(i - 1);
                    columnStart = prevHeader.x + prevHeader.width + 20;
                }

                if (i == sortedHeaders.size() - 1) {
                    columnEnd = table.xEnd;
                } else {
                    VisionOcr.TextBlock nextHeader = sortedHeaders.get(i + 1);
                    columnEnd = nextHeader.x - 20;
                }

                if (columnEnd - columnStart < 100) {
                    columnEnd = columnStart + 100;
                }

                table.columnBoundaries.put(columnName,
                        new ColumnBoundary(columnName, header.x, columnStart, columnEnd));
            }
        }
        else if (table.type.equals("COMPLETED_WASTE_2COL")) {
            // For completed waste, create ITEM and COUNT columns
            VisionOcr.TextBlock itemHeader = sortedHeaders.get(0);

            // ITEM column takes left 60%, COUNT takes right 40%
            int itemEnd = table.xStart + (int)((table.xEnd - table.xStart) * 0.6);

            table.columnBoundaries.put("ITEM",
                    new ColumnBoundary("ITEM", itemHeader.x, table.xStart, itemEnd));
            table.columnBoundaries.put("COUNT",
                    new ColumnBoundary("COUNT", itemEnd + 20, itemEnd + 20, table.xEnd));
        }
        else {
            // Fallback: generic calculation (your original logic)
            for (int i = 0; i < sortedHeaders.size(); i++) {
                VisionOcr.TextBlock header = sortedHeaders.get(i);
                String columnName = header.text.toUpperCase();

                int columnStart, columnEnd;

                if (i == 0) {
                    columnStart = table.xStart;
                } else {
                    VisionOcr.TextBlock prevHeader = sortedHeaders.get(i - 1);
                    columnStart = prevHeader.x + prevHeader.width + 20;
                }

                if (i == sortedHeaders.size() - 1) {
                    columnEnd = table.xEnd;
                } else {
                    VisionOcr.TextBlock nextHeader = sortedHeaders.get(i + 1);
                    columnEnd = nextHeader.x - 20;
                }

                table.columnBoundaries.put(columnName,
                        new ColumnBoundary(columnName, header.x, columnStart, columnEnd));
            }
        }
    }

    private static void assignDataToTables(List<VisionOcr.TextBlock> blocks, List<Table> tables) {
        System.out.println("\n📊 Assigning data blocks to tables...");
        Set<VisionOcr.TextBlock> assignedBlocks = new HashSet<>();

        for (VisionOcr.TextBlock block : blocks) {
            if (assignedBlocks.contains(block) || isKnownHeader(block.text)) continue;

            // Find best matching table
            Table bestTable = null;
            int bestScore = Integer.MIN_VALUE;

            for (Table table : tables) {
                // Block must be below table headers and within X range
                if (block.y > table.yStart + 50 &&
                        block.x >= table.xStart && block.x <= table.xEnd) {

                    // Score based on how well-centered the block is
                    int tableCenter = (table.xStart + table.xEnd) / 2;
                    int blockCenter = block.x + (block.width / 2);
                    int distanceFromCenter = Math.abs(blockCenter - tableCenter);
                    int score = 10000 - distanceFromCenter;

                    if (score > bestScore) {
                        bestScore = score;
                        bestTable = table;
                    }
                }
            }

            if (bestTable != null) {
                bestTable.data.add(block);
                assignedBlocks.add(block);
            }
        }

        for (Table table : tables) {
            System.out.println(table.name + " contains " + table.data.size() + " data blocks");

            // Debug: Show column boundaries
            if (!table.columnBoundaries.isEmpty()) {
                System.out.println("  Column boundaries:");
                for (ColumnBoundary boundary : table.columnBoundaries.values()) {
                    System.out.println("    " + boundary);
                }
            }
        }
    }

    public static class Row {
        public int yPosition;
        public List<VisionOcr.TextBlock> blocks;

        public Row(int y) {
            this.yPosition = y;
            this.blocks = new ArrayList<>();
        }
    }

    public static List<Row> groupIntoRows(List<VisionOcr.TextBlock> blocks, int yTolerance) {
        List<Row> rows = new ArrayList<>();
        if (blocks.isEmpty()) return rows;

        List<VisionOcr.TextBlock> sortedBlocks = new ArrayList<>(blocks);
        sortedBlocks.sort(Comparator.comparingInt(b -> b.y));

        Row currentRow = new Row(sortedBlocks.get(0).y);
        currentRow.blocks.add(sortedBlocks.get(0));

        for (int i = 1; i < sortedBlocks.size(); i++) {
            VisionOcr.TextBlock block = sortedBlocks.get(i);
            if (Math.abs(block.y - currentRow.yPosition) <= yTolerance) {
                currentRow.blocks.add(block);
            } else {
                rows.add(currentRow);
                currentRow = new Row(block.y);
                currentRow.blocks.add(block);
            }
        }

        if (!currentRow.blocks.isEmpty()) {
            rows.add(currentRow);
        }

        for (Row row : rows) {
            row.blocks.sort(Comparator.comparingInt(b -> b.x));
        }

        return rows;
    }

    public static class ParsedRow {
        public String item;
        public String size;
        public String open;
        public String swing;
        public String close;
        public String count;

        public boolean openNeedsReview = false;
        public boolean swingNeedsReview = false;
        public boolean closeNeedsReview = false;
        public boolean countNeedsReview = false;

        public String openIssue = null;
        public String swingIssue = null;
        public String closeIssue = null;
        public String countIssue = null;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (item != null) sb.append("Item: ").append(item);
            if (size != null) sb.append(" | Size: ").append(size);
            if (open != null) sb.append(" | OPEN: ").append(open);
            if (swing != null) sb.append(" | SWING: ").append(swing);
            if (close != null) sb.append(" | CLOSE: ").append(close);
            if (count != null) sb.append(" | COUNT: ").append(count);
            return sb.toString();
        }
    }

    // SMART: Separate numbers from text to extract correct data points
    public static ParsedRow parseRowIntoColumns(Row row, Table table, int dynamicItemEnd) {
        ParsedRow parsed = new ParsedRow();

// SPECIAL HANDLING for 2-column Completed Waste tables
        if (table.type.equals("COMPLETED_WASTE_2COL")) {
            // Sort blocks by X position to separate items from counts
            row.blocks.sort(Comparator.comparingInt(b -> b.x));

            int tableMidpoint = (table.xStart + table.xEnd) / 2;

            for (VisionOcr.TextBlock block : row.blocks) {
                String text = block.text.trim();
                boolean isNumber = text.matches("\\d+");

                if (block.x > tableMidpoint && isNumber) {
                    // This is a count in the right column - DON'T append, just assign
                    parsed.count = text;
                } else {
                    // This is an item name in the left column
                    if (parsed.item == null) {
                        parsed.item = text;
                    } else {
                        parsed.item = parsed.item + " " + text; // Only append if needed
                    }
                }
            }
            return parsed;
        }
        // EXISTING LOGIC for other table types below...
        for (VisionOcr.TextBlock block : row.blocks) {
            String column = determineColumnFromBoundaries(block, table);
            String text = block.text.trim();
            boolean isNumber = text.matches("\\d+");

            switch (column) {
                case "ITEM":
                    if (isNumber) {
                        if (table.type.equals("COMPLETED_WASTE_2COL")) {
                            parsed.item = appendText(parsed.item, text + " [COUNT]");
                        } else {
                            String quantityColumn = findClosestQuantityColumn(block, table);
                            if (quantityColumn != null && !quantityColumn.equals("ITEM")) {
                                assignToQuantityColumn(parsed, quantityColumn, text);
                            } else {
                                parsed.item = appendText(parsed.item, text);
                            }
                        }
                    } else {
                        parsed.item = appendText(parsed.item, text);
                    }
                    break;
                case "SIZE":
                    parsed.size = appendText(parsed.size, text);
                    break;
                case "OPEN":
                    parsed.open = appendText(parsed.open, text);
                    break;
                case "SWING":
                    parsed.swing = appendText(parsed.swing, text);
                    break;
                case "CLOSE":
                    parsed.close = appendText(parsed.close, text);
                    break;
                case "COUNT":
                    parsed.count = appendText(parsed.count, text);
                    break;
            }
        }

        return parsed;
    }

    private static boolean hasQuantityColumns(Table table) {
        return table.columnBoundaries.containsKey("OPEN") ||
                table.columnBoundaries.containsKey("SWING") ||
                table.columnBoundaries.containsKey("CLOSE") ||
                table.columnBoundaries.containsKey("COUNT");
    }

    private static String findClosestQuantityColumn(VisionOcr.TextBlock block, Table table) {
        int blockX = block.x + (block.width / 2);
        String closestColumn = null;
        int minDistance = Integer.MAX_VALUE;

        String[] quantityColumns = {"OPEN", "SWING", "CLOSE", "COUNT"};
        for (String column : quantityColumns) {
            ColumnBoundary boundary = table.columnBoundaries.get(column);
            if (boundary != null) {
                int distance = Math.abs(blockX - boundary.headerX);
                if (distance < minDistance && distance < 100) { // Must be reasonably close
                    minDistance = distance;
                    closestColumn = column;
                }
            }
        }

        return closestColumn;
    }

    private static void assignToQuantityColumn(ParsedRow parsed, String column, String value) {
        switch (column) {
            case "OPEN":
                parsed.open = appendText(parsed.open, value);
                break;
            case "SWING":
                parsed.swing = appendText(parsed.swing, value);
                break;
            case "CLOSE":
                parsed.close = appendText(parsed.close, value);
                break;
            case "COUNT":
                parsed.count = appendText(parsed.count, value);
                break;
        }
    }

    private static String determineColumnFromBoundaries(VisionOcr.TextBlock block, Table table) {
        int blockCenterX = block.x + (block.width / 2);

        // Check each column boundary
        for (Map.Entry<String, ColumnBoundary> entry : table.columnBoundaries.entrySet()) {
            ColumnBoundary boundary = entry.getValue();
            if (blockCenterX >= boundary.xStart && blockCenterX < boundary.xEnd) {
                return boundary.name;
            }
        }

        // Default to ITEM if no boundary matches
        return "ITEM";
    }

    private static String appendText(String existing, String newText) {
        if (existing == null) {
            return newText;
        }
        return existing + " " + newText;
    }

    private static final Set<String> SUB_HEADERS = Set.of(
            "BUNS", "SAUCES", "BREAKFAST BREAD", "MEAT AND CHICKEN",
            "SALAD AND TOPPINGS", "PREP TABLE", "POTATO PRODUCT",
            "EGGS", "CHEESES", "SEASONINGS", "BREAKFAST MEAT",
            "SHAKE AND SUNDAE", "MISCELLANEOUS", "SMOOTHIE MACHINE",
            "BREAKFAST SAUCES", "MCCAFE AND COFFEE", "POP",
            "POTATO", "MEAT", "BREAKFAST", "BREAD"
    );

    public static boolean isSubHeader(ParsedRow row) {
        String[] quantityFields = {row.open, row.swing, row.close, row.count};

        for (String field : quantityFields) {
            if (field != null) {
                String fieldUpper = field.toUpperCase().trim();
                if (fieldUpper.isEmpty()) continue;

                if (!fieldUpper.matches("[A-Z0-9\\s]*")) {
                    System.out.println("⚠️ Non-ASCII text detected: '" + field + "'");
                    return true;
                }

                if (fieldUpper.matches(".*[A-Z].*") && !fieldUpper.equals("TOO")) {
                    System.out.println("⚠️ Sub-header detected in quantity field: '" + field + "'");
                    return true;
                }

                for (String subHeader : SUB_HEADERS) {
                    if (fieldUpper.equals(subHeader)) {
                        return true;
                    }
                }

                if (fieldUpper.contains("AND") || fieldUpper.contains("PRODUCT") ||
                        fieldUpper.contains("MEAT") || fieldUpper.contains("CHICKEN")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void cleanQuantityFields(ParsedRow row) {
        row.open = extractNumbersOnly(row.open);
        row.swing = extractNumbersOnly(row.swing);
        row.close = extractNumbersOnly(row.close);
        row.count = extractNumbersOnly(row.count);
    }

    private static String extractNumbersOnly(String text) {
        if (text == null) return null;

        String[] parts = text.split("\\s+");
        StringBuilder numbers = new StringBuilder();

        for (String part : parts) {
            if (part.matches("\\d+")) {
                if (numbers.length() > 0) numbers.append(" ");
                numbers.append(part);
            }
        }

        return numbers.length() > 0 ? numbers.toString() : null;
    }

    public static int findItemColumnEnd(List<Row> rows, Table table) {
        ColumnBoundary itemBoundary = table.columnBoundaries.get("ITEM");
        if (itemBoundary != null) {
            return itemBoundary.xEnd;
        }
        return table.xEnd;
    }

    private static final Map<String, String> COMMON_MISREADS = Map.ofEntries(
            Map.entry("TO", "10"),
            Map.entry("O", "0"),
            Map.entry("l", "1"),
            Map.entry("I", "1"),
            Map.entry("S", "5"),
            Map.entry("B", "8"),
            Map.entry("Z", "2"),
            Map.entry("G", "6"),
            Map.entry("AT", "41"),
            Map.entry("at", "91"),
            Map.entry("༡༡", "22"),
            Map.entry("F", "7"),
            Map.entry("A", "4"),
            Map.entry("R", ""),
            Map.entry("C", ""),
            Map.entry("H", ""),
            Map.entry("प", "9"),
            Map.entry("09", "60"),
            Map.entry("416", "46"),
            Map.entry("RRRR", ""),
            Map.entry("SSSS", ""),
            Map.entry("TAIR", "")
    );

    private static String correctCommonMisreads(String text) {
        if (text == null) return null;
        if (COMMON_MISREADS.containsKey(text)) {
            return COMMON_MISREADS.get(text);
        }
        return text;
    }

    private static void correctQuantityMisreads(ParsedRow row) {
        row.open = correctCommonMisreads(row.open);
        row.swing = correctCommonMisreads(row.swing);
        row.close = correctCommonMisreads(row.close);
        row.count = correctCommonMisreads(row.count);
    }

    public static String createIntelligentMaskedImage(String originalImagePath,
                                                      List<Table> tables,
                                                      List<VisionOcr.TextBlock> allBlocks) throws IOException {
        BufferedImage original = ImageIO.read(new File(originalImagePath));
        BufferedImage masked = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = masked.createGraphics();

        g.drawImage(original, 0, 0, null);

        g.setColor(Color.BLACK);
        for (VisionOcr.TextBlock block : allBlocks) {
            if (!block.text.matches("\\d+")) {
                int padding = 8;
                g.fillRect(
                        block.x - padding,
                        block.y - padding,
                        block.width + (padding * 2),
                        block.height + (padding * 2)
                );
            }
        }

        for (Table table : tables) {
            int dataColumnsStart = Integer.MAX_VALUE;

            for (VisionOcr.TextBlock header : table.headerBlocks) {
                String headerName = header.text.toUpperCase();
                if (headerName.equals("OPEN") || headerName.equals("SWING") ||
                        headerName.equals("CLOSE") || headerName.equals("COUNT")) {
                    dataColumnsStart = Math.min(dataColumnsStart, header.x);
                }
            }

            if (dataColumnsStart < Integer.MAX_VALUE) {
                g.fillRect(
                        table.xStart,
                        table.yStart,
                        dataColumnsStart - table.xStart - 100,
                        original.getHeight() - table.yStart
                );
            }
        }

        g.dispose();

        String maskedPath = "masked_intelligent.png";
        ImageIO.write(masked, "png", new File(maskedPath));
        System.out.println("✓ Created intelligent masked image");

        return maskedPath;
    }

    public static String upscaleImage(String inputPath, double scale) throws IOException {
        BufferedImage input = ImageIO.read(new File(inputPath));

        int newWidth = (int) (input.getWidth() * scale);
        int newHeight = (int) (input.getHeight() * scale);

        BufferedImage upscaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = upscaled.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(input, 0, 0, newWidth, newHeight, null);
        g.dispose();

        String upscaledPath = inputPath.replace(".png", "_upscaled.png");
        ImageIO.write(upscaled, "png", new File(upscaledPath));
        System.out.println("✓ Upscaled image 2x: " + upscaledPath);

        return upscaledPath;
    }

    private static void cleanItemNames(ParsedRow row) {
        if (row.item == null) return;

        String[] parts = row.item.split("\\s+");
        StringBuilder cleanedItem = new StringBuilder();

        for (String part : parts) {
            String corrected = correctCommonMisreads(part);

            if (corrected == null || corrected.trim().isEmpty()) {
                continue;
            }

            if (corrected.matches("\\d+")) {
                if (isPartOfItemName(corrected, row.item)) {
                    if (cleanedItem.length() > 0) cleanedItem.append(" ");
                    cleanedItem.append(corrected);
                }
            } else {
                if (cleanedItem.length() > 0) cleanedItem.append(" ");
                cleanedItem.append(corrected);
            }
        }

        row.item = cleanedItem.length() > 0 ? cleanedItem.toString() : null;
    }

    private static boolean isPartOfItemName(String text, String fullItemName) {
        return fullItemName.contains("1/4") ||
                fullItemName.contains("10") && fullItemName.contains("Tortilla") ||
                fullItemName.contains("8") && fullItemName.contains("Shells") ||
                fullItemName.contains("348") ||
                fullItemName.contains("158") ||
                text.matches("[469]") && fullItemName.matches(".*pack.*");
    }

    private static void cleanSizeColumn(ParsedRow row) {
        if (row.size == null) return;

        String[] parts = row.size.split("\\s+");
        StringBuilder cleaned = new StringBuilder();

        for (String part : parts) {
            if (part.matches("(?i)(Each|Bag|Box|Tube|Jug|Bottle|Inner|CAN|Stick|Tub|Machine)")) {
                if (cleaned.length() > 0) cleaned.append(" ");
                cleaned.append(part);
                continue;
            }

            String corrected = correctCommonMisreads(part);

            if (corrected == null || corrected.trim().isEmpty()) {
                continue;
            }

            if (corrected.matches("\\d+")) {
                int num = Integer.parseInt(corrected);
                if (num < 200) {
                    if (cleaned.length() > 0) cleaned.append(" ");
                    cleaned.append(corrected);
                }
            } else {
                if (cleaned.length() > 0) cleaned.append(" ");
                cleaned.append(corrected);
            }
        }

        row.size = cleaned.length() > 0 ? cleaned.toString() : null;
    }

    public static class ParsedRowWithPosition {
        ParsedRow parsed;
        int yPosition;
    }

    // Improved Pass 2: More lenient proximity matching
    private static void pass2SmartProximityImproved(List<VisionOcr.TextBlock> pass2Numbers,
                                                    Map<Table, List<ParsedRowWithPosition>> pass1Results,
                                                    List<Table> tables) {
        System.out.println("\n=== PASS 2: IMPROVED SMART PROXIMITY MATCHING ===");

        int filled = 0;
        int rejected = 0;
        int reviewFlagged = 0;

        for (Table table : tables) {
            if (!table.name.contains("5Column") && !table.name.contains("3Column")) continue;

            System.out.println("\nProcessing " + table.name + "...");

            List<ParsedRowWithPosition> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            Map<String, Integer> columnX = new HashMap<>();
            for (String columnName : Arrays.asList("OPEN", "SWING", "CLOSE", "COUNT")) {
                ColumnBoundary boundary = table.columnBoundaries.get(columnName);
                if (boundary != null) {
                    columnX.put(columnName, boundary.headerX);
                }
            }

            if (columnX.isEmpty()) continue;

            for (VisionOcr.TextBlock num : pass2Numbers) {
                if (num.x < table.xStart || num.x >= table.xEnd) continue;

                String column = null;
                int minDistX = Integer.MAX_VALUE;

                for (Map.Entry<String, Integer> entry : columnX.entrySet()) {
                    int dist = Math.abs(num.x - entry.getValue());
                    if (dist < minDistX) {
                        minDistX = dist;
                        column = entry.getKey();
                    }
                }

                if (minDistX > 150) {
                    rejected++;
                    continue;
                }

                ParsedRowWithPosition closestRow = null;
                int minDistY = Integer.MAX_VALUE;

                for (ParsedRowWithPosition row : rows) {
                    int dist = Math.abs(num.y - row.yPosition);
                    if (dist < minDistY) {
                        minDistY = dist;
                        closestRow = row;
                    }
                }

                boolean acceptNumber = false;
                boolean needsReview = false;

                if (minDistY <= 50) {
                    acceptNumber = true;
                } else if (minDistY <= 120) {
                    acceptNumber = true;
                    needsReview = true;
                } else if (minDistY <= 200 && minDistX <= 50) {
                    acceptNumber = true;
                    needsReview = true;
                }

                if (!acceptNumber) {
                    System.out.println("  ⚠ Rejected " + num.text + " - too far (X:" + minDistX + "px, Y:" + minDistY + "px)");
                    rejected++;
                    continue;
                }

                ParsedRow row = closestRow.parsed;
                boolean changed = false;
                String confidence = needsReview ? " [REVIEW]" : " [CONFIDENT]";

                switch (column) {
                    case "OPEN":
                        if (row.open == null || row.open.isEmpty()) {
                            row.open = num.text;
                            if (needsReview) {
                                row.openNeedsReview = true;
                                row.openIssue = "Auto-filled with medium confidence (Y-dist:" + minDistY + "px)";
                            }
                            changed = true;
                            filled++;
                            if (needsReview) reviewFlagged++;
                        }
                        break;
                    case "SWING":
                        if (row.swing == null || row.swing.isEmpty()) {
                            row.swing = num.text;
                            if (needsReview) {
                                row.swingNeedsReview = true;
                                row.swingIssue = "Auto-filled with medium confidence (Y-dist:" + minDistY + "px)";
                            }
                            changed = true;
                            filled++;
                            if (needsReview) reviewFlagged++;
                        }
                        break;
                    case "CLOSE":
                        if (row.close == null || row.close.isEmpty()) {
                            row.close = num.text;
                            if (needsReview) {
                                row.closeNeedsReview = true;
                                row.closeIssue = "Auto-filled with medium confidence (Y-dist:" + minDistY + "px)";
                            }
                            changed = true;
                            filled++;
                            if (needsReview) reviewFlagged++;
                        }
                        break;
                    case "COUNT":
                        if (row.count == null || row.count.isEmpty()) {
                            row.count = num.text;
                            if (needsReview) {
                                row.countNeedsReview = true;
                                row.countIssue = "Auto-filled with medium confidence (Y-dist:" + minDistY + "px)";
                            }
                            changed = true;
                            filled++;
                            if (needsReview) reviewFlagged++;
                        }
                        break;
                }

                if (changed) {
                    String itemName = row.item != null ? row.item : "unknown";
                    System.out.println("  ✓ Filled " + column + " = " + num.text +
                            " for '" + itemName + "' (Y:" + minDistY + "px, X:" + minDistX + "px)" + confidence);
                }
            }
        }

        System.out.println("\nImproved Pass 2 Results:");
        System.out.println("  Fields filled: " + filled);
        System.out.println("  Auto-flagged for review: " + reviewFlagged);
        System.out.println("  Numbers rejected: " + rejected);
        System.out.println("  Acceptance rate: " + String.format("%.1f%%", (filled * 100.0 / (filled + rejected))));
    }

    // Add this improved method (don't remove the old one, add alongside):
    public static List<Row> groupIntoRowsAdaptive(List<VisionOcr.TextBlock> blocks, Table table) {
        List<Row> rows = new ArrayList<>();
        if (blocks.isEmpty()) return rows;

        // Separate item-column blocks from quantity blocks
        List<VisionOcr.TextBlock> itemBlocks = new ArrayList<>();
        List<VisionOcr.TextBlock> quantityBlocks = new ArrayList<>();

        ColumnBoundary itemBoundary = table.columnBoundaries.get("ITEM");
        int itemEndX = (itemBoundary != null) ? itemBoundary.xEnd : table.xStart + 300;

        for (VisionOcr.TextBlock block : blocks) {
            if (block.x < itemEndX && !block.text.matches("\\d+")) {
                itemBlocks.add(block);
            } else {
                quantityBlocks.add(block);
            }
        }

        // Sort item blocks by Y position
        itemBlocks.sort(Comparator.comparingInt(b -> b.y));

        // Create rows anchored by item names
        for (VisionOcr.TextBlock itemBlock : itemBlocks) {
            Row row = new Row(itemBlock.y);
            row.blocks.add(itemBlock);

            // Find all quantity blocks within reasonable Y range of this item
            // Use GENEROUS tolerance since handwriting varies
            int yMin = itemBlock.y - 40;
            int yMax = itemBlock.y + itemBlock.height + 40;

            for (VisionOcr.TextBlock qtyBlock : quantityBlocks) {
                if (qtyBlock.y >= yMin && qtyBlock.y <= yMax) {
                    row.blocks.add(qtyBlock);
                }
            }

            row.blocks.sort(Comparator.comparingInt(b -> b.x));
            rows.add(row);
        }

        return rows;
    }

    // Add this method - determines if a block is in the "count" area for 2-column tables
    private static boolean isInCountArea(VisionOcr.TextBlock block, Table table) {
        // For 2-column tables, anything in the right half is a count
        int tableMidpoint = (table.xStart + table.xEnd) / 2;
        return block.x > tableMidpoint;
    }
    /**
     * PASS 3: Ultra-aggressive masking - ONLY expose number cell regions
     * Masks headers, sub-headers, item column, everything except number cells
     */
    public static String createNumberCellOnlyMask(String originalImagePath,
                                                  List<Table> tables,
                                                  Map<Table, List<ParsedRowWithPosition>> pass1Results) throws IOException {
        BufferedImage original = ImageIO.read(new File(originalImagePath));
        BufferedImage masked = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = masked.createGraphics();

        // Start with completely black image
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, original.getWidth(), original.getHeight());

        // Now expose ONLY the number cell regions
        for (Table table : tables) {
            List<ParsedRowWithPosition> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            // Get number column boundaries
            Map<String, ColumnBoundary> numberColumns = new HashMap<>();
            for (String col : Arrays.asList("OPEN", "SWING", "CLOSE", "COUNT")) {
                ColumnBoundary boundary = table.columnBoundaries.get(col);
                if (boundary != null) {
                    numberColumns.put(col, boundary);
                }
            }

            if (numberColumns.isEmpty()) continue;

            // For each row, expose number cell regions
            for (ParsedRowWithPosition rowPos : rows) {
                int rowY = rowPos.yPosition;
                int cellHeight = 60; // Generous cell height
                int cellYStart = rowY - 20;
                int cellYEnd = rowY + 40;

                // Expose each number column cell for this row
                for (ColumnBoundary colBoundary : numberColumns.values()) {
                    int cellXStart = colBoundary.xStart - 10;
                    int cellXEnd = colBoundary.xEnd + 10;
                    int cellWidth = cellXEnd - cellXStart;

                    // Copy original image pixels for this cell region
                    g.drawImage(original,
                            cellXStart, cellYStart, cellXEnd, cellYEnd,  // destination
                            cellXStart, cellYStart, cellXEnd, cellYEnd,  // source
                            null);
                }
            }
        }

        g.dispose();

        String maskedPath = "masked_pass3_cells_only.png";
        ImageIO.write(masked, "png", new File(maskedPath));
        System.out.println("✓ Created Pass 3 ultra-masked image (number cells only)");

        return maskedPath;
    }
    private static void pass3AggressiveFill(List<VisionOcr.TextBlock> pass3Numbers,
                                            Map<Table, List<ParsedRowWithPosition>> pass1Results,
                                            List<Table> tables) {
        System.out.println("\n=== PASS 3: AGGRESSIVE CELL-BASED FILL ===");

        int filled = 0;
        int rejected = 0;

        for (Table table : tables) {
            if (!table.name.contains("5Column") && !table.name.contains("3Column")) continue;

            List<ParsedRowWithPosition> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            Map<String, Integer> columnX = new HashMap<>();
            for (String columnName : Arrays.asList("OPEN", "SWING", "CLOSE", "COUNT")) {
                ColumnBoundary boundary = table.columnBoundaries.get(columnName);
                if (boundary != null) {
                    columnX.put(columnName, boundary.headerX);
                }
            }

            if (columnX.isEmpty()) continue;

            for (VisionOcr.TextBlock num : pass3Numbers) {
                if (num.x < table.xStart || num.x >= table.xEnd) continue;

                // Find closest column
                String column = null;
                int minDistX = Integer.MAX_VALUE;
                for (Map.Entry<String, Integer> entry : columnX.entrySet()) {
                    int dist = Math.abs(num.x - entry.getValue());
                    if (dist < minDistX) {
                        minDistX = dist;
                        column = entry.getKey();
                    }
                }

                if (minDistX > 100) {  // Tighter X threshold since we know cells
                    rejected++;
                    continue;
                }

                // Find closest row
                ParsedRowWithPosition closestRow = null;
                int minDistY = Integer.MAX_VALUE;
                for (ParsedRowWithPosition row : rows) {
                    int dist = Math.abs(num.y - row.yPosition);
                    if (dist < minDistY) {
                        minDistY = dist;
                        closestRow = row;
                    }
                }

                // More lenient Y distance since we masked aggressively
                if (minDistY > 80) {
                    rejected++;
                    continue;
                }

                ParsedRow row = closestRow.parsed;
                boolean changed = false;
                boolean needsReview = minDistY > 50 || minDistX > 60;

                switch (column) {
                    case "OPEN":
                        if (row.open == null || row.open.isEmpty()) {
                            row.open = num.text;
                            if (needsReview) {
                                row.openNeedsReview = true;
                                row.openIssue = "Pass 3 auto-fill (Y:" + minDistY + "px, X:" + minDistX + "px)";
                            }
                            changed = true;
                            filled++;
                            System.out.println("  ✓ Pass 3 filled OPEN = " + num.text + " for '" + row.item + "'");
                        }
                        break;
                    case "SWING":
                        if (row.swing == null || row.swing.isEmpty()) {
                            row.swing = num.text;
                            if (needsReview) {
                                row.swingNeedsReview = true;
                                row.swingIssue = "Pass 3 auto-fill (Y:" + minDistY + "px, X:" + minDistX + "px)";
                            }
                            changed = true;
                            filled++;
                            System.out.println("  ✓ Pass 3 filled SWING = " + num.text + " for '" + row.item + "'");
                        }
                        break;
                    case "CLOSE":
                        if (row.close == null || row.close.isEmpty()) {
                            row.close = num.text;
                            if (needsReview) {
                                row.closeNeedsReview = true;
                                row.closeIssue = "Pass 3 auto-fill (Y:" + minDistY + "px, X:" + minDistX + "px)";
                            }
                            changed = true;
                            filled++;
                            System.out.println("  ✓ Pass 3 filled CLOSE = " + num.text + " for '" + row.item + "'");
                        }
                        break;
                    case "COUNT":
                        if (row.count == null || row.count.isEmpty()) {
                            row.count = num.text;
                            if (needsReview) {
                                row.countNeedsReview = true;
                                row.countIssue = "Pass 3 auto-fill (Y:" + minDistY + "px, X:" + minDistX + "px)";
                            }
                            changed = true;
                            filled++;
                            System.out.println("  ✓ Pass 3 filled COUNT = " + num.text + " for '" + row.item + "'");
                        }
                        break;
                }
            }
        }

        System.out.println("\nPass 3 Results:");
        System.out.println("  Additional fields filled: " + filled);
        System.out.println("  Numbers rejected: " + rejected);
    }

    public static void main(String[] args) {
        String imagePath = "images/main_filled_form.JPG";

        // Initialize ItemMatcher
        ItemMatcher itemMatcher = new ItemMatcher();

        System.out.println("========== PASS 1: Full Page OCR ==========");
        List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);

        // Apply common misreads to blocks early
        blocks = applyCommonMisreadsToBlocks(blocks);

        List<Table> tables = segmentTables(blocks);

        Map<Table, List<ParsedRowWithPosition>> pass1ResultsMap = new HashMap<>();

        for (Table table : tables) {
            System.out.println("\n=== " + table.name + " ===");
            System.out.println("Headers: " + table.getHeaderTexts());

            List<Row> rows;
            if (table.type.contains("RAW_WASTE") && (table.type.contains("5Column") || table.type.contains("3Column"))) {
                rows = groupIntoRows(table.data, 8); // Very strict for multi-column
            } else {
                rows = groupIntoRows(table.data, 12); // Moderate for 2-column
            }

            // Filter out sub-header rows
            rows = filterOutSubHeaderRows(rows, table);

            System.out.println("Total rows: " + rows.size());

            int dynamicItemEnd = findItemColumnEnd(rows, table);

            System.out.println("\nParsed data rows:");
            List<ParsedRowWithPosition> tableResults = new ArrayList<>();
            int dataRowCount = 0;

            for (Row row : rows) {
                ParsedRow parsed = parseRowIntoColumns(row, table, dynamicItemEnd);

                if (parsed == null) continue; // Skip invalid rows

                // === MATCH ITEM NAME AGAINST MASTER LIST ===
                if (parsed.item != null && !parsed.item.trim().isEmpty()) {
                    boolean isCompletedWaste = table.type.equals("COMPLETED_WASTE_2COL");
                    String matchedItem = itemMatcher.matchItem(parsed.item, isCompletedWaste);
                    parsed.item = matchedItem;
                }

                if (isSubHeader(parsed)) continue;

                correctQuantityMisreads(parsed);

                // Only clean item names for raw waste with quantity columns
                if (table.type.contains("RAW_WASTE") && hasQuantityColumns(table)) {
                    cleanItemNames(parsed);
                }

                cleanSizeColumn(parsed);
                cleanQuantityFields(parsed);
                WasteFormReviewGenerator.flagFieldsForReview(parsed);

                // Skip completely empty rows
                if (parsed.item == null && parsed.size == null &&
                        parsed.open == null && parsed.swing == null &&
                        parsed.close == null && parsed.count == null) {
                    continue;
                }

                ParsedRowWithPosition rowWithPos = new ParsedRowWithPosition();
                rowWithPos.parsed = parsed;
                rowWithPos.yPosition = row.yPosition;
                tableResults.add(rowWithPos);

                dataRowCount++;
                System.out.println("  " + parsed);
            }

            System.out.println("Data rows found: " + dataRowCount);
            pass1ResultsMap.put(table, tableResults);
        }

        // ========== PASS 2: Masked OCR ==========
        System.out.println("\n========== PASS 2: MASKED OCR ==========");
        try {
            String maskedImagePath = createIntelligentMaskedImage(imagePath, tables, blocks);
            String upscaledPath = upscaleImage(maskedImagePath, 2.0);

            System.out.println("\nRunning OCR on upscaled masked image...");
            List<VisionOcr.TextBlock> pass2Blocks = VisionOcr.performOcrWithBoundingBoxes(upscaledPath, false);

            for (VisionOcr.TextBlock block : pass2Blocks) {
                block.x = block.x / 2;
                block.y = block.y / 2;
            }

            List<VisionOcr.TextBlock> pass2Numbers = new ArrayList<>();
            for (VisionOcr.TextBlock block : pass2Blocks) {
                String corrected = correctCommonMisreads(block.text);
                if (corrected != null && corrected.matches("\\d+")) {
                    block.text = corrected;
                    pass2Numbers.add(block);
                }
            }

            System.out.println("Pass 2 detected " + pass2Numbers.size() + " numbers (after corrections)!");

            pass2SmartProximityImproved(pass2Numbers, pass1ResultsMap, tables);

            // ========== FINAL RESULTS ==========
            System.out.println("\n========== FINAL RESULTS ==========");
            for (Table table : tables) {
                System.out.println("\n=== " + table.name + " FINAL DATA ===");
                List<ParsedRowWithPosition> results = pass1ResultsMap.get(table);
                if (results != null) {
                    for (ParsedRowWithPosition rowWithPos : results) {
                        System.out.println(rowWithPos.parsed);

                        // Show review flags
                        if (rowWithPos.parsed.openNeedsReview) {
                            System.out.println("    ⚠ OPEN needs review: " + rowWithPos.parsed.openIssue);
                        }
                        if (rowWithPos.parsed.swingNeedsReview) {
                            System.out.println("    ⚠ SWING needs review: " + rowWithPos.parsed.swingIssue);
                        }
                        if (rowWithPos.parsed.closeNeedsReview) {
                            System.out.println("    ⚠ CLOSE needs review: " + rowWithPos.parsed.closeIssue);
                        }
                        if (rowWithPos.parsed.countNeedsReview) {
                            System.out.println("    ⚠ COUNT needs review: " + rowWithPos.parsed.countIssue);
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error in Pass 2: " + e.getMessage());
            e.printStackTrace();
        }
        // ========== PASS 3: Ultra-Aggressive Masking (OPTIONAL) ==========
        if (ENABLE_PASS_3_AGGRESSIVE_MASK) {
            System.out.println("\n========== PASS 3: ULTRA-AGGRESSIVE CELL MASKING ==========");
            try {
                String pass3MaskedPath = createNumberCellOnlyMask(imagePath, tables, pass1ResultsMap);
                String pass3UpscaledPath = upscaleImage(pass3MaskedPath, 2.5); // Even more upscaling

                System.out.println("\nRunning OCR on Pass 3 cell-only masked image...");
                List<VisionOcr.TextBlock> pass3Blocks = VisionOcr.performOcrWithBoundingBoxes(pass3UpscaledPath, false);

                // Scale coordinates back
                for (VisionOcr.TextBlock block : pass3Blocks) {
                    block.x = (int)(block.x / 2.5);
                    block.y = (int)(block.y / 2.5);
                }

                // Extract only numeric values
                List<VisionOcr.TextBlock> pass3Numbers = new ArrayList<>();
                for (VisionOcr.TextBlock block : pass3Blocks) {
                    String corrected = correctCommonMisreads(block.text);
                    if (corrected != null && corrected.matches("\\d+")) {
                        block.text = corrected;
                        pass3Numbers.add(block);
                    }
                }

                System.out.println("Pass 3 detected " + pass3Numbers.size() + " numbers!");

                // Reuse your existing proximity matching with more lenient thresholds
                pass3AggressiveFill(pass3Numbers, pass1ResultsMap, tables);

            } catch (IOException e) {
                System.err.println("⚠️ Pass 3 failed, but Pass 1+2 data preserved: " + e.getMessage());
                // Fail gracefully - your existing data is safe
            }
        }

    }

// Add these helper methods:

    private static List<VisionOcr.TextBlock> applyCommonMisreadsToBlocks(List<VisionOcr.TextBlock> blocks) {
        for (VisionOcr.TextBlock block : blocks) {
            String corrected = correctCommonMisreads(block.text);
            if (corrected != null && !corrected.equals(block.text)) {
                System.out.println("  🔧 Corrected block: '" + block.text + "' → '" + corrected + "'");
                block.text = corrected;
            }
        }
        return blocks;
    }

    private static List<Row> filterOutSubHeaderRows(List<Row> rows, Table table) {
        return rows.stream()
                .filter(row -> {
                    // Check if this row contains a sub-header
                    for (VisionOcr.TextBlock block : row.blocks) {
                        String text = block.text.toUpperCase().trim();

                        // Check against known sub-headers
                        if (SUB_HEADERS.contains(text)) {
                            System.out.println("  🗑️ Filtered sub-header row: " + text);
                            return false;
                        }

                        // Filter out category labels
                        if (text.equals("BREAKFAST SAUCES") || text.equals("SALAD AND TOPPINGS") ||
                                text.equals("PREP TABLE") || text.equals("SMOOTHIE MACHINE") ||
                                text.equals("SHAKE AND SUNDAE") || text.equals("POP") ||
                                text.equals("MISCELLANEOUS") || text.equals("BREAKFAST BREAD") ||
                                text.equals("MEAT AND CHICKEN") || text.equals("POTATO PRODUCT") ||
                                text.equals("EGGS") || text.equals("BREAKFAST MEAT") ||
                                text.equals("CHEESES") || text.equals("SEASONINGS")) {
                            System.out.println("  🗑️ Filtered category label: " + text);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
}