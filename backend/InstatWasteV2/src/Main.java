//import javax.imageio.ImageIO;
//import java.awt.image.BufferedImage;
//import java.io.File;
//import java.nio.file.Path;
//
//public class Main {
//    public static void main(String[] args) {
//        // NO OPENCV LOADING NEEDED!
//
//        String inputPath = "images/testN.jpeg";
//        String outputDir = "test_output";
//
//        try {
//            // STEP 1: LOAD THE IMAGE
//            System.out.println("1. Loading image...");
//            BufferedImage originalImage = ImageIO.read(new File(inputPath));
//            saveImage(originalImage, outputDir, "01_loaded.png");
//
//            // STEP 2: Check for blur and sharpen if needed
//            System.out.println("2. Checking for blur...");
//            if (ImageProcessor.isImageBlurry(originalImage)) {
//                System.out.println(" - Image is blurry. Sharpening...");
//                originalImage = ImageProcessor.sharpenImage(originalImage);
//                saveImage(originalImage, outputDir, "02_sharpened.png");
//            } else {
//                System.out.println(" - Image is sharp.");
//            }
//
//            // STEP 3: CONTRAST ENHANCEMENT (IF NEEDED) - ADD THIS STEP
//            System.out.println("3. Checking contrast...");
//            originalImage = ImageProcessor.enhanceContrastIfNeeded(originalImage);
//            saveImage(originalImage, outputDir, "03_contrast_enhanced.png");
//
//            // STEP 4: Binarize - THIS SHOULD CREATE PURE BLACK & WHITE
////            System.out.println("4. Binarizing image...");
////            BufferedImage binaryImage = ImageProcessor.binarizeImage(originalImage);
////            saveImage(binaryImage, outputDir, "04_binarized.png");
//
//            // STEP 5: Scale up for OCR
////            System.out.println("5. Scaling up for OCR...");
////            BufferedImage finalImage = ImageProcessor.scaleImage(binaryImage, 2500, 2000);
////            saveImage(finalImage, outputDir, "05_final_ocr_ready.png");
//
//            // STEP 6: OCR - Extract text from the processed image
//            System.out.println("4. Performing OCR with Google Vision API...");
//            String ocrOutputPath = outputDir + "/extracted_text.txt";
//            String extractedText = VisionOcr.performOcr(outputDir + "/03_contrast_enhanced.png", ocrOutputPath);
//            System.out.println("\n=== EXTRACTED TEXT ===");
//            System.out.println(extractedText);
//            System.out.println("=== END EXTRACTED TEXT ===\n");
//
//            System.out.println("Done! Check the '" + outputDir + "' folder.");
//
//        } catch (Exception e) {
//            System.err.println("Error during processing: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    private static void saveImage(BufferedImage image, String directory, String filename) {
//        try {
//            File outputDir = new File(directory);
//            if (!outputDir.exists()) outputDir.mkdirs();
//            File outputFile = new File(outputDir, filename);
//
//            // Use PNG format for lossless saving
//            // For binary images, this will preserve pure black and white
//            ImageIO.write(image, "PNG", outputFile);
//
//        } catch (Exception e) {
//            System.err.println("Could not save image: " + filename);
//            e.printStackTrace();
//        }
//    }
//}