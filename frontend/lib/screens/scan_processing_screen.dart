import 'dart:io';
import 'package:flutter/material.dart';
import '../services/waste_form_api.dart';
import '../models/waste_form_models.dart';
import 'edit_review_screen.dart';
import '../widgets/progress_indicator.dart';
import 'dart:async';
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
  String _statusMessage = 'Starting OCR process...';
  double _progress = 0.0;
  String? _sessionId; // To track this specific OCR job
  Timer? _progressTimer;

  @override
  void initState() {
    super.initState();
    _startOCRWithProgress();
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    super.dispose();
  }

  Future<void> _startOCRWithProgress() async {
    try {
      setState(() {
        _statusMessage = 'Uploading image...';
        _progress = 0.1;
      });

      // Start the OCR process and get a session ID
      _sessionId = await _api.startOCRWithProgress(widget.imageFile);
      
      // Start polling for progress updates
      _startProgressPolling();

    } catch (e) {
      _handleError('Failed to start OCR: $e');
    }
  }

  void _startProgressPolling() {
    _progressTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      try {
        if (_sessionId == null) return;

        // Get actual progress from backend
        final progressData = await _api.getOCRProgress(_sessionId!);
        
        if (mounted) {
          setState(() {
            _progress = progressData['progress'];
            _statusMessage = progressData['message'];
          });

          // Check if processing is complete
          if (_progress >= 1.0) {
            timer.cancel();
            _getFinalResult();
          }
        }
      } catch (e) {
        print('Progress polling error: $e');
        // Don't show error to user - just retry next poll
      }
    });
  }

  Future<void> _getFinalResult() async {
    try {
      setState(() {
        _statusMessage = 'Finalizing results...';
      });

      // Get the final OCR result
      final ScanResult result = await _api.getOCRResult(_sessionId!);
      
      setState(() {
        _statusMessage = 'Complete!';
        _progress = 1.0;
      });

      await Future.delayed(const Duration(milliseconds: 500));

      // Navigate to results
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (context) => EditReviewScreen(scanResult: result),
          ),
        );
      }
    } catch (e) {
      _handleError('Failed to get results: $e');
    }
  }

  void _handleError(String error) {
    if (mounted) {
      setState(() {
        _isProcessing = false;
        _statusMessage = error;
      });

      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Processing Failed'),
          content: Text(error),
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
                _restartProcess();
              },
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }
  }

  void _restartProcess() {
    _progressTimer?.cancel();
    setState(() {
      _isProcessing = true;
      _statusMessage = 'Restarting...';
      _progress = 0.0;
      _sessionId = null;
    });
    _startOCRWithProgress();
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
                      // Animated Progress Circle
                      Stack(
                        alignment: Alignment.center,
                        children: [
                          SizedBox(
                            width: 120,
                            height: 120,
                            child: CircularProgressIndicator(
                              value: _isProcessing ? _progress : null,
                              strokeWidth: 8,
                              valueColor: AlwaysStoppedAnimation<Color>(
                                _getProgressColor(),
                              ),
                              backgroundColor: Colors.grey[300],
                            ),
                          ),
                          Column(
                            children: [
                              Text(
                                '${(_progress * 100).toInt()}%',
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              if (_progress > 0 && _progress < 1.0)
                                Text(
                                  _getStageName(),
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.grey[600],
                                  ),
                                ),
                            ],
                          ),
                        ],
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
                      
                      const SizedBox(height: 24),
                      
                      // Progress Bar
                      LinearProgressIndicator(
                        value: _progress,
                        backgroundColor: Colors.grey[300],
                        color: _getProgressColor(),
                        minHeight: 6,
                      ),
                      
                      const SizedBox(height: 40),
                      
                      // Info Text
                      Text(
                        _getInfoText(),
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey[600],
                        ),
                        textAlign: TextAlign.center,
                      ),
                      
                      // Cancel button
                      if (_isProcessing && _progress < 1.0)
                        Padding(
                          padding: const EdgeInsets.only(top: 20),
                          child: TextButton(
                            onPressed: () {
                              _progressTimer?.cancel();
                              Navigator.of(context).pop();
                            },
                            child: const Text('Cancel'),
                          ),
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

  Color _getProgressColor() {
    if (_progress >= 1.0) return Colors.green;
    if (_progress >= 0.7) return Colors.blue;
    if (_progress >= 0.4) return Colors.orange;
    return const Color(0xFF0000FF);
  }

  String _getStageName() {
    if (_progress < 0.3) return 'Uploading';
    if (_progress < 0.5) return 'Analyzing';
    if (_progress < 0.8) return 'Processing';
    return 'Finalizing';
  }

  String _getInfoText() {
    if (_progress >= 1.0) return 'Ready for review!';
    if (_progress >= 0.8) return 'Almost done...';
    if (_progress >= 0.5) return 'Extracting data from tables...';
    if (_progress >= 0.3) return 'Detecting tables and structure...';
    return 'Please wait while we analyze your waste form...';
  }
}