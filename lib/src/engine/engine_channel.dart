import 'package:flutter/services.dart';

import 'engine_models.dart';

/// The only code in the app that touches platform channels.
/// Everything the user does while awake goes through here; everything that
/// must be true at 3 AM lives natively on the other side.
class EngineChannel {
  EngineChannel({MethodChannel? methodChannel})
      : _method = methodChannel ?? const MethodChannel('bedrock/engine');

  final MethodChannel _method;
  static const _events = EventChannel('bedrock/events');

  Future<String> ping() async => await _method.invokeMethod<String>('ping') ?? '';

  Future<SessionSnapshot> getSessionState() async {
    final wire =
        await _method.invokeMethod<Map<Object?, Object?>>('getSessionState');
    return SessionSnapshot.fromWire(wire!);
  }

  Future<ConfigView> getConfig() async {
    final wire = await _method.invokeMethod<Map<Object?, Object?>>('getConfig');
    return ConfigView.fromWire(wire!);
  }

  /// Sends a change request; the native freeze rules decide whether it
  /// applies now or tomorrow morning. Returns the resulting view.
  Future<ConfigView> updateConfig(ConfigPatch patch) async {
    final wire = await _method.invokeMethod<Map<Object?, Object?>>(
      'updateConfig',
      {'patch': patch.toJsonString()},
    );
    return ConfigView.fromWire(wire!);
  }

  /// Current state of the four permissions Bedrock needs.
  Future<PermissionStatus> getPermissions() async {
    final wire =
        await _method.invokeMethod<Map<Object?, Object?>>('getPermissions');
    return PermissionStatus.fromWire(wire ?? const {});
  }

  /// Show the POST_NOTIFICATIONS dialog; returns whether it's granted after.
  /// Immediate true on Android < 13, where notifications are on by default.
  Future<bool> requestNotifications() async =>
      await _method.invokeMethod<bool>('requestNotifications') ?? false;

  /// Toggle on-demand downtime. Only acts outside a scheduled window: starts
  /// blocking now, or ends a running manual session. No-op otherwise.
  Future<void> setManualDowntime(bool on) =>
      _method.invokeMethod('setManualDowntime', {'on': on});

  /// Whether in-app feed blocking is on. Runs independently of downtime
  /// windows: when on, feeds inside the watched social apps stay blocked
  /// around the clock while the rest of each app keeps working.
  Future<bool> getFeedBlocking() async =>
      await _method.invokeMethod<bool>('getFeedBlocking') ?? false;

  Future<void> setFeedBlocking(bool on) =>
      _method.invokeMethod('setFeedBlocking', {'on': on});

  /// Debug builds only: the view IDs on screen in the app currently in front,
  /// for capturing feed fingerprints from real installed versions.
  Future<List<String>> dumpWindowIds() async {
    final wire = await _method.invokeMethod<List<Object?>>('dumpWindowIds');
    return (wire ?? const []).map((e) => e as String).toList();
  }

  Future<void> openAccessibilitySettings() =>
      _method.invokeMethod('openAccessibilitySettings');

  Future<void> openUsageAccessSettings() =>
      _method.invokeMethod('openUsageAccessSettings');

  Future<void> openOverlaySettings() =>
      _method.invokeMethod('openOverlaySettings');

  /// App notification settings; the fallback when the runtime dialog no longer
  /// appears (notifications permanently denied).
  Future<void> openNotificationSettings() =>
      _method.invokeMethod('openNotificationSettings');

  /// Best-effort open of Android's Bedtime mode (inside Digital Wellbeing).
  /// No public intent targets the exact screen, so this may land on Digital
  /// Wellbeing or the top-level Settings.
  Future<void> openBedtimeSettings() =>
      _method.invokeMethod('openBedtimeSettings');

  /// Whether first-run onboarding has been completed.
  Future<bool> isOnboarded() async =>
      await _method.invokeMethod<bool>('isOnboarded') ?? false;

  Future<void> markOnboarded() => _method.invokeMethod('markOnboarded');

  /// The user's chosen display name, or null if unset. Cosmetic only, stored
  /// on-device.
  Future<String?> getDisplayName() =>
      _method.invokeMethod<String>('getDisplayName');

  Future<void> setDisplayName(String name) =>
      _method.invokeMethod('setDisplayName', {'name': name});

  /// Seconds the user must hold the $1 bypass button to reveal the free reset.
  /// Native floors this at 10s; default is 15s.
  Future<int> getBypassHoldSeconds() async =>
      await _method.invokeMethod<int>('getBypassHoldSeconds') ?? 15;

  Future<void> setBypassHoldSeconds(int seconds) =>
      _method.invokeMethod('setBypassHoldSeconds', {'seconds': seconds});

  /// Every launchable user app (label + icon), for the Always Allowed picker.
  Future<List<InstalledApp>> getInstalledApps() async {
    final wire =
        await _method.invokeMethod<List<Object?>>('getInstalledApps');
    return (wire ?? const [])
        .map((e) => InstalledApp.fromWire(e as Map<Object?, Object?>))
        .toList();
  }

  /// Packages the blocker always allows regardless of the user's list (dialer,
  /// launcher, Settings, Play Store, Bedrock). Shown non-removable atop the picker.
  Future<Set<String>> getSystemAllowlist() async {
    final wire = await _method.invokeMethod<List<Object?>>('getSystemAllowlist');
    return (wire ?? const []).map((e) => e as String).toSet();
  }

  /// Aggregated local sleep stats (streak, totals, recent nights).
  Future<StatsView> getStats() async {
    final wire = await _method.invokeMethod<Map<Object?, Object?>>('getStats');
    return StatsView.fromWire(wire!);
  }

  /// The hardcore escape code, if the engine will currently reveal it.
  /// Hidden (password == null) only while a window is actively blocking.
  Future<HardcorePasswordView> getHardcorePassword() async {
    final wire = await _method
        .invokeMethod<Map<Object?, Object?>>('getHardcorePassword');
    return HardcorePasswordView.fromWire(wire!);
  }

  Stream<EngineEvent> events() =>
      _events.receiveBroadcastStream().map(EngineEvent.fromWire);
}
