import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

/// Streak summary for the collapsed section header, or null when nothing has
/// been recorded yet.
String? statsSummary(StatsView v) =>
    v.totalWindows == 0 ? null : '${v.currentStreak} clean';

/// Sleep stats: current streak, lifetime totals, and a strip of recent nights
/// coloured by how each one ended. Editorial layout - big type, hairlines, no
/// cards. Re-reads on resume since the event stream may have died while
/// backgrounded. Returns the body directly for the single-page home scroll.
class StatsContent extends ConsumerStatefulWidget {
  const StatsContent({super.key});

  @override
  ConsumerState<StatsContent> createState() => _StatsContentState();
}

class _StatsContentState extends ConsumerState<StatsContent>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) ref.invalidate(statsProvider);
  }

  @override
  Widget build(BuildContext context) {
    final stats = ref.watch(statsProvider);
    return switch (stats) {
      AsyncData(:final value) => _StatsBody(stats: value),
      AsyncError() =>
        const _Empty('Stats unavailable. Reopen the app to retry.'),
      _ => const Padding(
          padding: EdgeInsets.only(top: 40),
          child: Center(child: CircularProgressIndicator()),
        ),
    };
  }
}

class _StatsBody extends StatelessWidget {
  const _StatsBody({required this.stats});

  final StatsView stats;

  @override
  Widget build(BuildContext context) {
    if (stats.totalWindows == 0) {
      return const _Empty(
          'No windows recorded yet. Your first downtime shows up here.');
    }

    final s = stats.currentStreak;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '$s',
          style: const TextStyle(
            fontSize: 72,
            height: 1.0,
            fontWeight: FontWeight.w700,
            letterSpacing: -1,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          s == 1 ? 'clean window in a row' : 'clean windows in a row',
          style: const TextStyle(
            fontSize: 17,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
        const SizedBox(height: 56),
        const SectionLabel('Totals'),
        Row(
          children: [
            Expanded(
              child: _NumberStat(
                value: '${stats.windowsKept}',
                label: 'Clean windows',
              ),
            ),
            Expanded(
              child: _NumberStat(
                value: '${stats.totalWindows}',
                label: 'Total windows',
              ),
            ),
          ],
        ),
        const SizedBox(height: 56),
        const SectionLabel('Recent windows'),
        _RecentStrip(recent: stats.recent),
      ],
    );
  }
}

class _NumberStat extends StatelessWidget {
  const _NumberStat({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 34,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(
            fontSize: 14,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
      ],
    );
  }
}

class _RecentStrip extends StatelessWidget {
  const _RecentStrip({required this.recent});

  final List<RecentWindow> recent;

  static Color _color(WindowOutcome o) => switch (o) {
        WindowOutcome.clean => BedrockColors.accent,
        WindowOutcome.unlocked => BedrockColors.onSurfaceMuted,
        WindowOutcome.violated => const Color(0xFFB05A5A),
      };

  static String _initial(String windowKey) {
    final date = DateTime.tryParse(windowKey);
    if (date == null) return '·';
    const letters = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    return letters[date.weekday - 1];
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        for (final window in recent)
          _OutcomeDot(
            label: _initial(window.windowKey),
            color: _color(window.outcome),
          ),
      ],
    );
  }
}

class _OutcomeDot extends StatelessWidget {
  const _OutcomeDot({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      alignment: Alignment.center,
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: BedrockColors.onAccent,
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty(this.message);

  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Text(
        message,
        style: const TextStyle(
          fontSize: 17,
          height: 1.4,
          color: BedrockColors.onSurfaceMuted,
        ),
      ),
    );
  }
}
