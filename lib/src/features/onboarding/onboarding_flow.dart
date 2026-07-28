import 'dart:async';

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
  why,
  problems,
  promise,
  scheduleIntro,
  schedule,
  bedtimeMode,
  apps,
  feeds,
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
  String? _problem;
  final TextEditingController _nameController = TextEditingController();
  TimeOfDay _bedtime = const TimeOfDay(hour: 23, minute: 0);
  TimeOfDay _wake = const TimeOfDay(hour: 7, minute: 0);
  final Set<String> _allowlist = {};

  /// In-app feed blocking. Always on out of onboarding - it's the reason most
  /// people install Bedrock, and the accessibility step right after depends on
  /// it. The off switch lives in Settings, which the feeds step points at.
  final bool _feedBlocking = true;

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

  /// Set when we hand the user off to a settings screen we can't verify
  /// (Bedtime mode isn't a Bedrock permission). The step advances when they
  /// come back, not when they leave.
  bool _awaitingSettingsReturn = false;

  PermissionStatus? _perms;

  /// Whether the current step's typed-out header has finished. Gates that
  /// step's primary button; tapping where the button will be sets it early and
  /// snaps the text to full.
  bool _typedDone = false;

  EngineChannel get _engine => ref.read(engineChannelProvider);

  _Step get _step => _order[_index];

  int get _windDown =>
      ref.read(configProvider).valueOrNull?.active.windDownMinutes ?? 30;

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
    _nameController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Returning from a system settings screen: re-check so the granted
    // confirmation and the "Continue" button appear. The user taps to move on.
    if (state != AppLifecycleState.resumed) return;
    _refreshPerms();
    if (_awaitingSettingsReturn) {
      _awaitingSettingsReturn = false;
      if (_step == _Step.bedtimeMode) _next();
    }
  }

  Future<void> _refreshPerms() async {
    final p = await _engine.getPermissions();
    if (!mounted) return;
    setState(() => _perms = p);
  }

  void _next() {
    if (_index < _order.length - 1) {
      setState(() {
        _index++;
        _typedDone = false;
      });
    }
  }

  void _back() {
    if (_index > 0) {
      setState(() {
        _index--;
        _typedDone = false;
      });
    }
  }

  void _finishTyping() {
    if (!_typedDone) setState(() => _typedDone = true);
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
    await _engine.setFeedBlocking(_feedBlocking);
    ref.invalidate(feedBlockingProvider);
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
          _Step.why => _whyStep(),
          _Step.problems => _problemsStep(),
          _Step.promise => _promiseStep(),
          _Step.scheduleIntro => _scheduleIntroStep(),
          _Step.schedule => _scheduleStep(),
          _Step.bedtimeMode => _bedtimeModeStep(),
          _Step.apps => _appsStep(),
          _Step.feeds => _feedsStep(),
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
        revealed: _typedDone,
        onReveal: _finishTyping,
        header: _TypedLines(
          key: const ValueKey(_Step.welcome),
          gap: 22,
          skip: _typedDone,
          onDone: _finishTyping,
          lines: const [
            (
              'Bedrock',
              TextStyle(
                fontSize: 60,
                height: 1.05,
                fontWeight: FontWeight.w800,
                letterSpacing: -2,
                color: BedrockColors.onSurface,
              ),
            ),
            (
              'Escape the algorithms, get back to enjoying life.',
              TextStyle(
                fontSize: 21,
                height: 1.4,
                color: BedrockColors.onSurfaceMuted,
              ),
            ),
            (
              'No sign-up. No ads. No data collected.',
              TextStyle(
                fontSize: 15,
                height: 1.4,
                fontWeight: FontWeight.w600,
                color: BedrockColors.accent,
              ),
            ),
          ],
        ),
        primaryLabel: 'Get started',
        onPrimary: _next,
        body: const SizedBox.shrink(),
      );

  Widget _whyStep() => _Scaffold(
        onBack: _back,
        revealed: _typedDone,
        onReveal: _finishTyping,
        header: _TypedLines(
          key: const ValueKey(_Step.why),
          skip: _typedDone,
          onDone: _finishTyping,
          lines: const [
            ('Social media is designed to be addictive.', _lead),
            (
              'It\'s not your fault. But they will keep trying to pull you '
                  'back.',
              _lead,
            ),
            ('We help you quit.', _leadStrong),
          ],
        ),
        primaryLabel: 'Continue',
        onPrimary: _next,
        body: const SizedBox.shrink(),
      );

  Widget _problemsStep() => _Scaffold(
        onBack: _back,
        title: 'What matters\nmost to you?',
        intro: 'Pick the one that fits best. This just sets the tone - your '
            'setup is yours to change anytime.',
        primaryLabel: 'Continue',
        onPrimary: _problem == null ? null : _next,
        body: SettingGroup(
          rows: [
            for (final o in _promises.keys)
              SettingRow(
                title: o,
                onTap: () => setState(() => _problem = o),
                trailing: _problem == o
                    ? const Icon(Icons.check,
                        color: BedrockColors.accent, size: 22)
                    : const SizedBox(width: 22),
              ),
          ],
        ),
      );

  /// Reflects the pick back at the user as what Bedrock will actually do about
  /// it. Deliberately no typing animation - by now they want to get on with it.
  Widget _promiseStep() {
    final (title, intro) = _promises[_problem]!;
    return _Scaffold(
      onBack: _back,
      title: title,
      intro: intro,
      primaryLabel: 'Let\'s set it up',
      onPrimary: _next,
      body: const SizedBox.shrink(),
    );
  }

  Widget _scheduleIntroStep() => _Scaffold(
        onBack: _back,
        revealed: _typedDone,
        onReveal: _finishTyping,
        tapToContinue: true,
        header: _TypedLines(
          key: const ValueKey(_Step.scheduleIntro),
          skip: _typedDone,
          onDone: _finishTyping,
          lines: const [
            ('Let\'s set up your bedtime blocker.', _lead),
            ('So you never stay up late doomscrolling again.', _leadStrong),
          ],
        ),
        primaryLabel: 'Continue',
        onPrimary: _next,
        body: const SizedBox.shrink(),
      );

  Widget _scheduleStep() {
    return _Scaffold(
      onBack: _back,
      title: 'When is\nbedtime?',
      intro: 'Downtime starts half an hour before bed and lifts when you wake. '
          'While it runs, your apps stop opening - all except the essentials '
          'you pick in a moment, and your clock is already one of them. All of '
          'it is editable later.',
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
        title: 'Pair with your\ndevice\'s Bedtime\nmode.',
        intro: 'Bedrock blocks the apps. Your device\'s Bedtime mode dims and '
            'greys the screen so what\'s left feels calm. They work best '
            'together.',
        primaryLabel: 'Open Bedtime settings',
        onPrimary: () {
          _awaitingSettingsReturn = true;
          _engine.openBedtimeSettings();
        },
        secondaryLabel: 'Set up later',
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
      intro: 'This list is only about downtime. While it runs, everything is '
          'blocked except phone, messages, and whatever you allow here. Most '
          'people keep it short.',
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

  Widget _feedsStep() => _Scaffold(
        onBack: _back,
        revealed: _typedDone,
        onReveal: _finishTyping,
        header: _TypedLines(
          key: const ValueKey(_Step.feeds),
          skip: _typedDone,
          onDone: _finishTyping,
          lines: const [
            ('Reels and Shorts are blocked automatically.', _lead),
            (
              'All day, not just at bedtime. Your DMs, search and messages keep '
                  'working.',
              _lead,
            ),
            ('Not for you? Turn it off in Settings.', _leadStrong),
          ],
        ),
        primaryLabel: 'Continue',
        onPrimary: _next,
        body: const SizedBox.shrink(),
      );

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
    // Specifically the accessibility service, not [foregroundDetection]: usage
    // access can name the app in front but can't see a screen's layout, so it
    // can't block feeds, which onboarding has already promised.
    final on = _perms?.accessibility ?? false;
    return _Scaffold(
      onBack: _back,
      title: 'Let Bedrock see\nwhat\'s open.',
      intro: 'To block an app, Bedrock needs to know which app just came to the '
          'front. To block a feed without breaking the rest of the app, it '
          'needs to see the shape of the screen. Turn it on under Accessibility '
          'on the next screen.',
      primaryLabel: on ? 'Continue' : 'Open Accessibility settings',
      onPrimary: on ? _next : _engine.openAccessibilitySettings,
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
        primaryLabel: 'Continue',
        onPrimary: _next,
        secondaryLabel: 'Skip',
        onSecondary: () {
          _nameController.clear();
          _next();
        },
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
          'while it\'s on. '
          'Feeds stay blocked around the clock. '
          'Change any of this anytime in Settings.',
      primaryLabel: 'Turn on Bedrock',
      onPrimary: _finish,
      body: const SizedBox.shrink(),
    );
  }
}

/// Shared editorial step chrome: an optional back affordance, a big quiet title,
/// intro copy, a scrollable body, and one or two bottom actions. Steps that
/// want their own headline (the typed-out intro pages) pass [header] instead of
/// [title]/[intro].
class _Scaffold extends StatelessWidget {
  const _Scaffold({
    required this.body,
    required this.primaryLabel,
    required this.onPrimary,
    this.title,
    this.header,
    this.intro,
    this.secondaryLabel,
    this.onSecondary,
    this.onBack,
    this.revealed = true,
    this.onReveal,
    this.tapToContinue = false,
  }) : assert(title != null || header != null);

  final String? title;
  final Widget? header;
  final String? intro;
  final Widget body;
  final String primaryLabel;
  final VoidCallback? onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;
  final VoidCallback? onBack;

  /// False while a typed-out header is still animating: the primary button is
  /// invisible and its footprint instead calls [onReveal], so an impatient tap
  /// where the button is about to appear skips the typing.
  final bool revealed;
  final VoidCallback? onReveal;

  /// Keeps the primary button invisible even once [revealed] and makes the
  /// whole page fire [onPrimary] instead. The button stays in the tree, so it
  /// still reserves its space and still exists for screen readers.
  final bool tapToContinue;

  @override
  Widget build(BuildContext context) {
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: 48,
          child: onBack == null
              ? null
              : Align(
                  alignment: Alignment.centerLeft,
                  child: IconButton(
                    onPressed: onBack,
                    icon: const Icon(Icons.arrow_back,
                        color: BedrockColors.onSurfaceMuted),
                  ),
                ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
            children: [
              if (header != null)
                header!
              else
                Text(
                  title!,
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
              GestureDetector(
                behavior: revealed
                    ? HitTestBehavior.deferToChild
                    : HitTestBehavior.opaque,
                onTap: revealed ? null : onReveal,
                child: AnimatedOpacity(
                  opacity: revealed && !tapToContinue ? 1 : 0,
                  duration: const Duration(milliseconds: 200),
                  child: ExcludeSemantics(
                    excluding: !revealed,
                    child: IgnorePointer(
                      ignoring: !revealed,
                      child: FilledButton(
                        onPressed: onPrimary,
                        child: Text(primaryLabel),
                      ),
                    ),
                  ),
                ),
              ),
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
    if (!tapToContinue) return content;
    // Tap anywhere to move on - but only once the text is done, so the first
    // impatient tap lands on the button's footprint and skips the typing.
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: revealed ? onPrimary : null,
      child: content,
    );
  }
}

/// The "what matters most" options, each paired with the (title, body) of the
/// screen right after it. One map so an option can never exist without the
/// answer screen that follows it. Key order is the order they're listed in.
const _promises = <String, (String, String)>{
  'Stop doomscrolling': (
    'No more\nrabbit holes.',
    'Bedrock cuts the infinite feeds out of the apps you keep - Reels, Shorts, '
        'the home feed. Messages and search still work, so you can do the thing '
        'you opened the app for and then put it down.',
  ),
  'Get my attention back': (
    'Your focus,\nreturned.',
    'The endless feed is the part engineered to hold you, so that is the part '
        'Bedrock takes away. The apps you pick also stop opening during your '
        'downtime window, so there is nothing to fall into.',
  ),
  'Sleep without scrolling': (
    'Nights without\nthe feed.',
    'Choose a downtime window and the apps to keep out of it. When it starts '
        'they stop opening, and your alarm still goes off in the morning - so '
        'none of it rides on your willpower at 1 AM.',
  ),
  'Protect my mental health': (
    'Less noise,\nmore you.',
    'Most of what a feed shows you is not from anyone you know, and it is '
        'picked to keep you comparing. Bedrock blocks those feeds and leaves '
        'the parts you actually chose: your DMs and search.',
  ),
  'Quit one app for good': (
    'One app,\nhandled.',
    'Pick it in a moment. Bedrock keeps it shut through your downtime window '
        'and strips its feed the rest of the time. Getting back in takes real '
        'effort, which is the entire point.',
  ),
};

const _lead = TextStyle(
  fontSize: 26,
  height: 1.3,
  fontWeight: FontWeight.w600,
  letterSpacing: -0.4,
  color: BedrockColors.onSurface,
);

const _leadStrong = TextStyle(
  fontSize: 26,
  height: 1.3,
  fontWeight: FontWeight.w700,
  letterSpacing: -0.4,
  color: BedrockColors.accent,
);

/// Types each line out a character at a time, one line after the next. The full
/// text is laid out invisibly underneath so nothing reflows as it appears.
class _TypedLines extends StatefulWidget {
  /// Pass a distinct [key] per step: consecutive steps put this widget at the
  /// same spot in the tree, and without one the next step inherits the previous
  /// step's typing progress.
  const _TypedLines({
    super.key,
    required this.lines,
    this.gap = 18,
    this.skip = false,
    this.onDone,
  });

  static const _speed = Duration(milliseconds: 28);
  static const _pause = Duration(milliseconds: 420);

  final List<(String, TextStyle)> lines;
  final double gap;

  /// Set by the parent once the user has asked to skip ahead: show everything.
  final bool skip;

  /// Fires once, when every line is on screen (including when it got there by
  /// [skip] or because the platform has animations turned off).
  final VoidCallback? onDone;

  @override
  State<_TypedLines> createState() => _TypedLinesState();
}

class _TypedLinesState extends State<_TypedLines> {
  int _line = 0;
  int _chars = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(_TypedLines._pause, _tick);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Respect "remove animations": everything is already on screen, so report
    // done straight away (after this frame - onDone rebuilds the parent).
    if (MediaQuery.disableAnimationsOf(context)) {
      _timer?.cancel();
      WidgetsBinding.instance
          .addPostFrameCallback((_) => widget.onDone?.call());
    }
  }

  @override
  void didUpdateWidget(_TypedLines oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.skip) _timer?.cancel();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _tick() {
    if (_chars < widget.lines[_line].$1.length) {
      setState(() => _chars++);
      _timer = Timer(_TypedLines._speed, _tick);
    } else if (_line < widget.lines.length - 1) {
      setState(() {
        _line++;
        _chars = 0;
      });
      _timer = Timer(_TypedLines._pause, _tick);
    } else {
      widget.onDone?.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    final instant = widget.skip || MediaQuery.disableAnimationsOf(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < widget.lines.length; i++) ...[
          if (i > 0) SizedBox(height: widget.gap),
          _lineAt(i, instant),
        ],
      ],
    );
  }

  Widget _lineAt(int i, bool instant) {
    final (text, style) = widget.lines[i];
    final shown = instant || i < _line
        ? text
        : i == _line
            ? text.substring(0, _chars)
            : '';
    return Stack(
      children: [
        Opacity(opacity: 0, child: Text(text, style: style)),
        Text(shown, style: style),
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
          'Bedrock sees which app is in front. In Instagram and YouTube it also '
              'checks the shape of the screen - just enough to tell a feed from '
              'your messages. It never reads your messages, captions, or what '
              'you type.',
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
              'standard wording for this permission. Bedrock uses a sliver of '
              'it: which app is open, and the layout inside those two apps.',
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
