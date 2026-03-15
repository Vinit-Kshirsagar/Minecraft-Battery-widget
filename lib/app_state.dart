import 'dart:math';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'supabase_service.dart';

String genCode() {
  const c = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return List.generate(6, (_) => c[Random.secure().nextInt(c.length)]).join();
}

class S extends ChangeNotifier {
  String myCode = '', partnerCode = '';
  int? partnerBattery;
  bool isPaired = false;
  String myLabel = 'Y', partnerLabel = 'P';
  Color myColor = const Color(0xFFE03030),
      partnerColor = const Color(0xFFD44080);
  double heartSize = 30;

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    myCode = p.getString('my_code') ?? '';
    if (myCode.isEmpty) {
      myCode = genCode();
      await p.setString('my_code', myCode);
    }
    pushBattery(myCode, 0);
    partnerCode = p.getString('partner_code') ?? '';
    isPaired = partnerCode.isNotEmpty;
    myLabel = p.getString('my_label') ?? 'Y';
    partnerLabel = p.getString('partner_label') ?? 'P';
    myColor = Color(p.getInt('my_color') ?? 0xFFE03030);
    partnerColor = Color(p.getInt('partner_color') ?? 0xFFD44080);
    heartSize = p.getDouble('heart_size') ?? 30;
    notifyListeners();
    if (isPaired) _fetchPartner();
  }

  Future<void> _fetchPartner() async {
    final b = await fetchBattery(partnerCode);
    partnerBattery = b;
    notifyListeners();
  }

  Future<void> refresh() => _fetchPartner();

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString('my_label', myLabel);
    await p.setString('partner_label', partnerLabel);
    await p.setInt('my_color', myColor.value);
    await p.setInt('partner_color', partnerColor.value);
    await p.setDouble('heart_size', heartSize);
  }

  Future<void> pair(String code) async {
    final p = await SharedPreferences.getInstance();
    await p.setString('partner_code', code);
    partnerCode = code;
    isPaired = true;
    notifyListeners();
    _fetchPartner();
  }

  Future<void> unpair() async {
    final p = await SharedPreferences.getInstance();
    await p.remove('partner_code');
    partnerCode = '';
    isPaired = false;
    partnerBattery = null;
    notifyListeners();
  }
}