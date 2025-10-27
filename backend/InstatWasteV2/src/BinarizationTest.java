import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;

public class BinarizationTest {
    public static void main(String[] args) {
        try {
            // 1. Create a simple test image programmatically
            BufferedImage testImage = createTestImage();
            ImageIO.write(testImage, "PNG", new File("randomtest.jpg"));

            // 2. Test the Otsu threshold calculation
            int threshold = ImageProcessor.calculateOtsuThreshold(testImage);
            System.out.println("Calculated Otsu threshold: " + threshold);

            // 3. Test the full binarization
            BufferedImage result = ImageProcessor.binarizeImage(testImage);
            ImageIO.write(result, "PNG", new File("test_output.png"));

            // 4. Debug the output
            debugImageContents(result, "Final output");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage createTestImage() {
        // Create a simple image with known black text on white background
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = img.getRaster();

        // White background
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                raster.setSample(x, y, 0, 240); // Almost white
            }
        }

        // Black text "X"
        for (int i = 10; i < 90; i++) {
            raster.setSample(i, i, 0, 20);     // Dark
            raster.setSample(90-i, i, 0, 20);  // Dark
        }

        return img;
    }

    private static void debugImageContents(BufferedImage image, String label) {
        System.out.println("\n=== " + label + " ===");
        System.out.println("Image type: " + image.getType());
        System.out.println("Expected TYPE_BYTE_BINARY: " + BufferedImage.TYPE_BYTE_BINARY);

        WritableRaster raster = image.getRaster();
        int[] pixel = new int[1];

        // Check first 10x10 pixels
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                raster.getPixel(x, y, pixel);
                System.out.print(pixel[0] + " ");
            }
            System.out.println();
        }
    }
}