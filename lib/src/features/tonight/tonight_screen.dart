import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

/// The home dashboard: a gradient hero card summarising tonight's session
/// status, the upcoming bed/wake window, and a strip of the week's nights.
class TonightScreen extends ConsumerWidget {
  const TonightScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionStateProvider);
    final config = ref.watch(configProvider).valueOrNull;

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const _Greeting(),
        const SizedBox(height: 20),
        _HeroCard(session: session),
        const SizedBox(height: 24),
        if (config != null) ...[
          const SectionLabel('Today'),
          _TonightPlanCard(config: config.active),
          const SizedBox(height: 24),
          const SectionLabel('This week'),
          _WeekStrip(config: config.active),
        ],
      ],
    );
  }
}

class _Greeting extends StatelessWidget {
  const _Greeting();

  @override
  Widget build(BuildContext context) {
    final hour = DateTime.now().hour;
    final greeting = hour < 5
        ? 'Still up?'
        : hour < 12
            ? 'Good morning'
            : hour < 18
                ? 'Good afternoon'
                : 'Good evening';
    return Text(
      greeting,
      style: const TextStyle(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        color: BedrockColors.onSurface,
      ),
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({required this.session});

  final AsyncValue<SessionSnapshot> session;

  @override
  Widget build(BuildContext context) {
    final (title, detail, icon) = switch (session) {
      AsyncData(:final value) => (
          switch (value.state) {
            SessionState.idle => 'Apps unblocked',
            SessionState.active => 'Downtime on',
          },
          _heroDetail(context, value),
          switch (value.state) {
            SessionState.idle => Icons.wb_sunny_outlined,
            SessionState.active => Icons.lock_outline,
          },
        ),
      AsyncError() => ('Engine unreachable', 'Reopen the app to retry.', Icons.cloud_off),
      _ => ('Contacting engine...', '', Icons.hourglass_empty),
    };

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
            child: Icon(icon, color: Colors.white, size: 26),
          ),
          const SizedBox(height: 20),
          Text(
            title,
            style: const TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w700,
              color: Colors.white,
            ),
          ),
          if (detail.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              detail,
              style: TextStyle(
                fontSize: 15,
                height: 1.4,
                color: Colors.white.withValues(alpha: 0.85),
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _heroDetail(BuildContext context, SessionSnapshot s) {
    String at(DateTime? t) =>
        t == null ? '' : TimeOfDay.fromDateTime(t).format(context);
    return switch (s.state) {
      SessionState.idle => s.windowOpen == null
          ? 'No window scheduled.'
          : 'Downtime starts at ${at(s.windowOpen)}.',
      SessionState.active => s.windowClose == null
          ? 'Blocked apps stay blocked until your window ends.'
          : 'Blocked apps unblock at ${at(s.windowClose)}.',
    };
  }
}

class _TonightPlanCard extends StatelessWidget {
  const _TonightPlanCard({required this.config});

  final EngineConfig config;

  @override
  Widget build(BuildContext context) {
    // The night that starts today is keyed by today's ISO weekday.
    final plan = config.schedule[DateTime.now().weekday];

    if (plan == null || !plan.enabled) {
      return const SectionCard(
        child: Row(
          children: [
            Icon(Icons.do_not_disturb_on_outlined,
                color: BedrockColors.onSurfaceMuted),
            SizedBox(width: 14),
            Expanded(
              child: Text(
                'No downtime today. Edit your schedule to add one.',
                style: TextStyle(
                  fontSize: 15,
                  color: BedrockColors.onSurfaceMuted,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return SectionCard(
      child: Row(
        children: [
          Expanded(
            child: _TimeStat(
              icon: Icons.bedtime_outlined,
              label: 'Starts',
              value: _fmt(context, plan.bedMinutes),
            ),
          ),
          Container(
            width: 1,
            height: 44,
            color: const Color(0xFF2C2B3D),
          ),
          Expanded(
            child: _TimeStat(
              icon: Icons.wb_twilight_outlined,
              label: 'Ends',
              value: _fmt(context, plan.wakeMinutes),
            ),
          ),
        ],
      ),
    );
  }
}

class _TimeStat extends StatelessWidget {
  const _TimeStat({
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

class _WeekStrip extends StatelessWidget {
  const _WeekStrip({required this.config});

  final EngineConfig config;

  static const _initials = {
    1: 'M',
    2: 'T',
    3: 'W',
    4: 'T',
    5: 'F',
    6: 'S',
    7: 'S',
  };

  @override
  Widget build(BuildContext context) {
    final today = DateTime.now().weekday;
    return SectionCard(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 18),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          for (final day in _initials.keys)
            _DayDot(
              label: _initials[day]!,
              enabled: config.schedule[day]?.enabled ?? false,
              isToday: day == today,
            ),
        ],
      ),
    );
  }
}

class _DayDot extends StatelessWidget {
  const _DayDot({
    required this.label,
    required this.enabled,
    required this.isToday,
  });

  final String label;
  final bool enabled;
  final bool isToday;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        color: enabled ? BedrockColors.accent : Colors.transparent,
        shape: BoxShape.circle,
        border: isToday && !enabled
            ? Border.all(color: BedrockColors.accent, width: 1.5)
            : null,
      ),
      alignment: Alignment.center,
      child: Text(
        label,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: enabled
              ? Colors.white
              : (isToday
                  ? BedrockColors.accent
                  : BedrockColors.onSurfaceMuted),
        ),
      ),
    );
  }
}

String _fmt(BuildContext context, int minutes) =>
    TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60).format(context);
