import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';
import 'allowed_apps_screen.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;
    final engine = ref.read(engineChannelProvider);

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const Text(
          'Settings',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 20),
        if (config == null)
          const Center(child: CircularProgressIndicator())
        else ...[
          const SectionLabel('Downtime'),
          SettingGroup(
            rows: [
              SettingRow(
                title: 'Always Allowed',
                subtitle: 'Apps that stay available during downtime.',
                trailing: const Icon(Icons.chevron_right,
                    color: BedrockColors.onSurfaceMuted, size: 20),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => const AllowedAppsScreen(),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          const SectionLabel('Passcode'),
          const _PasscodeSection(),
          if (kDebugMode) ...[
            const SizedBox(height: 24),
            const SectionLabel('Debug'),
            SectionCard(
              child: OutlinedButton(
                onPressed: () => engine.startDemoSession(
                    windDownSeconds: 5, sleepSeconds: 30),
                child: const Text('Run demo window (35s)'),
              ),
            ),
          ],
        ],
      ],
    );
  }
}

/// Shows the current passcode and lets the user roll a new one. The passcode
/// gates per-app time grants during a window; it is hidden only while a window
/// is actively blocking (revealing it then would defeat the blocker).
class _PasscodeSection extends ConsumerWidget {
  const _PasscodeSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final view = ref.watch(hardcorePasswordProvider);

    return SectionCard(
      child: view.when(
        loading: () => const SizedBox(
          height: 96,
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (_, _) => const _CodeHidden(
          message: 'Your passcode is unavailable right now.',
        ),
        data: (v) => (v.viewable && v.password != null)
            ? _CodeVisible(
                code: v.password!,
                onRegenerate: () async {
                  await ref
                      .read(engineChannelProvider)
                      .regenerateHardcorePassword();
                  ref.invalidate(hardcorePasswordProvider);
                },
              )
            : const _CodeHidden(
                message: 'Hidden while a window is active, so you can\'t look '
                    'it up once blocking has started.',
              ),
      ),
    );
  }
}

class _CodeVisible extends StatelessWidget {
  const _CodeVisible({required this.code, required this.onRegenerate});

  final String code;
  final Future<void> Function() onRegenerate;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: Text(
            code,
            style: const TextStyle(
              fontSize: 44,
              fontWeight: FontWeight.w700,
              letterSpacing: 14,
              color: BedrockColors.onSurface,
            ),
          ),
        ),
        const SizedBox(height: 10),
        const Text(
          'Save this where you can reach it - write it down or hand it to '
          'someone. It stays the same until you reset it.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 13,
            height: 1.35,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
        const SizedBox(height: 16),
        OutlinedButton(
          onPressed: onRegenerate,
          child: const Text('Generate a new code'),
        ),
      ],
    );
  }
}

class _CodeHidden extends StatelessWidget {
  const _CodeHidden({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Center(
          child: Icon(
            Icons.lock_outline,
            size: 40,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          message,
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 13,
            height: 1.35,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
      ],
    );
  }
}

