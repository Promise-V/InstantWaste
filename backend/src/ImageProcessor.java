import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

public class ImageProcessor {

    // Kernel for sharpening - precomputed for efficiency
    private static final float[] SHARPEN_KERNEL = {
            0, -1,  0,
            -1,  5, -1,
            0, -1,  0
    };
    private static final ConvolveOp SHARPEN_OP = new ConvolveOp(
            new Kernel(3, 3, SHARPEN_KERNEL),
            ConvolveOp.EDGE_NO_OP,
            null
    );

    // Kernel for blur detection - precomputed
    private static final float[] LAPLACIAN_KERNEL = {
            0,  1, 0,
            1, -4, 1,
            0,  1, 0
    };
    private static final ConvolveOp LAPLACIAN_OP = new ConvolveOp(
            new Kernel(3, 3, LAPLACIAN_KERNEL),
            ConvolveOp.EDGE_NO_OP,
            null
    );

    public static BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaledImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);

        g2d.dispose();
        return scaledImage;
    }

    public static BufferedImage sharpenImage(BufferedImage originalImage) {
        if (originalImage == null) {
            throw new IllegalArgumentException("Original image cannot be null.");
        }

        // Larger 5x5 kernel for broader, more even sharpening
        float[] sharpenKernel = {
                -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1,
                -1, -1, 25, -1, -1,
                -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1
        };

        // Normalize the kernel so it doesn't oversaturate
        float sum = 0;
        for (float value : sharpenKernel) {
            sum += value;
        }
        // Avoid division by zero (though sum should be positive for sharpening kernels)
        if (sum != 0) {
            for (int i = 0; i < sharpenKernel.length; i++) {
                sharpenKernel[i] /= sum;
            }
        }

        ConvolveOp sharpenOp = new ConvolveOp(
                new Kernel(5, 5, sharpenKernel),
                ConvolveOp.EDGE_NO_OP,
                null
        );

        return sharpenOp.filter(originalImage, null);
    }

    public static BufferedImage binarizeImage(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        WritableRaster grayRaster = gray.getRaster();

        // Build histogram - O(n) single pass
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grayValue = grayRaster.getSample(x, y, 0);
                histogram[grayValue]++;
            }
        }

        // Calculate Otsu threshold - O(256) = constant time
        int totalPixels = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 0;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;

            wF = totalPixels - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);

            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);

            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        // Apply threshold - O(n) single pass
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster binaryRaster = binary.getRaster();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grayValue = grayRaster.getSample(x, y, 0);
                int binaryValue = (grayValue > threshold) ? 1 : 0;
                binaryRaster.setSample(x, y, 0, binaryValue);
            }
        }

        return binary;
    }


    public static int calculateOtsuThreshold(BufferedImage grayImage) {
        // Calculate histogram (256 bins for 8-bit grayscale)
        int[] histogram = new int[256];
        WritableRaster raster = grayImage.getRaster();
        int width = grayImage.getWidth();
        int height = grayImage.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelValue = raster.getSample(x, y, 0);
                histogram[pixelValue]++;
            }
        }

        // Otsu's method for optimal threshold calculation
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 0;

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;

            wF = total - wB;
            if (wF == 0) break;

            sumB += i * histogram[i];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float varBetween = (float) wB * wF * (mB - mF) * (mB - mF);

            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = i;
            }
        }

        return threshold;
    }

    public static boolean isImageBlurry(BufferedImage image) {
        // Convert to grayscale if needed
        BufferedImage grayImage;
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            grayImage = image;
        } else {
            grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics g = grayImage.getGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        // Apply Laplacian using precomputed op
        BufferedImage laplacianImage = LAPLACIAN_OP.filter(grayImage, null);

        // Calculate variance
        WritableRaster raster = laplacianImage.getRaster();
        int width = raster.getWidth();
        int height = raster.getHeight();
        int totalPixels = width * height;

        long sum = 0;
        long sumSquared = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = raster.getSample(x, y, 0);
                sum += value;
                sumSquared += value * value;
            }
        }

        double mean = (double) sum / totalPixels;
        double variance = ((double) sumSquared / totalPixels) - (mean * mean);

        return variance < 150.0; // Tune this threshold
    }
    public static BufferedImage enhanceContrastIfNeeded(BufferedImage image) {
        // Quick convert to grayscale
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // FAST analysis: just check average brightness
        WritableRaster raster = grayImage.getRaster();
        long sum = 0;
        int totalPixels = image.getWidth() * image.getHeight();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                sum += raster.getSample(x, y, 0);
            }
        }

        double meanBrightness = (double) sum / totalPixels;

        // Only enhance if the image is too dark (typical document should be bright)
        if (meanBrightness < 160) { // Adjust this threshold as needed
            System.out.println("Enhancing contrast - image too dark: " + meanBrightness);
            // Simple linear stretch to make darks darker and brights brighter
            BufferedImage enhanced = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            WritableRaster enhancedRaster = enhanced.getRaster();

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int value = raster.getSample(x, y, 0);
                    int newValue = (int) (value * 1.3); // Simple multiplier
                    newValue = Math.min(255, newValue); // Clamp to prevent overflow
                    enhancedRaster.setSample(x, y, 0, newValue);
                }
            }
            return enhanced;
        }

        System.out.println("Contrast OK - skipping enhancement: " + meanBrightness);
        return image;
    }
}