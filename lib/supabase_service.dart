import 'dart:convert';
import 'package:http/http.dart' as http;
import 'secrets.dart';

final _headers = {
  'Content-Type': 'application/json',
  'apikey': supabaseKey,
  'Authorization': 'Bearer $supabaseKey',
};

Future<void> pushBattery(String code, int battery) async {
  try {
    await http.post(
      Uri.parse('$supabaseUrl/rest/v1/users'),
      headers: {..._headers, 'Prefer': 'resolution=merge-duplicates'},
      body: jsonEncode({
        'code': code,
        'battery': battery,
        'updated_at': DateTime.now().toIso8601String(),
      }),
    );
  } catch (_) {}
}

Future<int?> fetchBattery(String code) async {
  try {
    final r = await http.get(
      Uri.parse('$supabaseUrl/rest/v1/users?code=eq.$code&select=battery'),
      headers: _headers,
    );
    if (r.statusCode == 200) {
      final d = jsonDecode(r.body) as List;
      if (d.isNotEmpty) return d[0]['battery'] as int?;
    }
  } catch (_) {}
  return null;
}

Future<bool> codeExists(String code) async {
  try {
    final r = await http.get(
      Uri.parse('$supabaseUrl/rest/v1/users?code=eq.$code&select=code'),
      headers: _headers,
    );
    if (r.statusCode == 200) {
      return (jsonDecode(r.body) as List).isNotEmpty;
    }
  } catch (_) {}
  return false;
}