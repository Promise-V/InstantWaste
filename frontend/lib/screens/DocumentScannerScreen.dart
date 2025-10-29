import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:async';
import 'dart:io';
import '../services/document_detector.dart';

/// DocumentScannerScreen - Live camera scanner with gallery picker option
class DocumentScannerScreen extends StatefulWidget {
  const DocumentScannerScreen({Key? key}) : super(key: key);

  @override
  State<DocumentScannerScreen> createState() => _DocumentScannerScreenState();
}

class _DocumentScannerScreenState extends State<DocumentScannerScreen> {
  CameraController? _cameraController;
  bool _isInitialized = false;
  bool _isProcessing = false;
  int _frameSkipCounter = 0;
  
  // Frame processing configuration
  static const int _frameSkipRate = 5;
  
  // Detection state
  List<Map<String, double>>? _detectedCorners;
  String _feedbackMessage = "Position document in frame";
  Color _feedbackColor = Colors.white;
  bool _isQualityGood = false;
  
  // Auto-capture stability tracking
  int _stableFrameCount = 0;
  static const int _requiredStableFrames = 15;
  bool _isCapturing = false;
  
  final ImagePicker _imagePicker = ImagePicker();
  
  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }
  
  /// Initialize camera and start image stream
  Future<void> _initializeCamera() async {
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) {
        _showError("No cameras available");
        return;
      }
      
      final camera = cameras.first;
      
      _cameraController = CameraController(
        camera,
        ResolutionPreset.high,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );
      
      await _cameraController!.initialize();
      await _cameraController!.startImageStream(_processCameraImage);
      
      setState(() {
        _isInitialized = true;
      });
    } catch (e) {
      _showError("Camera initialization failed: $e");
    }
  }
  
  /// Pick image from gallery
  Future<void> _pickFromGallery() async {
    try {
      final XFile? pickedFile = await _imagePicker.pickImage(
        source: ImageSource.gallery,
      );
      
      if (pickedFile != null && mounted) {
        // Return the picked image immediately
        Navigator.pop(context, {
          'imagePath': pickedFile.path,
          'corners': null, // No corners detected from gallery pick
        });
      }
    } catch (e) {
      _showError("Failed to pick image: $e");
    }
  }
  
  /// Process each camera frame for document detection
  void _processCameraImage(CameraImage image) {
    _frameSkipCounter++;
    if (_frameSkipCounter % _frameSkipRate != 0) return;
    
    if (_isProcessing || _isCapturing) return;
    
    _isProcessing = true;
    
    DocumentDetector.detectDocument(image).then((result) {
      if (!mounted) return;
      
      setState(() {
        _detectedCorners = result.corners;
        _feedbackMessage = result.message;
        _feedbackColor = result.isGood ? Colors.green : Colors.red;
        _isQualityGood = result.isGood;
        
        if (_isQualityGood) {
          _stableFrameCount++;
          
          if (_stableFrameCount >= _requiredStableFrames) {
            _captureDocument();
          }
        } else {
          _stableFrameCount = 0;
        }
      });
      
      _isProcessing = false;
    }).catchError((error) {
      _isProcessing = false;
    });
  }
  
  /// Capture the document image
  Future<void> _captureDocument() async {
    if (_isCapturing) return;
    
    setState(() {
      _isCapturing = true;
    });
    
    try {
      await _cameraController!.stopImageStream();
      
      final XFile imageFile = await _cameraController!.takePicture();
      
      if (mounted) {
        Navigator.pop(context, {
          'imagePath': imageFile.path,
          'corners': _detectedCorners,
        });
      }
    } catch (e) {
      _showError("Capture failed: $e");
      
      setState(() {
        _isCapturing = false;
        _stableFrameCount = 0;
      });
      
      if (_cameraController != null && _cameraController!.value.isInitialized) {
        await _cameraController!.startImageStream(_processCameraImage);
      }
    }
  }
  
  /// Show error dialog
  void _showError(String message) {
    if (!mounted) return;
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Error'),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              Navigator.pop(context);
            },
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
  
  @override
  Widget build(BuildContext context) {
    if (!_isInitialized || _cameraController == null) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: CircularProgressIndicator(color: Colors.white),
        ),
      );
    }
    
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          // Camera preview
          Center(
  child: CameraPreview(_cameraController!),
),
          
          // Corner overlay
          if (_detectedCorners != null)
            Positioned.fill(
              child: CustomPaint(
                painter: CornerOverlayPainter(
                  corners: _detectedCorners!,
                  color: _feedbackColor,
                ),
              ),
            ),
          
          // Feedback message at top
          Positioned(
            top: MediaQuery.of(context).padding.top + 20,
            left: 0,
            right: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              margin: const EdgeInsets.symmetric(horizontal: 40),
              decoration: BoxDecoration(
                color: _feedbackColor.withOpacity(0.9),
                borderRadius: BorderRadius.circular(8),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.3),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _getFeedbackIcon(),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      _feedbackMessage,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          
          // Stability progress bar
          if (_isQualityGood && _stableFrameCount > 0)
            Positioned(
              bottom: 140,
              left: 40,
              right: 40,
              child: Column(
                children: [
                  Text(
                    'Capturing in ${((_requiredStableFrames - _stableFrameCount) / 30).toStringAsFixed(1)}s',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      shadows: [
                        Shadow(
                          color: Colors.black,
                          blurRadius: 4,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: LinearProgressIndicator(
                      value: _stableFrameCount / _requiredStableFrames,
                      backgroundColor: Colors.white.withOpacity(0.3),
                      valueColor: const AlwaysStoppedAnimation<Color>(Colors.green),
                      minHeight: 8,
                    ),
                  ),
                ],
              ),
            ),
          
          // Bottom action buttons
          Positioned(
            bottom: 40,
            left: 0,
            right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // Gallery button (NEW!)
                FloatingActionButton(
                  heroTag: 'gallery',
                  backgroundColor: Colors.white,
                  onPressed: _pickFromGallery,
                  child: const Icon(
                    Icons.photo_library,
                    color: Color(0xFF0000FF),
                  ),
                ),
                
                // Manual capture button
                FloatingActionButton.extended(
                  heroTag: 'capture',
                  backgroundColor: _isQualityGood ? Colors.green : Colors.grey[700],
                  onPressed: _isQualityGood && !_isCapturing ? _captureDocument : null,
                  icon: Icon(
                    Icons.camera,
                    color: _isQualityGood ? Colors.white : Colors.grey[500],
                  ),
                  label: Text(
                    _isCapturing ? 'CAPTURING...' : (_isQualityGood ? 'CAPTURE' : 'WAITING...'),
                    style: TextStyle(
                      color: _isQualityGood ? Colors.white : Colors.grey[500],
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
          ),
          
          // Close button
          Positioned(
            top: MediaQuery.of(context).padding.top + 10,
            left: 10,
            child: IconButton(
              icon: const Icon(Icons.close, color: Colors.white, size: 30),
              onPressed: () => Navigator.pop(context),
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _getFeedbackIcon() {
    if (_feedbackMessage.contains('Hold steady')) {
      return const Icon(Icons.check_circle, color: Colors.white, size: 20);
    } else if (_feedbackMessage.contains('dark')) {
      return const Icon(Icons.lightbulb_outline, color: Colors.white, size: 20);
    } else if (_feedbackMessage.contains('bright') || _feedbackMessage.contains('glare')) {
      return const Icon(Icons.wb_sunny_outlined, color: Colors.white, size: 20);
    } else if (_feedbackMessage.contains('blurry')) {
      return const Icon(Icons.blur_on, color: Colors.white, size: 20);
    } else if (_feedbackMessage.contains('parallel') || _feedbackMessage.contains('angled')) {
      return const Icon(Icons.screen_rotation, color: Colors.white, size: 20);
    } else {
      return const Icon(Icons.crop_free, color: Colors.white, size: 20);
    }
  }
  
  @override
  void dispose() {
    _cameraController?.dispose();
    super.dispose();
  }
}

/// Custom painter for drawing corner overlay
class CornerOverlayPainter extends CustomPainter {
  final List<Map<String, double>> corners;
  final Color color;
  
  CornerOverlayPainter({
    required this.corners,
    required this.color,
  });
  
  @override
  void paint(Canvas canvas, Size size) {
    if (corners.length != 4) return;
    
    final linePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4;
    
    final path = Path();
    path.moveTo(
      corners[0]['x']! * size.width,
      corners[0]['y']! * size.height,
    );
    
    for (int i = 1; i < corners.length; i++) {
      path.lineTo(
        corners[i]['x']! * size.width,
        corners[i]['y']! * size.height,
      );
    }
    path.close();
    
    canvas.drawPath(path, linePaint);
    
    final circlePaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    
    final circleBorderPaint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    
    for (int i = 0; i < corners.length; i++) {
      final center = Offset(
        corners[i]['x']! * size.width,
        corners[i]['y']! * size.height,
      );
      
      canvas.drawCircle(center, 12, circlePaint);
      canvas.drawCircle(center, 12, circleBorderPaint);
    }
    
    final overlayPaint = Paint()
      ..color = Colors.black.withOpacity(0.5);
    
    final overlayPath = Path()
      ..addRect(Rect.fromLTWH(0, 0, size.width, size.height))
      ..addPath(path, Offset.zero)
      ..fillType = PathFillType.evenOdd;
    
    canvas.drawPath(overlayPath, overlayPaint);
  }
  
  @override
  bool shouldRepaint(CornerOverlayPainter oldDelegate) {
    return oldDelegate.corners != corners || oldDelegate.color != color;
  }
}