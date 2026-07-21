import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/allowed_apps_picker.dart';

/// iOS Downtime "Always Allowed" screen: apps that stay reachable during a
/// window. Wraps the shared [AllowedAppsPicker], writing every change straight
/// to the engine.
class AllowedAppsScreen extends ConsumerWidget {
  const AllowedAppsScreen({super.key});

  Future<void> _setAllowlist(WidgetRef ref, Set<String> next) =>
      ref.read(engineChannelProvider).updateConfig(ConfigPatch(allowlist: next));

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;
    final apps = ref.watch(installedAppsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Always Allowed')),
      body: switch ((config, apps)) {
        (final c?, AsyncData(:final value)) => ListView(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 4),
                child: Text(
                  'Always allowed apps stay available during downtime.',
                  style: TextStyle(
                    fontSize: 13,
                    height: 1.4,
                    color: BedrockColors.onSurfaceMuted,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              AllowedAppsPicker(
                allowed: c.active.allowlist,
                apps: value,
                onChanged: (next) => _setAllowlist(ref, next),
              ),
            ],
          ),
        (_, AsyncError(:final error)) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text('Could not load apps: $error'),
            ),
          ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}
