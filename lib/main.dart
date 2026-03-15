import 'dart:convert';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'secrets.dart';

void main() => runApp(const MinecraftBatteryApp());

class MinecraftBatteryApp extends StatelessWidget {
  const MinecraftBatteryApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Minecraft Battery',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0A0A0A),
      ),
      home: const SettingsPage(),
    );
  }
}

// ── Supabase helper ───────────────────────────────────────────────────────────
class SupabaseService {
  static Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'apikey': supabaseKey,
        'Authorization': 'Bearer $supabaseKey',
      };

  static Future<bool> pushBattery(String code, int battery) async {
    try {
      final res = await http.post(
        Uri.parse('$supabaseUrl/rest/v1/users'),
        headers: {..._headers, 'Prefer': 'resolution=merge-duplicates'},
        body: jsonEncode({
          'code': code,
          'battery': battery,
          'updated_at': DateTime.now().toIso8601String(),
        }),
      );
      return res.statusCode == 200 || res.statusCode == 201;
    } catch (_) {
      return false;
    }
  }

  static Future<int?> fetchPartnerBattery(String partnerCode) async {
    try {
      final res = await http.get(
        Uri.parse(
            '$supabaseUrl/rest/v1/users?code=eq.$partnerCode&select=battery'),
        headers: _headers,
      );
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as List;
        if (data.isNotEmpty) return data[0]['battery'] as int?;
      }
    } catch (_) {}
    return null;
  }

  static Future<bool> codeExists(String code) async {
    try {
      final res = await http.get(
        Uri.parse('$supabaseUrl/rest/v1/users?code=eq.$code&select=code'),
        headers: _headers,
      );
      if (res.statusCode == 200) {
        return (jsonDecode(res.body) as List).isNotEmpty;
      }
    } catch (_) {}
    return false;
  }
}

// ── Code generator ────────────────────────────────────────────────────────────
String generateCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  final rng = Random.secure();
  return List.generate(6, (_) => chars[rng.nextInt(chars.length)]).join();
}

// ── Pixel heart ───────────────────────────────────────────────────────────────
enum HeartType { full, half, cracked, empty }

class PixelHeartPainter extends CustomPainter {
  final HeartType type;
  final Color fullColor;

  PixelHeartPainter(this.type, {this.fullColor = const Color(0xFFE03030)});

  static const List<String> _grid = [
    '.XX.XX.',
    'XXXXXXX',
    'XXXXXXX',
    '.XXXXX.',
    '..XXX..',
    '...X...',
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final cols = _grid[0].length;
    final rows = _grid.length;
    final cellW = size.width / cols;
    final cellH = size.height / rows;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        if (_grid[r][c] != 'X') continue;
        canvas.drawRect(
          Rect.fromLTWH(c * cellW, r * cellH, cellW, cellH),
          Paint()..color = _colorForPixel(c, cols),
        );
      }
    }
  }

  Color _colorForPixel(int col, int totalCols) {
    switch (type) {
      case HeartType.full:
        return fullColor;
      case HeartType.empty:
        return const Color(0xFF2A2A2A);
      case HeartType.half:
        return col < totalCols ~/ 2 ? fullColor : const Color(0xFF2A2A2A);
      case HeartType.cracked:
        return col == totalCols ~/ 2 ? const Color(0xFF1A0000) : fullColor;
    }
  }

  @override
  bool shouldRepaint(PixelHeartPainter old) =>
      old.type != type || old.fullColor != fullColor;
}

class PixelHeart extends StatelessWidget {
  final HeartType type;
  final double size;
  final Color color;

  const PixelHeart({
    super.key,
    required this.type,
    this.size = 28,
    this.color = const Color(0xFFE03030),
  });

  @override
  Widget build(BuildContext context) => SizedBox(
        width: size,
        height: size,
        child: CustomPaint(painter: PixelHeartPainter(type, fullColor: color)),
      );
}

// ── Heart row ─────────────────────────────────────────────────────────────────
class HeartRow extends StatelessWidget {
  final int battery;
  final double heartSize;
  final Color color;

  const HeartRow({
    super.key,
    required this.battery,
    this.heartSize = 24,
    this.color = const Color(0xFFE03030),
  });

  List<HeartType> _states() {
    final units = battery % 10;
    final halves =
        units >= 2 ? (battery ~/ 10) * 2 + 1 : (battery ~/ 10) * 2;
    return List.generate(10, (i) {
      final r = halves - i * 2;
      if (r >= 2) return HeartType.full;
      if (r == 1) return units >= 6 ? HeartType.cracked : HeartType.half;
      return HeartType.empty;
    });
  }

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: _states()
            .map((s) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: PixelHeart(type: s, size: heartSize, color: color),
                ))
            .toList(),
      );
}

// ── Settings Page ─────────────────────────────────────────────────────────────
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String _myCode       = '';
  String _partnerCode  = '';
  int    _previewBattery = 75;
  int?   _partnerBattery;
  String _myLabel      = 'Y';
  String _partnerLabel = 'P';

  bool   _isPaired   = false;
  bool   _isLinking  = false;
  bool   _codeCopied = false;
  String? _error;

  final _pairController  = TextEditingController();
  final _myLabelController      = TextEditingController();
  final _partnerLabelController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void dispose() {
    _pairController.dispose();
    _myLabelController.dispose();
    _partnerLabelController.dispose();
    super.dispose();
  }

  Future<void> _init() async {
    final prefs = await SharedPreferences.getInstance();
    String? code = prefs.getString('my_code');
    if (code == null) {
      code = generateCode();
      await prefs.setString('my_code', code);
    }

    // Register on Supabase so partner can find this code
    await SupabaseService.pushBattery(code, 0);

    final partner      = prefs.getString('partner_code') ?? '';
    final myLabel      = prefs.getString('my_label') ?? 'Y';
    final partnerLabel = prefs.getString('partner_label') ?? 'P';

    setState(() {
      _myCode       = code!;
      _partnerCode  = partner;
      _isPaired     = partner.isNotEmpty;
      _myLabel      = myLabel;
      _partnerLabel = partnerLabel;
      _pairController.text          = partner;
      _myLabelController.text       = myLabel;
      _partnerLabelController.text  = partnerLabel;
    });

    if (partner.isNotEmpty) _fetchPartner();
  }

  Future<void> _fetchPartner() async {
    if (_partnerCode.isEmpty) return;
    final b = await SupabaseService.fetchPartnerBattery(_partnerCode);
    if (mounted) setState(() => _partnerBattery = b);
  }

  Future<void> _pair() async {
    final code = _pairController.text.trim().toUpperCase();
    if (code.length != 6) {
      setState(() => _error = 'Code must be 6 characters');
      return;
    }
    if (code == _myCode) {
      setState(() => _error = "That's your own code!");
      return;
    }
    setState(() { _isLinking = true; _error = null; });

    final exists = await SupabaseService.codeExists(code);
    if (!exists) {
      setState(() {
        _isLinking = false;
        _error = 'Code not found — ask your partner to open the app first.';
      });
      return;
    }

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('partner_code', code);
    setState(() { _partnerCode = code; _isPaired = true; _isLinking = false; });
    _fetchPartner();
  }

  Future<void> _unpair() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('partner_code');
    setState(() {
      _partnerCode   = '';
      _isPaired      = false;
      _partnerBattery = null;
      _pairController.clear();
    });
  }

  Future<void> _saveLabels() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('my_label', _myLabel);
    await prefs.setString('partner_label', _partnerLabel);
  }

  Future<void> _copyCode() async {
    await Clipboard.setData(ClipboardData(text: _myCode));
    setState(() => _codeCopied = true);
    await Future.delayed(const Duration(seconds: 2));
    if (mounted) setState(() => _codeCopied = false);
  }

  // ── Build ──────────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _header(),
              const SizedBox(height: 28),
              _previewCard(),
              const SizedBox(height: 16),
              _myCodeCard(),
              const SizedBox(height: 14),
              _labelCard(),
              const SizedBox(height: 14),
              _isPaired ? _pairedCard() : _pairCard(),
              const SizedBox(height: 20),
              _infoBox(),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }

  // ── Header ─────────────────────────────────────────────────────────────────
  Widget _header() => Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF1C0A0A),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF3A1010), width: 1.5),
            ),
            child: const PixelHeart(type: HeartType.full, size: 24),
          ),
          const SizedBox(width: 14),
          const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Minecraft Battery',
                  style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                      fontFamily: 'monospace')),
              Text('Widget Settings',
                  style: TextStyle(
                      fontSize: 11,
                      color: Color(0xFF555555),
                      fontFamily: 'monospace')),
            ],
          ),
        ],
      );

  // ── Preview card ────────────────────────────────────────────────────────────
  Widget _previewCard() => _card(
        child: Column(
          children: [
            _sectionLabel('WIDGET PREVIEW'),
            const SizedBox(height: 18),
            _heartRowWithLabel(
                _myLabel.isEmpty ? 'Y' : _myLabel,
                _previewBattery,
                const Color(0xFFE03030)),
            if (_isPaired) ...[
              const SizedBox(height: 10),
              _heartRowWithLabel(
                  _partnerLabel.isEmpty ? 'P' : _partnerLabel,
                  _partnerBattery ?? 0,
                  const Color(0xFFD44080)),
            ],
            const SizedBox(height: 18),
            SliderTheme(
              data: SliderTheme.of(context).copyWith(
                activeTrackColor: const Color(0xFFE03030),
                inactiveTrackColor: const Color(0xFF222222),
                thumbColor: const Color(0xFFE03030),
                overlayColor: const Color(0x22E03030),
                trackHeight: 2,
                thumbShape:
                    const RoundSliderThumbShape(enabledThumbRadius: 6),
              ),
              child: Slider(
                value: _previewBattery.toDouble(),
                min: 0,
                max: 100,
                onChanged: (v) => setState(() => _previewBattery = v.round()),
              ),
            ),
            Text(
              'drag to preview  •  ${_previewBattery}%',
              style: const TextStyle(
                  color: Color(0xFF3A3A3A),
                  fontSize: 10,
                  fontFamily: 'monospace'),
            ),
          ],
        ),
      );

  Widget _heartRowWithLabel(String label, int battery, Color color) => Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 24,
            child: Text(
              label.isEmpty ? ' ' : label[0].toUpperCase(),
              textAlign: TextAlign.center,
              style: TextStyle(
                  color: color,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  fontFamily: 'monospace'),
            ),
          ),
          const SizedBox(width: 6),
          HeartRow(battery: battery, heartSize: 22, color: color),
        ],
      );

  // ── My code card ────────────────────────────────────────────────────────────
  Widget _myCodeCard() => _card(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _sectionLabel('YOUR PAIRING CODE'),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        vertical: 14, horizontal: 18),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0D0D0D),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFF222222)),
                    ),
                    child: Text(
                      _myCode.isEmpty ? '......' : _myCode,
                      style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                          fontFamily: 'monospace',
                          letterSpacing: 8),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                GestureDetector(
                  onTap: _copyCode,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: _codeCopied
                          ? const Color(0xFF0A2A0A)
                          : const Color(0xFF1A1A1A),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: _codeCopied
                            ? const Color(0xFF2A6A2A)
                            : const Color(0xFF2A2A2A),
                      ),
                    ),
                    child: Icon(
                      _codeCopied ? Icons.check_rounded : Icons.copy_rounded,
                      color: _codeCopied
                          ? const Color(0xFF4CAF50)
                          : const Color(0xFF555555),
                      size: 20,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            const Text('Share this code with your partner.',
                style: TextStyle(
                    color: Color(0xFF3A3A3A),
                    fontSize: 11,
                    fontFamily: 'monospace')),
          ],
        ),
      );

  // ── Label card ──────────────────────────────────────────────────────────────
  Widget _labelCard() => _card(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _sectionLabel('DISPLAY LABELS'),
            const SizedBox(height: 14),
            Row(
              children: [
                // My label
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Your initial',
                          style: TextStyle(
                              color: Color(0xFF555555),
                              fontSize: 11,
                              fontFamily: 'monospace')),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _myLabelController,
                        maxLength: 1,
                        textCapitalization: TextCapitalization.characters,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFE03030),
                            fontFamily: 'monospace'),
                        decoration: InputDecoration(
                          counterText: '',
                          hintText: 'Y',
                          hintStyle: const TextStyle(
                              color: Color(0xFF2A2A2A),
                              fontFamily: 'monospace',
                              fontSize: 28),
                          filled: true,
                          fillColor: const Color(0xFF0D0D0D),
                          border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFF222222))),
                          enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFF222222))),
                          focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFFE03030))),
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 14),
                        ),
                        onChanged: (v) {
                          if (v.isNotEmpty) {
                            setState(() => _myLabel = v.toUpperCase());
                            _saveLabels();
                          }
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                // Partner label
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Partner initial',
                          style: TextStyle(
                              color: Color(0xFF555555),
                              fontSize: 11,
                              fontFamily: 'monospace')),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _partnerLabelController,
                        maxLength: 1,
                        textCapitalization: TextCapitalization.characters,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFD44080),
                            fontFamily: 'monospace'),
                        decoration: InputDecoration(
                          counterText: '',
                          hintText: 'P',
                          hintStyle: const TextStyle(
                              color: Color(0xFF2A2A2A),
                              fontFamily: 'monospace',
                              fontSize: 28),
                          filled: true,
                          fillColor: const Color(0xFF0D0D0D),
                          border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFF222222))),
                          enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFF222222))),
                          focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                              borderSide: const BorderSide(
                                  color: Color(0xFFD44080))),
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 14),
                        ),
                        onChanged: (v) {
                          if (v.isNotEmpty) {
                            setState(() => _partnerLabel = v.toUpperCase());
                            _saveLabels();
                          }
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            const Text(
              'Single letter shown next to each heart row on the widget.',
              style: TextStyle(
                  color: Color(0xFF3A3A3A),
                  fontSize: 11,
                  fontFamily: 'monospace'),
            ),
          ],
        ),
      );

  // ── Pair card ───────────────────────────────────────────────────────────────
  Widget _pairCard() => _card(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _sectionLabel("ENTER PARTNER'S CODE"),
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _pairController,
                    textCapitalization: TextCapitalization.characters,
                    maxLength: 6,
                    style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                        fontFamily: 'monospace',
                        letterSpacing: 6),
                    decoration: InputDecoration(
                      counterText: '',
                      hintText: '······',
                      hintStyle: const TextStyle(
                          color: Color(0xFF2A2A2A),
                          fontFamily: 'monospace',
                          letterSpacing: 6,
                          fontSize: 24),
                      filled: true,
                      fillColor: const Color(0xFF0D0D0D),
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide:
                              const BorderSide(color: Color(0xFF222222))),
                      enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide:
                              const BorderSide(color: Color(0xFF222222))),
                      focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide:
                              const BorderSide(color: Color(0xFFE03030))),
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 14),
                    ),
                    onChanged: (_) => setState(() => _error = null),
                  ),
                ),
                const SizedBox(width: 10),
                GestureDetector(
                  onTap: _isLinking ? null : _pair,
                  child: Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xFF2A0A0A),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFE03030)),
                    ),
                    child: _isLinking
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Color(0xFFE03030)))
                        : const Icon(Icons.link_rounded,
                            color: Color(0xFFE03030), size: 20),
                  ),
                ),
              ],
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: const TextStyle(
                      color: Color(0xFFE03030),
                      fontSize: 11,
                      fontFamily: 'monospace')),
            ],
          ],
        ),
      );

  // ── Paired card ─────────────────────────────────────────────────────────────
  Widget _pairedCard() => Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: const Color(0xFF110A10),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFF2A1020), width: 1.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _sectionLabel('PAIRED WITH'),
                GestureDetector(
                  onTap: _unpair,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1A0A0A),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: const Color(0xFF3A1515)),
                    ),
                    child: const Text('Unpair',
                        style: TextStyle(
                            color: Color(0xFF884444),
                            fontSize: 11,
                            fontFamily: 'monospace')),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                      vertical: 12, horizontal: 16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF0D0D0D),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: const Color(0xFF2A1020)),
                  ),
                  child: Text(_partnerCode,
                      style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFFD44080),
                          fontFamily: 'monospace',
                          letterSpacing: 5)),
                ),
                const SizedBox(width: 16),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Partner battery',
                        style: TextStyle(
                            color: Color(0xFF555555),
                            fontSize: 11,
                            fontFamily: 'monospace')),
                    const SizedBox(height: 4),
                    Text(
                      _partnerBattery != null ? '${_partnerBattery}%' : '—',
                      style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: _partnerBattery != null
                              ? const Color(0xFFD44080)
                              : const Color(0xFF333333),
                          fontFamily: 'monospace'),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 14),
            GestureDetector(
              onTap: _fetchPartner,
              child: Row(
                children: const [
                  Icon(Icons.refresh_rounded,
                      color: Color(0xFF3A3A3A), size: 13),
                  SizedBox(width: 6),
                  Text('Tap to refresh',
                      style: TextStyle(
                          color: Color(0xFF3A3A3A),
                          fontSize: 11,
                          fontFamily: 'monospace')),
                ],
              ),
            ),
          ],
        ),
      );

  // ── Info box ─────────────────────────────────────────────────────────────────
  Widget _infoBox() => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: const Color(0xFF0D0D0D),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFF1A1A1A)),
        ),
        child: const Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('💡 ', style: TextStyle(fontSize: 13)),
            Expanded(
              child: Text(
                'Battery syncs every 60s automatically.\nPartner hearts appear in pink on the widget.\nLabels update on the widget after the next sync.',
                style: TextStyle(
                    color: Color(0xFF444444),
                    fontSize: 11,
                    fontFamily: 'monospace',
                    height: 1.6),
              ),
            ),
          ],
        ),
      );

  // ── Helpers ──────────────────────────────────────────────────────────────────
  Widget _card({required Widget child}) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: const Color(0xFF111111),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFF1E1E1E), width: 1.5),
        ),
        child: child,
      );

  Widget _sectionLabel(String text) => Text(
        text,
        style: const TextStyle(
            color: Color(0xFF484848),
            fontSize: 10,
            fontFamily: 'monospace',
            letterSpacing: 2),
      );
}