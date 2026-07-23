import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/bedtime_pairing.dart';
import '../../widgets/section_card.dart';
import '../onboarding/onboarding_flow.dart';
import 'allowed_apps_screen.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        if (config == null)
          const Center(child: CircularProgressIndicator())
        else ...[
          const SectionLabel('You'),
          const _NameSection(),
          const SizedBox(height: 36),
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
          const SizedBox(height: 12),
          const BedtimePairingRow(),
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
        ],
      ],
    );
  }
}

/// The display name, editable anytime. A plain on-device string used only to
/// personalize greetings - stored via the engine's [setDisplayName], never
/// uploaded (release builds ship without the INTERNET permission).
class _NameSection extends ConsumerWidget {
  const _NameSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final name = ref.watch(displayNameProvider).valueOrNull?.trim();
    final hasName = name != null && name.isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SettingGroup(
          rows: [
            SettingRow(
              title: 'Your name',
              subtitle: hasName ? name : 'Add a name',
              trailing: const Icon(Icons.chevron_right,
                  color: BedrockColors.onSurfaceMuted, size: 20),
              onTap: () => _edit(context, ref, name ?? ''),
            ),
          ],
        ),
        const Padding(
          padding: EdgeInsets.only(top: 10),
          child: Text(
            'Saved only on this phone. There\'s no account and nothing is '
            'uploaded.',
            style: TextStyle(
              fontSize: 13,
              height: 1.35,
              color: BedrockColors.onSurfaceMuted,
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _edit(BuildContext context, WidgetRef ref, String current) async {
    final next = await showDialog<String>(
      context: context,
      builder: (_) => _NameDialog(initial: current),
    );
    if (next == null) return; // cancelled
    await ref.read(engineChannelProvider).setDisplayName(next.trim());
    ref.invalidate(displayNameProvider);
  }
}

/// The name-edit dialog. Stateful so it owns its controller and disposes it in
/// [State.dispose] - disposing it inline right after `showDialog` returns races
/// the dialog's exit animation and throws "used after disposed".
class _NameDialog extends StatefulWidget {
  const _NameDialog({required this.initial});

  final String initial;

  @override
  State<_NameDialog> createState() => _NameDialogState();
}

class _NameDialogState extends State<_NameDialog> {
  late final _controller = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Your name'),
      content: TextField(
        controller: _controller,
        autofocus: true,
        maxLength: 30,
        textCapitalization: TextCapitalization.words,
        textInputAction: TextInputAction.done,
        onSubmitted: (v) => Navigator.of(context).pop(v),
        cursorColor: BedrockColors.accent,
        decoration: const InputDecoration(
          hintText: 'Your name',
          counterText: '',
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(_controller.text),
          child: const Text('Save'),
        ),
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

