import 'dart:async';
import 'package:flutter/material.dart';
import 'package:battery_plus/battery_plus.dart';

class MinecraftBatteryWidget extends StatefulWidget {
  const MinecraftBatteryWidget({super.key});

  @override
  State<MinecraftBatteryWidget> createState() =>
      _MinecraftBatteryWidgetState();
}

class _MinecraftBatteryWidgetState extends State<MinecraftBatteryWidget> {
  final Battery _battery = Battery();

  int _batteryLevel = 0;
  StreamSubscription<BatteryState>? _batterySubscription;

  @override
  void initState() {
    super.initState();
    _initializeBattery();
  }

  Future<void> _initializeBattery() async {
    // Get initial battery level
    final level = await _battery.batteryLevel;
    setState(() => _batteryLevel = level);

    // Listen for charging state changes
    _batterySubscription =
        _battery.onBatteryStateChanged.listen((BatteryState state) async {
      final newLevel = await _battery.batteryLevel;
      setState(() => _batteryLevel = newLevel);
    });
  }

  @override
  void dispose() {
    _batterySubscription?.cancel();
    super.dispose();
  }

  int get fullHearts => _batteryLevel ~/ 10;
  bool get hasHalfHeart => _batteryLevel % 10 >= 5;
  int get emptyHearts => 10 - fullHearts - (hasHalfHeart ? 1 : 0);

  @override
  Widget build(BuildContext context) {
    List<Widget> hearts = [];

    for (int i = 0; i < fullHearts; i++) {
      hearts.add(const Text("❤️", style: TextStyle(fontSize: 28)));
    }

    if (hasHalfHeart) {
      hearts.add(const Text("💔", style: TextStyle(fontSize: 28)));
    }

    for (int i = 0; i < emptyHearts; i++) {
      hearts.add(const Text("🖤", style: TextStyle(fontSize: 28)));
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 2,
          children: hearts,
        ),
        const SizedBox(height: 12),
        Text(
          "$_batteryLevel%",
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}