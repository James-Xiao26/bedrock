import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';

/// The "Today" block: not a stack of cards but one calm surface - a greeting,
/// the session state as large quiet type, the on-demand downtime button, and a
/// slim timeline for tonight's window. Returns a [Column] so it can sit at the
/// top of the single-page home scroll.
class TonightContent extends ConsumerWidget {
  const TonightContent({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionStateProvider);
    final config = ref.watch(configProvider).valueOrNull;

    final plan = config?.active.schedule[DateTime.now().weekday];
    final hasWindow = plan != null && plan.enabled;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _Greeting(),
        const SizedBox(height: 56),
        _Status(session: session),
        const _DowntimeToggle(),
        if (hasWindow) ...[
          const SizedBox(height: 64),
          _Timeline(plan: plan),
        ],
      ],
    );
  }
}

/// On-demand downtime button. Shown only outside a scheduled window: it starts
/// a manual session when idle and ends it when running. During a scheduled
/// window it renders nothing - that downtime is governed by the schedule.
class _DowntimeToggle extends ConsumerWidget {
  const _DowntimeToggle();

  static const _danger = Color(0xFFE5695B);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionStateProvider).valueOrNull;
    if (session == null) return const SizedBox.shrink();

    final active = session.state == SessionState.active;
    // A scheduled window is in effect: the manual toggle doesn't apply.
    if (active && !session.manual) return const SizedBox.shrink();

    final on = active && session.manual;
    Future<void> toggle() =>
        ref.read(engineChannelProvider).setManualDowntime(!on);

    return Padding(
      padding: const EdgeInsets.only(top: 32),
      // Fixed width and left-anchored so the button holds its spot as the label
      // swaps between the on and off states.
      child: SizedBox(
        width: 200,
        height: 54,
        child: Material(
          color: on ? BedrockColors.surface : BedrockColors.accent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: on
                ? const BorderSide(color: BedrockColors.hairline)
                : BorderSide.none,
          ),
          child: InkWell(
            onTap: toggle,
            borderRadius: BorderRadius.circular(16),
            child: Center(
              child: Text(
                on ? 'Turn Off For Now' : 'Turn On Now',
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                  color: on ? _danger : BedrockColors.onAccent,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Greeting extends ConsumerWidget {
  const _Greeting();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final name = ref.watch(displayNameProvider).valueOrNull?.trim();
    final hasName = name != null && name.isNotEmpty;
    final hour = DateTime.now().hour;
    final greeting = hour < 5
        ? (hasName ? 'Still up, $name?' : 'Still up?')
        : hasName
            ? (hour < 12
                ? 'Good morning, $name'
                : hour < 18
                    ? 'Good afternoon, $name'
                    : 'Good evening, $name')
            : (hour < 12
                ? 'Good morning'
                : hour < 18
                    ? 'Good afternoon'
                    : 'Good evening');
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
          : s.manual
              ? 'Ends at ${at(s.windowClose)}.'
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
