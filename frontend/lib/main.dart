
import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

void main() {
  runApp(const InstantWasteApp());
}

class InstantWasteApp extends StatelessWidget {
  const InstantWasteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Instant Waste',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: const Color(0xFF0000FF),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0000FF),
          primary: const Color(0xFF0000FF),
        ),
        scaffoldBackgroundColor: Colors.grey[100],
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF0000FF),
          foregroundColor: Colors.white,
          elevation: 0,
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF0000FF),
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(30),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 20),
          ),
        ),
      ),
      home: const HomeScreen(),
    );
  }
}