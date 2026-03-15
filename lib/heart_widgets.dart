import 'package:flutter/material.dart';

enum HT { full, half, cracked, empty }

class HeartPainter extends CustomPainter {
  final HT type;
  final Color color;
  HeartPainter(this.type, this.color);

  static const g = [
    '.XX.XX.',
    'XXXXXXX',
    'XXXXXXX',
    '.XXXXX.',
    '..XXX..',
    '...X...'
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final cw = size.width / 7, ch = size.height / 6;
    for (int r = 0; r < 6; r++) {
      for (int c = 0; c < 7; c++) {
        if (g[r][c] != 'X') continue;
        final col = switch (type) {
          HT.full => color,
          HT.empty => const Color(0xFF2A2A2A),
          HT.half => c < 3 ? color : const Color(0xFF2A2A2A),
          HT.cracked => c == 3 ? const Color(0xFF1A0000) : color,
        };
        canvas.drawRect(
            Rect.fromLTWH(c * cw, r * ch, cw, ch), Paint()..color = col);
      }
    }
  }

  @override
  bool shouldRepaint(HeartPainter o) => o.type != type || o.color != color;
}

class Heart extends StatelessWidget {
  final HT type;
  final double size;
  final Color color;
  const Heart(this.type,
      {super.key,
      this.size = 26,
      this.color = const Color(0xFFE03030)});

  @override
  Widget build(BuildContext context) => SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: HeartPainter(type, color)));
}

class HeartRow extends StatelessWidget {
  final int battery;
  final double size;
  final Color color;
  const HeartRow(this.battery,
      {super.key,
      this.size = 26,
      this.color = const Color(0xFFE03030)});

  List<HT> get states {
    final u = battery % 10;
    final h = u >= 2 ? (battery ~/ 10) * 2 + 1 : (battery ~/ 10) * 2;
    return List.generate(10, (i) {
      final r = h - i * 2;
      if (r >= 2) return HT.full;
      if (r == 1) return u >= 6 ? HT.cracked : HT.half;
      return HT.empty;
    });
  }

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: states
            .map((s) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 1),
                  child: Heart(s, size: size, color: color),
                ))
            .toList(),
      );
}