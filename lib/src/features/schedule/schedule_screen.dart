import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/bedtime_pairing.dart';
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

// Sunday first, matching both iOS Downtime and Android's Bedtime day row.
const _dayOrder = [7, 1, 2, 3, 4, 5, 6];

const _dayInitials = {
  1: 'M',
  2: 'T',
  3: 'W',
  4: 'T',
  5: 'F',
  6: 'S',
  7: 'S',
};

/// Today's downtime window as a short string for the collapsed section header:
/// the time range when today runs, or "Off today".
String scheduleSummary(BuildContext context, ConfigView view) {
  final plan = view.active.schedule[DateTime.now().weekday];
  if (plan == null || !plan.enabled) return 'Off today';
  return _fmtRange(context, plan);
}

/// Downtime schedule editor, styled after Android's Bedtime routine (day
/// circles + big Start/End) but keeping iOS-Downtime's per-day flexibility.
/// Every edit goes straight to the engine; the native freeze rules decide
/// whether it applies now or at the next window. Returns a [Column] for the
/// single-page home scroll.
class ScheduleContent extends ConsumerWidget {
  const ScheduleContent({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider);
    return switch (config) {
      AsyncData(:final value) => _ScheduleEditor(view: value),
      AsyncError(:final error) => Padding(
          padding: const EdgeInsets.all(24),
          child: Text('Engine error: $error'),
        ),
      _ => const Center(child: CircularProgressIndicator()),
    };
  }
}

class _ScheduleEditor extends ConsumerStatefulWidget {
  const _ScheduleEditor({required this.view});

  final ConfigView view;

  @override
  ConsumerState<_ScheduleEditor> createState() => _ScheduleEditorState();
}

class _ScheduleEditorState extends ConsumerState<_ScheduleEditor> {
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

  Future<void> _toggleDay(int day) =>
      _writeAll([day], (p) => p.copyWith(enabled: !p.enabled));

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
    final ref0 = _schedule[_dayOrder.first];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _Caption('During downtime, only apps you allow and phone calls '
            'will be available. Tap a day to schedule it.'),
        const SizedBox(height: 12),
        const _Caption('For better health, aim to sleep and wake at the same '
            'time every day - keep the window identical across days. One rest '
            'day a week is fine if you need it.'),
        const SizedBox(height: 12),
        const _Caption('Give yourself at least 8 hours of sleep. Set downtime '
            'to start when you should be asleep, or about 15 minutes earlier to '
            'cover your nighttime routine.'),
        const SizedBox(height: 20),
        if (view.hasPendingChanges) ...[
          const _PendingBanner(),
          const SizedBox(height: 16),
        ],
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            for (final d in _dayOrder)
              if (_schedule[d] case final p?)
                _DayCircle(
                  label: _dayInitials[d]!,
                  on: p.enabled,
                  onTap: () => _toggleDay(d),
                ),
          ],
        ),
        if (_scheduled) ...[
          const SizedBox(height: 24),
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
                _StartEndRow(
                  plan: ref0,
                  onTap: () => _editWindow(_dayNames.keys, ref0),
                ),
              ],
            ),
        ],
        const SizedBox(height: 12),
        const _Caption('Downtime runs from your bedtime until you wake. It '
            'begins a little earlier, and a reminder appears five minutes '
            'before it starts.'),
        const SizedBox(height: 28),
        const BedtimePairingRow(),
        const SizedBox(height: 8),
        const _Caption('Your phone\'s Bedtime mode can dim and quiet the screen '
            'as an early warning before Bedrock locks in.'),
      ],
    );
  }
}

/// A single day toggle: an accent-filled circle with the weekday initial when
/// that day is scheduled, an outlined circle when it is off.
class _DayCircle extends StatelessWidget {
  const _DayCircle({
    required this.label,
    required this.on,
    required this.onTap,
  });

  final String label;
  final bool on;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: on ? BedrockColors.accent : Colors.transparent,
          shape: BoxShape.circle,
          border: on
              ? null
              : Border.all(color: BedrockColors.hairline, width: 1.5),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w600,
            color: on ? BedrockColors.onAccent : BedrockColors.onSurfaceMuted,
          ),
        ),
      ),
    );
  }
}

/// The shared "Every Day" window shown as big Start / End times, à la Android
/// Bedtime. The whole block is tappable and opens the time pickers.
class _StartEndRow extends StatelessWidget {
  const _StartEndRow({required this.plan, required this.onTap});

  final NightPlan plan;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 18),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            _BigTime(
              label: 'Start',
              time: _fmt(context, plan.bedtimeMinutes),
              align: CrossAxisAlignment.start,
            ),
            _BigTime(
              label: 'End',
              time: _fmt(context, plan.wakeMinutes),
              align: CrossAxisAlignment.end,
            ),
          ],
        ),
      ),
    );
  }
}

class _BigTime extends StatelessWidget {
  const _BigTime({
    required this.label,
    required this.time,
    required this.align,
  });

  final String label;
  final String time;
  final CrossAxisAlignment align;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: align,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 13,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          time,
          style: const TextStyle(
            fontSize: 34,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.5,
            color: BedrockColors.onSurface,
          ),
        ),
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
