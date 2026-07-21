import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/engine/engine_providers.dart';
import 'src/features/onboarding/onboarding_flow.dart';
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
      home: const _RootGate(),
    );
  }
}

/// Shows onboarding on first launch, the tab shell thereafter. The flag is read
/// once from the engine; completing onboarding flips to the shell in place.
class _RootGate extends ConsumerStatefulWidget {
  const _RootGate();

  @override
  ConsumerState<_RootGate> createState() => _RootGateState();
}

class _RootGateState extends ConsumerState<_RootGate> {
  bool? _onboarded;

  @override
  void initState() {
    super.initState();
    ref.read(engineChannelProvider).isOnboarded().then((v) {
      if (mounted) setState(() => _onboarded = v);
    });
  }

  @override
  Widget build(BuildContext context) {
    return switch (_onboarded) {
      null => const Scaffold(body: SizedBox.shrink()),
      true => const AppShell(),
      false => OnboardingFlow(
          onDone: () => setState(() => _onboarded = true),
        ),
    };
  }
}
