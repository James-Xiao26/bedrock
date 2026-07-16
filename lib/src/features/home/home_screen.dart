import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../schedule/schedule_editor.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionStateProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Bedrock')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _StatusCard(session: session),
          const SizedBox(height: 16),
          Text('Schedule', style: Theme.of(context).textTheme.titleLarge),
          const ScheduleEditor(),
          const Divider(height: 32),
          Text('Settings', style: Theme.of(context).textTheme.titleLarge),
          const _SettingsSection(),
          if (kDebugMode) ...[
            const Divider(height: 32),
            Text('Debug', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: () => ref
                  .read(engineChannelProvider)
                  .startDemoSession(windDownSeconds: 10, sleepSeconds: 30),
              child: const Text('Run demo night (10s + 30s)'),
            ),
            OutlinedButton(
              onPressed: () =>
                  ref.read(engineChannelProvider).showNightClock(),
              child: const Text('Show night clock'),
            ),
          ],
        ],
      ),
    );
  }
}

class _SettingsSection extends ConsumerWidget {
  const _SettingsSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;
    if (config == null) return const SizedBox.shrink();
    final engine = ref.read(engineChannelProvider);

    return Column(
      children: [
        SwitchListTile(
          title: const Text('Hardcore mode'),
          subtitle: const Text(
            'The only way out of a locked night is the \$1 emergency bypass. '
            'Turning this off takes effect tomorrow morning.',
          ),
          value: config.active.mode == Mode.hardcore,
          onChanged: (v) => engine.updateConfig(
            ConfigPatch(mode: v ? Mode.hardcore : Mode.normal),
          ),
        ),
        SwitchListTile(
          title: const Text('Wake-up alarm'),
          subtitle: const Text('Ring at wake time; snoozing keeps the lock on.'),
          value: config.active.alarmEnabled,
          onChanged: (v) => engine.updateConfig(ConfigPatch(alarmEnabled: v)),
        ),
      ],
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.session});

  final AsyncValue<SessionSnapshot> session;

  @override
  Widget build(BuildContext context) {
    final (title, detail) = switch (session) {
      AsyncData(:final value) => (
          switch (value.state) {
            SessionState.idle => 'Awake',
            SessionState.winddown => 'Winding down',
            SessionState.locked => 'Locked for the night',
            SessionState.escaped => 'Escaped tonight',
            SessionState.bypassed => 'Bypassed tonight',
            SessionState.wakeAlarm => 'Alarm ringing',
          },
          value.plannedWake == null
              ? 'No night in progress.'
              : 'Until ${TimeOfDay.fromDateTime(value.plannedWake!).format(context)}',
        ),
      AsyncError(:final error) => ('Engine unreachable', '$error'),
      _ => ('Contacting engine...', ''),
    };

    return Card(
      child: ListTile(
        leading: const Icon(Icons.nightlight_round),
        title: Text(title),
        subtitle: detail.isEmpty ? null : Text(detail),
      ),
    );
  }
}
