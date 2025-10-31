import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_mlkit_document_scanner/google_mlkit_document_scanner.dart';
import 'prescan_checklist_screen.dart';

class MLScannerScreen extends StatefulWidget {
  const MLScannerScreen({super.key});

  @override
  State<MLScannerScreen> createState() => _MLScannerScreenState();
}

class _MLScannerScreenState extends State<MLScannerScreen> {
  @override
  void initState() {
    super.initState();
    _startScanning();
  }

  Future<void> _startScanning() async {
    DocumentScanner? scanner; // ✅ Declare outside try block
    
    try {
      print("📱 Initializing ML Kit Document Scanner...");
      
      final options = DocumentScannerOptions(
        documentFormat: DocumentFormat.jpeg,
        mode: ScannerMode.full,
        pageLimit: 1,
      );
      
      scanner = DocumentScanner(options: options);
      
      print("📷 Opening scanner...");
      final result = await scanner.scanDocument();
      
      print("📊 Scan result: ${result?.images.length ?? 0} images");
      
      if (result != null && result.images.isNotEmpty) {
        final scannedImagePath = result.images.first;
        final imageFile = File(scannedImagePath);
        
        print("✅ Document scanned: ${imageFile.path}");
        
        scanner.close();
        
        // Navigate to next screen
        if (mounted) {
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(
              builder: (context) => PreScanChecklistScreen(
                imageFile: imageFile,
                corners: null,
              ),
            ),
          );
        }
      } else {
        print("❌ No document scanned (user cancelled)");
        scanner.close();
        
        // Go back to home screen
        if (mounted) {
          Navigator.pop(context);
        }
      }
      
    } catch (e, stackTrace) {
      print("❌ ML Kit error: $e");
      print("Stack trace: $stackTrace");
      
      scanner?.close(); // ✅ Now scanner is in scope
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Scan failed: $e'),
            backgroundColor: Colors.red,
          ),
        );
        
        // Go back
        Navigator.pop(context);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Colors.black,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(color: Color(0xFF0000FF)),
            SizedBox(height: 20),
            Text(
              'Opening Scanner...',
              style: TextStyle(color: Colors.white, fontSize: 18),
            ),
          ],
        ),
      ),
    );
  }
}