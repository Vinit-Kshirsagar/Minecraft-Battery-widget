import 'package:flutter/material.dart';
import 'app_state.dart';
import 'heart_widgets.dart';
import 'shared_widgets.dart';
import 'supabase_service.dart';

class PartnerPage extends StatefulWidget {
  final S s;
  const PartnerPage(this.s, {super.key});
  @override
  State<PartnerPage> createState() => _PartnerState();
}

class _PartnerState extends State<PartnerPage> {
  final _ctrl = TextEditingController();
  bool _loading = false;
  String? _error;
  S get s => widget.s;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _pair() async {
    final code = _ctrl.text.trim().toUpperCase();
    if (code.length != 6) {
      setState(() => _error = 'Must be 6 characters');
      return;
    }
    if (code == s.myCode) {
      setState(() => _error = "That's your own code!");
      return;
    }
    setState(() { _loading = true; _error = null; });

    if (!await codeExists(code)) {
      setState(() {
        _loading = false;
        _error = 'Not found — ask partner to open app first.';
      });
      return;
    }
    await s.pair(code);
    setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) => SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            // ── Header ──────────────────────────────────────────────────────
            Row(children: [
              Heart(HT.full, size: 28, color: s.partnerColor),
              const SizedBox(width: 12),
              const Text('Partner',
                  style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      fontFamily: 'monospace')),
            ]),
            const SizedBox(height: 24),

            if (s.isPaired) ...[
              // ── Paired card ────────────────────────────────────────────────
              Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: const Color(0xFF110A10),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                      color: const Color(0xFF2A1020), width: 1.5),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        sectionLabel('PAIRED WITH'),
                        GestureDetector(
                          onTap: () async {
                            await s.unpair();
                            setState(() {});
                          },
                          child: const Text('Unpair',
                              style: TextStyle(
                                  color: Color(0xFF884444),
                                  fontSize: 12,
                                  fontFamily: 'monospace')),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(s.partnerCode,
                        style: TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            color: s.partnerColor,
                            fontFamily: 'monospace',
                            letterSpacing: 5)),
                    const SizedBox(height: 20),
                    Center(
                      child: HeartRow(s.partnerBattery ?? 0,
                          size: s.heartSize, color: s.partnerColor),
                    ),
                    const SizedBox(height: 10),
                    Center(
                      child: Text(
                        s.partnerBattery != null
                            ? '${s.partnerBattery}%'
                            : 'Waiting...',
                        style: TextStyle(
                            color: s.partnerBattery != null
                                ? s.partnerColor
                                : const Color(0xFF444444),
                            fontSize: 14,
                            fontFamily: 'monospace',
                            fontWeight: FontWeight.bold),
                      ),
                    ),
                    const SizedBox(height: 16),
                    GestureDetector(
                      onTap: () async {
                        await s.refresh();
                        setState(() {});
                      },
                      child: const Row(children: [
                        Icon(Icons.refresh_rounded,
                            color: Color(0xFF444444), size: 14),
                        SizedBox(width: 6),
                        Text('Refresh',
                            style: TextStyle(
                                color: Color(0xFF444444),
                                fontSize: 12,
                                fontFamily: 'monospace')),
                      ]),
                    ),
                  ],
                ),
              ),
            ] else ...[
              // ── Pair card ──────────────────────────────────────────────────
              appCard(Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  sectionLabel("ENTER PARTNER'S CODE"),
                  const SizedBox(height: 12),
                  Row(children: [
                    Expanded(
                      child: TextField(
                        controller: _ctrl,
                        textCapitalization: TextCapitalization.characters,
                        maxLength: 6,
                        style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            fontFamily: 'monospace',
                            letterSpacing: 6),
                        decoration: InputDecoration(
                          counterText: '',
                          hintText: '······',
                          hintStyle: const TextStyle(
                              color: Color(0xFF2A2A2A),
                              fontFamily: 'monospace',
                              letterSpacing: 6,
                              fontSize: 22),
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
                              borderSide: const BorderSide(
                                  color: Color(0xFFE03030))),
                          contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16, vertical: 14),
                        ),
                        onChanged: (_) => setState(() => _error = null),
                      ),
                    ),
                    const SizedBox(width: 10),
                    GestureDetector(
                      onTap: _loading ? null : _pair,
                      child: Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                            color: const Color(0xFF2A0A0A),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                                color: const Color(0xFFE03030))),
                        child: _loading
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
                  ]),
                  if (_error != null) ...[
                    const SizedBox(height: 8),
                    Text(_error!,
                        style: const TextStyle(
                            color: Color(0xFFE03030),
                            fontSize: 11,
                            fontFamily: 'monospace')),
                  ],
                  const SizedBox(height: 16),
                  Row(children: [
                    sectionLabel('YOUR CODE  '),
                    const SizedBox(width: 8),
                    Text(s.myCode,
                        style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            fontFamily: 'monospace',
                            letterSpacing: 4)),
                    const Spacer(),
                    CopyBtn(s.myCode, small: true),
                  ]),
                ],
              )),
            ],
            const SizedBox(height: 20),

            // ── Info ─────────────────────────────────────────────────────────
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFF0D0D0D),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFF1A1A1A)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('💡 ', style: TextStyle(fontSize: 13)),
                  Expanded(
                    child: Text(
                      s.isPaired
                          ? 'Partner syncs every 60s.\nBoth devices need the app installed.'
                          : 'Ask your partner to open the app first,\nthen enter their 6-character code above.',
                      style: const TextStyle(
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
}