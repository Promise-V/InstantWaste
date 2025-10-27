import 'dart:io'; // ADD THIS - for File class
import 'package:flutter/material.dart';
import 'package:instant_waste/screens/DocumentScannerScreen.dart';
import 'package:instant_waste/screens/prescan_checklist_screen.dart'; // ADD THIS
//import 'capture_screen.dart';
import 'manual_entry_screen.dart';

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
                                  final result = await Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (context) => const DocumentScannerScreen(),
                                    ),
                                  );
                                  
                                  if (result != null) {
                                    // Convert path string to File
                                    final imageFile = File(result['imagePath']);
                                    
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (context) => PreScanChecklistScreen(
                                          imageFile: imageFile,
                                          corners: result['corners'], // Pass corners for deskewing
                                        ),
                                      ),
                                    );
                                  }
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
                      
                      // Manual Entry Button - NOW CONNECTED!
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