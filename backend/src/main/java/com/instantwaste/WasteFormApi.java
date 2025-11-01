package com.instantwaste;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = "*",
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS, RequestMethod.PUT, RequestMethod.DELETE}
)
public class WasteFormApi {

    private final Gson gson = new Gson();
    private final Map<String, Double> progressStore = new HashMap<>();
    private final Map<String, String> messageStore = new HashMap<>();

//    public static void main(String[] args) {
//        SpringApplication.run(WasteFormApi.class, args);
//        System.out.println("\n✅ Instant Waste API Server Started!");
//        System.out.println("🌐 API running at: http://localhost:8080");
//        System.out.println("📝 Health check: http://localhost:8080/api/health\n");
//    }
    /**
     * PASS 2: Fill empty fields from masked OCR numbers
     */
    private void fillEmptyFieldsWithPass2(List<VisionOcr.TextBlock> pass2Numbers,
                                          Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> pass1Results,
                                          List<TableSegmenter.Table> tables) {
        int filled = 0;

        for (TableSegmenter.Table table : tables) {
            if (!table.type.contains("RAW_WASTE")) continue;

            List<ImprovedTableParser.ValidatedRow> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            for (VisionOcr.TextBlock num : pass2Numbers) {
                if (num.x < table.xStart || num.x >= table.xEnd) continue;

                // Find closest column
                String column = findClosestColumn(num, table);
                if (column == null) continue;

                // Find closest row
                ImprovedTableParser.ValidatedRow closestRow = findClosestRow(num, rows);
                if (closestRow == null) continue;

                int distance = Math.abs(num.y - closestRow.anchorY);
                if (distance > 120) continue; // Too far

                boolean needsReview = distance > 65;

                // Fill empty field
                if (fillFieldIfEmpty(closestRow, column, num.text, needsReview, distance)) {
                    filled++;
                    System.out.println("  ✓ Pass 2 filled " + column + " = " + num.text + " for '" + closestRow.itemName + "'");
                }
            }
        }

        System.out.println("Pass 2 filled " + filled + " additional fields");
    }

    /**
     * PASS 3: Fill empty fields from aggressive cell masking
     */
    private void fillEmptyFieldsWithPass3(List<VisionOcr.TextBlock> pass3Numbers,
                                          Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> pass1Results,
                                          List<TableSegmenter.Table> tables) {
        int filled = 0;

        for (TableSegmenter.Table table : tables) {
            if (!table.type.contains("RAW_WASTE")) continue;

            List<ImprovedTableParser.ValidatedRow> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            for (VisionOcr.TextBlock num : pass3Numbers) {
                if (num.x < table.xStart || num.x >= table.xEnd) continue;

                String column = findClosestColumn(num, table);
                if (column == null) continue;

                ImprovedTableParser.ValidatedRow closestRow = findClosestRow(num, rows);
                if (closestRow == null) continue;

                int distance = Math.abs(num.y - closestRow.anchorY);
                if (distance > 80) continue; // Tighter threshold for Pass 3

                boolean needsReview = distance > 50;

                if (fillFieldIfEmpty(closestRow, column, num.text, needsReview, distance)) {
                    filled++;
                    System.out.println("  ✓ Pass 3 filled " + column + " = " + num.text + " for '" + closestRow.itemName + "'");
                }
            }
        }

        System.out.println("Pass 3 filled " + filled + " additional fields");
    }

    /**
     * Find closest column for a number block
     */
    private String findClosestColumn(VisionOcr.TextBlock num, TableSegmenter.Table table) {
        String closestColumn = null;
        int minDist = Integer.MAX_VALUE;

        for (String col : Arrays.asList("OPEN", "SWING", "CLOSE", "COUNT")) {
            TableSegmenter.ColumnBoundary boundary = table.columnBoundaries.get(col);
            if (boundary != null) {
                int dist = Math.abs(num.x - boundary.headerX);
                if (dist < minDist) {
                    minDist = dist;
                    closestColumn = col;
                }
            }
        }

        return (minDist < 150) ? closestColumn : null;
    }

    /**
     * Find closest row for a number block
     */
    private ImprovedTableParser.ValidatedRow findClosestRow(VisionOcr.TextBlock num,
                                                            List<ImprovedTableParser.ValidatedRow> rows) {
        ImprovedTableParser.ValidatedRow closest = null;
        int minDist = Integer.MAX_VALUE;

        for (ImprovedTableParser.ValidatedRow row : rows) {
            int dist = Math.abs(num.y - row.anchorY);
            if (dist < minDist) {
                minDist = dist;
                closest = row;
            }
        }

        return closest;
    }

    /**
     * Fill a field if it's empty, return true if filled
     */
    private boolean fillFieldIfEmpty(ImprovedTableParser.ValidatedRow row, String column,
                                     String value, boolean needsReview, int distance) {
        switch (column) {
            case "OPEN":
                if (row.open == null || row.open.isEmpty()) {
                    row.open = value;
                    if (needsReview) {
                        row.openNeedsReview = true;
                        row.openIssue = "Auto-filled (distance: " + distance + "px)";
                    }
                    return true;
                }
                break;
            case "SWING":
                if (row.swing == null || row.swing.isEmpty()) {
                    row.swing = value;
                    if (needsReview) {
                        row.swingNeedsReview = true;
                        row.swingIssue = "Auto-filled (distance: " + distance + "px)";
                    }
                    return true;
                }
                break;
            case "CLOSE":
                if (row.close == null || row.close.isEmpty()) {
                    row.close = value;
                    if (needsReview) {
                        row.closeNeedsReview = true;
                        row.closeIssue = "Auto-filled (distance: " + distance + "px)";
                    }
                    return true;
                }
                break;
            case "COUNT":
                if (row.count == null || row.count.isEmpty()) {
                    row.count = value;
                    if (needsReview) {
                        row.countNeedsReview = true;
                        row.countIssue = "Auto-filled (distance: " + distance + "px)";
                    }
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * PASS 3: Create ultra-aggressive mask showing only number cells
     */
    private String createNumberCellOnlyMask(String originalImagePath,
                                            List<TableSegmenter.Table> tables,
                                            Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> pass1Results)
            throws IOException {
        java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(new java.io.File(originalImagePath));
        java.awt.image.BufferedImage masked = new java.awt.image.BufferedImage(
                original.getWidth(),
                original.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB
        );
        java.awt.Graphics2D g = masked.createGraphics();

        // Start with black
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, original.getWidth(), original.getHeight());

        // Expose only number cell regions
        for (TableSegmenter.Table table : tables) {
            List<ImprovedTableParser.ValidatedRow> rows = pass1Results.get(table);
            if (rows == null || rows.isEmpty()) continue;

            // Get number column boundaries
            List<TableSegmenter.ColumnBoundary> numberCols = new ArrayList<>();
            for (String col : Arrays.asList("OPEN", "SWING", "CLOSE", "COUNT")) {
                TableSegmenter.ColumnBoundary boundary = table.columnBoundaries.get(col);
                if (boundary != null) {
                    numberCols.add(boundary);
                }
            }

            // Expose each cell
            for (ImprovedTableParser.ValidatedRow row : rows) {
                for (TableSegmenter.ColumnBoundary col : numberCols) {
                    int cellX = col.xStart - 10;
                    int cellY = row.anchorY - 20;
                    int cellWidth = (col.xEnd - col.xStart) + 20;
                    int cellHeight = 60;

                    g.drawImage(original, cellX, cellY, cellX + cellWidth, cellY + cellHeight,
                            cellX, cellY, cellX + cellWidth, cellY + cellHeight, null);
                }
            }
        }

        g.dispose();

        String maskedPath = "masked_pass3_cells_only.png";
        javax.imageio.ImageIO.write(masked, "png", new java.io.File(maskedPath));
        System.out.println("✓ Created Pass 3 ultra-masked image");

        return maskedPath;
    }
    // Helper method to update progress
    private void updateProgress(String sessionId, double progress, String message) {
        progressStore.put(sessionId, progress);
        messageStore.put(sessionId, message);
        System.out.println("🔄 Progress: " + (progress * 100) + "% - " + message);
    }
    // === ADD THIS METHOD - it's your existing process but with progress calls ===
    private void processWithProgress(String sessionId, String imagePath) throws Exception {

        try {
            updateProgress(sessionId, 0.1, "Uploading image...");
            updateProgress(sessionId, 0.2, "Starting OCR analysis...");

            // Initialize components (your existing code)
            ItemMatcher itemMatcher = new ItemMatcher();

            // PASS 1: Full OCR
            updateProgress(sessionId, 0.3, "Performing initial OCR...");
            List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);

            updateProgress(sessionId, 0.4, "Detecting tables...");
            List<TableSegmenter.Table> tables = TableSegmenter.segmentTables(blocks);

            updateProgress(sessionId, 0.5, "Parsing table data...");
            Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> pass1ResultsMap = new HashMap<>();
            for (TableSegmenter.Table table : tables) {
                List<ImprovedTableParser.ValidatedRow> validatedRows =
                        ImprovedTableParser.parseTableWithValidation(table, table.data, itemMatcher);
                pass1ResultsMap.put(table, validatedRows);
            }

            // PASS 2: Masked OCR
            updateProgress(sessionId, 0.6, "Running enhanced OCR pass...");
            System.out.println("\n========== PASS 2: MASKED OCR ==========");
            try {
                String maskedImagePath = TableSegmenter.createIntelligentMaskedImage(imagePath, tables, blocks);
                String upscaledPath = TableSegmenter.upscaleImage(maskedImagePath, 1.5);

                updateProgress(sessionId, 0.7, "Analyzing masked image...");
                List<VisionOcr.TextBlock> pass2Blocks = VisionOcr.performOcrWithBoundingBoxes(upscaledPath, false);

                // Scale coordinates back (your existing code)
                for (VisionOcr.TextBlock block : pass2Blocks) {
                    block.x = (int)(block.x / 1.5); // Was /2
                    block.y = (int)(block.y / 1.5); // Was /2
                }

                // Extract only numeric values (your existing code)
                List<VisionOcr.TextBlock> pass2Numbers = new ArrayList<>();
                for (VisionOcr.TextBlock block : pass2Blocks) {
                    if (block.text.matches("\\d+")) {
                        pass2Numbers.add(block);
                    }
                }

                updateProgress(sessionId, 0.75, "Filling empty fields...");
                System.out.println("Pass 2 detected " + pass2Numbers.size() + " numbers!");
                fillEmptyFieldsWithPass2(pass2Numbers, pass1ResultsMap, tables);

            } catch (Exception e) {
                System.err.println("⚠️ Pass 2 failed, but Pass 1 data preserved: " + e.getMessage());
            }

            // PASS 3: Ultra-Aggressive Cell Masking
            boolean ENABLE_PASS_3 = false;
            if (ENABLE_PASS_3) {
                updateProgress(sessionId, 0.8, "Running final OCR pass...");
                System.out.println("\n========== PASS 3: CELL-ONLY MASKING ==========");
                try {
                    String pass3MaskedPath = createNumberCellOnlyMask(imagePath, tables, pass1ResultsMap);
                    String pass3UpscaledPath = TableSegmenter.upscaleImage(pass3MaskedPath, 2.5);

                    updateProgress(sessionId, 0.85, "Analyzing cell data...");
                    List<VisionOcr.TextBlock> pass3Blocks = VisionOcr.performOcrWithBoundingBoxes(pass3UpscaledPath, false);

                    // Scale coordinates back (your existing code)
                    for (VisionOcr.TextBlock block : pass3Blocks) {
                        block.x = (int)(block.x / 2.5);
                        block.y = (int)(block.y / 2.5);
                    }

                    // Extract only numeric values (your existing code)
                    List<VisionOcr.TextBlock> pass3Numbers = new ArrayList<>();
                    for (VisionOcr.TextBlock block : pass3Blocks) {
                        if (block.text.matches("\\d+")) {
                            pass3Numbers.add(block);
                        }
                    }

                    updateProgress(sessionId, 0.9, "Final validation...");
                    System.out.println("Pass 3 detected " + pass3Numbers.size() + " numbers!");
                    fillEmptyFieldsWithPass3(pass3Numbers, pass1ResultsMap, tables);

                } catch (Exception e) {
                    System.err.println("⚠️ Pass 3 failed, but Pass 1+2 data preserved: " + e.getMessage());
                }
            }

            updateProgress(sessionId, 0.95, "Generating final results...");

            // Generate review JSON (your existing code)
            String reviewJSON = WasteFormReviewGenerator.generateReviewJSONFromValidatedRows(pass1ResultsMap);

            updateProgress(sessionId, 1.0, "Completed! Ready for review.");

            // Store the final result (so Flutter can get it)
            progressStore.put(sessionId + "_result", 1.0); // Using as flag
            messageStore.put(sessionId + "_result", reviewJSON);

            System.out.println("✅ OCR processing complete!");

        } catch (Exception e) {
            updateProgress(sessionId, 0.0, "Error: " + e.getMessage());
            throw e;
        } finally {
            // Your existing cleanup code...
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(imagePath));
                    System.out.println("🗑️ Cleaned up temp file");
                } catch (IOException e) {
                    System.err.println("⚠️ Could not delete temp file: " + e.getMessage());
                }
            }
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Instant Waste API is running",
                "version", "1.0.0"
        ));
    }
    @GetMapping("/")
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Instant Waste API",
                "version", "1.0.0"
        ));
    }

    @GetMapping("")  // This handles /api
    public ResponseEntity<?> apiRoot() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Instant Waste API - v1.0.0"
        ));
    }
    // Check progress
    @GetMapping("/waste-form/progress/{sessionId}")
    public ResponseEntity<?> getProgress(@PathVariable String sessionId) {
        Double progress = progressStore.get(sessionId);
        String message = messageStore.get(sessionId);

        if (progress == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "progress", progress,
                "message", message != null ? message : "Processing..."
        ));
    }
    // === ADD THIS METHOD ===
    @GetMapping("/waste-form/result/{sessionId}")
    public ResponseEntity<?> getResult(@PathVariable String sessionId) {
        String result = messageStore.get(sessionId + "_result");

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        // Clean up
        progressStore.remove(sessionId);
        messageStore.remove(sessionId);
        progressStore.remove(sessionId + "_result");
        messageStore.remove(sessionId + "_result");

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(result);
    }
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/waste-form/process")
    public ResponseEntity<?> processWasteForm(@RequestParam("image") MultipartFile imageFile) {
        String imagePath = null;
        try {
            System.out.println("📸 Received image: " + imageFile.getOriginalFilename());

            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("waste_form_", ".jpg");
            imagePath = tempFile.toString();

            // Copy with explicit close
            try (var inputStream = imageFile.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("💾 Saved to: " + imagePath);
            System.out.println("🔍 Starting OCR processing...");

            // Initialize components
            ItemMatcher itemMatcher = new ItemMatcher();

            // PASS 1: Full OCR
            List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);
            List<TableSegmenter.Table> tables = TableSegmenter.segmentTables(blocks);

            // Use ImprovedTableParser for each table
            Map<TableSegmenter.Table, List<ImprovedTableParser.ValidatedRow>> pass1ResultsMap = new HashMap<>();

            for (TableSegmenter.Table table : tables) {
                List<ImprovedTableParser.ValidatedRow> validatedRows =
                        ImprovedTableParser.parseTableWithValidation(table, table.data, itemMatcher);
                pass1ResultsMap.put(table, validatedRows);
            }

// ========== PASS 2: Masked OCR ==========
            System.out.println("\n========== PASS 2: MASKED OCR ==========");
            try {
                String maskedImagePath = TableSegmenter.createIntelligentMaskedImage(imagePath, tables, blocks);
                String upscaledPath = TableSegmenter.upscaleImage(maskedImagePath, 2.0);

                System.out.println("Running OCR on upscaled masked image...");
                List<VisionOcr.TextBlock> pass2Blocks = VisionOcr.performOcrWithBoundingBoxes(upscaledPath, false);

                // Scale coordinates back
                for (VisionOcr.TextBlock block : pass2Blocks) {
                    block.x = block.x / 2;
                    block.y = block.y / 2;
                }

                // Extract only numeric values
                List<VisionOcr.TextBlock> pass2Numbers = new ArrayList<>();
                for (VisionOcr.TextBlock block : pass2Blocks) {
                    if (block.text.matches("\\d+")) {
                        pass2Numbers.add(block);
                    }
                }

                System.out.println("Pass 2 detected " + pass2Numbers.size() + " numbers!");
                fillEmptyFieldsWithPass2(pass2Numbers, pass1ResultsMap, tables);

            } catch (Exception e) {
                System.err.println("⚠️ Pass 2 failed, but Pass 1 data preserved: " + e.getMessage());
            }

// ========== PASS 3: Ultra-Aggressive Cell Masking (OPTIONAL) ==========
            boolean ENABLE_PASS_3 = true; // Feature flag
            if (ENABLE_PASS_3) {
                System.out.println("\n========== PASS 3: CELL-ONLY MASKING ==========");
                try {
                    String pass3MaskedPath = createNumberCellOnlyMask(imagePath, tables, pass1ResultsMap);
                    String pass3UpscaledPath = TableSegmenter.upscaleImage(pass3MaskedPath, 2.5);

                    System.out.println("Running OCR on Pass 3 cell-only masked image...");
                    List<VisionOcr.TextBlock> pass3Blocks = VisionOcr.performOcrWithBoundingBoxes(pass3UpscaledPath, false);

                    // Scale coordinates back
                    for (VisionOcr.TextBlock block : pass3Blocks) {
                        block.x = (int)(block.x / 2.5);
                        block.y = (int)(block.y / 2.5);
                    }

                    // Extract only numeric values
                    List<VisionOcr.TextBlock> pass3Numbers = new ArrayList<>();
                    for (VisionOcr.TextBlock block : pass3Blocks) {
                        if (block.text.matches("\\d+")) {
                            pass3Numbers.add(block);
                        }
                    }

                    System.out.println("Pass 3 detected " + pass3Numbers.size() + " numbers!");
                    fillEmptyFieldsWithPass3(pass3Numbers, pass1ResultsMap, tables);

                } catch (Exception e) {
                    System.err.println("⚠️ Pass 3 failed, but Pass 1+2 data preserved: " + e.getMessage());
                }
            }

// Generate review JSON from validated rows
            String reviewJSON = WasteFormReviewGenerator.generateReviewJSONFromValidatedRows(pass1ResultsMap);

            System.out.println("✅ OCR processing complete!");
            System.out.println("📤 Sending JSON response (first 500 chars):");
            System.out.println(reviewJSON);

            // CRITICAL: Return the JSON string directly with proper content type
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(reviewJSON);

        } catch (Exception e) {
            System.err.println("❌ Processing failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to process image: " + e.getMessage()
            ));
        } finally {
            // Clean up temp file
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(Paths.get(imagePath));
                    System.out.println("🗑️ Cleaned up temp file");
                } catch (IOException e) {
                    System.err.println("⚠️ Could not delete temp file: " + e.getMessage());
                }
            }
        }
    }
    // Start OCR with progress tracking
    @PostMapping("/waste-form/process-with-progress")
    public ResponseEntity<?> processWasteFormWithProgress(@RequestParam("image") MultipartFile imageFile) {
        String sessionId = UUID.randomUUID().toString();

        try {
            // ✅ CRITICAL: Save the file BEFORE starting background thread
            // Otherwise Spring will delete it when the request completes
            Path tempFile = Files.createTempFile("waste_form_session_", ".jpg");
            String savedImagePath = tempFile.toString();

            System.out.println("📸 Saving uploaded image for session: " + sessionId);

            // Copy uploaded file to our own temp location
            try (var inputStream = imageFile.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("💾 Saved to: " + savedImagePath);

            // Now start background processing with the FILE PATH (not MultipartFile)
            new Thread(() -> {
                try {
                    processWithProgress(sessionId, savedImagePath);
                } catch (Exception e) {
                    System.err.println("❌ Background processing failed: " + e.getMessage());
                    e.printStackTrace();
                    updateProgress(sessionId, 0.0, "Error: " + e.getMessage());
                }
            }).start();

            return ResponseEntity.ok(Map.of("sessionId", sessionId));

        } catch (Exception e) {
            System.err.println("❌ Failed to save uploaded file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to save image: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/waste-form/submit")
    public ResponseEntity<?> submitWasteForm(@RequestBody Map<String, Object> reviewedData) {
        try {
            System.out.println("📝 Received reviewed data for submission");

            // Validate
            WasteFormValidator.ValidationResult validation =
                    WasteFormValidator.validateReviewedData(reviewedData);

            if (!validation.isValid) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "errors", validation.errors,
                        "warnings", validation.warnings
                ));
            }

            // TODO: Submit to Clearview website
            System.out.println("✅ Validation passed - ready to submit to Clearview");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Waste form validated successfully",
                    "warnings", validation.warnings
            ));

        } catch (Exception e) {
            System.err.println("❌ Submission failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to submit: " + e.getMessage()
            ));
        }
    }
    @PostMapping("/test-simple")
    public ResponseEntity<?> testSimple() {
        System.out.println("✅ SIMPLE POST ENDPOINT WORKING!");
        return ResponseEntity.ok("Simple POST works!");
    }
}