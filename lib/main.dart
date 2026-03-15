import 'package:flutter/material.dart';
import 'app_state.dart';
import 'root_page.dart';

void main() => runApp(MaterialApp(
      debugShowCheckedModeBanner: false,
      home: const RootPage(),
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0A0A0A),
      ),
    ));