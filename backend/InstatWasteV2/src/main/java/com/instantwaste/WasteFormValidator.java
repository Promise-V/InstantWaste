package com.instantwaste;
import java.util.*;

public class WasteFormValidator {

    public static class ValidationResult {
        public boolean isValid;
        public List<String> errors;
        public List<String> warnings;

        public ValidationResult() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.isValid = true;
        }
    }

    /**
     * Validate reviewed waste form data before submission
     */
    public static ValidationResult validateReviewedData(Map<String, Object> reviewedData) {
        ValidationResult result = new ValidationResult();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) reviewedData.get("tables");

        if (tables == null) {
            result.errors.add("No tables found in reviewed data");
            result.isValid = false;
            return result;
        }

        int totalFields = 0;
        int filledFields = 0;
        int emptyFields = 0;

        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("tableName");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) table.get("rows");

            if (rows == null) continue;

            for (Map<String, Object> row : rows) {
                // Validate quantity fields based on table type
                if (tableName.contains("5Column")) {
                    ValidationResult fieldResult = validateField(row, "open", (String) row.get("item"));
                    result.errors.addAll(fieldResult.errors);
                    result.warnings.addAll(fieldResult.warnings);

                    fieldResult = validateField(row, "swing", (String) row.get("item"));
                    result.errors.addAll(fieldResult.errors);
                    result.warnings.addAll(fieldResult.warnings);

                    fieldResult = validateField(row, "close", (String) row.get("item"));
                    result.errors.addAll(fieldResult.errors);
                    result.warnings.addAll(fieldResult.warnings);

                    totalFields += 3;
                    if (getFieldValue(row, "open") != null) filledFields++;
                    if (getFieldValue(row, "swing") != null) filledFields++;
                    if (getFieldValue(row, "close") != null) filledFields++;

                } else if (tableName.contains("3Column")) {
                    ValidationResult fieldResult = validateField(row, "count", (String) row.get("item"));
                    result.errors.addAll(fieldResult.errors);
                    result.warnings.addAll(fieldResult.warnings);

                    totalFields++;
                    if (getFieldValue(row, "count") != null) filledFields++;
                }
            }
        }

        emptyFields = totalFields - filledFields;

        // Warnings (not errors) for suspicious patterns
        if (filledFields == 0) {
            result.warnings.add("No waste recorded - is this correct?");
        }

        if (emptyFields > totalFields * 0.95) {
            result.warnings.add("Less than 5% of fields filled - is this correct?");
        }

        result.isValid = result.errors.isEmpty();

        System.out.println("\n========== VALIDATION SUMMARY ==========");
        System.out.println("Total fields: " + totalFields);
        System.out.println("Filled: " + filledFields);
        System.out.println("Empty: " + emptyFields);
        System.out.println("Errors: " + result.errors.size());
        System.out.println("Warnings: " + result.warnings.size());

        return result;
    }

    private static ValidationResult validateField(Map<String, Object> row, String fieldName, String itemName) {
        ValidationResult result = new ValidationResult();

        @SuppressWarnings("unchecked")
        Map<String, Object> field = (Map<String, Object>) row.get(fieldName);

        if (field == null) return result;

        String value = (String) field.get("value");

        // If field has a value, validate it
        if (value != null && !value.trim().isEmpty()) {
            // Must be numeric
            if (!value.matches("\\d+")) {
                result.errors.add("Invalid value '" + value + "' for " + itemName + " " + fieldName.toUpperCase() + " (must be numeric)");
            } else {
                int numValue = Integer.parseInt(value);

                // Reasonable range check
                if (numValue < 0) {
                    result.errors.add("Negative value for " + itemName + " " + fieldName.toUpperCase());
                } else if (numValue > 999) {
                    result.warnings.add("Large value (" + numValue + ") for " + itemName + " " + fieldName.toUpperCase() + " - is this correct?");
                }
            }
        }

        return result;
    }

    private static String getFieldValue(Map<String, Object> row, String fieldName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> field = (Map<String, Object>) row.get(fieldName);
        if (field == null) return null;
        return (String) field.get("value");
    }
}