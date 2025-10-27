package com.instantwaste;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class VisionOcr {

    /**
     * Represents a detected text element with its position
     */
    public static class TextBlock {
        public String text;
        public int x;      // Left edge
        public int y;      // Top edge
        public int width;
        public int height;

        public TextBlock(String text, int x, int y, int width, int height) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("'%s' at (%d, %d) [%dx%d]", text, x, y, width, height);
        }
    }

    /**
     * Creates ImageAnnotatorClient with credentials from environment variable
     */
    private static ImageAnnotatorClient createVisionClient() throws IOException {
        String credentialsJson = System.getenv("GOOGLE_CREDENTIALS_JSON");

        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            System.out.println("✓ Loading Google credentials from environment variable");

            try {
                // Remove any whitespace/newlines
                credentialsJson = credentialsJson.trim().replaceAll("\\s+", "");

                // Check if it's already JSON (starts with {) or base64
                GoogleCredentials credentials;
                if (credentialsJson.startsWith("{")) {
                    // It's raw JSON
                    System.out.println("✓ Detected raw JSON credentials");
                    credentials = GoogleCredentials.fromStream(
                            new ByteArrayInputStream(credentialsJson.getBytes())
                    );
                } else {
                    // It's base64 encoded
                    System.out.println("✓ Detected base64 credentials");
                    byte[] decoded = Base64.getDecoder().decode(credentialsJson);
                    credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
                }

                ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();

                return ImageAnnotatorClient.create(settings);
            } catch (Exception e) {
                System.err.println("❌ Failed to load credentials: " + e.getMessage());
                throw e;
            }
        }

        System.out.println("⚠️ No GOOGLE_CREDENTIALS_JSON found, using default credentials");
        return ImageAnnotatorClient.create();
    }

    /**
     * Performs OCR and returns list of text blocks with bounding boxes
     *
     * @param inputPath Path to the input image file
     * @param includeEmpty If true, includes all blocks. If false, filters out empty/whitespace
     * @return List of TextBlock objects
     */
    public static List<TextBlock> performOcrWithBoundingBoxes(String inputPath, boolean includeEmpty) {
        List<TextBlock> results = new ArrayList<>();

        try (ImageAnnotatorClient vision = createVisionClient()) {
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
                            for (Word word : paragraph.getWordsList()) {
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

                                    results.add(new TextBlock(text, x, y, width, height));
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("Found " + results.size() + " text blocks");

        } catch (IOException e) {
            System.err.println("Vision API Error: " + e.getMessage());
            e.printStackTrace();
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
        String imagePath = "C:/Users/user/Documents/InstatWasteV2-20251004T032038Z-1-001/InstatWasteV2/test_output/03_contrast_enhanced.png";

        System.out.println("=== FILTER 1: Everything (includeEmpty=true) ===");
        List<VisionOcr.TextBlock> allBlocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, true);
        for (VisionOcr.TextBlock block : allBlocks) {
            System.out.println(block);
        }

        System.out.println("\n=== FILTER 2: Non-empty only (includeEmpty=false) ===");
        List<VisionOcr.TextBlock> filteredBlocks = VisionOcr.performOcrWithBoundingBoxes(imagePath, false);
        for (VisionOcr.TextBlock block : filteredBlocks) {
            System.out.println(block);
        }

        System.out.println("\nTotal blocks: " + allBlocks.size());
        System.out.println("Non-empty blocks: " + filteredBlocks.size());
        System.out.println("Filtered out: " + (allBlocks.size() - filteredBlocks.size()));
    }
}