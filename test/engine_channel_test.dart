import 'dart:convert';

import 'package:bedrock/main.dart';
import 'package:bedrock/src/engine/engine_channel.dart';
import 'package:bedrock/src/engine/engine_models.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

String _activeConfigJson() => jsonEncode({
      'schemaVersion': 1,
      'schedule': {
        for (var day = 1; day <= 7; day++)
          '$day': {'bedMinutes': 23 * 60, 'wakeMinutes': 7 * 60, 'enabled': true},
      },
      'mode': 'NORMAL',
      'windDownMinutes': 30,
      'allowlist': <String>[],
      'alarmEnabled': false,
      'dndEnabled': true,
      'grayscaleEnabled': false,
    });

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('bedrock/engine');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return switch (call.method) {
        'ping' => 'pong',
        'getSessionState' => <Object?, Object?>{
            'state': 'IDLE',
            'blocking': false,
            'plannedBedtime': null,
            'plannedWake': null,
          },
        'getConfig' || 'updateConfig' => <Object?, Object?>{
            'active': _activeConfigJson(),
            'pending': '{}',
          },
        _ => null,
      };
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('ping round-trips over the engine channel', () async {
    expect(await EngineChannel().ping(), 'pong');
  });

  test('getSessionState decodes the wire format', () async {
    final snapshot = await EngineChannel().getSessionState();
    expect(snapshot.state, SessionState.idle);
    expect(snapshot.blocking, isFalse);
    expect(snapshot.plannedWake, isNull);
  });

  test('getConfig decodes the active schedule', () async {
    final view = await EngineChannel().getConfig();
    expect(view.active.schedule.length, 7);
    expect(view.active.schedule[1]!.bedMinutes, 23 * 60);
    expect(view.active.mode, Mode.normal);
    expect(view.hasPendingChanges, isFalse);
  });

  test('updateConfig sends a sparse JSON patch', () async {
    await EngineChannel().updateConfig(
      ConfigPatch(
        schedule: {5: const NightPlan(bedMinutes: 1410, wakeMinutes: 480)},
        mode: Mode.hardcore,
      ),
    );
    final call = calls.singleWhere((c) => c.method == 'updateConfig');
    final patch =
        jsonDecode((call.arguments as Map)['patch'] as String) as Map;
    expect(patch.keys.toSet(), {'schedule', 'mode'});
    expect(patch['mode'], 'HARDCORE');
    expect(patch['schedule'], {
      '5': {'bedMinutes': 1410, 'wakeMinutes': 480, 'enabled': true},
    });
  });

  testWidgets('app boots, shows status and schedule', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: BedrockApp()));
    await tester.pumpAndSettle();
    expect(find.text('Bedrock'), findsOneWidget);
    expect(find.text('Awake'), findsOneWidget);
    expect(find.text('Monday'), findsOneWidget);
    expect(find.text('Bed 23:00'), findsNWidgets(7));
  });
}
