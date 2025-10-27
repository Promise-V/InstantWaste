package com.instantwaste;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

/**
 * Generates review data with confidence flags for Flutter UI
 * UPDATED: Supports both ParsedRowWithPosition and ValidatedRow objects
 */
public class WasteFormReviewGenerator {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * NEW: Generate JSON from ValidatedRow objects (ImprovedTableParser output)
     */
    public static String generateReviewJSONFromValidatedRows(
            Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> tableResults) {

        Map<String, Object> output = new HashMap<>();

        // Stats at root level
        int totalItems = 0;
        int totalUnmatched = 0;
        int totalFieldsNeedingReview = 0;
        int totalEmptyFields = 0;

        // Process tables
        List<Map<String, Object>> tables = new ArrayList<>();

        for (Map.Entry<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> entry : tableResults.entrySet()) {
            TableSegmenter.Table table = entry.getKey();
            List<ImprovedTableParser.ValidatedRow> validatedRows = entry.getValue();

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("tableName", table.name);
            tableData.put("tableType", getTableType(table.name));

            List<Map<String, Object>> rowsData = new ArrayList<>();

            for (ImprovedTableParser.ValidatedRow row : validatedRows) {
                Map<String, Object> rowData = new HashMap<>();

                // Item is always a string (validated from master list)
                rowData.put("item", row.itemName);
                totalItems++;

                // Process each field with the nested structure
                if (table.name.contains("5Column")) {
                    rowData.put("open", createFieldObject(row.open, row.openNeedsReview, row.openIssue));
                    rowData.put("swing", createFieldObject(row.swing, row.swingNeedsReview, row.swingIssue));
                    rowData.put("close", createFieldObject(row.close, row.closeNeedsReview, row.closeIssue));
                    rowData.put("size", createFieldObject(row.size, false, null)); // Size rarely needs review
                    rowData.put("count", createFieldObject(null, false, null)); // Empty for 5-col

                    // Update counts
                    totalFieldsNeedingReview += countIfTrue(row.openNeedsReview);
                    totalFieldsNeedingReview += countIfTrue(row.swingNeedsReview);
                    totalFieldsNeedingReview += countIfTrue(row.closeNeedsReview);

                    totalEmptyFields += countIfEmpty(row.open);
                    totalEmptyFields += countIfEmpty(row.swing);
                    totalEmptyFields += countIfEmpty(row.close);

                } else if (table.name.contains("3Column")) {
                    rowData.put("count", createFieldObject(row.count, row.countNeedsReview, row.countIssue));
                    rowData.put("size", createFieldObject(row.size, false, null));
                    rowData.put("open", createFieldObject(null, false, null)); // Empty for 3-col
                    rowData.put("swing", createFieldObject(null, false, null)); // Empty for 3-col
                    rowData.put("close", createFieldObject(null, false, null)); // Empty for 3-col

                    totalFieldsNeedingReview += countIfTrue(row.countNeedsReview);
                    totalEmptyFields += countIfEmpty(row.count);

                } else if (table.name.contains("2Column")) {
                    rowData.put("count", createFieldObject(row.count, row.countNeedsReview, row.countIssue));
                    rowData.put("size", createFieldObject(null, false, null)); // Empty for 2-col
                    rowData.put("open", createFieldObject(null, false, null)); // Empty for 2-col
                    rowData.put("swing", createFieldObject(null, false, null)); // Empty for 2-col
                    rowData.put("close", createFieldObject(null, false, null)); // Empty for 2-col

                    totalFieldsNeedingReview += countIfTrue(row.countNeedsReview);
                    totalEmptyFields += countIfEmpty(row.count);
                }

                rowsData.add(rowData);
            }

            tableData.put("rows", rowsData);
            tables.add(tableData);
        }

        // Build final output in your desired format
        output.put("itemsDetected", totalItems);
        output.put("itemsUnmatched", totalUnmatched); // This will be low with validated rows
        output.put("tables", tables);
        output.put("fieldsNeedingReview", totalFieldsNeedingReview);
        output.put("emptyFields", totalEmptyFields);
        output.put("accuracy", calculateAccuracy(totalItems, totalUnmatched));

        return gson.toJson(output);
    }

    /**
     * Creates the nested field object structure
     */
    private static Map<String, Object> createFieldObject(String value, boolean needsReview, String issue) {
        Map<String, Object> field = new HashMap<>();
        field.put("value", value != null ? value : "");
        field.put("isEmpty", value == null || value.isEmpty());
        field.put("needsReview", needsReview);
        field.put("issue", issue != null ? issue : "");
        return field;
    }

    private static int countIfTrue(boolean value) {
        return value ? 1 : 0;
    }

    private static int countIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? 1 : 0;
    }

    private static double calculateAccuracy(int itemsDetected, int itemsUnmatched) {
        if (itemsDetected == 0) return 0.0;
        return (double) (itemsDetected - itemsUnmatched) / itemsDetected * 100.0;
    }

    /**
     * ORIGINAL: Generate JSON for ParsedRowWithPosition objects (keep for backward compatibility)
     */
    public static String generateReviewJSON(Map<TableSegmenter.Table, List<TableSegmenter.ParsedRowWithPosition>> allResults) {
        Map<String, Object> output = new HashMap<>();
        output.put("timestamp", System.currentTimeMillis());
        output.put("totalTables", allResults.size());

        List<Map<String, Object>> tables = new ArrayList<>();
        int totalFieldsNeedingReview = 0;
        int totalEmptyFields = 0;
        int totalConfidentFields = 0;
        int totalItems = 0;
        int totalUnmatched = 0;

        for (Map.Entry<TableSegmenter.Table, List<TableSegmenter.ParsedRowWithPosition>> entry : allResults.entrySet()) {
            TableSegmenter.Table table = entry.getKey();
            List<TableSegmenter.ParsedRowWithPosition> rows = entry.getValue();

            // Debug info
            System.out.println("🔍 PARSED TABLE:");
            System.out.println("   Table name: " + table.name);
            System.out.println("   Headers: " + table.getHeaderTexts());
            System.out.println("   Row count: " + rows.size());
            if (!rows.isEmpty()) {
                System.out.println("   First item: " + rows.get(0).parsed.item);
            }
            System.out.println();

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("tableName", table.name);
            tableData.put("tableType", getTableType(table.name));

            List<Map<String, Object>> rowsData = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                TableSegmenter.ParsedRow row = rows.get(i).parsed;
                Map<String, Object> rowData = new HashMap<>();

                rowData.put("rowIndex", i);
                rowData.put("item", row.item != null ?
                        createFieldData(row.item, false, null) :
                        createFieldData(null, false, "No item detected"));
                rowData.put("size", createFieldData(row.size, false, null));

                // Count items and unmatched
                if (row.item != null && !row.item.isEmpty()) {
                    totalItems++;
                } else {
                    totalUnmatched++;
                }

                // Add fields with confidence flags
                if (table.name.contains("5Column")) {
                    Map<String, Object> openField = createFieldData(row.open, row.openNeedsReview, row.openIssue);
                    Map<String, Object> swingField = createFieldData(row.swing, row.swingNeedsReview, row.swingIssue);
                    Map<String, Object> closeField = createFieldData(row.close, row.closeNeedsReview, row.closeIssue);

                    rowData.put("open", openField);
                    rowData.put("swing", swingField);
                    rowData.put("close", closeField);

                    // Count field states
                    totalFieldsNeedingReview += countIfTrue((Boolean)openField.get("needsReview"));
                    totalFieldsNeedingReview += countIfTrue((Boolean)swingField.get("needsReview"));
                    totalFieldsNeedingReview += countIfTrue((Boolean)closeField.get("needsReview"));

                    totalEmptyFields += countIfTrue((Boolean)openField.get("isEmpty"));
                    totalEmptyFields += countIfTrue((Boolean)swingField.get("isEmpty"));
                    totalEmptyFields += countIfTrue((Boolean)closeField.get("isEmpty"));

                    totalConfidentFields += countIfConfident(openField);
                    totalConfidentFields += countIfConfident(swingField);
                    totalConfidentFields += countIfConfident(closeField);

                } else if (table.name.contains("3Column")) {
                    Map<String, Object> countField = createFieldData(row.count, row.countNeedsReview, row.countIssue);
                    rowData.put("count", countField);

                    totalFieldsNeedingReview += countIfTrue((Boolean)countField.get("needsReview"));
                    totalEmptyFields += countIfTrue((Boolean)countField.get("isEmpty"));
                    totalConfidentFields += countIfConfident(countField);
                }

                rowsData.add(rowData);
            }

            tableData.put("rows", rowsData);
            tables.add(tableData);
        }

        output.put("tables", tables);
        output.put("stats", Map.of(
                "itemsDetected", totalItems,
                "itemsUnmatched", totalUnmatched,
                "accuracy", calculateAccuracy(totalItems, totalUnmatched),
                "fieldsNeedingReview", totalFieldsNeedingReview,
                "emptyFields", totalEmptyFields,
                "confidentFields", totalConfidentFields,
                "totalFields", countTotalFields(allResults)
        ));
        output.put("needsReview", totalFieldsNeedingReview > 0);

        return gson.toJson(output);
    }

    // ========== HELPER METHODS ==========

    private static Map<String, Object> createValidatedItemData(String value, int yPosition) {
        Map<String, Object> field = new HashMap<>();
        field.put("value", value);
        field.put("validated", true);
        field.put("needsReview", false);
        field.put("isEmpty", value == null || value.isEmpty());
        field.put("position", Map.of("y", yPosition));
        return field;
    }

    private static Map<String, Object> createFieldData(String value, boolean needsReview, String issue) {
        Map<String, Object> field = new HashMap<>();
        field.put("value", value);
        field.put("needsReview", needsReview);
        field.put("issue", issue);
        field.put("isEmpty", value == null || value.isEmpty());
        return field;
    }

    private static String getTableType(String tableName) {
        if (tableName.contains("5Column")) return "RAW_WASTE_5COL";
        if (tableName.contains("3Column")) return "RAW_WASTE_3COL";
        return "COMPLETED_WASTE_2COL";
    }

    private static int countTotalFieldsFromValidated(Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> tableResults) {
        int total = 0;
        for (Map.Entry<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> entry : tableResults.entrySet()) {
            String tableName = entry.getKey().name;
            int rowCount = entry.getValue().size();

            if (tableName.contains("5Column")) {
                total += rowCount * 3; // OPEN, SWING, CLOSE
            } else if (tableName.contains("3Column") || tableName.contains("2Column")) {
                total += rowCount * 1; // COUNT
            }
        }
        return total;
    }

    private static int countTotalFields(Map<TableSegmenter.Table, List<TableSegmenter.ParsedRowWithPosition>> allResults) {
        int total = 0;
        for (Map.Entry<TableSegmenter.Table, List<TableSegmenter.ParsedRowWithPosition>> entry : allResults.entrySet()) {
            String tableName = entry.getKey().name;
            int rowCount = entry.getValue().size();

            if (tableName.contains("5Column")) {
                total += rowCount * 3; // OPEN, SWING, CLOSE
            } else if (tableName.contains("3Column")) {
                total += rowCount * 1; // COUNT
            }
        }
        return total;
    }

    private static int countIfTrue(Boolean value) {
        return (value != null && value) ? 1 : 0;
    }

    private static int countIfConfident(Map<String, Object> field) {
        boolean isEmpty = (Boolean) field.get("isEmpty");
        boolean needsReview = (Boolean) field.get("needsReview");
        return (!isEmpty && !needsReview) ? 1 : 0;
    }
    /**
     * Flag fields that need manual review based on validation rules
     */
    public static void flagFieldsForReview(TableSegmenter.ParsedRow row) {
        // Check OPEN field
        if (row.open != null && !row.open.isEmpty()) {
            if (!row.open.matches("\\d+")) {
                row.openNeedsReview = true;
                row.openIssue = "Contains non-numeric characters";
            } else {
                int value = Integer.parseInt(row.open);
                if (value > 999) {
                    row.openNeedsReview = true;
                    row.openIssue = "Number seems unusually large";
                }
            }
        }

        // Check SWING field
        if (row.swing != null && !row.swing.isEmpty()) {
            if (!row.swing.matches("\\d+")) {
                row.swingNeedsReview = true;
                row.swingIssue = "Contains non-numeric characters";
            } else {
                int value = Integer.parseInt(row.swing);
                if (value > 999) {
                    row.swingNeedsReview = true;
                    row.swingIssue = "Number seems unusually large";
                }
            }
        }

        // Check CLOSE field
        if (row.close != null && !row.close.isEmpty()) {
            if (!row.close.matches("\\d+")) {
                row.closeNeedsReview = true;
                row.closeIssue = "Contains non-numeric characters";
            } else {
                int value = Integer.parseInt(row.close);
                if (value > 999) {
                    row.closeNeedsReview = true;
                    row.closeIssue = "Number seems unusually large";
                }
            }
        }

        // Check COUNT field
        if (row.count != null && !row.count.isEmpty()) {
            if (!row.count.matches("\\d+")) {
                row.countNeedsReview = true;
                row.countIssue = "Contains non-numeric characters";
            } else {
                int value = Integer.parseInt(row.count);
                if (value > 999) {
                    row.countNeedsReview = true;
                    row.countIssue = "Number seems unusually large";
                }
            }
        }
    }
}