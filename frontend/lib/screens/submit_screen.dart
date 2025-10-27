import 'package:flutter/material.dart';
import '../models/waste_form_models.dart';
import '../services/waste_form_api.dart';
import '../widgets/progress_indicator.dart';
import 'home_screen.dart';

class SubmitScreen extends StatefulWidget {
  final ScanResult scanResult;

  const SubmitScreen({
    super.key,
    required this.scanResult,
  });

  @override
  State<SubmitScreen> createState() => _SubmitScreenState();
}

class _SubmitScreenState extends State<SubmitScreen> {
  final WasteFormApi _api = WasteFormApi();
  bool _isSubmitting = true;
  bool _success = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _submitData();
  }

  Future<void> _submitData() async {
    try {
      await Future.delayed(const Duration(seconds: 1));

      final success = await _api.submitWasteForm(widget.scanResult);

      setState(() {
        _isSubmitting = false;
        _success = success;
      });

      // Auto-navigate after success
      if (_success) {
        await Future.delayed(const Duration(seconds: 2));
        if (mounted) {
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(builder: (context) => const HomeScreen()),
            (route) => false,
          );
        }
      }
    } catch (e) {
      setState(() {
        _isSubmitting = false;
        _success = false;
        _errorMessage = e.toString();
      });
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
              child: const WasteFormProgressIndicator(currentStep: 3),
            ),
            
            Expanded(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(40.0),
                  child: _isSubmitting
                      ? _buildSubmittingView()
                      : _success
                          ? _buildSuccessView()
                          : _buildErrorView(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSubmittingView() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const SizedBox(
          width: 100,
          height: 100,
          child: CircularProgressIndicator(
            strokeWidth: 8,
            valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF0000FF)),
          ),
        ),
        const SizedBox(height: 40),
        const Text(
          'Submitting your waste form...',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.w500,
          ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildSuccessView() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Success Checkmark
        Container(
          width: 150,
          height: 150,
          decoration: BoxDecoration(
            color: Colors.green.shade100,
            shape: BoxShape.circle,
          ),
          child: Icon(
            Icons.check_circle,
            size: 100,
            color: Colors.green.shade600,
          ),
        ),
        
        const SizedBox(height: 40),
        
        const Text(
          'Your Document Was Scanned Successfully!',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
          textAlign: TextAlign.center,
        ),
        
        const SizedBox(height: 16),
        
        Text(
          'Returning to home...',
          style: TextStyle(
            fontSize: 16,
            color: Colors.grey[600],
          ),
        ),
      ],
    );
  }

  Widget _buildErrorView() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Error Icon
        Container(
          width: 150,
          height: 150,
          decoration: BoxDecoration(
            color: Colors.red.shade100,
            shape: BoxShape.circle,
          ),
          child: Icon(
            Icons.error_outline,
            size: 100,
            color: Colors.red.shade600,
          ),
        ),
        
        const SizedBox(height: 40),
        
        const Text(
          'Submission Failed',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
          textAlign: TextAlign.center,
        ),
        
        const SizedBox(height: 16),
        
        Text(
          _errorMessage,
          style: TextStyle(
            fontSize: 14,
            color: Colors.grey[700],
          ),
          textAlign: TextAlign.center,
        ),
        
        const SizedBox(height: 40),
        
        // Retry Button
        ElevatedButton(
          onPressed: () {
            setState(() {
              _isSubmitting = true;
              _success = false;
              _errorMessage = '';
            });
            _submitData();
          },
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF0000FF),
            padding: const EdgeInsets.symmetric(
              horizontal: 40,
              vertical: 16,
            ),
          ),
          child: const Text('Retry'),
        ),
        
        const SizedBox(height: 16),
        
        // Go Back Button
        TextButton(
          onPressed: () {
            Navigator.pop(context);
          },
          child: const Text('Go Back to Edit'),
        ),
      ],
    );
  }
}