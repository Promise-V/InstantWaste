import 'package:flutter/material.dart';
import '../models/waste_form_models.dart';
import '../models/master_list.dart';
import '../services/waste_form_api.dart';
//import '../widgets/progress_indicator.dart';
import 'home_screen.dart';

class ManualEntryScreen extends StatefulWidget {
  const ManualEntryScreen({super.key});

  @override
  State<ManualEntryScreen> createState() => _ManualEntryScreenState();
}

class _ManualEntryScreenState extends State<ManualEntryScreen> {
  final TextEditingController _searchController = TextEditingController();
  final WasteFormApi _api = WasteFormApi();
  
  List<String> _allItems = [];
  List<String> _filteredItems = [];
  
  // Store field values for each item
  Map<String, Map<String, String>> _itemValues = {};
  
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _allItems = MasterList.getAllItems();
    _filteredItems = List.from(_allItems);
    
    // Initialize empty values for all items
    for (String item in _allItems) {
      _itemValues[item] = {
        'open': '',
        'swing': '',
        'close': '',
        'size': '',
        'count': '',
      };
    }
  }

  void _filterItems(String query) {
    setState(() {
      if (query.isEmpty) {
        _filteredItems = List.from(_allItems);
      } else {
        _filteredItems = _allItems
            .where((item) => item.toLowerCase().contains(query.toLowerCase()))
            .toList();
      }
    });
  }

  // Group filtered items by type
  Map<String, List<String>> _getGroupedItems() {
    Map<String, List<String>> grouped = {
      'Completed Waste': [],
      'Raw Waste': [],
    };
    
    for (String item in _filteredItems) {
      if (MasterList.isCompletedWaste(item)) {
        grouped['Completed Waste']!.add(item);
      } else {
        grouped['Raw Waste']!.add(item);
      }
    }
    
    return grouped;
  }

  void _updateField(String item, String field, String value) {
    setState(() {
      _itemValues[item]![field] = value;
    });
  }

  Future<void> _submitManualEntry() async {
    setState(() => _isSubmitting = true);

    try {
      // Build ScanResult from manual entries
      final scanResult = _buildScanResult();
      
      // Submit to backend
      final success = await _api.submitWasteForm(scanResult);
      
      if (success && mounted) {
        // Show success and return home
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('✅ Manual entry submitted successfully!'),
            backgroundColor: Colors.green,
          ),
        );
        
        await Future.delayed(const Duration(seconds: 1));
        
        if (mounted) {
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(builder: (context) => const HomeScreen()),
            (route) => false,
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('❌ Submission failed: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  ScanResult _buildScanResult() {
    // Count filled items
    int itemsDetected = 0;
    int fieldsNeedingReview = 0;
    int emptyFields = 0;

    List<WasteRow> completedRows = [];
    List<WasteRow> rawRows = [];

    for (var entry in _itemValues.entries) {
      String item = entry.key;
      Map<String, String> values = entry.value;

      // Check if any field has data
      bool hasData = values.values.any((v) => v.isNotEmpty);
      if (!hasData) continue;

      itemsDetected++;

      // Create field data objects
      FieldData createField(String value) {
        bool isEmpty = value.isEmpty;
        if (!isEmpty) {
          if (value.isEmpty) emptyFields++;
        }
        return FieldData(
          value: value,
          isEmpty: isEmpty,
          needsReview: false, // Manual entry doesn't need review
          issue: '',
        );
      }

      WasteRow row = WasteRow(
        item: item,
        open: createField(values['open']!),
        swing: createField(values['swing']!),
        close: createField(values['close']!),
        size: createField(values['size']!),
        count: createField(values['count']!),
      );

      // Separate by type
      if (MasterList.isCompletedWaste(item)) {
        completedRows.add(row);
      } else {
        rawRows.add(row);
      }
    }

    // Create tables
    List<WasteTable> tables = [];
    
    if (completedRows.isNotEmpty) {
      tables.add(WasteTable(
        tableName: 'Manual_CompletedWaste_2Column',
        tableType: TableType.completedWaste2Col,
        rows: completedRows,
      ));
    }
    
    if (rawRows.isNotEmpty) {
      tables.add(WasteTable(
        tableName: 'Manual_RawWaste_5Column',
        tableType: TableType.rawWaste5Col,
        rows: rawRows,
      ));
    }
// Count total fields across all tables
int totalFields = 0;
for (final table in tables) {
  for (final row in table.rows) {
    final fields = [row.open, row.swing, row.close, row.count];
    totalFields += fields.length;
  }
}

final scanResult = ScanResult(
  totalFields: totalFields,
  fieldsNeedingReview: 0,
  emptyFields: 0,
  tables: tables,
);
return scanResult;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[100],
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(16),
              color: const Color(0xFF0000FF),
              child: Column(
                children: [
                  Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back, color: Colors.white),
                        onPressed: () => Navigator.pop(context),
                      ),
                      const Expanded(
                        child: Text(
                          'Manual Entry',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                      const SizedBox(width: 48), // Balance the back button
                    ],
                  ),
                  
                  const SizedBox(height: 16),
                  
                  // Search bar
                  TextField(
                    controller: _searchController,
                    onChanged: _filterItems,
                    style: const TextStyle(color: Colors.black),
                    decoration: InputDecoration(
                      hintText: 'Search items...',
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: _searchController.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear),
                              onPressed: () {
                                _searchController.clear();
                                _filterItems('');
                              },
                            )
                          : null,
                      filled: true,
                      fillColor: Colors.white,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(30),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            
            // Items count breakdown
            Container(
              padding: const EdgeInsets.all(12),
              color: Colors.white,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _buildCountBadge(
                    _getGroupedItems()['Completed Waste']!.length,
                    'Completed',
                    Colors.green,
                  ),
                  const SizedBox(width: 16),
                  _buildCountBadge(
                    _getGroupedItems()['Raw Waste']!.length,
                    'Raw',
                    Colors.orange,
                  ),
                  const SizedBox(width: 16),
                  _buildCountBadge(
                    _filteredItems.length,
                    'Total',
                    Colors.blue,
                  ),
                ],
              ),
            ),
            
            // Items list with sections
            Expanded(
              child: _buildGroupedItemsList(),
            ),
            
            // Submit button
            Container(
              padding: const EdgeInsets.all(20),
              color: Colors.white,
              child: ElevatedButton(
                onPressed: _isSubmitting ? null : _submitManualEntry,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF0000FF),
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(30),
                  ),
                ),
                child: _isSubmitting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )
                    : const Text(
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
    );
  }

  Widget _buildCountBadge(int count, String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        children: [
          Text(
            '$count',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              color: color.withOpacity(0.8),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGroupedItemsList() {
    final grouped = _getGroupedItems();
    
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Completed Waste Section
        if (grouped['Completed Waste']!.isNotEmpty) ...[
          _buildSectionHeader(
            'Completed Waste',
            grouped['Completed Waste']!.length,
            Colors.green,
            Icons.check_circle,
          ),
          ...grouped['Completed Waste']!.map((item) => _buildItemCard(item)),
          const SizedBox(height: 24),
        ],
        
        // Raw Waste Section
        if (grouped['Raw Waste']!.isNotEmpty) ...[
          _buildSectionHeader(
            'Raw Waste',
            grouped['Raw Waste']!.length,
            Colors.orange,
            Icons.restaurant,
          ),
          ...grouped['Raw Waste']!.map((item) => _buildItemCard(item)),
        ],
      ],
    );
  }

  Widget _buildSectionHeader(String title, int count, Color color, IconData icon) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(width: 12),
          Text(
            title,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: color.withOpacity(0.9),
            ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              '$count items',
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildItemCard(String item) {
    final bool isCompleted = MasterList.isCompletedWaste(item);
    
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isCompleted 
              ? Colors.green.withOpacity(0.3) 
              : Colors.orange.withOpacity(0.3),
          width: 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Item name with badge
            Row(
              children: [
                Expanded(
                  child: Text(
                    item,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                // Type badge
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: isCompleted ? Colors.green : Colors.orange,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        isCompleted ? Icons.check_circle : Icons.restaurant,
                        color: Colors.white,
                        size: 14,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        isCompleted ? '2-COL' : '5-COL',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 12),
            
            // Fields based on type
            if (isCompleted)
              _buildCompletedWasteFields(item)
            else
              _buildRawWasteFields(item),
          ],
        ),
      ),
    );
  }

  Widget _buildCompletedWasteFields(String item) {
    return Column(
      children: [
        _buildTextField(item, 'count', 'Count'),
      ],
    );
  }

  Widget _buildRawWasteFields(String item) {
    return Column(
      children: [
        _buildTextField(item, 'size', 'Size'),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(child: _buildTextField(item, 'open', 'Open')),
            const SizedBox(width: 8),
            Expanded(child: _buildTextField(item, 'swing', 'Swing')),
            const SizedBox(width: 8),
            Expanded(child: _buildTextField(item, 'close', 'Close')),
          ],
        ),
      ],
    );
  }

  Widget _buildTextField(String item, String field, String label) {
    return TextField(
      onChanged: (value) => _updateField(item, field, value),
      keyboardType: field == 'size' ? TextInputType.text : TextInputType.number,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        isDense: true,
      ),
    );
  }
}