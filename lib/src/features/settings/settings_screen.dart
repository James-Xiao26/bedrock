import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';
import '../onboarding/onboarding_flow.dart';
import 'allowed_apps_screen.dart';

String _formatLead(int minutes) {
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return '$m min';
  if (m == 0) return h == 1 ? '1 hour' : '$h hours';
  return '${h}h ${m}m';
}

/// Bottom-sheet picker for the wind-down lead. Returns the chosen minutes, or
/// null if dismissed.
Future<int?> _pickLead(BuildContext context, int current) {
  const options = [15, 30, 45, 60, 90];
  return showModalBottomSheet<int>(
    context: context,
    backgroundColor: BedrockColors.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(BedrockRadii.hero)),
    ),
    builder: (ctx) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.only(bottom: 12, left: 4),
              child: Text(
                'Start downtime before bedtime',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  color: BedrockColors.onSurface,
                ),
              ),
            ),
            SettingGroup(
              rows: [
                for (final o in options)
                  SettingRow(
                    title: _formatLead(o),
                    onTap: () => Navigator.pop(ctx, o),
                    trailing: o == current
                        ? const Icon(Icons.check,
                            color: BedrockColors.accent, size: 22)
                        : const SizedBox(width: 22),
                  ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

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
              SettingRow(
                title: 'Wind-down',
                subtitle: 'How long before bedtime downtime begins.',
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      _formatLead(config.active.windDownMinutes),
                      style: const TextStyle(
                          fontSize: 16, color: BedrockColors.onSurfaceMuted),
                    ),
                    const SizedBox(width: 6),
                    const Icon(Icons.chevron_right,
                        color: BedrockColors.onSurfaceMuted, size: 20),
                  ],
                ),
                onTap: () async {
                  final picked = await _pickLead(
                      context, config.active.windDownMinutes);
                  if (picked != null) {
                    await engine
                        .updateConfig(ConfigPatch(windDownMinutes: picked));
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 36),
          const SectionLabel('Passcode'),
          const _PasscodeSection(),
          const SizedBox(height: 36),
          const SectionLabel('Setup'),
          SettingGroup(
            rows: [
              SettingRow(
                title: 'Rerun setup',
                subtitle: 'Walk through onboarding again.',
                trailing: const Icon(Icons.chevron_right,
                    color: BedrockColors.onSurfaceMuted, size: 20),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (ctx) =>
                        OnboardingFlow(onDone: () => Navigator.of(ctx).pop()),
                  ),
                ),
              ),
            ],
          ),
          if (kDebugMode) ...[
            const SizedBox(height: 36),
            const SectionLabel('Debug'),
            SizedBox(
              width: double.infinity,
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

    return view.when(
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

