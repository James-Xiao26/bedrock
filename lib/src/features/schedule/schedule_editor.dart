import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';

const _dayNames = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

String _fmt(int minutes) {
  final h = (minutes ~/ 60).toString().padLeft(2, '0');
  final m = (minutes % 60).toString().padLeft(2, '0');
  return '$h:$m';
}

/// Per-weekday bed/wake editor. Every edit goes straight to the engine;
/// the native freeze rules decide whether it applies tonight or tomorrow.
class ScheduleEditor extends ConsumerWidget {
  const ScheduleEditor({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider);
    return switch (config) {
      AsyncData(:final value) => _ScheduleList(view: value),
      AsyncError(:final error) => Center(child: Text('Engine error: $error')),
      _ => const Center(child: CircularProgressIndicator()),
    };
  }
}

class _ScheduleList extends ConsumerWidget {
  const _ScheduleList({required this.view});

  final ConfigView view;

  Future<void> _edit(
    BuildContext context,
    WidgetRef ref,
    int day,
    NightPlan plan, {
    required bool editingBed,
  }) async {
    final initial = editingBed ? plan.bedMinutes : plan.wakeMinutes;
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: initial ~/ 60, minute: initial % 60),
      helpText: editingBed
          ? 'Bedtime for ${_dayNames[day]} night'
          : 'Wake time for ${_dayNames[day]} night',
    );
    if (picked == null) return;
    final minutes = picked.hour * 60 + picked.minute;
    final updated = editingBed
        ? plan.copyWith(bedMinutes: minutes)
        : plan.copyWith(wakeMinutes: minutes);
    await ref
        .read(engineChannelProvider)
        .updateConfig(ConfigPatch(schedule: {day: updated}));
  }

  Future<void> _toggle(WidgetRef ref, int day, NightPlan plan, bool enabled) =>
      ref.read(engineChannelProvider).updateConfig(
            ConfigPatch(schedule: {day: plan.copyWith(enabled: enabled)}),
          );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (view.hasPendingChanges)
          MaterialBanner(
            content: const Text(
              'Some changes loosen tonight\'s lockdown and will apply '
              'tomorrow morning.',
            ),
            leading: const Icon(Icons.schedule),
            actions: const [SizedBox.shrink()],
          ),
        for (final day in _dayNames.keys)
          if (view.active.schedule[day] case final plan?)
            ListTile(
              title: Text(_dayNames[day]!),
              subtitle: Row(
                children: [
                  TextButton(
                    onPressed: plan.enabled
                        ? () => _edit(context, ref, day, plan, editingBed: true)
                        : null,
                    child: Text('Bed ${_fmt(plan.bedMinutes)}'),
                  ),
                  TextButton(
                    onPressed: plan.enabled
                        ? () =>
                            _edit(context, ref, day, plan, editingBed: false)
                        : null,
                    child: Text('Wake ${_fmt(plan.wakeMinutes)}'),
                  ),
                ],
              ),
              trailing: Switch(
                value: plan.enabled,
                onChanged: (v) => _toggle(ref, day, plan, v),
              ),
            ),
      ],
    );
  }
}
