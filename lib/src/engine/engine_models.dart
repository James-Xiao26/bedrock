/// Models mirroring the native engine's state. The Kotlin engine is the
/// single source of truth; these are read-only projections plus patch
/// builders. JSON shapes must match the kotlinx-serialization schema in
/// android/.../engine/Config.kt.
library;

import 'dart:convert';

enum SessionState {
  idle,
  winddown,
  locked,
  escaped,
  bypassed,
  wakeAlarm;

  static SessionState fromWire(String value) => switch (value) {
        'IDLE' => SessionState.idle,
        'WINDDOWN' => SessionState.winddown,
        'LOCKED' => SessionState.locked,
        'ESCAPED' => SessionState.escaped,
        'BYPASSED' => SessionState.bypassed,
        'WAKE_ALARM' => SessionState.wakeAlarm,
        _ => throw ArgumentError('Unknown session state: $value'),
      };
}

enum Mode {
  normal,
  hardcore;

  String toWire() => name.toUpperCase();

  static Mode fromWire(String value) => Mode.values.byName(value.toLowerCase());
}

class SessionSnapshot {
  const SessionSnapshot({
    required this.state,
    required this.blocking,
    this.plannedBedtime,
    this.plannedWake,
  });

  final SessionState state;
  final bool blocking;
  final DateTime? plannedBedtime;
  final DateTime? plannedWake;

  factory SessionSnapshot.fromWire(Map<Object?, Object?> wire) {
    DateTime? epoch(Object? v) => v == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch((v as num).toInt());
    return SessionSnapshot(
      state: SessionState.fromWire(wire['state'] as String),
      blocking: wire['blocking'] as bool? ?? false,
      plannedBedtime: epoch(wire['plannedBedtime']),
      plannedWake: epoch(wire['plannedWake']),
    );
  }
}

class NightPlan {
  const NightPlan({
    required this.bedMinutes,
    required this.wakeMinutes,
    this.enabled = true,
  });

  /// Minutes since midnight; bedtimes before noon mean "past midnight".
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

class EngineConfig {
  const EngineConfig({
    required this.schedule,
    required this.mode,
    required this.windDownMinutes,
    required this.allowlist,
    required this.alarmEnabled,
    required this.dndEnabled,
    required this.grayscaleEnabled,
  });

  /// ISO day-of-week (1=Mon..7=Sun) -> plan for the night starting that day.
  final Map<int, NightPlan> schedule;
  final Mode mode;
  final int windDownMinutes;
  final Set<String> allowlist;
  final bool alarmEnabled;
  final bool dndEnabled;
  final bool grayscaleEnabled;

  factory EngineConfig.fromJsonString(String raw) {
    final json = jsonDecode(raw) as Map<String, Object?>;
    return EngineConfig(
      schedule: (json['schedule'] as Map<String, Object?>).map(
        (day, plan) => MapEntry(
          int.parse(day),
          NightPlan.fromJson(plan as Map<String, Object?>),
        ),
      ),
      mode: Mode.fromWire(json['mode'] as String? ?? 'NORMAL'),
      windDownMinutes: json['windDownMinutes'] as int? ?? 30,
      allowlist:
          (json['allowlist'] as List<Object?>? ?? []).cast<String>().toSet(),
      alarmEnabled: json['alarmEnabled'] as bool? ?? false,
      dndEnabled: json['dndEnabled'] as bool? ?? true,
      grayscaleEnabled: json['grayscaleEnabled'] as bool? ?? false,
    );
  }
}

/// Sparse change request; only non-null fields are sent. The native
/// ChangeClassifier decides what applies now versus tomorrow morning.
class ConfigPatch {
  const ConfigPatch({
    this.schedule,
    this.mode,
    this.windDownMinutes,
    this.allowlist,
    this.alarmEnabled,
    this.dndEnabled,
    this.grayscaleEnabled,
  });

  final Map<int, NightPlan>? schedule;
  final Mode? mode;
  final int? windDownMinutes;
  final Set<String>? allowlist;
  final bool? alarmEnabled;
  final bool? dndEnabled;
  final bool? grayscaleEnabled;

  String toJsonString() => jsonEncode({
        if (schedule != null)
          'schedule':
              schedule!.map((day, plan) => MapEntry('$day', plan.toJson())),
        if (mode != null) 'mode': mode!.toWire(),
        if (windDownMinutes != null) 'windDownMinutes': windDownMinutes,
        if (allowlist != null) 'allowlist': allowlist!.toList(),
        if (alarmEnabled != null) 'alarmEnabled': alarmEnabled,
        if (dndEnabled != null) 'dndEnabled': dndEnabled,
        if (grayscaleEnabled != null) 'grayscaleEnabled': grayscaleEnabled,
      });
}

class ConfigView {
  const ConfigView({required this.active, required this.pendingRaw});

  final EngineConfig active;

  /// Raw pending patch JSON; non-empty (not '{}') means loosening changes
  /// are waiting for morning.
  final String pendingRaw;

  bool get hasPendingChanges => pendingRaw.trim() != '{}';

  factory ConfigView.fromWire(Map<Object?, Object?> wire) => ConfigView(
        active: EngineConfig.fromJsonString(wire['active'] as String),
        pendingRaw: wire['pending'] as String,
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
