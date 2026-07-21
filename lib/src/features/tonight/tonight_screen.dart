import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';

/// The home screen. Deliberately not a stack of cards: an ambient glow at the
/// top, the session state set as large quiet type, and a single slim timeline
/// for tonight's window. Everything reads as one calm surface, iOS-like.
class TonightScreen extends ConsumerWidget {
  const TonightScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionStateProvider);
    final config = ref.watch(configProvider).valueOrNull;
    final snap = session.valueOrNull;
    final active = snap?.state == SessionState.active;

    final plan = config?.active.schedule[DateTime.now().weekday];
    final hasWindow = plan != null && plan.enabled;

    return Stack(
      children: [
        // Ambient glow bleeding from the top edge - warm when downtime is on.
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          height: 420,
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: const Alignment(0, -1.1),
                  radius: 1.1,
                  colors: [
                    (active
                            ? BedrockColors.accent
                            : BedrockColors.onSurfaceMuted)
                        .withValues(alpha: active ? 0.16 : 0.06),
                    BedrockColors.background.withValues(alpha: 0),
                  ],
                ),
              ),
            ),
          ),
        ),
        ListView(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 40),
          children: [
            const _Greeting(),
            const SizedBox(height: 56),
            _Status(session: session),
            if (hasWindow) ...[
              const SizedBox(height: 64),
              _Timeline(plan: plan),
            ],
          ],
        ),
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
      greeting.toUpperCase(),
      style: const TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w600,
        letterSpacing: 1.5,
        color: BedrockColors.onSurfaceMuted,
      ),
    );
  }
}

/// The session state as the page's centre of gravity: one big line of type and
/// a supporting sentence. No container, no gradient card.
class _Status extends StatelessWidget {
  const _Status({required this.session});

  final AsyncValue<SessionSnapshot> session;

  @override
  Widget build(BuildContext context) {
    final (title, detail) = switch (session) {
      AsyncData(:final value) => (
          switch (value.state) {
            SessionState.idle => 'Apps unblocked',
            SessionState.active => 'Downtime on',
          },
          _detail(context, value),
        ),
      AsyncError() => ('Engine unreachable', 'Reopen the app to retry.'),
      _ => ('One moment', ''),
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontSize: 44,
            height: 1.05,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.5,
            color: BedrockColors.onSurface,
          ),
        ),
        if (detail.isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(
            detail,
            style: const TextStyle(
              fontSize: 17,
              height: 1.4,
              color: BedrockColors.onSurfaceMuted,
            ),
          ),
        ],
      ],
    );
  }

  String _detail(BuildContext context, SessionSnapshot s) {
    String at(DateTime? t) =>
        t == null ? '' : TimeOfDay.fromDateTime(t).format(context);
    return switch (s.state) {
      SessionState.idle => s.windowOpen == null
          ? ''
          : 'Downtime starts at ${at(s.windowOpen)}.',
      SessionState.active => s.windowClose == null
          ? 'Blocked apps stay blocked until your window ends.'
          : 'Everything unblocks at ${at(s.windowClose)}.',
    };
  }
}

/// Tonight's window as a slim horizontal timeline: start on the left, end on
/// the right, a hairline track between with a soft filled span.
class _Timeline extends StatelessWidget {
  const _Timeline({required this.plan});

  final NightPlan plan;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'TONIGHT',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            letterSpacing: 1.5,
            color: BedrockColors.onSurfaceMuted,
          ),
        ),
        const SizedBox(height: 20),
        Row(
          children: [
            _Endpoint(
              label: 'Bed',
              time: _fmt(context, plan.bedtimeMinutes),
              align: CrossAxisAlignment.start,
            ),
            const Expanded(child: _Track()),
            _Endpoint(
              label: 'Wake',
              time: _fmt(context, plan.wakeMinutes),
              align: CrossAxisAlignment.end,
            ),
          ],
        ),
      ],
    );
  }
}

class _Track extends StatelessWidget {
  const _Track();

  @override
  Widget build(BuildContext context) {
    return Padding(
      // Sit against the time row, above the small labels.
      padding: const EdgeInsets.only(bottom: 20, left: 14, right: 14),
      child: Row(
        children: [
          _dot(BedrockColors.accent),
          Expanded(
            child: Container(
              height: 2,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    BedrockColors.accent.withValues(alpha: 0.6),
                    BedrockColors.onSurfaceMuted.withValues(alpha: 0.25),
                  ],
                ),
              ),
            ),
          ),
          _dot(BedrockColors.onSurfaceMuted.withValues(alpha: 0.7)),
        ],
      ),
    );
  }

  Widget _dot(Color c) => Container(
        width: 8,
        height: 8,
        decoration: BoxDecoration(color: c, shape: BoxShape.circle),
      );
}

class _Endpoint extends StatelessWidget {
  const _Endpoint({
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
          time,
          style: const TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: BedrockColors.onSurface,
          ),
        ),
        const SizedBox(height: 4),
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

String _fmt(BuildContext context, int minutes) =>
    TimeOfDay(hour: minutes ~/ 60, minute: minutes % 60).format(context);
