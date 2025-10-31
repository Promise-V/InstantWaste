import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:instant_waste/screens/prescan_checklist_screen.dart';
import 'manual_entry_screen.dart';
import 'ml_scanner_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Colors.grey[100]!,
              const Color(0xFF0000FF).withOpacity(0.1),
            ],
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // App Logo/Title
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0000FF),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Text(
                    'Instant\nWaste.',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      height: 1.2,
                    ),
                  ),
                ),
                
                const SizedBox(height: 40),
                
                // Title and Description
                const Text(
                  'Instant Waste.',
                  style: TextStyle(
                    fontSize: 42,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0000FF),
                  ),
                ),
                
                const SizedBox(height: 8),
                
                const Text(
                  'Document OCR Scanner',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                
                const SizedBox(height: 40),
                
                // Process Steps
                _buildProcessStep('📸', 'Capture', 'Take a clear photo of your waste sheet'),
                _buildProcessStep('🔍', 'Scan', 'AI automatically extracts the data'),
                _buildProcessStep('✏️', 'Edit', 'Review and make any corrections'),
                _buildProcessStep('✅', 'Done', 'Submit for processing'),
                
                const Spacer(),
                
                // Action Buttons
                Center(
                  child: Column(
                    children: [
                      // Automatic Entry Button
                      ElevatedButton.icon(
                        onPressed: () async {
                          // Show dialog with Camera or Gallery options
                          _showScanOptions(context);
                        },
                        icon: const Icon(Icons.camera_alt, size: 28),
                        label: const Text(
                          'Automatic Entry',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF0000FF),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 50,
                            vertical: 20,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(30),
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 16),
                      
                      // Manual Entry Button
                      OutlinedButton.icon(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const ManualEntryScreen(),
                            ),
                          );
                        },
                        icon: const Icon(Icons.edit_note, size: 28),
                        label: const Text(
                          'Manual Entry',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: const Color(0xFF0000FF),
                          side: const BorderSide(color: Color(0xFF0000FF), width: 2),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 50,
                            vertical: 20,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(30),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(height: 40),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // Show Camera or Gallery options
  void _showScanOptions(BuildContext context) {
  showModalBottomSheet(
    context: context,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (BuildContext context) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Choose Source',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 20),
              
              // Camera option (ML Kit)
              ListTile(
                leading: const Icon(Icons.camera_alt, color: Color(0xFF0000FF), size: 32),
                title: const Text('Scan with Camera', style: TextStyle(fontSize: 18)),
                subtitle: const Text('Auto-detect document edges'),
                onTap: () {
                  Navigator.pop(context); // Close bottom sheet
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const MLScannerScreen(), // ✅ Navigate to intermediate screen
                    ),
                  );
                },
              ),
              
              const Divider(),
              
              // Gallery option
              ListTile(
                leading: const Icon(Icons.photo_library, color: Color(0xFF0000FF), size: 32),
                title: const Text('Pick from Gallery', style: TextStyle(fontSize: 18)),
                subtitle: const Text('Choose existing photo'),
                onTap: () {
                  Navigator.pop(context);
                  _pickFromGallery(context);
                },
              ),
            ],
          ),
        ),
      );
    },
  );
}

  // Gallery Picker
  Future<void> _pickFromGallery(BuildContext context) async {
    try {
      print("📸 Opening gallery...");
      
      final ImagePicker picker = ImagePicker();
      final XFile? pickedFile = await picker.pickImage(
        source: ImageSource.gallery,
      );
      
      if (pickedFile != null) {
        final imageFile = File(pickedFile.path);
        
        print("✅ Image picked: ${imageFile.path}");
        
        if (context.mounted) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => PreScanChecklistScreen(
                imageFile: imageFile,
                corners: null,
              ),
            ),
          );
        }
      }
      
    } catch (e) {
      print("❌ Gallery error: $e");
      
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to pick image: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Widget _buildProcessStep(String emoji, String title, String description) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            emoji,
            style: const TextStyle(fontSize: 28),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey[700],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}