import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_colorpicker/flutter_colorpicker.dart';

// ── Card container ────────────────────────────────────────────────────────────
Widget appCard(Widget child) => Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFF111111),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1E1E1E), width: 1.5),
      ),
      child: child,
    );

// ── Section label ─────────────────────────────────────────────────────────────
Widget sectionLabel(String t) => Text(t,
    style: const TextStyle(
        color: Color(0xFF484848),
        fontSize: 10,
        fontFamily: 'monospace',
        letterSpacing: 2));

// ── Copy button ───────────────────────────────────────────────────────────────
class CopyBtn extends StatefulWidget {
  final String text;
  final bool small;
  const CopyBtn(this.text, {super.key, this.small = false});
  @override
  State<CopyBtn> createState() => _CopyBtnState();
}

class _CopyBtnState extends State<CopyBtn> {
  bool _copied = false;

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: widget.text));
    setState(() => _copied = true);
    await Future.delayed(const Duration(seconds: 2));
    if (mounted) setState(() => _copied = false);
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: _copy,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: EdgeInsets.all(widget.small ? 8 : 12),
          decoration: BoxDecoration(
            color: _copied ? const Color(0xFF0A2A0A) : const Color(0xFF1A1A1A),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
                color: _copied
                    ? const Color(0xFF2A6A2A)
                    : const Color(0xFF2A2A2A)),
          ),
          child: Icon(
            _copied ? Icons.check_rounded : Icons.copy_rounded,
            color:
                _copied ? const Color(0xFF4CAF50) : const Color(0xFF555555),
            size: widget.small ? 16 : 20,
          ),
        ),
      );
}

// ── Initial + color picker field ──────────────────────────────────────────────
class InitialField extends StatefulWidget {
  final String label, value;
  final Color color;
  final ValueChanged<String> onChanged;
  final ValueChanged<Color> onColor;
  const InitialField(
      this.label, this.value, this.color, this.onChanged, this.onColor,
      {super.key});
  @override
  State<InitialField> createState() => _InitialFieldState();
}

class _InitialFieldState extends State<InitialField> {
  late final _ctrl = TextEditingController(text: widget.value);

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _pickColor() {
    Color tmp = widget.color;
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        title: Text('${widget.label} color',
            style: const TextStyle(fontSize: 14, fontFamily: 'monospace')),
        content: SingleChildScrollView(
          child: ColorPicker(
            pickerColor: widget.color,
            onColorChanged: (c) => tmp = c,
            enableAlpha: false,
            labelTypes: const [],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel',
                  style: TextStyle(color: Color(0xFF666666)))),
          TextButton(
              onPressed: () {
                widget.onColor(tmp);
                Navigator.pop(context);
              },
              child: const Text('Apply',
                  style: TextStyle(color: Color(0xFFE03030)))),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(widget.label,
              style: const TextStyle(
                  color: Color(0xFF555555),
                  fontSize: 11,
                  fontFamily: 'monospace')),
          const SizedBox(height: 8),
          Row(children: [
            GestureDetector(
              onTap: _pickColor,
              child: Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                    color: widget.color,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFF333333))),
                child: const Icon(Icons.colorize, color: Colors.white, size: 16),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _ctrl,
                maxLength: 1,
                textCapitalization: TextCapitalization.characters,
                textAlign: TextAlign.center,
                style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: widget.color,
                    fontFamily: 'monospace'),
                decoration: InputDecoration(
                  counterText: '',
                  hintText: widget.label[0],
                  hintStyle: const TextStyle(
                      color: Color(0xFF2A2A2A), fontSize: 24),
                  filled: true,
                  fillColor: const Color(0xFF0D0D0D),
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: Color(0xFF222222))),
                  enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: Color(0xFF222222))),
                  focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide(color: widget.color)),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
                ),
                onChanged: (v) {
                  if (v.isNotEmpty) widget.onChanged(v.toUpperCase());
                },
              ),
            ),
          ]),
        ],
      );
}