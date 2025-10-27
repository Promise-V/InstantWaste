import 'package:flutter/material.dart';

class WasteFormProgressIndicator extends StatelessWidget {
  final int currentStep;

  const WasteFormProgressIndicator({
    super.key,
    required this.currentStep,
  });

  @override
  Widget build(BuildContext context) {
    final steps = ['CAPTURE', 'SCAN', 'EDIT', 'SUBMIT'];

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(
        steps.length * 2 - 1,
        (index) {
          if (index.isOdd) {
            // Separator
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                '---',
                style: TextStyle(
                  color: Colors.grey[400],
                  fontWeight: FontWeight.bold,
                ),
              ),
            );
          }

          final stepIndex = index ~/ 2;
          final isActive = stepIndex <= currentStep;
          final isCurrent = stepIndex == currentStep;

          return Text(
            steps[stepIndex],
            style: TextStyle(
              color: isActive
                  ? (isCurrent ? Colors.green : const Color(0xFF0000FF))
                  : Colors.grey[400],
              fontWeight: isCurrent ? FontWeight.bold : FontWeight.normal,
              fontSize: isCurrent ? 16 : 14,
            ),
          );
        },
      ),
    );
  }
}