import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/features/shell/app_shell.dart';
import 'src/theme/app_theme.dart';

void main() {
  runApp(const ProviderScope(child: BedrockApp()));
}

class BedrockApp extends StatelessWidget {
  const BedrockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Bedrock',
      debugShowCheckedModeBanner: false,
      theme: buildBedrockTheme(),
      home: const AppShell(),
    );
  }
}
