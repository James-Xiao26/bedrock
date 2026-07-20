import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

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
          const SectionLabel('Passcode'),
          _PasscodeSection(
            cutoffMinutes: config.active.passwordViewCutoffMinutes,
          ),
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

/// Shows the current passcode while it is allowed to be seen, lets the user
/// roll a new one, and edits the daily visibility cutoff. The passcode gates
/// per-app time grants during a window.
class _PasscodeSection extends ConsumerWidget {
  const _PasscodeSection({required this.cutoffMinutes});

  final int cutoffMinutes;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final view = ref.watch(hardcorePasswordProvider);
    final cutoff = TimeOfDay(
      hour: cutoffMinutes ~/ 60,
      minute: cutoffMinutes % 60,
    );

    return Column(
      children: [
        SectionCard(
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
                : _CodeHidden(
                    message: 'Hidden for now. Your code is only visible before '
                        '${cutoff.format(context)} each day, so you can\'t look '
                        'it up once a window has started.',
                  ),
          ),
        ),
        const SizedBox(height: 12),
        SettingGroup(
          rows: [
            SettingRow(
              title: 'Visible until',
              subtitle: 'After this time each day the code is hidden. Moving '
                  'it later takes effect at your next window.',
              trailing: _ValuePill(cutoff.format(context)),
              onTap: () => _pickCutoff(context, ref, cutoff),
            ),
          ],
        ),
      ],
    );
  }

  Future<void> _pickCutoff(
    BuildContext context,
    WidgetRef ref,
    TimeOfDay initial,
  ) async {
    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked == null) return;
    await ref.read(engineChannelProvider).updateConfig(
          ConfigPatch(
            passwordViewCutoffMinutes: picked.hour * 60 + picked.minute,
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

class _ValuePill extends StatelessWidget {
  const _ValuePill(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: BedrockColors.surfaceHigh,
        borderRadius: BorderRadius.circular(BedrockRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            text,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: BedrockColors.onSurface,
            ),
          ),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right,
              size: 18, color: BedrockColors.onSurfaceMuted),
        ],
      ),
    );
  }
}
