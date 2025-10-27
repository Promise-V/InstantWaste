import 'package:flutter/material.dart';
import '../models/waste_form_models.dart';

class StatsDashboard extends StatefulWidget {
  final ScanResult scanResult;
  final FieldFilter selectedFilter;
  final Function(FieldFilter) onFilterChanged;

  const StatsDashboard({
    super.key,
    required this.scanResult,
    required this.selectedFilter,
    required this.onFilterChanged,
  });

  @override
  State<StatsDashboard> createState() => _StatsDashboardState();
}

class _StatsDashboardState extends State<StatsDashboard> {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      color: Colors.white,
      child: Column(
        children: [
          // Accuracy Badge - Live calculation
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: BoxDecoration(
              color: _getAccuracyColor(widget.scanResult.accuracy),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.verified, color: Colors.white, size: 20),
                const SizedBox(width: 8),
                Text(
                  '${widget.scanResult.accuracy.toStringAsFixed(1)}% Accuracy',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
              ],
            ),
          ),
          
          const SizedBox(height: 16),
          
          // ✅ FIXED: Stats Row with verified filter
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildStatCard(
                icon: Icons.check_circle,
                label: 'Verified',  // ✅ Changed from 'All'
                value: _getVerifiedCount().toString(),  // ✅ Changed method
                color: Colors.green,  // ✅ Changed from blue
                isSelected: widget.selectedFilter == FieldFilter.verified,  // ✅ Changed
                onTap: () => widget.onFilterChanged(FieldFilter.verified),  // ✅ Changed
              ),
              _buildStatCard(
                icon: Icons.warning_amber_rounded,
                label: 'Need Review',
                value: widget.scanResult.fieldsNeedingReview.toString(),
                color: Colors.orange,
                isSelected: widget.selectedFilter == FieldFilter.needReview,
                onTap: () => widget.onFilterChanged(FieldFilter.needReview),
              ),
              _buildStatCard(
                icon: Icons.remove_circle_outline,
                label: 'Empty',
                value: widget.scanResult.emptyFields.toString(),
                color: Colors.red,
                isSelected: widget.selectedFilter == FieldFilter.empty,
                onTap: () => widget.onFilterChanged(FieldFilter.empty),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ✅ NEW: Count verified rows (has data and no review flags)
  int _getVerifiedCount() {
    int count = 0;
    for (final table in widget.scanResult.tables) {
      for (final row in table.rows) {
        if (row.matchesFilter(FieldFilter.verified, table.tableType)) {
          count++;
        }
      }
    }
    return count;
  }

  Widget _buildStatCard({
    required IconData icon,
    required String label,
    required String value,
    required Color color,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? color : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: color,
            width: 2,
          ),
        ),
        child: Column(
          children: [
            Icon(
              icon,
              color: isSelected ? Colors.white : color,
              size: 28,
            ),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: isSelected ? Colors.white : color,
              ),
            ),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                color: isSelected ? Colors.white : Colors.grey[600],
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Color _getAccuracyColor(double accuracy) {
    if (accuracy >= 90) return Colors.green;
    if (accuracy >= 70) return Colors.orange;
    return Colors.red;
  }
}