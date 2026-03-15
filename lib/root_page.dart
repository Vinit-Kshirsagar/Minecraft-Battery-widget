import 'package:flutter/material.dart';
import 'app_state.dart';
import 'home_page.dart';
import 'partner_page.dart';

class RootPage extends StatefulWidget {
  const RootPage({super.key});
  @override
  State<RootPage> createState() => _RootState();
}

class _RootState extends State<RootPage> {
  int _tab = 0;
  final s = S();

  @override
  void initState() {
    super.initState();
    s.load();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: s,
        builder: (_, __) => Scaffold(
          backgroundColor: const Color(0xFF0A0A0A),
          body: _tab == 0 ? HomePage(s) : PartnerPage(s),
          bottomNavigationBar: BottomNavigationBar(
            currentIndex: _tab,
            onTap: (i) => setState(() => _tab = i),
            backgroundColor: const Color(0xFF0D0D0D),
            selectedItemColor: const Color(0xFFE03030),
            unselectedItemColor: const Color(0xFF444444),
            selectedLabelStyle: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 11,
                fontWeight: FontWeight.bold),
            unselectedLabelStyle:
                const TextStyle(fontFamily: 'monospace', fontSize: 11),
            items: const [
              BottomNavigationBarItem(
                  icon: Icon(Icons.favorite), label: 'Home'),
              BottomNavigationBarItem(
                  icon: Icon(Icons.people), label: 'Partner'),
            ],
          ),
        ),
      );
}