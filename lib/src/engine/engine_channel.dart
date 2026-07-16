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

  /// Debug builds only: run a compressed fake night.
  Future<void> startDemoSession({
    int windDownSeconds = 15,
    int sleepSeconds = 30,
  }) =>
      _method.invokeMethod('startDemoSession', {
        'windDownSeconds': windDownSeconds,
        'sleepSeconds': sleepSeconds,
      });

  /// Debug shortcut: bring up the native night clock directly.
  Future<void> showNightClock({String wakeLabel = '--:--'}) =>
      _method.invokeMethod('showNightClock', {'wakeLabel': wakeLabel});

  Stream<EngineEvent> events() =>
      _events.receiveBroadcastStream().map(EngineEvent.fromWire);
}
