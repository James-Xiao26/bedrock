import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

/// Sleep stats: current streak, lifetime totals, and a strip of recent nights
/// coloured by how each one ended. Re-reads on resume since the event stream
/// may have been dead while backgrounded.
class StatsScreen extends ConsumerStatefulWidget {
  const StatsScreen({super.key});

  @override
  ConsumerState<StatsScreen> createState() => _StatsScreenState();
}

class _StatsScreenState extends ConsumerState<StatsScreen>
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
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const Text(
          'Your history',
          style: TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 20),
        switch (stats) {
          AsyncData(:final value) => _StatsBody(stats: value),
          AsyncError() => const SectionCard(
              child: Text(
                'Stats unavailable. Reopen the app to retry.',
                style: TextStyle(color: BedrockColors.onSurfaceMuted),
              ),
            ),
          _ => const SectionCard(
              child: Center(child: CircularProgressIndicator()),
            ),
        },
      ],
    );
  }
}

class _StatsBody extends StatelessWidget {
  const _StatsBody({required this.stats});

  final StatsView stats;

  @override
  Widget build(BuildContext context) {
    if (stats.totalWindows == 0) {
      return const SectionCard(
        child: Row(
          children: [
            Icon(Icons.insights_outlined, color: BedrockColors.onSurfaceMuted),
            SizedBox(width: 14),
            Expanded(
              child: Text(
                'No windows recorded yet. Your first downtime shows up here.',
                style: TextStyle(fontSize: 15, color: BedrockColors.onSurfaceMuted),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _StreakCard(streak: stats.currentStreak),
        const SizedBox(height: 24),
        const SectionLabel('Totals'),
        SectionCard(
          child: Row(
            children: [
              Expanded(
                child: _NumberStat(
                  icon: Icons.check_circle_outline,
                  label: 'Clean windows',
                  value: '${stats.windowsKept}',
                ),
              ),
              Container(width: 1, height: 44, color: const Color(0xFF2C2B3D)),
              Expanded(
                child: _NumberStat(
                  icon: Icons.nightlight_outlined,
                  label: 'Total windows',
                  value: '${stats.totalWindows}',
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        const SectionLabel('Recent windows'),
        _RecentStrip(recent: stats.recent),
      ],
    );
  }
}

class _StreakCard extends StatelessWidget {
  const _StreakCard({required this.streak});

  final int streak;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: BedrockColors.heroGradient,
        ),
        borderRadius: BorderRadius.circular(BedrockRadii.hero),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.local_fire_department,
                color: Colors.white, size: 26),
          ),
          const SizedBox(height: 20),
          Text(
            '$streak',
            style: const TextStyle(
              fontSize: 48,
              fontWeight: FontWeight.w800,
              color: Colors.white,
              height: 1.0,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            streak == 1 ? 'clean window in a row' : 'clean windows in a row',
            style: TextStyle(
              fontSize: 15,
              color: Colors.white.withValues(alpha: 0.85),
            ),
          ),
        ],
      ),
    );
  }
}

class _NumberStat extends StatelessWidget {
  const _NumberStat({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: BedrockColors.accent, size: 22),
        const SizedBox(height: 10),
        Text(
          value,
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: const TextStyle(
            fontSize: 13,
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
    return SectionCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 18),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          for (final window in recent)
            _OutcomeDot(
              label: _initial(window.windowKey),
              color: _color(window.outcome),
            ),
        ],
      ),
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
      width: 36,
      height: 36,
      decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      alignment: Alignment.center,
      child: Text(
        label,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: Colors.white,
        ),
      ),
    );
  }
}
