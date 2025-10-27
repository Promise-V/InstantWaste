import 'package:flutter/material.dart';
import '../models/waste_form_models.dart';
import '../widgets/progress_indicator.dart';
import '../widgets/stats_dashboard.dart';
//import '../widgets/table_widget.dart';
import 'submit_screen.dart';

class EditReviewScreen extends StatefulWidget {
  final ScanResult scanResult;

  const EditReviewScreen({
    super.key,
    required this.scanResult,
  });

  @override
  State<EditReviewScreen> createState() => _EditReviewScreenState();
}

class _EditReviewScreenState extends State<EditReviewScreen> {
  late ScanResult _scanResult;
  String _selectedCategory = 'Raw Waste';
FieldFilter _selectedFilter = FieldFilter.verified;

  @override
  void initState() {
    super.initState();
    _scanResult = widget.scanResult;
    
    final categories = _getOrderedCategories();
    if (categories.isNotEmpty) {
      _selectedCategory = categories.first;
    }
  }

  List<String> _getOrderedCategories() {
    final grouped = _groupTablesByCategory();
    final categories = grouped.keys.toList();
    
    categories.sort((a, b) {
      if (a == 'Completed Waste') return -1;
      if (b == 'Completed Waste') return 1;
      return a.compareTo(b);
    });
    
    return categories;
  }

  Map<String, List<WasteTable>> _groupTablesByCategory() {
    Map<String, List<WasteTable>> grouped = {};
    
    for (var table in _scanResult.tables) {
      String category = table.tableType.category;
      grouped.putIfAbsent(category, () => []);
      grouped[category]!.add(table);
    }
    
    return grouped;
  }

  void _onFieldEdited() {
    setState(() {
      _scanResult.recordEdit();
    });
  }
  
  void _onFilterChanged(FieldFilter newFilter) {
    setState(() {
      _selectedFilter = newFilter;
    });
  }

  void _proceedToSubmit() {
    if (_scanResult.fieldsNeedingReview > 0) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('⚠️ Review Required'),
          content: Text(
            'There are ${_scanResult.fieldsNeedingReview} fields that need your review.\n\nAre you sure you want to continue?',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Go Back'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                _navigateToSubmit();
              },
              child: const Text('Continue Anyway'),
            ),
          ],
        ),
      );
    } else {
      _navigateToSubmit();
    }
  }

  void _navigateToSubmit() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SubmitScreen(scanResult: _scanResult),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final groupedTables = _groupTablesByCategory();
    final categories = _getOrderedCategories();

    return Scaffold(
      backgroundColor: Colors.grey[100],
      body: SafeArea(
        child: Column(
          children: [
            // Progress Indicator
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.white,
              child: const WasteFormProgressIndicator(currentStep: 2),
            ),
            
            // Stats Dashboard with filter
            StatsDashboard(
              scanResult: _scanResult,
              selectedFilter: _selectedFilter,
              onFilterChanged: _onFilterChanged,
            ),
            
            // Table Navigation Tabs
            if (categories.length > 1)
              Container(
                height: 50,
                color: Colors.white,
                child: Row(
                  children: categories.map((category) {
                    final isSelected = category == _selectedCategory;
                    
                    return Expanded(
                      child: GestureDetector(
                        onTap: () {
                          setState(() {
                            _selectedCategory = category;
                          });
                        },
                        child: Container(
                          decoration: BoxDecoration(
                            border: Border(
                              bottom: BorderSide(
                                color: isSelected
                                    ? const Color(0xFF0000FF)
                                    : Colors.transparent,
                                width: 3,
                              ),
                            ),
                          ),
                          child: Center(
                            child: Text(
                              category,
                              style: TextStyle(
                                color: isSelected
                                    ? const Color(0xFF0000FF)
                                    : Colors.grey[600],
                                fontWeight: isSelected
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                fontSize: 16,
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            
            // ✅ FIXED: Single scrollable list of all rows from all tables in category
            Expanded(
              child: _buildCombinedTableView(groupedTables),
            ),
            
            // Action Buttons
            Container(
              padding: const EdgeInsets.all(20),
              color: Colors.white,
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Tap any field to edit'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFF0000FF),
                        side: const BorderSide(
                          color: Color(0xFF0000FF),
                          width: 2,
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      child: const Text(
                        'EDIT',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                  
                  const SizedBox(width: 16),
                  
                  Expanded(
                    child: ElevatedButton(
                      onPressed: _proceedToSubmit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0000FF),
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      child: const Text(
                        'SUBMIT',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ✅ NEW: Build a single unified view with all rows from all tables in category
  Widget _buildCombinedTableView(Map<String, List<WasteTable>> groupedTables) {
    final tablesInCategory = groupedTables[_selectedCategory] ?? [];
    
    if (tablesInCategory.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.inbox_outlined, size: 64, color: Colors.grey[400]),
            const SizedBox(height: 16),
            Text(
              'No tables in $_selectedCategory category',
              style: TextStyle(fontSize: 16, color: Colors.grey[600]),
            ),
          ],
        ),
      );
    }

    // ✅ Combine all rows from all tables in this category
    List<_TableRowPair> allRows = [];
    for (var table in tablesInCategory) {
      for (var row in table.rows) {
        if (row.matchesFilter(_selectedFilter, table.tableType)) {
          allRows.add(_TableRowPair(table: table, row: row));
        }
      }
    }

    if (allRows.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.check_circle_outline,
              size: 64,
              color: Colors.grey[400],
            ),
            const SizedBox(height: 16),
            Text(
              _getEmptyMessage(_selectedFilter),
              style: TextStyle(
                fontSize: 16,
                color: Colors.grey[600],
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }

    return Container(
      color: Colors.grey[200],
      child: ListView.builder(
        padding: const EdgeInsets.all(8),
        itemCount: allRows.length,
        itemBuilder: (context, index) {
          final pair = allRows[index];
          return _buildRowCard(context, pair.row, pair.table);
        },
      ),
    );
  }

String _getEmptyMessage(FieldFilter filter) {
  switch (filter) {
    case FieldFilter.verified:
      return 'No verified items found';
    case FieldFilter.needReview:
      return 'No fields need review! 🎉';
    case FieldFilter.empty:
      return 'No empty fields! 🎉';
  }
}

  Widget _buildRowCard(BuildContext context, WasteRow row, WasteTable table) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Item Name
            Row(
              children: [
                Expanded(
                  child: Text(
                    row.item,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                // Size badge if present
                if (!row.size.isEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.grey[300],
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      row.size.value,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // Fields based on table type
            if (table.tableType == TableType.rawWaste5Col)
              _build5ColumnFields(context, row)
            else if (table.tableType == TableType.rawWaste3Col)
              _build3ColumnFields(context, row)
            else
              _build2ColumnFields(context, row),
          ],
        ),
      ),
    );
  }

  Widget _build5ColumnFields(BuildContext context, WasteRow row) {
    return Row(
      children: [
        Expanded(
          child: _buildEditableField(
            context,
            label: 'OPEN',
            field: row.open,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _buildEditableField(
            context,
            label: 'SWING',
            field: row.swing,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _buildEditableField(
            context,
            label: 'CLOSE',
            field: row.close,
          ),
        ),
      ],
    );
  }

  Widget _build3ColumnFields(BuildContext context, WasteRow row) {
    return _buildEditableField(
      context,
      label: 'COUNT',
      field: row.count,
    );
  }

  Widget _build2ColumnFields(BuildContext context, WasteRow row) {
    return _buildEditableField(
      context,
      label: 'COUNT',
      field: row.count,
    );
  }

  Widget _buildEditableField(
    BuildContext context, {
    required String label,
    required FieldData field,
  }) {
    return GestureDetector(
      onTap: () => _showEditDialog(context, label, field),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: field.getStatusColor(),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: field.needsReview
                ? Colors.orange.shade700
                : Colors.transparent,
            width: 2,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Label with icon
            Row(
              children: [
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: Colors.grey[700],
                  ),
                ),
                const Spacer(),
                Icon(
                  field.getStatusIcon(),
                  size: 16,
                  color: field.needsReview
                      ? Colors.orange.shade700
                      : field.isEmpty
                          ? Colors.red.shade700
                          : Colors.green.shade700,
                ),
              ],
            ),
            const SizedBox(height: 4),
            
            // Value
            Text(
              field.isEmpty ? '—' : field.value,
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: field.isEmpty ? Colors.grey[400] : Colors.black87,
              ),
            ),
            
            // Issue message
            if (field.needsReview && field.issue.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  field.issue,
                  style: TextStyle(
                    fontSize: 10,
                    color: Colors.orange.shade700,
                    fontStyle: FontStyle.italic,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _showEditDialog(BuildContext context, String label, FieldData field) {
    final controller = TextEditingController(text: field.value);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Edit $label'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (field.needsReview && field.issue.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(12),
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: Colors.orange.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.orange.shade300),
                ),
                child: Row(
                  children: [
                    Icon(Icons.warning_amber, color: Colors.orange.shade700),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        field.issue,
                        style: TextStyle(
                          color: Colors.orange.shade900,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: label,
                border: const OutlineInputBorder(),
                hintText: 'Enter value',
              ),
              autofocus: true,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              field.value = controller.text;
              _onFieldEdited();
              Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}

// ✅ Helper class to pair table with row
class _TableRowPair {
  final WasteTable table;
  final WasteRow row;
  
  _TableRowPair({required this.table, required this.row});
}