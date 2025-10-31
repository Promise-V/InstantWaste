import 'dart:io';
import 'package:http/http.dart' as http;
import 'dart:convert';
import '../models/waste_form_models.dart';

class WasteFormApi {
  // IMPORTANT: Update this URL based on your environment
  // For Android emulator: http://10.0.2.2:8080
  // For iOS simulator: http://localhost:8080
  // For physical device: http://YOUR_COMPUTER_IP:8080
  static const String baseUrl = 'https://instant-waste-519509549362.us-central1.run.app/api';

  /// ========== EXISTING METHODS (Keep these) ==========
  
  /// Upload image and get scan results
  Future<ScanResult> processWasteForm(File imageFile) async {
    try {
      final uri = Uri.parse('$baseUrl/waste-form/process');
      
      // Create multipart request
      final request = http.MultipartRequest('POST', uri);
      
      // Add image file
      final imageStream = http.ByteStream(imageFile.openRead());
      final imageLength = await imageFile.length();
      
      final multipartFile = http.MultipartFile(
        'image',
        imageStream,
        imageLength,
        filename: 'waste_form.jpg',
      );
      
      request.files.add(multipartFile);
      
      print('📤 Uploading image to: $uri');
      
      // Send request
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);
      
      print('📥 Response status: ${response.statusCode}');
      
      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        print('✅ Scan successful!');
        print('Items detected: ${jsonData['itemsDetected']}');
        print('Accuracy: ${jsonData['accuracy']}%');
        
        return ScanResult.fromJson(jsonData);
      } else {
        throw Exception('Failed to process image: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ API Error: $e');
      rethrow;
    }
  }

  /// Submit reviewed/edited data
  Future<bool> submitWasteForm(ScanResult scanResult) async {
    try {
      final uri = Uri.parse('$baseUrl/waste-form/submit');
      
      final response = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: json.encode(scanResult.toJson()),
      );
      
      print('📥 Submit response: ${response.statusCode}');
      
      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        
        if (jsonData['success'] == true) {
          print('✅ Submission successful!');
          return true;
        } else {
          print('⚠️ Validation errors: ${jsonData['errors']}');
          throw Exception(jsonData['errors']?.toString() ?? 'Validation failed');
        }
      } else {
        throw Exception('Failed to submit: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Submit Error: $e');
      rethrow;
    }
  }

  /// Health check to verify backend connection
  Future<bool> checkHealth() async {
    try {
      final uri = Uri.parse('$baseUrl/health');
      final response = await http.get(uri);
      
      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        print('✅ Backend health: ${jsonData['status']}');
        return jsonData['status'] == 'ok';
      }
      return false;
    } catch (e) {
      print('❌ Health check failed: $e');
      return false;
    }
  }

  /// ========== NEW PROGRESS TRACKING METHODS ==========
  
  /// Start OCR with progress tracking and return session ID
  Future<String> startOCRWithProgress(File imageFile) async {
    try {
      final uri = Uri.parse('$baseUrl/waste-form/process-with-progress');
      
      // Create multipart request
      final request = http.MultipartRequest('POST', uri);
      
      // Add image file
      final imageStream = http.ByteStream(imageFile.openRead());
      final imageLength = await imageFile.length();
      
      final multipartFile = http.MultipartFile(
        'image',
        imageStream,
        imageLength,
        filename: 'waste_form.jpg',
      );
      
      request.files.add(multipartFile);
      
      print('🚀 Starting OCR with progress tracking...');
      
      // Send request
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);
      
      print('📥 Start OCR response: ${response.statusCode}');
      
 if (response.statusCode == 200) {
    final jsonData = json.decode(response.body);
    final sessionId = jsonData['sessionId'];
    return sessionId;
} else {
        throw Exception('Failed to start OCR: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Start OCR Error: $e');
      rethrow;
    }
  }

  /// Get current progress for an OCR session
  Future<Map<String, dynamic>> getOCRProgress(String sessionId) async {
    try {
      final uri = Uri.parse('$baseUrl/waste-form/progress/$sessionId');
      
      print('🔄 Checking progress for session: $sessionId');
      
      final response = await http.get(uri);
      
      if (response.statusCode == 200) {
        final progressData = json.decode(response.body);
        final progress = progressData['progress'];
        final message = progressData['message'];
        
        print('📊 Progress: ${(progress * 100).toInt()}% - $message');
        
        return {
          'progress': progress,
          'message': message,
        };
      } else if (response.statusCode == 404) {
        throw Exception('Session not found - may have expired');
      } else {
        throw Exception('Failed to get progress: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Progress check Error: $e');
      rethrow;
    }
  }

  /// Get final OCR result when progress reaches 100%
  Future<ScanResult> getOCRResult(String sessionId) async {
    try {
      final uri = Uri.parse('$baseUrl/waste-form/result/$sessionId');
      
      print('🎯 Getting final result for session: $sessionId');
      
      final response = await http.get(uri);
      
      print('📥 Result response: ${response.statusCode}');
      
      if (response.statusCode == 200) {
        final jsonData = json.decode(response.body);
        print('✅ Final result received successfully!');
        
        return ScanResult.fromJson(jsonData);
      } else if (response.statusCode == 404) {
        throw Exception('Result not found - session may have expired');
      } else {
        throw Exception('Failed to get result: ${response.statusCode}');
      }
    } catch (e) {
      print('❌ Get Result Error: $e');
      rethrow;
    }
  }

  /// Fallback method - use regular process if progress endpoints aren't available
  Future<ScanResult> processWithProgressFallback(File imageFile) async {
    try {
      // First try the progress endpoint
      return await processWasteForm(imageFile);
    } catch (e) {
      print('⚠️ Progress endpoint not available, using regular process');
      return await processWasteForm(imageFile);
    }
  }
}