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

// iOS Downtime lists Sunday first.
const _dayOrder = [7, 1, 2, 3, 4, 5, 6];

/// Downtime schedule editor, modelled on iOS Screen Time > Downtime. Every edit
/// goes straight to the engine; the native freeze rules decide whether it
/// applies now or at the next window.
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

class _ScheduleList extends ConsumerStatefulWidget {
  const _ScheduleList({required this.view});

  final ConfigView view;

  @override
  ConsumerState<_ScheduleList> createState() => _ScheduleListState();
}

class _ScheduleListState extends ConsumerState<_ScheduleList> {
  /// null until first build derives it from the active config.
  bool? _customize;

  Map<int, NightPlan> get _schedule => widget.view.active.schedule;

  bool get _scheduled => _schedule.values.any((p) => p.enabled);

  /// True when every day shares the same window (the "Every Day" case).
  bool get _allDaysEqual {
    final plans = _dayOrder.map((d) => _schedule[d]).whereType<NightPlan>();
    if (plans.isEmpty) return true;
    final first = plans.first;
    return plans.every(
      (p) => p.bedtimeMinutes == first.bedtimeMinutes && p.wakeMinutes == first.wakeMinutes,
    );
  }

  bool get _customizeMode => _customize ?? !_allDaysEqual;

  Future<void> _writeAll(Iterable<int> days, NightPlan Function(NightPlan) f) {
    final patch = {
      for (final d in days)
        if (_schedule[d] case final p?) d: f(p),
    };
    return ref.read(engineChannelProvider).updateConfig(ConfigPatch(schedule: patch));
  }

  Future<void> _setScheduled(bool on) =>
      _writeAll(_dayNames.keys, (p) => p.copyWith(enabled: on));

  /// Collapse every day onto the first day's window.
  Future<void> _selectEveryDay() async {
    setState(() => _customize = false);
    final ref0 = _schedule[_dayOrder.first];
    if (ref0 == null || _allDaysEqual) return;
    await _writeAll(
      _dayNames.keys,
      (p) => p.copyWith(bedtimeMinutes: ref0.bedtimeMinutes, wakeMinutes: ref0.wakeMinutes),
    );
  }

  /// Prompt for a From then To time; returns null if cancelled at either step.
  Future<({int bed, int wake})?> _pickWindow(NightPlan plan) async {
    TimeOfDay tod(int m) => TimeOfDay(hour: m ~/ 60, minute: m % 60);
    final from = await showTimePicker(
      context: context,
      initialTime: tod(plan.bedtimeMinutes),
      helpText: 'Bedtime',
    );
    if (from == null || !mounted) return null;
    final to = await showTimePicker(
      context: context,
      initialTime: tod(plan.wakeMinutes),
      helpText: 'Wake up',
    );
    if (to == null) return null;
    return (bed: from.hour * 60 + from.minute, wake: to.hour * 60 + to.minute);
  }

  Future<void> _editWindow(Iterable<int> days, NightPlan plan) async {
    final picked = await _pickWindow(plan);
    if (picked == null) return;
    await _writeAll(
      days,
      (p) => p.copyWith(bedtimeMinutes: picked.bed, wakeMinutes: picked.wake),
    );
  }

  @override
  Widget build(BuildContext context) {
    final view = widget.view;
    final scheduled = _scheduled;
    final ref0 = _schedule[_dayOrder.first];

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const Text(
          'Downtime',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 8),
        const _Caption('During downtime, only apps you allow and phone calls '
            'will be available.'),
        const SizedBox(height: 20),
        if (view.hasPendingChanges) ...[
          const _PendingBanner(),
          const SizedBox(height: 16),
        ],
        SettingGroup(
          rows: [
            SettingRow(
              title: 'Scheduled',
              trailing: Switch(value: scheduled, onChanged: _setScheduled),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const _Caption('Downtime runs from your bedtime until you wake. It '
            'begins a little earlier - set the wind-down time in Settings - and '
            'a reminder appears five minutes before it starts.'),
        if (scheduled) ...[
          const SizedBox(height: 20),
          SettingGroup(
            rows: [
              _SelectRow(
                label: 'Every Day',
                selected: !_customizeMode,
                onTap: _selectEveryDay,
              ),
              _SelectRow(
                label: 'Customize Days',
                selected: _customizeMode,
                onTap: () => setState(() => _customize = true),
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (_customizeMode)
            SettingGroup(
              rows: [
                for (final d in _dayOrder)
                  if (_schedule[d] case final p?)
                    _WindowRow(
                      label: _dayNames[d]!,
                      value: _fmtRange(context, p),
                      onTap: () => _editWindow([d], p),
                    ),
              ],
            )
          else if (ref0 != null)
            SettingGroup(
              rows: [
                _WindowRow(
                  label: 'Time',
                  value: _fmtRange(context, ref0),
                  onTap: () => _editWindow(_dayNames.keys, ref0),
                ),
              ],
            ),
        ],
        const SizedBox(height: 8),
        const _Caption('Downtime will apply to this device. A downtime reminder '
            'will appear five minutes before downtime begins.'),
      ],
    );
  }
}

class _Caption extends StatelessWidget {
  const _Caption(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 13,
          height: 1.4,
          color: BedrockColors.onSurfaceMuted,
        ),
      ),
    );
  }
}

/// A radio-style row: title on the left, accent checkmark when selected.
class _SelectRow extends StatelessWidget {
  const _SelectRow({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SettingRow(
      title: label,
      onTap: onTap,
      trailing: selected
          ? const Icon(Icons.check, color: BedrockColors.accent, size: 22)
          : const SizedBox(width: 22),
    );
  }
}

/// A tappable row showing a time range with a chevron.
class _WindowRow extends StatelessWidget {
  const _WindowRow({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final String value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SettingRow(
      title: label,
      onTap: onTap,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            value,
            style: const TextStyle(
              fontSize: 16,
              color: BedrockColors.onSurfaceMuted,
            ),
          ),
          const SizedBox(width: 6),
          const Icon(Icons.chevron_right,
              color: BedrockColors.onSurfaceMuted, size: 20),
        ],
      ),
    );
  }
}

class _PendingBanner extends StatelessWidget {
  const _PendingBanner();

  @override
  Widget build(BuildContext context) {
    return const Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(Icons.schedule, color: BedrockColors.accent, size: 20),
        SizedBox(width: 12),
        Expanded(
          child: Text(
            'Some changes loosen the current window and will apply at '
            'your next window.',
            style: TextStyle(
              fontSize: 14,
              height: 1.4,
              color: BedrockColors.onSurfaceMuted,
            ),
          ),
        ),
      ],
    );
  }
}

String _fmt(BuildContext context, int minutes) =>
    TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60).format(context);

String _fmtRange(BuildContext context, NightPlan p) =>
    '${_fmt(context, p.bedtimeMinutes)} - ${_fmt(context, p.wakeMinutes)}';
