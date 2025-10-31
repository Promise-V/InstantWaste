import 'dart:io';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:opencv_dart/opencv_dart.dart' as cv;
import 'scan_processing_screen.dart';
import 'ml_scanner_screen.dart'; 
import '../widgets/progress_indicator.dart';
import 'dart:math' as math;
import 'gallery_picker_screen.dart';

class PreScanChecklistScreen extends StatefulWidget {
  final File imageFile;
  final List<Map<String, double>>? corners;
  final bool fromGallery;
   // Add corners parameter

  const PreScanChecklistScreen({
    super.key,
    required this.imageFile,
    this.corners, // Optional - if null, show original
    this.fromGallery = false, // Default to false
  });

  @override
  State<PreScanChecklistScreen> createState() => _PreScanChecklistScreenState();
}

class _PreScanChecklistScreenState extends State<PreScanChecklistScreen> {
  File? _correctedImageFile;
  bool _isProcessing = true;
  String? _errorMessage;
  
  @override
  void initState() {
    super.initState();
    _applyCorrectionIfNeeded();
  }
  
  /// Apply perspective correction (deskewing) if corners are available
Future<void> _applyCorrectionIfNeeded() async {
  print("🖼️ Starting image processing...");
  
  // If no corners provided (ML Kit already cropped), just use the image directly
  if (widget.corners == null || widget.corners!.length != 4) {
    print("🖼️ No corners provided - using image as-is (already cropped by ML Kit)");
    setState(() {
      _correctedImageFile = widget.imageFile;
      _isProcessing = false;
    });
    return;
  }
  
  // If corners ARE provided (from old custom scanner), apply perspective correction
  try {
    print("🖼️ Applying perspective correction...");
    final correctedFile = await _applyPerspectiveCorrection(
      widget.imageFile,
      widget.corners!,
    );
    
    print("🖼️ ✅ Correction successful");
    setState(() {
      _correctedImageFile = correctedFile;
      _isProcessing = false;
    });
  } catch (e, stackTrace) {
    print("🖼️ ❌ Correction failed: $e");
    print("🖼️ ❌ Stack: $stackTrace");
    // If correction fails, fall back to original
    setState(() {
      _correctedImageFile = widget.imageFile;
      _isProcessing = false;
      _errorMessage = 'Using original image (correction failed)';
    });
  }
}
  
  /// Applies perspective correction to straighten the document
  /// 
  /// Concept: Takes the 4 detected corners and transforms the image so the
  /// document appears as if photographed straight-on (bird's eye view).
  /// This removes perspective distortion and slight rotation.
  /// 
  /// Real-world example: A receipt photographed at an angle gets straightened
  /// to look like a flat scan, making OCR much more accurate.
 Future<File> _applyPerspectiveCorrection(
  File imageFile,
  List<Map<String, double>> normalizedCorners,
) async {
  cv.Mat? srcImage;
  cv.Mat? correctedImage;
  cv.Mat? transformMatrix;
  cv.VecPoint? srcCornersMat;
  cv.VecPoint? dstCorners;
  
  try {
    print("🖼️ Reading image from: ${imageFile.path}");
    print("🖼️ File exists: ${await imageFile.exists()}");
    
    // Load image
    srcImage = cv.imread(imageFile.path);
    print("🖼️ Image loaded: ${srcImage.cols}x${srcImage.rows}");
    
    final double imageWidth = srcImage.cols.toDouble();
    final double imageHeight = srcImage.rows.toDouble();
    
    // Convert normalized corners (0-1 range) back to pixel coordinates
    final List<cv.Point> corners = normalizedCorners.map((corner) {
      return cv.Point(
        (corner['x']! * imageWidth).round(),
        (corner['y']! * imageHeight).round(),
      );
    }).toList();
    
    print("🖼️ Corners in pixels: $corners");
    
    // Order corners: top-left, top-right, bottom-right, bottom-left
    final orderedCorners = _orderCorners(corners);
    print("🖼️ Ordered corners: $orderedCorners");
    
    // Calculate output dimensions
    final double widthTop = _distance(orderedCorners[0], orderedCorners[1]);
    final double widthBottom = _distance(orderedCorners[2], orderedCorners[3]);
    final double heightLeft = _distance(orderedCorners[0], orderedCorners[3]);
    final double heightRight = _distance(orderedCorners[1], orderedCorners[2]);
    
    final int outputWidth = widthTop.compareTo(widthBottom) > 0 
        ? widthTop.round() 
        : widthBottom.round();
    final int outputHeight = heightLeft.compareTo(heightRight) > 0 
        ? heightLeft.round() 
        : heightRight.round();
    
    print("🖼️ Output size: ${outputWidth}x${outputHeight}");
    
    // Define destination corners (rectangle)
    dstCorners = cv.VecPoint.fromList([
      cv.Point(0, 0),
      cv.Point(outputWidth, 0),
      cv.Point(outputWidth, outputHeight),
      cv.Point(0, outputHeight),
    ]);
    
    // Calculate perspective transform matrix
    srcCornersMat = cv.VecPoint.fromList(orderedCorners);
    transformMatrix = cv.getPerspectiveTransform(srcCornersMat, dstCorners);
    
    print("🖼️ Applying warp...");
    // Apply transformation
    correctedImage = cv.warpPerspective(
      srcImage,
      transformMatrix,
      (outputWidth, outputHeight),
    );
    
    // Save corrected image
    final correctedPath = imageFile.path.replaceAll('.jpg', '_corrected.jpg');
    print("🖼️ Saving to: $correctedPath");
    cv.imwrite(correctedPath, correctedImage);
    
    print("🖼️ ✅ Perspective correction complete");
    
    return File(correctedPath);
    
  } catch (e, stackTrace) {
    print("🖼️ ❌ Error in _applyPerspectiveCorrection: $e");
    print("🖼️ ❌ Stack: $stackTrace");
    rethrow;
  } finally {
    // Clean up ALL OpenCV objects
    print("🖼️ Cleaning up OpenCV objects...");
    srcImage?.dispose();
    correctedImage?.dispose();
    transformMatrix?.dispose();
    srcCornersMat?.dispose();
    dstCorners?.dispose();
    print("🖼️ Cleanup complete");
  }
}
  
  /// Order corners clockwise from top-left
  List<cv.Point> _orderCorners(List<cv.Point> points) {
    final List<cv.Point> ordered = List.filled(4, cv.Point(0, 0));
    
    // Calculate sums and differences
    final sums = points.map((p) => p.x + p.y.toDouble()).toList();
    final diffs = points.map((p) => p.y - p.x.toDouble()).toList();
    
    // Find indices
    int tlIdx = 0, brIdx = 0, trIdx = 0, blIdx = 0;
    for (int i = 1; i < 4; i++) {
      if (sums[i] < sums[tlIdx]) tlIdx = i;
      if (sums[i] > sums[brIdx]) brIdx = i;
      if (diffs[i] < diffs[trIdx]) trIdx = i;
      if (diffs[i] > diffs[blIdx]) blIdx = i;
    }
    
    ordered[0] = points[tlIdx];
    ordered[1] = points[trIdx];
    ordered[2] = points[brIdx];
    ordered[3] = points[blIdx];
    
    return ordered;
  }
  
  /// Calculate distance between two points
double _distance(cv.Point p1, cv.Point p2) {
  final dx = p1.x - p2.x.toDouble();
  final dy = p1.y - p2.y.toDouble();
  return math.sqrt(dx * dx + dy * dy); // ✅ CORRECT - actual distance
}

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[100],
      body: SafeArea(
        child: Column(
          children: [
            // Progress Indicator
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.white,
              child: const WasteFormProgressIndicator(currentStep: 0),
            ),
            
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Image Preview
                    Container(
                      height: 300,
                      decoration: BoxDecoration(
                        color: Colors.grey[800],
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: _isProcessing
                            ? const Center(
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    CircularProgressIndicator(color: Colors.white),
                                    SizedBox(height: 16),
                                    Text(
                                      'Straightening document...',
                                      style: TextStyle(color: Colors.white70),
                                    ),
                                  ],
                                ),
                              )
                            : kIsWeb
                                ? Center(
                                    child: Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Icon(
                                          Icons.image,
                                          size: 80,
                                          color: Colors.grey[600],
                                        ),
                                        const SizedBox(height: 16),
                                        Text(
                                          'Image loaded: ${_correctedImageFile?.path.split('/').last ?? ''}',
                                          style: TextStyle(
                                            color: Colors.grey[400],
                                            fontSize: 14,
                                          ),
                                          textAlign: TextAlign.center,
                                        ),
                                      ],
                                    ),
                                  )
                                : Image.file(
                                    _correctedImageFile!,
                                    fit: BoxFit.contain, // Show full corrected image
                                    errorBuilder: (context, error, stackTrace) {
                                      return Center(
                                        child: Column(
                                          mainAxisAlignment: MainAxisAlignment.center,
                                          children: [
                                            Icon(
                                              Icons.error_outline,
                                              size: 60,
                                              color: Colors.red[300],
                                            ),
                                            const SizedBox(height: 16),
                                            Text(
                                              'Error loading image',
                                              style: TextStyle(
                                                color: Colors.grey[400],
                                              ),
                                            ),
                                          ],
                                        ),
                                      );
                                    },
                                  ),
                      ),
                    ),
                    
                    // Show correction status
                    if (_errorMessage != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 8.0),
                        child: Text(
                          _errorMessage!,
                          style: const TextStyle(
                            color: Colors.orange,
                            fontSize: 12,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    
                    if (widget.corners != null && !_isProcessing)
                      Padding(
                        padding: const EdgeInsets.only(top: 8.0),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.check_circle, color: Colors.green, size: 16),
                            const SizedBox(width: 8),
                            const Text(
                              'Document automatically straightened',
                              style: TextStyle(
                                color: Colors.green,
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ),
                    
                    const SizedBox(height: 24),
                    
                    // Checklist Title
                    const Text(
                      'Before We Continue.....',
                      style: TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    
                    const SizedBox(height: 20),
                    
                    // Checklist Items
                    _buildChecklistItem(
                      '1. Are Numbers Bold and Legible?',
                    ),
                    _buildChecklistItem(
                      '2. Are numbers written with dark ink?',
                    ),
                    _buildChecklistItem(
                      '3. Is the document properly aligned?',
                    ),
                    
                    const SizedBox(height: 20),
                    
                    const Text(
                      'If No to any of those questions, Retake',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    
                    const SizedBox(height: 40),
                    
                    // Action Buttons
Row(
  children: [
// Retake Button
Expanded(
  child: ElevatedButton(
    onPressed: _isProcessing ? null : () {
      // Navigate to appropriate screen based on source
      if (widget.fromGallery) {
        // Pop to home, then push gallery picker
        Navigator.of(context).popUntil((route) => route.isFirst);
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const GalleryPickerScreen(),
          ),
        );
      } else {
        // Pop to home, then push ML scanner
        Navigator.of(context).popUntil((route) => route.isFirst);
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => const MLScannerScreen(),
          ),
        );
      }
    },
    style: ElevatedButton.styleFrom(
      backgroundColor: const Color(0xFF0000FF),
      padding: const EdgeInsets.symmetric(vertical: 20),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(30),
      ),
    ),
                            child: const Text(
                              'RETAKE',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                        
                        const SizedBox(width: 16),
                        
                        // Scan Button
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _isProcessing ? null : () {
                              Navigator.pushReplacement(
                                context,
                                MaterialPageRoute(
                                  builder: (context) => ScanProcessingScreen(
                                    imageFile: _correctedImageFile!, // Use corrected image
                                  ),
                                ),
                              );
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFF0000FF),
                              padding: const EdgeInsets.symmetric(vertical: 20),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(30),
                              ),
                            ),
                            child: const Text(
                              'SCAN',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChecklistItem(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.check_circle_outline,
            color: Color(0xFF0000FF),
            size: 24,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontSize: 16,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }
}