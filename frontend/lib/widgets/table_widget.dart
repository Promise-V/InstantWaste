import 'package:flutter/material.dart';
import '../models/waste_form_models.dart';

class WasteTableWidget extends StatelessWidget {
  final WasteTable table;
  final FieldFilter currentFilter;
  final VoidCallback onFieldEdited;

  const WasteTableWidget({
    super.key,
    required this.table,
    required this.currentFilter,
    required this.onFieldEdited,
  });

  @override
  Widget build(BuildContext context) {
    final filteredRows = table.rows
        .where((row) => row.matchesFilter(currentFilter, table.tableType))
        .toList();

    return Container(
      color: Colors.grey[200],
      child: Column(
        children: [
          // Table Header
          Container(
            padding: const EdgeInsets.all(16),
            color: const Color(0xFF0000FF),
            child: Row(
              children: [
                const Icon(Icons.table_chart, color: Colors.white),
                const SizedBox(width: 12),
                Text(
                  table.tableType.displayName,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                Text(
                  '${filteredRows.length} items',
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          
          // Table Content
          Expanded(
            child: filteredRows.isEmpty
                ? Center(
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
                          _getEmptyMessage(currentFilter),
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey[600],
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: filteredRows.length,
                    itemBuilder: (context, index) {
                      return _buildRowCard(context, filteredRows[index]);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  String _getEmptyMessage(FieldFilter filter) {
    switch (filter) {
      case FieldFilter.verified:  // ✅ Changed from .all
        return 'No verified items found';
      case FieldFilter.needReview:
        return 'No fields need review! 🎉';
      case FieldFilter.empty:
        return 'No empty fields! 🎉';
    }
  }

  Widget _buildRowCard(BuildContext context, WasteRow row) {
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
              onFieldEdited();
              Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}