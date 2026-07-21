import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_channel.dart';
import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/allowed_apps_picker.dart';
import '../../widgets/section_card.dart';

enum _Step {
  welcome,
  problems,
  alarm,
  schedule,
  apps,
  notifications,
  accessibility,
  overlay,
  confirm,
}

/// First-run onboarding. Answers live locally and are written to the engine
/// only at the final "Turn on Bedrock" step, so stepping back and forth is free
/// and nothing is persisted until the user commits.
class OnboardingFlow extends ConsumerStatefulWidget {
  const OnboardingFlow({super.key, required this.onDone});

  final VoidCallback onDone;

  @override
  ConsumerState<OnboardingFlow> createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends ConsumerState<OnboardingFlow>
    with WidgetsBindingObserver {
  static const _order = _Step.values;

  int _index = 0;

  // Local answers.
  final Set<String> _problems = {};
  final TextEditingController _otherController = TextEditingController();
  TimeOfDay _bedtime = const TimeOfDay(hour: 23, minute: 0);
  TimeOfDay _wake = const TimeOfDay(hour: 7, minute: 0);
  final Set<String> _allowlist = {};

  bool _wantClock = false;
  bool _clockSeeded = false;

  PermissionStatus? _perms;

  EngineChannel get _engine => ref.read(engineChannelProvider);

  _Step get _step => _order[_index];

  int get _windDown =>
      ref.read(configProvider).valueOrNull?.active.windDownMinutes ?? 60;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshPerms();
    // Subscribe now so the app list starts loading; seed the clock once it
    // resolves (the alarm answer may come before or after it loads).
    ref.listenManual(installedAppsProvider, (_, _) => _maybeSeedClock());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _otherController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Returning from a system settings screen: re-check and skip ahead if the
    // grant the current step was asking for has landed.
    if (state == AppLifecycleState.resumed) _refreshPerms(autoAdvance: true);
  }

  Future<void> _refreshPerms({bool autoAdvance = false}) async {
    final p = await _engine.getPermissions();
    if (!mounted) return;
    setState(() => _perms = p);
    if (autoAdvance && _granted(_step, p)) _next();
  }

  bool _granted(_Step step, PermissionStatus p) => switch (step) {
        _Step.notifications => p.notifications,
        _Step.accessibility => p.foregroundDetection,
        _Step.overlay => p.overlay,
        _ => false,
      };

  void _next() {
    if (_index < _order.length - 1) setState(() => _index++);
  }

  void _back() {
    if (_index > 0) setState(() => _index--);
  }

  void _setAlarm(bool isAlarm) {
    _wantClock = isAlarm;
    _maybeSeedClock();
    _next();
  }

  /// Pre-allow the phone's clock app(s) so alarms stay reachable during
  /// downtime. Runs once, whenever both the answer is "yes" and the app list
  /// has loaded - either order.
  void _maybeSeedClock() {
    if (!_wantClock || _clockSeeded) return;
    final apps = ref.read(installedAppsProvider).valueOrNull;
    if (apps == null) return; // retry when the listener fires
    _clockSeeded = true;
    final clocks = apps
        .where((a) =>
            a.label.toLowerCase().contains('clock') ||
            a.packageName.contains('deskclock') ||
            a.packageName.contains('alarmclock'))
        .map((a) => a.packageName);
    if (clocks.isNotEmpty && mounted) {
      setState(() => _allowlist.addAll(clocks));
    }
  }

  int _mins(TimeOfDay t) => t.hour * 60 + t.minute;

  /// The moment downtime actually begins: [_windDown] before the chosen bedtime.
  TimeOfDay get _downtimeStart {
    final m = (_mins(_bedtime) - _windDown) % (24 * 60);
    final wrapped = m < 0 ? m + 24 * 60 : m;
    return TimeOfDay(hour: wrapped ~/ 60, minute: wrapped % 60);
  }

  Future<void> _finish() async {
    final schedule = {
      for (var d = 1; d <= 7; d++)
        d: NightPlan(
          bedtimeMinutes: _mins(_bedtime),
          wakeMinutes: _mins(_wake),
          enabled: true,
        ),
    };
    await _engine
        .updateConfig(ConfigPatch(schedule: schedule, allowlist: _allowlist));
    await _engine.markOnboarded();
    if (mounted) widget.onDone();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: switch (_step) {
          _Step.welcome => _welcome(),
          _Step.problems => _problemsStep(),
          _Step.alarm => _alarmStep(),
          _Step.schedule => _scheduleStep(),
          _Step.apps => _appsStep(),
          _Step.notifications => _notificationsStep(),
          _Step.accessibility => _accessibilityStep(),
          _Step.overlay => _overlayStep(),
          _Step.confirm => _confirmStep(),
        },
      ),
    );
  }

  // --- Steps ---

  Widget _welcome() => _Scaffold(
        showBack: false,
        title: 'The apps that keep\nyou up, closed by\nbedtime.',
        intro: 'Set a bedtime and Bedrock blocks the apps you choose from an '
            'hour before until you wake. Your alarm and calls still work.',
        primaryLabel: 'Get started',
        onPrimary: _next,
        body: const SizedBox.shrink(),
      );

  Widget _problemsStep() {
    const options = [
      'Sleeping better',
      'Doomscrolling less',
      'More focus during the day',
      'Improve mental health',
      'Breaking a specific app habit',
    ];
    return _Scaffold(
      onBack: _back,
      title: 'What matters\nmost to you?',
      intro: 'Pick whatever fits. This just helps set the tone - you can change '
          'your setup anytime.',
      primaryLabel: 'Continue',
      onPrimary: _next,
      body: SettingGroup(
        rows: [
          for (final o in options)
            SettingRow(
              title: o,
              onTap: () => setState(() =>
                  _problems.contains(o) ? _problems.remove(o) : _problems.add(o)),
              trailing: _problems.contains(o)
                  ? const Icon(Icons.check, color: BedrockColors.accent, size: 22)
                  : const SizedBox(width: 22),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: TextField(
              controller: _otherController,
              maxLength: 60,
              cursorColor: BedrockColors.accent,
              style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w500,
                color: BedrockColors.onSurface,
              ),
              buildCounter: (_,
                      {required currentLength, required isFocused, maxLength}) =>
                  null,
              decoration: const InputDecoration(
                hintText: 'Something else',
                hintStyle: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w500,
                  color: BedrockColors.onSurfaceMuted,
                ),
                border: InputBorder.none,
                isDense: true,
                contentPadding: EdgeInsets.symmetric(vertical: 12),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _alarmStep() => _Scaffold(
        onBack: _back,
        title: 'Do you wake up to\nthis phone?',
        intro: 'If your alarm lives here, Bedrock keeps the Clock app open '
            "during downtime so it always goes off. You won't be locked out of "
            'your morning.',
        primaryLabel: 'Yes, it\'s my alarm',
        onPrimary: () => _setAlarm(true),
        secondaryLabel: 'No, I use another alarm',
        onSecondary: () => _setAlarm(false),
        body: const SizedBox.shrink(),
      );

  Widget _scheduleStep() {
    final start = _downtimeStart.format(context);
    return _Scaffold(
      onBack: _back,
      title: 'When is\nbedtime?',
      intro: 'Downtime starts $start (${_windDown ~/ 60 == 1 ? "an hour" : "${_windDown}m"} '
          'before bed) and lifts when you wake. You can fine-tune this later.',
      primaryLabel: 'Continue',
      onPrimary: _next,
      body: SettingGroup(
        rows: [
          _TimeRow(
            label: 'Bedtime',
            value: _bedtime.format(context),
            onTap: () async {
              final t = await showTimePicker(
                  context: context, initialTime: _bedtime, helpText: 'Bedtime');
              if (t != null) setState(() => _bedtime = t);
            },
          ),
          _TimeRow(
            label: 'Wake up',
            value: _wake.format(context),
            onTap: () async {
              final t = await showTimePicker(
                  context: context, initialTime: _wake, helpText: 'Wake up');
              if (t != null) setState(() => _wake = t);
            },
          ),
        ],
      ),
    );
  }

  Widget _appsStep() {
    final apps = ref.watch(installedAppsProvider);
    return _Scaffold(
      onBack: _back,
      title: 'Anything to keep\nopen?',
      intro: 'Everything is blocked during downtime except phone, messages, and '
          'anything you allow here. Most people keep this short.',
      primaryLabel: 'Continue',
      onPrimary: _next,
      body: switch (apps) {
        AsyncData(:final value) => AllowedAppsPicker(
            allowed: _allowlist,
            apps: value,
            onChanged: (next) => setState(() {
              _allowlist
                ..clear()
                ..addAll(next);
            }),
          ),
        AsyncError() => const Text(
            'Could not load your apps. You can set this up later in Settings.',
            style: TextStyle(color: BedrockColors.onSurfaceMuted),
          ),
        _ => const Padding(
            padding: EdgeInsets.only(top: 40),
            child: Center(child: CircularProgressIndicator()),
          ),
      },
    );
  }

  Widget _notificationsStep() {
    final on = _perms?.notifications ?? false;
    return _Scaffold(
      onBack: _back,
      title: 'A nudge before\ndowntime.',
      intro: 'Bedrock sends one reminder five minutes before downtime starts, '
          'and a quiet note while it\'s on. Nothing else.',
      primaryLabel: on ? 'Continue' : 'Allow notifications',
      onPrimary: on
          ? _next
          : () async {
              await _engine.requestNotifications();
              await _refreshPerms();
              _next();
            },
      secondaryLabel: on ? null : 'Not now',
      onSecondary: on ? null : _next,
      body: _GrantState(granted: on, grantedLabel: 'Notifications on'),
    );
  }

  Widget _accessibilityStep() {
    final on = _perms?.foregroundDetection ?? false;
    return _Scaffold(
      onBack: _back,
      title: 'Let Bedrock see\nwhat\'s open.',
      intro: 'To block an app, Bedrock needs to know which app just came to the '
          'front. Turn it on under Accessibility on the next screen.',
      primaryLabel: on ? 'Continue' : 'Open Accessibility settings',
      onPrimary: on ? _next : _engine.openAccessibilitySettings,
      secondaryLabel: on ? null : 'Use App Usage access instead',
      onSecondary: on ? null : _engine.openUsageAccessSettings,
      tertiaryLabel: on ? null : 'Set up later',
      onTertiary: on ? null : _next,
      body: on
          ? const _GrantState(
              granted: true, grantedLabel: 'Foreground detection on')
          : const _PrivacyNote(),
    );
  }

  Widget _overlayStep() {
    final on = _perms?.overlay ?? false;
    return _Scaffold(
      onBack: _back,
      title: 'Show the block\nscreen.',
      intro: 'When you open a blocked app, Bedrock draws its block screen on top. '
          'Allow "Display over other apps" to let it appear.',
      primaryLabel: on ? 'Continue' : 'Open overlay settings',
      onPrimary: on ? _next : _engine.openOverlaySettings,
      secondaryLabel: on ? null : 'Not now',
      onSecondary: on ? null : _next,
      body: _GrantState(granted: on, grantedLabel: 'Overlay allowed'),
    );
  }

  Widget _confirmStep() {
    final start = _downtimeStart.format(context);
    final wake = _wake.format(context);
    final allowed = _allowlist.length;
    return _Scaffold(
      onBack: _back,
      title: 'You\'re set.',
      intro: 'Downtime runs $start to $wake, every day. '
          '${allowed == 0 ? 'No extra apps allowed' : '$allowed app${allowed == 1 ? '' : 's'} stay open'} '
          'while it\'s on. Change any of this anytime in Settings.',
      primaryLabel: 'Turn on Bedrock',
      onPrimary: _finish,
      body: const SizedBox.shrink(),
    );
  }
}

/// Shared editorial step chrome: an optional back affordance, a big quiet title,
/// intro copy, a scrollable body, and one or two bottom actions.
class _Scaffold extends StatelessWidget {
  const _Scaffold({
    required this.title,
    required this.body,
    required this.primaryLabel,
    required this.onPrimary,
    this.intro,
    this.secondaryLabel,
    this.onSecondary,
    this.tertiaryLabel,
    this.onTertiary,
    this.onBack,
    this.showBack = true,
  });

  final String title;
  final String? intro;
  final Widget body;
  final String primaryLabel;
  final VoidCallback? onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;
  final String? tertiaryLabel;
  final VoidCallback? onTertiary;
  final VoidCallback? onBack;
  final bool showBack;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: 48,
          child: (showBack && onBack != null)
              ? Align(
                  alignment: Alignment.centerLeft,
                  child: IconButton(
                    onPressed: onBack,
                    icon: const Icon(Icons.arrow_back,
                        color: BedrockColors.onSurfaceMuted),
                  ),
                )
              : null,
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 34,
                  height: 1.08,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.5,
                  color: BedrockColors.onSurface,
                ),
              ),
              if (intro != null) ...[
                const SizedBox(height: 16),
                Text(
                  intro!,
                  style: const TextStyle(
                    fontSize: 16,
                    height: 1.45,
                    color: BedrockColors.onSurfaceMuted,
                  ),
                ),
              ],
              const SizedBox(height: 32),
              body,
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FilledButton(onPressed: onPrimary, child: Text(primaryLabel)),
              if (secondaryLabel != null) ...[
                const SizedBox(height: 10),
                TextButton(
                  onPressed: onSecondary,
                  style: TextButton.styleFrom(
                    foregroundColor: BedrockColors.onSurfaceMuted,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: Text(secondaryLabel!),
                ),
              ],
              if (tertiaryLabel != null)
                TextButton(
                  onPressed: onTertiary,
                  style: TextButton.styleFrom(
                    foregroundColor: BedrockColors.onSurfaceMuted.withValues(alpha: 0.7),
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    textStyle: const TextStyle(fontSize: 13),
                  ),
                  child: Text(tertiaryLabel!),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

/// A tappable row showing a time value with a chevron.
class _TimeRow extends StatelessWidget {
  const _TimeRow({required this.label, required this.value, required this.onTap});

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
          Text(value,
              style: const TextStyle(
                  fontSize: 16, color: BedrockColors.onSurfaceMuted)),
          const SizedBox(width: 6),
          const Icon(Icons.chevron_right,
              color: BedrockColors.onSurfaceMuted, size: 20),
        ],
      ),
    );
  }
}

/// Privacy reassurance for the accessibility step. Every claim here is
/// enforced by the code: the service sets canRetrieveWindowContent=false and
/// listens for window-state changes only, and the release build ships without
/// the INTERNET permission, so nothing can leave the device.
class _PrivacyNote extends StatelessWidget {
  const _PrivacyNote();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _row(
          Icons.visibility_off_outlined,
          'Bedrock only sees which app is in front - never your screen, your '
              'messages, or what you type.',
        ),
        const SizedBox(height: 18),
        _row(
          Icons.smartphone_outlined,
          'Everything stays on this phone. There\'s no account and no upload, so '
              'no one else can ever see it.',
        ),
        const SizedBox(height: 18),
        _row(
          Icons.info_outline,
          'Android\'s next prompt warns about "full control" - that\'s its '
              'standard wording for this permission. Bedrock uses only the '
              'which-app-is-open part.',
        ),
        const SizedBox(height: 24),
        const Padding(
          padding: EdgeInsets.only(left: 34),
          child: Text(
            'Blocking can\'t work without this.',
            style: TextStyle(
                fontSize: 13, height: 1.4, color: BedrockColors.onSurfaceMuted),
          ),
        ),
      ],
    );
  }

  Widget _row(IconData icon, String text) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 22, color: BedrockColors.accent),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              fontSize: 15,
              height: 1.45,
              color: BedrockColors.onSurface,
            ),
          ),
        ),
      ],
    );
  }
}

/// A small "granted" confirmation line for permission steps; empty until then.
class _GrantState extends StatelessWidget {
  const _GrantState({required this.granted, required this.grantedLabel});

  final bool granted;
  final String grantedLabel;

  @override
  Widget build(BuildContext context) {
    if (!granted) return const SizedBox.shrink();
    return Row(
      children: [
        const Icon(Icons.check_circle, color: BedrockColors.accent, size: 22),
        const SizedBox(width: 10),
        Text(grantedLabel,
            style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: BedrockColors.onSurface)),
      ],
    );
  }
}
