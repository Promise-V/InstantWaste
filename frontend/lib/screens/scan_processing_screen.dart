import 'dart:io';
import 'package:flutter/material.dart';
import '../services/waste_form_api.dart';
import '../models/waste_form_models.dart';
import 'edit_review_screen.dart';
import '../widgets/progress_indicator.dart';

class ScanProcessingScreen extends StatefulWidget {
  final File imageFile;

  const ScanProcessingScreen({
    super.key,
    required this.imageFile,
  });

  @override
  State<ScanProcessingScreen> createState() => _ScanProcessingScreenState();
}

class _ScanProcessingScreenState extends State<ScanProcessingScreen> {
  final WasteFormApi _api = WasteFormApi();
  bool _isProcessing = true;
  String _statusMessage = 'Uploading image...';
  double _progress = 0.0;

  @override
  void initState() {
    super.initState();
    _processImage();
  }

  Future<void> _processImage() async {
    try {
      // Simulate upload progress
      setState(() {
        _statusMessage = 'Uploading image...';
        _progress = 0.3;
      });

      await Future.delayed(const Duration(milliseconds: 500));

      setState(() {
        _statusMessage = 'Performing OCR...';
        _progress = 0.6;
      });

      // Call API with detailed error catching
      ScanResult result;
      try {
        result = await _api.processWasteForm(widget.imageFile);
      } catch (e, stackTrace) {
        print('❌❌❌ FULL ERROR DETAILS ❌❌❌');
        print('Error: $e');
        print('Stack trace: $stackTrace');
        rethrow;
      }

      setState(() {
        _statusMessage = 'Processing complete!';
        _progress = 1.0;
      });

      await Future.delayed(const Duration(milliseconds: 500));

      // Navigate to edit/review screen
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (context) => EditReviewScreen(scanResult: result),
          ),
        );
      }
    } catch (e) {
      setState(() {
        _isProcessing = false;
        _statusMessage = 'Error: $e';
      });

      // Show error dialog
      if (mounted) {
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Processing Failed'),
            content: Text('Failed to process image:\n\n$e'),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  Navigator.of(context).pop();
                },
                child: const Text('Go Back'),
              ),
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  setState(() {
                    _isProcessing = true;
                    _statusMessage = 'Uploading image...';
                    _progress = 0.0;
                  });
                  _processImage();
                },
                child: const Text('Retry'),
              ),
            ],
          ),
        );
      }
    }
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
              child: const WasteFormProgressIndicator(currentStep: 1),
            ),
            
            Expanded(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(40.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // Loading Animation
                      SizedBox(
                        width: 100,
                        height: 100,
                        child: CircularProgressIndicator(
                          value: _isProcessing ? _progress : null,
                          strokeWidth: 8,
                          valueColor: const AlwaysStoppedAnimation<Color>(
                            Color(0xFF0000FF),
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 40),
                      
                      // Status Message
                      Text(
                        _statusMessage,
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w500,
                        ),
                        textAlign: TextAlign.center,
                      ),
                      
                      const SizedBox(height: 16),
                      
                      // Progress Percentage
                      if (_isProcessing)
                        Text(
                          '${(_progress * 100).toInt()}%',
                          style: TextStyle(
                            fontSize: 48,
                            fontWeight: FontWeight.bold,
                            color: Colors.grey[700],
                          ),
                        ),
                      
                      const SizedBox(height: 40),
                      
                      // Info Text
                      Text(
                        'Please wait while we extract the data\nfrom your waste form...',
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey[600],
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}