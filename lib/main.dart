import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/features/home/home_screen.dart';

void main() {
  runApp(const ProviderScope(child: BedrockApp()));
}

class BedrockApp extends StatelessWidget {
  const BedrockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Bedrock',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF5C6BC0),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
