import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility: Load an image with EXIF orientation fixed.
 */
public class ImageOrientation {

//    public static BufferedImage loadFixed(Path path) throws IOException {
//        File f = path.toFile();
//        BufferedImage img = ImageIO.read(f);
//        System.out.println("Original image dimensions: " + img.getWidth() + "x" + img.getHeight());
//
//        int orientation = 1;
//        try {
//            Metadata metadata = ImageMetadataReader.readMetadata(f);
//            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
//            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
//                orientation = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
//                System.out.println("EXIF Orientation tag found: " + orientation);
//            } else {
//                System.out.println("No EXIF orientation tag found, using default: " + orientation);
//            }
//        } catch (Exception e) {
//            System.out.println("Could not read metadata: " + e.getMessage());
//        }
//
//        System.out.println("Applying orientation correction: " + orientation);
//        BufferedImage result = applyOrientation(img, orientation);
//        System.out.println("Corrected image dimensions: " + result.getWidth() + "x" + result.getHeight());
//
//        return result;
//    }

    public static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform tx = new AffineTransform();

        switch (orientation) {
            case 2: // Flip X
                tx.scale(-1.0, 1.0);
                tx.translate(-w, 0);
                break;
            case 3: // PI rotation
                tx.translate(w, h);
                tx.rotate(Math.PI);
                break;
            case 4: // Flip Y
                tx.scale(1.0, -1.0);
                tx.translate(0, -h);
                break;
            case 5: // -PI/2 and Flip X
                tx.rotate(-Math.PI / 2);
                tx.scale(-1.0, 1.0);
                break;
            case 6: // -PI/2
                tx.translate(h, 0);
                tx.rotate(Math.PI / 2);
                break;
            case 7: // PI/2 and Flip
                tx.scale(-1.0, 1.0);
                tx.translate(-h, 0);
                tx.translate(0, w);
                tx.rotate(3 * Math.PI / 2);
                break;
            case 8: // PI / 2
                tx.translate(0, w);
                tx.rotate(3 * Math.PI / 2);
                break;
            default:
                return src;
        }

        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BICUBIC);
        BufferedImage dst = new BufferedImage(
                (orientation >= 5 && orientation <= 8) ? h : w,
                (orientation >= 5 && orientation <= 8) ? w : h,
                src.getType());
        op.filter(src, dst);
        return dst;
    }
}