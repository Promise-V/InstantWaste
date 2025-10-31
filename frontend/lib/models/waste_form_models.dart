import 'package:flutter/material.dart';

/// Represents the complete scan result from the backend
class ScanResult {
  final int totalFields;
  final int fieldsNeedingReview;
  final int emptyFields;
  final List<WasteTable> tables;
  int _editedFieldsCount = 0;

  ScanResult({
    required this.totalFields,
    required this.fieldsNeedingReview,
    required this.emptyFields,
    required this.tables,
  });

  double get accuracy {
    if (totalFields == 0) return 100.0;
    return ((totalFields - _editedFieldsCount) / totalFields * 100)
        .clamp(0.0, 100.0);
  }

  void recordEdit() {
    _editedFieldsCount++;
  }

  factory ScanResult.fromJson(Map<String, dynamic> json) {
    return ScanResult(
      totalFields: json['totalFields'] ?? 0,
      fieldsNeedingReview: json['fieldsNeedingReview'] ?? 0,
      emptyFields: json['emptyFields'] ?? 0,
      tables: (json['tables'] as List?)
              ?.map((table) => WasteTable.fromJson(table))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'totalFields': totalFields,
      'fieldsNeedingReview': fieldsNeedingReview,
      'emptyFields': emptyFields,
      'tables': tables.map((table) => table.toJson()).toList(),
    };
  }
}

/// Represents a single table in the waste form
class WasteTable {
  final String tableName;
  final TableType tableType;
  final List<WasteRow> rows;

  WasteTable({
    required this.tableName,
    required this.tableType,
    required this.rows,
  });

  factory WasteTable.fromJson(Map<String, dynamic> json) {
    return WasteTable(
      tableName: json['tableName'] ?? '',
      tableType: _parseTableType(json['tableType']),
      rows: (json['rows'] as List?)
              ?.map((row) => WasteRow.fromJson(row))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'tableName': tableName,
      'tableType': tableType.name,
      'rows': rows.map((row) => row.toJson()).toList(),
    };
  }

  static TableType _parseTableType(String? type) {
    switch (type) {
      case 'RAW_WASTE_5COL':
        return TableType.rawWaste5Col;
      case 'RAW_WASTE_3COL':
        return TableType.rawWaste3Col;
      case 'COMPLETED_WASTE_2COL':
        return TableType.completedWaste2Col;
      default:
        return TableType.unknown;
    }
  }
}

/// Enum for table types
enum TableType {
  rawWaste5Col,
  rawWaste3Col,
  completedWaste2Col,
  unknown;

  String get name {
    switch (this) {
      case TableType.rawWaste5Col:
        return 'RAW_WASTE_5COL';
      case TableType.rawWaste3Col:
        return 'RAW_WASTE_3COL';
      case TableType.completedWaste2Col:
        return 'COMPLETED_WASTE_2COL';
      default:
        return 'UNKNOWN';
    }
  }

  String get displayName {
    switch (this) {
      case TableType.rawWaste5Col:
      case TableType.rawWaste3Col:
        return 'Raw Waste';
      case TableType.completedWaste2Col:
        return 'Completed Waste';
      default:
        return 'Unknown Table';
    }
  }
  
  String get category {
    switch (this) {
      case TableType.rawWaste5Col:
      case TableType.rawWaste3Col:
        return 'Raw Waste';
      case TableType.completedWaste2Col:
        return 'Completed Waste';
      default:
        return 'Unknown';
    }
  }
}

/// Represents a single row in a waste table
class WasteRow {
  final String item;
  FieldData open; // Remove 'final'
  FieldData swing; // Remove 'final'
  FieldData close; // Remove 'final'
  FieldData size; // Remove 'final'
  FieldData count; // Remove 'final'
  String comments = ""; // NEW: Add comments field

  WasteRow({
    required this.item,
    required this.open,
    required this.swing,
    required this.close,
    required this.size,
    required this.count,
    this.comments = "", // NEW: constructor with default value
  });

  factory WasteRow.fromJson(Map<String, dynamic> json) {
    return WasteRow(
      item: json['item'] ?? '',
      open: FieldData.fromJson(json['open'] ?? {}),
      swing: FieldData.fromJson(json['swing'] ?? {}),
      close: FieldData.fromJson(json['close'] ?? {}),
      size: FieldData.fromJson(json['size'] ?? {}),
      count: FieldData.fromJson(json['count'] ?? {}),
      comments: json['comments'] ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'item': item,
      'open': open.toJson(),
      'swing': swing.toJson(),
      'close': close.toJson(),
      'size': size.toJson(),
      'count': count.toJson(),
      'comments': comments, 
    };
  }
  
  // ✅ FIXED: New filter logic with verified, needReview, empty
  bool matchesFilter(FieldFilter filter, TableType tableType) {
    List<FieldData> relevantFields;
    
    // Determine which fields are relevant based on table type
    if (tableType == TableType.completedWaste2Col || tableType == TableType.rawWaste3Col) {
      relevantFields = [count];
    } else if (tableType == TableType.rawWaste5Col) {
      relevantFields = [open, swing, close];
    } else {
      relevantFields = [open, swing, close, count];
    }
    
    switch (filter) {
      case FieldFilter.verified:
        // ✅ Verified: At least one field has data AND none need review
        bool hasAnyData = relevantFields.any((f) => !f.isEmpty);
        bool noReviewNeeded = !relevantFields.any((f) => f.needsReview);
        return hasAnyData && noReviewNeeded;
        
      case FieldFilter.needReview:
        // ✅ Need Review: At least one field needs review
        return relevantFields.any((f) => f.needsReview);
        
      case FieldFilter.empty:
        // ✅ Empty: All fields are empty
        return relevantFields.every((f) => f.isEmpty);
    }
  }
}

// ✅ UPDATED: Changed 'all' to 'verified'
enum FieldFilter {
  verified,
  needReview,
  empty;
}

/// Represents a single field with validation metadata
class FieldData {
  String value;
  bool isEmpty; // Remove 'final'
  bool needsReview; // Remove 'final'
  String issue; // Remove 'final'
  String _originalValue;

  FieldData({
    required this.value,
    required this.isEmpty,
    required this.needsReview,
    required this.issue,
  }) : _originalValue = value;

// NEW: a simple constructor for creating editable fields
  FieldData.editable(String initialValue) 
    : value = initialValue,
      isEmpty = initialValue.isEmpty,
      needsReview = false,
      issue = '',
      _originalValue = initialValue;

  factory FieldData.fromJson(Map<String, dynamic> json) {
    return FieldData(
      value: json['value']?.toString() ?? '',
      isEmpty: json['isEmpty'] ?? true,
      needsReview: json['needsReview'] ?? false,
      issue: json['issue']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'value': value,
      'isEmpty': isEmpty,
      'needsReview': needsReview,
      'issue': issue,
    };
  }

  bool get wasEdited => value != _originalValue;
  // NEW: method to update the value and recalculate properties
  void updateValue(String newValue) {
    value = newValue;
    isEmpty = newValue.trim().isEmpty;
    needsReview = false; // Assume user has reviewed it
    issue = ''; // Clear any issues when user edits
  }
  Color getStatusColor() {
    if (needsReview) return Colors.orange;
    if (isEmpty) return Colors.red.shade100;
    return Colors.green.shade100;
  }

  IconData getStatusIcon() {
    if (needsReview) return Icons.warning_amber_rounded;
    if (isEmpty) return Icons.error_outline;
    return Icons.check_circle_outline;
  }
}