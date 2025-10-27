//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//import com.google.gson.Gson;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.StandardCopyOption;
//import java.util.*;
//
//@RestController
//@RequestMapping("/api/waste-form")
//@CrossOrigin(origins = "*") // Allow Flutter to call from localhost
//public class WasteFormController {
//
//    private final Gson gson = new Gson();
//
//    /**
//     * ENDPOINT 1: Process uploaded waste form image
//     * Flutter calls: POST http://localhost:8080/api/waste-form/process
//     */
//    @PostMapping("/process")
//    public ResponseEntity<?> processWasteForm(@RequestParam("image") MultipartFile imageFile) {
//        try {
//            System.out.println("Received image: " + imageFile.getOriginalFilename());
//
//            // Save uploaded file temporarily
//            Path tempFile = Files.createTempFile("waste_form_", ".jpg");
//            Files.copy(imageFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
//            String imagePath = tempFile.toString();
//
//            System.out.println("Saved to: " + imagePath);
//
//            // Run your OCR pipeline (same code from TableSegmenter main)
//            List<VisionOcr.TextBlock> blocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);
//            List<TableSegmenter.Table> tables = TableSegmenter.segmentTables(blocks);
//
//            // Process all tables
//            Map<TableSegmenter.Table, List<TableSegmenter.ParsedRowWithPosition>> pass1ResultsMap = new HashMap<>();
//
//            for (TableSegmenter.Table table : tables) {
//                int yTolerance = table.name.contains("5Column") ? 15 : 30;
//                List<TableSegmenter.Row> rows = TableSegmenter.groupIntoRows(table.data, yTolerance);
//                int dynamicItemEnd = TableSegmenter.findItemColumnEnd(rows, table);
//
//                List<TableSegmenter.ParsedRowWithPosition> tableResults = new ArrayList<>();
//
//                for (TableSegmenter.Row row : rows) {
//                    TableSegmenter.ParsedRow parsed = TableSegmenter.parseRowIntoColumns(row, table, dynamicItemEnd);
//
//                    // Skip sub-headers and empty rows
//                    // ... (your existing logic)
//
//                    TableSegmenter.ParsedRowWithPosition rowWithPos = new TableSegmenter.ParsedRowWithPosition();
//                    rowWithPos.parsed = parsed;
//                    rowWithPos.yPosition = row.yPosition;
//                    tableResults.add(rowWithPos);
//                }
//
//                pass1ResultsMap.put(table, tableResults);
//            }
//
//            // TODO: Add Pass 2 (masked OCR) here
//
//            // Generate review JSON
//            String reviewJSON = WasteFormReviewGenerator.generateReviewJSON(pass1ResultsMap);
//
//            // Clean up temp file
//            Files.deleteIfExists(tempFile);
//
//            // Return JSON to Flutter
//            Map<String, Object> response = gson.fromJson(reviewJSON, Map.class);
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body(Map.of(
//                    "error", "Failed to process image: " + e.getMessage()
//            ));
//        }
//    }
//
//    /**
//     * ENDPOINT 2: Submit reviewed waste form data
//     * Flutter calls: POST http://localhost:8080/api/waste-form/submit
//     */
//    @PostMapping("/submit")
//    public ResponseEntity<?> submitWasteForm(@RequestBody Map<String, Object> reviewedData) {
//        try {
//            System.out.println("Received reviewed data for submission");
//
//            // Validate the data
//            WasteFormValidator.ValidationResult validation =
//                    WasteFormValidator.validateReviewedData(reviewedData);
//
//            if (!validation.isValid) {
//                return ResponseEntity.badRequest().body(Map.of(
//                        "success", false,
//                        "errors", validation.errors,
//                        "warnings", validation.warnings
//                ));
//            }
//
//            // TODO: Submit to Clearview website here
//            // For now, just return success
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "message", "Waste form submitted successfully",
//                    "warnings", validation.warnings
//            ));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body(Map.of(
//                    "error", "Failed to submit: " + e.getMessage()
//            ));
//        }
//    }
//
//    /**
//     * ENDPOINT 3: Health check (test if server is running)
//     * Flutter calls: GET http://localhost:8080/api/waste-form/health
//     */
//    @GetMapping("/health")
//    public ResponseEntity<?> healthCheck() {
//        return ResponseEntity.ok(Map.of(
//                "status", "ok",
//                "message", "Waste Form API is running"
//        ));
//    }
//}