import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_channel.dart';
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
          const SectionLabel('Lockdown'),
          SettingGroup(
            rows: [
              SettingRow(
                title: 'Hardcore mode',
                subtitle: 'The only way out of a locked night is the \$1 '
                    'emergency bypass. Turning this off takes effect '
                    'tomorrow morning.',
                trailing: Switch(
                  value: config.active.mode == Mode.hardcore,
                  onChanged: (v) => engine.updateConfig(
                    ConfigPatch(mode: v ? Mode.hardcore : Mode.normal),
                  ),
                ),
              ),
              SettingRow(
                title: 'Wind-down',
                subtitle: 'A gentle warning before lockdown begins.',
                trailing: _ValuePill('${config.active.windDownMinutes} min'),
                onTap: () => _pickWindDown(context, engine, config.active),
              ),
            ],
          ),
          const SizedBox(height: 24),
          const SectionLabel('During the night'),
          SettingGroup(
            rows: [
              SettingRow(
                title: 'Do Not Disturb',
                subtitle: 'Silence notifications while you sleep.',
                trailing: Switch(
                  value: config.active.dndEnabled,
                  onChanged: (v) =>
                      engine.updateConfig(ConfigPatch(dndEnabled: v)),
                ),
              ),
              SettingRow(
                title: 'Grayscale',
                subtitle: 'Drain the colour from the screen at bedtime.',
                trailing: Switch(
                  value: config.active.grayscaleEnabled,
                  onChanged: (v) =>
                      engine.updateConfig(ConfigPatch(grayscaleEnabled: v)),
                ),
              ),
              SettingRow(
                title: 'Wake-up alarm',
                subtitle: 'Ring at wake time; snoozing keeps the lock on.',
                trailing: Switch(
                  value: config.active.alarmEnabled,
                  onChanged: (v) =>
                      engine.updateConfig(ConfigPatch(alarmEnabled: v)),
                ),
              ),
            ],
          ),
          if (kDebugMode) ...[
            const SizedBox(height: 24),
            const SectionLabel('Debug'),
            SectionCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  OutlinedButton(
                    onPressed: () => engine.startDemoSession(
                        windDownSeconds: 10, sleepSeconds: 30),
                    child: const Text('Run demo night (10s + 30s)'),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: () => engine.showNightClock(),
                    child: const Text('Show night clock'),
                  ),
                ],
              ),
            ),
          ],
        ],
      ],
    );
  }

  Future<void> _pickWindDown(
    BuildContext context,
    EngineChannel engine,
    EngineConfig config,
  ) async {
    const options = [0, 5, 15, 30, 45, 60];
    final picked = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: BedrockColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.vertical(top: Radius.circular(BedrockRadii.hero)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 20, 24, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  'Wind-down length',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: BedrockColors.onSurface,
                  ),
                ),
              ),
            ),
            RadioGroup<int>(
              groupValue: config.windDownMinutes,
              onChanged: (v) => Navigator.pop(context, v),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final minutes in options)
                    RadioListTile<int>(
                      value: minutes,
                      activeColor: BedrockColors.accent,
                      title: Text(minutes == 0 ? 'Off' : '$minutes minutes'),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (picked != null && picked != config.windDownMinutes) {
      await engine.updateConfig(ConfigPatch(windDownMinutes: picked));
    }
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
