import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

const _dayNames = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

/// Per-weekday bed/wake editor. Every edit goes straight to the engine; the
/// native freeze rules decide whether it applies tonight or tomorrow.
class ScheduleScreen extends ConsumerWidget {
  const ScheduleScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider);
    return switch (config) {
      AsyncData(:final value) => _ScheduleList(view: value),
      AsyncError(:final error) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Engine error: $error'),
          ),
        ),
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
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const Text(
          'Schedule',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 20),
        if (view.hasPendingChanges) ...[
          const _PendingBanner(),
          const SizedBox(height: 16),
        ],
        for (final day in _dayNames.keys)
          if (view.active.schedule[day] case final plan?) ...[
            _DayCard(
              day: day,
              plan: plan,
              onEditBed: () => _edit(context, ref, day, plan, editingBed: true),
              onEditWake: () =>
                  _edit(context, ref, day, plan, editingBed: false),
              onToggle: (v) => _toggle(ref, day, plan, v),
            ),
            const SizedBox(height: 12),
          ],
      ],
    );
  }
}

class _PendingBanner extends StatelessWidget {
  const _PendingBanner();

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      color: BedrockColors.accent.withValues(alpha: 0.14),
      padding: const EdgeInsets.all(16),
      child: const Row(
        children: [
          Icon(Icons.schedule, color: BedrockColors.accent, size: 22),
          SizedBox(width: 14),
          Expanded(
            child: Text(
              'Some changes loosen tonight\'s lockdown and will apply '
              'tomorrow morning.',
              style: TextStyle(
                fontSize: 14,
                height: 1.35,
                color: BedrockColors.onSurface,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DayCard extends StatelessWidget {
  const _DayCard({
    required this.day,
    required this.plan,
    required this.onEditBed,
    required this.onEditWake,
    required this.onToggle,
  });

  final int day;
  final NightPlan plan;
  final VoidCallback onEditBed;
  final VoidCallback onEditWake;
  final ValueChanged<bool> onToggle;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      padding: const EdgeInsets.fromLTRB(20, 16, 16, 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  _dayNames[day]!,
                  style: TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: plan.enabled
                        ? BedrockColors.onSurface
                        : BedrockColors.onSurfaceMuted,
                  ),
                ),
              ),
              Switch(value: plan.enabled, onChanged: onToggle),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Expanded(
                child: _TimeChip(
                  icon: Icons.bedtime_outlined,
                  label: 'Bed',
                  value: _fmt(context, plan.bedMinutes),
                  onTap: plan.enabled ? onEditBed : null,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _TimeChip(
                  icon: Icons.wb_twilight_outlined,
                  label: 'Wake',
                  value: _fmt(context, plan.wakeMinutes),
                  onTap: plan.enabled ? onEditWake : null,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TimeChip extends StatelessWidget {
  const _TimeChip({
    required this.icon,
    required this.label,
    required this.value,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final String value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return Material(
      color: BedrockColors.surfaceHigh,
      borderRadius: BorderRadius.circular(BedrockRadii.chip),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Icon(
                icon,
                size: 18,
                color: enabled
                    ? BedrockColors.accent
                    : BedrockColors.onSurfaceMuted,
              ),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: const TextStyle(
                      fontSize: 11,
                      color: BedrockColors.onSurfaceMuted,
                    ),
                  ),
                  Text(
                    value,
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: enabled
                          ? BedrockColors.onSurface
                          : BedrockColors.onSurfaceMuted,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _fmt(BuildContext context, int minutes) =>
    TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60).format(context);
