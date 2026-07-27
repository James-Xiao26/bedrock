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
  schedule,
  bedtimeMode,
  apps,
  notifications,
  accessibility,
  overlay,
  name,
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
  final TextEditingController _nameController = TextEditingController();
  TimeOfDay _bedtime = const TimeOfDay(hour: 23, minute: 0);
  TimeOfDay _wake = const TimeOfDay(hour: 7, minute: 0);
  final Set<String> _allowlist = {};

  /// Clock app(s) Bedrock seeds so alarms survive downtime. Kept in [_allowlist]
  /// so they're actually allowed, but surfaced in the picker's locked "default"
  /// section so the user can't remove them and break their alarm.
  final Set<String> _seededClocks = {};

  // Always keep the phone's clock app(s) reachable during downtime so alarms
  // still go off; a bedtime blocker must never eat your morning alarm.
  bool _clockSeeded = false;

  /// True once we've shown the runtime notification dialog. After that a denial
  /// won't reappear, so the step routes to settings instead.
  bool _notifRequested = false;

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
    // resolves.
    ref.listenManual(installedAppsProvider, (_, _) => _maybeSeedClock());
    _maybeSeedClock();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _otherController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Returning from a system settings screen: re-check so the granted
    // confirmation and the "Continue" button appear. The user taps to move on.
    if (state == AppLifecycleState.resumed) _refreshPerms();
  }

  Future<void> _refreshPerms() async {
    final p = await _engine.getPermissions();
    if (!mounted) return;
    setState(() => _perms = p);
  }

  void _next() {
    if (_index < _order.length - 1) setState(() => _index++);
  }

  void _back() {
    if (_index > 0) setState(() => _index--);
  }

  /// Pre-allow the phone's clock app(s) so alarms stay reachable during
  /// downtime. Runs once, whenever the app list has loaded.
  void _maybeSeedClock() {
    if (_clockSeeded) return;
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
      setState(() {
        _allowlist.addAll(clocks);
        _seededClocks.addAll(clocks);
      });
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
    final name = _nameController.text.trim();
    if (name.isNotEmpty) await _engine.setDisplayName(name);
    ref.invalidate(displayNameProvider);
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
          _Step.schedule => _scheduleStep(),
          _Step.bedtimeMode => _bedtimeModeStep(),
          _Step.apps => _appsStep(),
          _Step.notifications => _notificationsStep(),
          _Step.accessibility => _accessibilityStep(),
          _Step.overlay => _overlayStep(),
          _Step.name => _nameStep(),
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

  /// Reminder (not a Bedrock permission) to pair with Android's Bedtime mode.
  /// Both actions just advance; the primary best-effort deep-links into
  /// Digital Wellbeing.
  Widget _bedtimeModeStep() => _Scaffold(
        onBack: _back,
        title: 'Pair with your\nphone\'s Bedtime\nmode.',
        intro: 'Bedrock blocks the apps. Android\'s Bedtime mode dims and greys '
            'the screen so what\'s left feels calm. They work best together.',
        primaryLabel: 'Open Bedtime settings',
        onPrimary: () {
          _engine.openBedtimeSettings();
          _next();
        },
        secondaryLabel: 'I\'ve set it up',
        onSecondary: _next,
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            _InfoRow(
              Icons.contrast,
              'Greyscale and dimming make your phone boring on purpose - the '
                  'last apps stop pulling you in.',
            ),
            SizedBox(height: 18),
            _InfoRow(
              Icons.bedtime_outlined,
              'Do Not Disturb silences notifications on the same schedule, so '
                  'nothing lights up your night.',
            ),
            SizedBox(height: 18),
            _InfoRow(
              Icons.schedule,
              'Set it to turn on 30 minutes before downtime does. It lives in '
                  'Settings > Digital Wellbeing.',
            ),
          ],
        ),
      );

  Widget _appsStep() {
    final apps = ref.watch(installedAppsProvider);
    final systemAllowed = {
      ...?ref.watch(systemAllowlistProvider).valueOrNull,
      ..._seededClocks,
    };
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
            systemAllowed: systemAllowed,
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
    // Before the dialog has been shown: request it. After a denial it won't
    // reappear, so send the user to notification settings instead.
    final useSettings = _notifRequested;
    return _Scaffold(
      onBack: _back,
      title: 'A nudge before\ndowntime.',
      intro: 'Bedrock sends one reminder five minutes before downtime starts, '
          'and a quiet note while it\'s on. Nothing else.',
      primaryLabel: on
          ? 'Continue'
          : useSettings
              ? 'Open notification settings'
              : 'Allow notifications',
      onPrimary: on
          ? _next
          : useSettings
              ? _engine.openNotificationSettings
              : () async {
                  await _engine.requestNotifications();
                  setState(() => _notifRequested = true);
                  await _refreshPerms();
                },
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
      body: _GrantState(granted: on, grantedLabel: 'Overlay allowed'),
    );
  }

  Widget _nameStep() => _Scaffold(
        onBack: _back,
        title: 'What should we\ncall you?',
        intro: 'We\'ll use it to greet you. It stays on this phone - no account, '
            'nothing uploaded. You can skip this or change it later.',
        primaryLabel: 'Continue',
        onPrimary: _next,
        body: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: TextField(
            controller: _nameController,
            maxLength: 30,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _next(),
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
              hintText: 'Your name',
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
      );

  Widget _confirmStep() {
    final start = _downtimeStart.format(context);
    final wake = _wake.format(context);
    final allowed = _allowlist.length;
    final name = _nameController.text.trim();
    return _Scaffold(
      onBack: _back,
      title: name.isEmpty ? 'You\'re set.' : 'You\'re set, $name.',
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
              : const Padding(
                  // ponytail: brand wordmark shown only where there's no back
                  // button, i.e. the welcome step.
                  padding: EdgeInsets.only(left: 24),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      'Bedrock',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.2,
                        color: BedrockColors.accent,
                      ),
                    ),
                  ),
                ),
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

/// Prominent disclosure for the accessibility step. Play requires this to
/// match what the service actually does, and every claim here is enforced by
/// the code: window content is only ever requested for packages that have a
/// FeedRules entry, and FeedDetector keeps nothing but a fingerprint of view
/// IDs, never text. The app has no network code of its own - the INTERNET
/// permission in the manifest comes from the Play Billing client and is only
/// ever used to talk to Play. Keep in sync with
/// accessibility_service_description.
class _PrivacyNote extends StatelessWidget {
  const _PrivacyNote();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _row(
          Icons.visibility_off_outlined,
          'Bedrock sees which app is in front, and inside the social apps you '
              'pick, whether the screen is an endless feed. It never reads your '
              'messages, captions, or what you type.',
        ),
        const SizedBox(height: 18),
        _row(
          Icons.smartphone_outlined,
          'Everything stays on this phone. Nothing it reads is stored, there\'s '
              'no account and no upload, so no one else can ever see it.',
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

/// An accent-icon + text row used for informational bullet lists.
class _InfoRow extends StatelessWidget {
  const _InfoRow(this.icon, this.text);

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
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
