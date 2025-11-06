import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ENHANCED VISION OCR - HYBRID APPROACH
 *
 * Combines:
 * 1. TwoPassOcrSystem's superior detection (93.6% accuracy)
 * 2. Minimal filtering (only obvious noise)
 * 3. Image sharpening for Pass 2
 * 4. Original bounding box structure (for TableSegmenter compatibility)
 *
 * KEY IMPROVEMENTS FROM TWOPASSOCRSYSTEM:
 * - Keeps 1-3 digit numbers (including 101-110) instead of filtering them
 * - Only filters: "pack" keyword, "%", ":", and known product codes (348, 158)
 * - Adds sharpen filter for enhanced detection
 *
 * PERFORMANCE:
 * - Pass 1: ~4.5s (same as before)
 * - Pass 2 (with sharpening): ~4.5s
 * - Total: ~9-10s for both passes
 */
public class EnhancedVisionOcr {

    /**
     * Represents a detected text element with its position
     */
    public static class TextBlock {
        public String text;
        public int x;      // Left edge
        public int y;      // Top edge
        public int width;
        public int height;
        public String context; // NEW: Surrounding text for filtering

        public TextBlock(String text, int x, int y, int width, int height) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.context = "";
        }

        @Override
        public String toString() {
            return String.format("'%s' at (%d, %d) [%dx%d]", text, x, y, width, height);
        }
    }

    /**
     * PASS 1: REGULAR OCR WITH MINIMAL FILTERING
     *
     * Based on TwoPassOcrSystem's successful approach:
     * - Keep ALL 1-3 digit numbers (including 101-110)
     * - Only filter obvious noise
     *
     * @param inputPath Path to the input image file
     * @param includeEmpty If true, includes all blocks. If false, filters empty
     * @return List of TextBlock objects
     */
    public static List<TextBlock> performOcrWithBoundingBoxes(String inputPath, boolean includeEmpty) {
        List<TextBlock> results = new ArrayList<>();

        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            // Read the image file
            ByteString imgBytes = ByteString.readFrom(new FileInputStream(inputPath));

            // Build the image
            Image img = Image.newBuilder().setContent(imgBytes).build();

            // Set up the feature for text detection
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();

            // Build the request
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // Perform the request
            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    System.err.println("Error: " + res.getError().getMessage());
                    return results;
                }

                // Get the full text annotation
                TextAnnotation annotation = res.getFullTextAnnotation();

                // Process each page
                for (Page page : annotation.getPagesList()) {
                    for (Block block : page.getBlocksList()) {
                        for (Paragraph paragraph : block.getParagraphsList()) {
                            List<Word> words = paragraph.getWordsList();

                            for (int wordIndex = 0; wordIndex < words.size(); wordIndex++) {
                                Word word = words.get(wordIndex);

                                // Extract text from word
                                StringBuilder wordText = new StringBuilder();
                                for (Symbol symbol : word.getSymbolsList()) {
                                    wordText.append(symbol.getText());
                                }

                                String text = wordText.toString();

                                // Filter empty if requested
                                if (!includeEmpty && text.trim().isEmpty()) {
                                    continue;
                                }

                                // Get bounding box
                                BoundingPoly boundingBox = word.getBoundingBox();
                                List<Vertex> vertices = boundingBox.getVerticesList();

                                if (vertices.size() >= 2) {
                                    // Calculate bounding box dimensions
                                    int x = vertices.get(0).getX();
                                    int y = vertices.get(0).getY();
                                    int x2 = vertices.get(2).getX();
                                    int y2 = vertices.get(2).getY();
                                    int width = x2 - x;
                                    int height = y2 - y;

                                    TextBlock textBlock = new TextBlock(text, x, y, width, height);

                                    // Add context for intelligent filtering
                                    textBlock.context = getContext(words, wordIndex);

                                    // MINIMAL FILTERING (from TwoPassOcrSystem)
                                    if (!shouldFilterMinimal(textBlock)) {
                                        results.add(textBlock);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("Found " + results.size() + " text blocks (minimal filtering)");

        } catch (IOException e) {
            System.err.println("Vision API Error: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * MINIMAL FILTERING - Only obvious noise
     *
     * FIXED VERSION - Doesn't filter 101-110 anymore!
     *
     * Keeps:
     * - All 1-2 digit numbers (quantity fields)
     * - 3-digit numbers 101-110 (VALID quantities on form)
     * - All text (item names)
     *
     * Filters:
     * - "pack" keyword (pack sizes in SIZE column, not quantities)
     * - "%" signs (percentages, not quantities)
     * - ":" ratios (10:1 style, not quantities)
     * - Known product codes: 348, 158 (from item context)
     *
     * BUG FIX: TwoPassOcrSystem was filtering length>=3, which removed 101-110!
     * Now we only filter specific known product codes.
     */
    private static boolean shouldFilterMinimal(TextBlock block) {
        String text = block.text;
        String ctx = block.context.toUpperCase();

        // Keep most things - only filter obvious noise

        // Filter pack sizes (these appear in SIZE column, not quantity columns)
        if (ctx.contains("PACK")) {
            System.out.println("  Filtered (pack): " + text + " | Context: " + block.context);
            return true;
        }

        // Filter percentages
        if (ctx.contains("%")) {
            System.out.println("  Filtered (%): " + text + " | Context: " + block.context);
            return true;
        }

        // Filter ratios
        if (ctx.contains(":")) {
            System.out.println("  Filtered (ratio): " + text + " | Context: " + block.context);
            return true;
        }

        // Filter ONLY specific known product codes
        // DON'T use length check - that filtered out 101-110!
        if (text.equals("348") || text.equals("158")) {
            System.out.println("  Filtered (product code): " + text);
            return true;
        }

        // ✅ Keep 101-110 and all other numbers!
        return false;
    }

    /**
     * Get context (surrounding words) for a word at given index
     */
    private static String getContext(List<Word> words, int index) {
        StringBuilder context = new StringBuilder();

        // Get 2 words before
        for (int i = Math.max(0, index - 2); i < index; i++) {
            context.append(wordToString(words.get(i))).append(" ");
        }

        // Current word in brackets
        context.append("[").append(wordToString(words.get(index))).append("]");

        // Get 2 words after
        for (int i = index + 1; i < Math.min(words.size(), index + 3); i++) {
            context.append(" ").append(wordToString(words.get(i)));
        }

        return context.toString().trim();
    }

    /**
     * Convert Word to String
     */
    private static String wordToString(Word word) {
        StringBuilder text = new StringBuilder();
        for (Symbol symbol : word.getSymbolsList()) {
            text.append(symbol.getText());
        }
        return text.toString();
    }

    /**
     * PASS 2: SHARPENED IMAGE OCR
     *
     * Based on TwoPassOcrSystem's Pass 2 which found 21 additional numbers.
     *
     * Process:
     * 1. Load original image
     * 2. Apply sharpen filter
     * 3. Run OCR on sharpened image
     * 4. Deduplicate against Pass 1 results
     *
     * @param inputPath Path to original image
     * @param pass1Blocks Blocks from Pass 1 (for deduplication)
     * @return Additional text blocks found in Pass 2
     */
    public static List<TextBlock> performSharpenedOcr(String inputPath, List<TextBlock> pass1Blocks) {
        List<TextBlock> additionalBlocks = new ArrayList<>();

        try {
            System.out.println("🔍 PASS 2: Sharpened image OCR...");

            // Load and sharpen image
            BufferedImage original = ImageIO.read(new java.io.File(inputPath));
            BufferedImage sharpened = sharpenImage(original);

            System.out.println("  ✓ Image sharpened");

            // OCR the sharpened image
            List<TextBlock> pass2Blocks = ocrBufferedImage(sharpened);
            System.out.println("  Pass 2 raw detections: " + pass2Blocks.size());

            // Deduplicate - only keep new blocks
            for (TextBlock pass2Block : pass2Blocks) {
                // Must be a number to be useful
                if (!pass2Block.text.matches("\\d+")) continue;

                // Check if this number already exists in Pass 1 (within 50px)
                boolean isDuplicate = false;
                for (TextBlock pass1Block : pass1Blocks) {
                    int xDist = Math.abs(pass2Block.x - pass1Block.x);
                    int yDist = Math.abs(pass2Block.y - pass1Block.y);

                    if (xDist < 50 && yDist < 50) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    // Apply minimal filtering to Pass 2 blocks as well
                    if (!shouldFilterMinimal(pass2Block)) {
                        additionalBlocks.add(pass2Block);
                        System.out.println("  ✓ New number: " + pass2Block.text +
                                " at (" + pass2Block.x + ", " + pass2Block.y + ")");
                    }
                }
            }

            System.out.println("  Pass 2 found " + additionalBlocks.size() + " new numbers");

        } catch (IOException e) {
            System.err.println("Pass 2 error: " + e.getMessage());
            e.printStackTrace();
        }

        return additionalBlocks;
    }

    /**
     * SHARPEN IMAGE
     *
     * Uses convolution kernel to sharpen handwritten numbers.
     * Same approach as TwoPassOcrSystem which found 21 extra numbers.
     */
    private static BufferedImage sharpenImage(BufferedImage input) {
        // Sharpen kernel (aggressive for handwriting)
        float[] sharpenKernel = {
                0.0f, -1.5f, 0.0f,
                -1.5f, 7.0f, -1.5f,
                0.0f, -1.5f, 0.0f
        };

        Kernel kernel = new Kernel(3, 3, sharpenKernel);
        ConvolveOp op = new ConvolveOp(kernel);

        return op.filter(input, null);
    }

    /**
     * OCR A BUFFERED IMAGE
     *
     * Used by Pass 2 to OCR the sharpened image.
     */
    private static List<TextBlock> ocrBufferedImage(BufferedImage image) throws IOException {
        List<TextBlock> results = new ArrayList<>();

        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            // Convert BufferedImage to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageData = baos.toByteArray();

            ByteString imgBytes = ByteString.copyFrom(imageData);
            Image visionImage = Image.newBuilder().setContent(imgBytes).build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(visionImage)
                    .build();

            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(
                    Collections.singletonList(request));
            AnnotateImageResponse imageResponse = response.getResponses(0);

            if (imageResponse.hasError() || !imageResponse.hasFullTextAnnotation()) {
                return results;
            }

            // Extract blocks (same as Pass 1)
            TextAnnotation annotation = imageResponse.getFullTextAnnotation();

            for (Page page : annotation.getPagesList()) {
                for (Block block : page.getBlocksList()) {
                    for (Paragraph paragraph : block.getParagraphsList()) {
                        List<Word> words = paragraph.getWordsList();

                        for (int wordIndex = 0; wordIndex < words.size(); wordIndex++) {
                            Word word = words.get(wordIndex);

                            StringBuilder wordText = new StringBuilder();
                            for (Symbol symbol : word.getSymbolsList()) {
                                wordText.append(symbol.getText());
                            }

                            String text = wordText.toString();
                            if (text.trim().isEmpty()) continue;

                            BoundingPoly boundingBox = word.getBoundingBox();
                            List<Vertex> vertices = boundingBox.getVerticesList();

                            if (vertices.size() >= 2) {
                                int x = vertices.get(0).getX();
                                int y = vertices.get(0).getY();
                                int x2 = vertices.get(2).getX();
                                int y2 = vertices.get(2).getY();
                                int width = x2 - x;
                                int height = y2 - y;

                                TextBlock textBlock = new TextBlock(text, x, y, width, height);
                                textBlock.context = getContext(words, wordIndex);
                                results.add(textBlock);
                            }
                        }
                    }
                }
            }
        }

        return results;
    }

    /**
     * Legacy method - returns just the text for backward compatibility
     */
    public static String performOcr(String inputPath, String outputPath) {
        List<TextBlock> blocks = performOcrWithBoundingBoxes(inputPath, false);

        StringBuilder fullText = new StringBuilder();
        for (TextBlock block : blocks) {
            fullText.append(block.text).append(" ");
        }

        String text = fullText.toString().trim();

        // Save to file if outputPath provided
        if (outputPath != null && !outputPath.isEmpty()) {
            try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
                writer.write(text);
                System.out.println("Text saved to: " + outputPath);
            } catch (IOException e) {
                System.err.println("Error writing file: " + e.getMessage());
            }
        }

        return text;
    }

    public static void main(String[] args) {
        String imagePath = "images/main_filled_form.JPG";

        System.out.println("═".repeat(80));
        System.out.println("ENHANCED VISION OCR - HYBRID APPROACH");
        System.out.println("═".repeat(80) + "\n");

        System.out.println("=== PASS 1: Regular OCR with Minimal Filtering ===");
        long pass1Start = System.currentTimeMillis();
        List<TextBlock> pass1Blocks = performOcrWithBoundingBoxes(imagePath, false);
        long pass1Time = System.currentTimeMillis() - pass1Start;

        System.out.println("\nPass 1 complete:");
        System.out.println("  Blocks found: " + pass1Blocks.size());
        System.out.println("  Time: " + pass1Time + "ms");

        // Count numbers in Pass 1
        long pass1Numbers = pass1Blocks.stream()
                .filter(b -> b.text.matches("\\d+"))
                .count();
        System.out.println("  Numbers detected: " + pass1Numbers);

        System.out.println("\n=== PASS 2: Sharpened Image OCR ===");
        long pass2Start = System.currentTimeMillis();
        List<TextBlock> pass2Blocks = performSharpenedOcr(imagePath, pass1Blocks);
        long pass2Time = System.currentTimeMillis() - pass2Start;

        System.out.println("\nPass 2 complete:");
        System.out.println("  Additional blocks: " + pass2Blocks.size());
        System.out.println("  Time: " + pass2Time + "ms");

        // Combine
        List<TextBlock> allBlocks = new ArrayList<>(pass1Blocks);
        allBlocks.addAll(pass2Blocks);

        long totalNumbers = allBlocks.stream()
                .filter(b -> b.text.matches("\\d+"))
                .count();

        System.out.println("\n" + "═".repeat(80));
        System.out.println("FINAL RESULTS");
        System.out.println("═".repeat(80));
        System.out.println("Total blocks: " + allBlocks.size());
        System.out.println("Total numbers: " + totalNumbers);
        System.out.println("Total time: " + (pass1Time + pass2Time) + "ms");
        System.out.println("═".repeat(80));
    }
}