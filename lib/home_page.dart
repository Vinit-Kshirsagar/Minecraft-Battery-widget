import 'package:flutter/material.dart';
import 'app_state.dart';
import 'heart_widgets.dart';
import 'shared_widgets.dart';

class HomePage extends StatefulWidget {
  final S s;
  const HomePage(this.s, {super.key});
  @override
  State<HomePage> createState() => _HomeState();
}

class _HomeState extends State<HomePage> {
  int _prev = 75;
  S get s => widget.s;

  @override
  Widget build(BuildContext context) => SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            // ── Header ──────────────────────────────────────────────────────
            Row(children: [
              Heart(HT.full, size: 28, color: s.myColor),
              const SizedBox(width: 12),
              const Text('Minecraft Battery',
                  style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      fontFamily: 'monospace')),
            ]),
            const SizedBox(height: 24),

            // ── Preview ──────────────────────────────────────────────────────
            appCard(Column(children: [
              sectionLabel('WIDGET PREVIEW'),
              const SizedBox(height: 16),
              _row(s.myLabel, _prev, s.myColor, s.heartSize),
              if (s.isPaired) ...[
                const SizedBox(height: 8),
                _row(s.partnerLabel, s.partnerBattery ?? 0, s.partnerColor,
                    s.heartSize),
              ],
              const SizedBox(height: 8),
              Slider(
                value: _prev.toDouble(),
                min: 0,
                max: 100,
                activeColor: const Color(0xFFE03030),
                inactiveColor: const Color(0xFF222222),
                onChanged: (v) => setState(() => _prev = v.round()),
              ),
              Text('$_prev%',
                  style: const TextStyle(
                      color: Color(0xFF444444),
                      fontSize: 11,
                      fontFamily: 'monospace')),
            ])),
            const SizedBox(height: 14),

            // ── My code ──────────────────────────────────────────────────────
            appCard(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              sectionLabel('YOUR CODE'),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(
                    child: Text(s.myCode,
                        style: const TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 8,
                            fontFamily: 'monospace'))),
                CopyBtn(s.myCode),
              ]),
              const SizedBox(height: 8),
              const Text('Share with your partner to pair.',
                  style: TextStyle(
                      color: Color(0xFF444444),
                      fontSize: 11,
                      fontFamily: 'monospace')),
            ])),
            const SizedBox(height: 14),

            // ── Appearance ───────────────────────────────────────────────────
            appCard(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              sectionLabel('APPEARANCE'),
              const SizedBox(height: 14),
              Row(children: [
                Expanded(
                  child: InitialField(
                    'You', s.myLabel, s.myColor,
                    (v) { s.myLabel = v; s.save(); setState(() {}); },
                    (c) { s.myColor = c; s.save(); setState(() {}); },
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: InitialField(
                    'Partner', s.partnerLabel, s.partnerColor,
                    (v) { s.partnerLabel = v; s.save(); setState(() {}); },
                    (c) { s.partnerColor = c; s.save(); setState(() {}); },
                  ),
                ),
              ]),
              const SizedBox(height: 10),
              const Text('Tap the color dot to change color.',
                  style: TextStyle(
                      color: Color(0xFF3A3A3A),
                      fontSize: 11,
                      fontFamily: 'monospace')),
            ])),
            const SizedBox(height: 14),

            // ── Heart size ───────────────────────────────────────────────────
            appCard(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                sectionLabel('HEART SIZE'),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF2A0A0A),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: const Color(0xFFE03030)),
                  ),
                  child: Text('${s.heartSize.round()} dp',
                      style: const TextStyle(
                          color: Color(0xFFE03030),
                          fontFamily: 'monospace',
                          fontWeight: FontWeight.bold,
                          fontSize: 12)),
                ),
              ]),
              const SizedBox(height: 4),
              Slider(
                value: s.heartSize,
                min: 20,
                max: 60,
                divisions: 20,
                activeColor: const Color(0xFFE03030),
                inactiveColor: const Color(0xFF222222),
                onChanged: (v) => setState(() => s.heartSize = v),
                onChangeEnd: (v) { s.heartSize = v; s.save(); },
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: const [
                    Text('Small', style: TextStyle(color: Color(0xFF444444),
                        fontSize: 10, fontFamily: 'monospace')),
                    Text('Medium', style: TextStyle(color: Color(0xFF444444),
                        fontSize: 10, fontFamily: 'monospace')),
                    Text('Large', style: TextStyle(color: Color(0xFF444444),
                        fontSize: 10, fontFamily: 'monospace')),
                  ],
                ),
              ),
            ])),
            const SizedBox(height: 14),

            // ── Info ─────────────────────────────────────────────────────────
            Container(
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
                      'Battery syncs every 60s.\nRe-add widget to apply size changes.',
                      style: TextStyle(
                          color: Color(0xFF444444),
                          fontSize: 11,
                          fontFamily: 'monospace',
                          height: 1.6),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );

  Widget _row(String lbl, int bat, Color col, double sz) => Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 20,
            child: Text(
              lbl.isEmpty ? ' ' : lbl[0].toUpperCase(),
              textAlign: TextAlign.center,
              style: TextStyle(
                  color: col,
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  fontFamily: 'monospace',
                  shadows: [
                    Shadow(
                        color: Colors.black.withOpacity(0.8),
                        offset: const Offset(1, 1),
                        blurRadius: 2)
                  ]),
            ),
          ),
          const SizedBox(width: 4),
          HeartRow(bat, size: sz, color: col),
        ],
      );
}