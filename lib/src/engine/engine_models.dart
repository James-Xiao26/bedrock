/// Models mirroring the native engine's state. The Kotlin engine is the
/// single source of truth; these are read-only projections plus patch
/// builders. JSON shapes must match the kotlinx-serialization schema in
/// android/.../engine/Config.kt.
library;

import 'dart:convert';
import 'dart:typed_data';

enum SessionState {
  idle,
  active;

  static SessionState fromWire(String value) => switch (value) {
        'IDLE' => SessionState.idle,
        'ACTIVE' => SessionState.active,
        _ => throw ArgumentError('Unknown session state: $value'),
      };
}

class SessionSnapshot {
  const SessionSnapshot({
    required this.state,
    required this.blocking,
    this.windowOpen,
    this.windowClose,
  });

  final SessionState state;
  final bool blocking;
  final DateTime? windowOpen;
  final DateTime? windowClose;

  factory SessionSnapshot.fromWire(Map<Object?, Object?> wire) {
    DateTime? epoch(Object? v) => v == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch((v as num).toInt());
    return SessionSnapshot(
      state: SessionState.fromWire(wire['state'] as String),
      blocking: wire['blocking'] as bool? ?? false,
      windowOpen: epoch(wire['windowOpen']),
      windowClose: epoch(wire['windowClose']),
    );
  }
}

class NightPlan {
  const NightPlan({
    required this.bedMinutes,
    required this.wakeMinutes,
    this.enabled = true,
  });

  /// Minutes since midnight. [bedMinutes] is the window start, [wakeMinutes]
  /// the end; a start before noon means "past midnight".
  final int bedMinutes;
  final int wakeMinutes;
  final bool enabled;

  NightPlan copyWith({int? bedMinutes, int? wakeMinutes, bool? enabled}) =>
      NightPlan(
        bedMinutes: bedMinutes ?? this.bedMinutes,
        wakeMinutes: wakeMinutes ?? this.wakeMinutes,
        enabled: enabled ?? this.enabled,
      );

  Map<String, Object?> toJson() => {
        'bedMinutes': bedMinutes,
        'wakeMinutes': wakeMinutes,
        'enabled': enabled,
      };

  factory NightPlan.fromJson(Map<String, Object?> json) => NightPlan(
        bedMinutes: json['bedMinutes'] as int,
        wakeMinutes: json['wakeMinutes'] as int,
        enabled: json['enabled'] as bool? ?? true,
      );

  @override
  bool operator ==(Object other) =>
      other is NightPlan &&
      other.bedMinutes == bedMinutes &&
      other.wakeMinutes == wakeMinutes &&
      other.enabled == enabled;

  @override
  int get hashCode => Object.hash(bedMinutes, wakeMinutes, enabled);
}

/// One launchable app for the Always Allowed picker.
class InstalledApp {
  const InstalledApp({
    required this.packageName,
    required this.label,
    required this.icon,
  });

  final String packageName;
  final String label;
  final Uint8List icon;

  factory InstalledApp.fromWire(Map<Object?, Object?> wire) => InstalledApp(
        packageName: wire['package'] as String,
        label: wire['label'] as String,
        icon: base64Decode(wire['icon'] as String),
      );
}

class EngineConfig {
  const EngineConfig({
    required this.schedule,
    required this.allowlist,
  });

  /// ISO day-of-week (1=Mon..7=Sun) -> the window starting that day.
  final Map<int, NightPlan> schedule;
  final Set<String> allowlist;

  factory EngineConfig.fromJsonString(String raw) {
    final json = jsonDecode(raw) as Map<String, Object?>;
    return EngineConfig(
      schedule: (json['schedule'] as Map<String, Object?>).map(
        (day, plan) => MapEntry(
          int.parse(day),
          NightPlan.fromJson(plan as Map<String, Object?>),
        ),
      ),
      allowlist:
          (json['allowlist'] as List<Object?>? ?? []).cast<String>().toSet(),
    );
  }
}

/// The passcode as the engine chooses to expose it: [password] is null
/// whenever the code is hidden (past the cutoff, or during an active window).
class HardcorePasswordView {
  const HardcorePasswordView({required this.viewable, this.password});

  final bool viewable;
  final String? password;

  factory HardcorePasswordView.fromWire(Map<Object?, Object?> wire) =>
      HardcorePasswordView(
        viewable: wire['viewable'] as bool? ?? false,
        password: wire['password'] as String?,
      );
}

/// Sparse change request; only non-null fields are sent. The native
/// ChangeClassifier decides what applies now versus at the next window.
class ConfigPatch {
  const ConfigPatch({
    this.schedule,
    this.allowlist,
  });

  final Map<int, NightPlan>? schedule;
  final Set<String>? allowlist;

  String toJsonString() => jsonEncode({
        if (schedule != null)
          'schedule':
              schedule!.map((day, plan) => MapEntry('$day', plan.toJson())),
        if (allowlist != null) 'allowlist': allowlist!.toList(),
      });
}

class ConfigView {
  const ConfigView({required this.active, required this.pendingRaw});

  final EngineConfig active;

  /// Raw pending patch JSON; non-empty (not '{}') means loosening changes
  /// are waiting for the next window.
  final String pendingRaw;

  bool get hasPendingChanges => pendingRaw.trim() != '{}';

  factory ConfigView.fromWire(Map<Object?, Object?> wire) => ConfigView(
        active: EngineConfig.fromJsonString(wire['active'] as String),
        pendingRaw: wire['pending'] as String,
      );
}

/// How a recorded window ended. Mirrors WindowOutcome in the Kotlin engine.
enum WindowOutcome {
  clean,
  unlocked,
  violated;

  static WindowOutcome fromWire(String value) =>
      WindowOutcome.values.byName(value.toLowerCase());
}

/// One recorded window, for the recent-windows strip.
class RecentWindow {
  const RecentWindow({required this.windowKey, required this.outcome});

  /// ISO yyyy-MM-dd of the day the window started on.
  final String windowKey;
  final WindowOutcome outcome;

  factory RecentWindow.fromWire(Map<Object?, Object?> wire) => RecentWindow(
        windowKey: wire['windowKey'] as String,
        outcome: WindowOutcome.fromWire(wire['outcome'] as String),
      );
}

/// Read-only projection of local usage stats. The engine does the counting.
class StatsView {
  const StatsView({
    required this.currentStreak,
    required this.windowsKept,
    required this.totalWindows,
    required this.recent,
  });

  /// Consecutive clean windows ending at the most recent recorded window.
  final int currentStreak;
  final int windowsKept;
  final int totalWindows;
  final List<RecentWindow> recent;

  factory StatsView.fromWire(Map<Object?, Object?> wire) => StatsView(
        currentStreak: (wire['currentStreak'] as num?)?.toInt() ?? 0,
        windowsKept: (wire['windowsKept'] as num?)?.toInt() ?? 0,
        totalWindows: (wire['totalWindows'] as num?)?.toInt() ?? 0,
        recent: (wire['recent'] as List<Object?>? ?? const [])
            .map((e) => RecentWindow.fromWire(e as Map<Object?, Object?>))
            .toList(),
      );
}

class EngineEvent {
  const EngineEvent({required this.name, required this.payload});

  final String name;
  final Map<Object?, Object?> payload;

  factory EngineEvent.fromWire(Object? wire) {
    final map = wire as Map<Object?, Object?>;
    return EngineEvent(
      name: map['event'] as String,
      payload: (map['payload'] as Map<Object?, Object?>?) ?? const {},
    );
  }
}
