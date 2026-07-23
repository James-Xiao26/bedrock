import 'dart:convert';

import 'package:bedrock/main.dart';
import 'package:bedrock/src/engine/engine_channel.dart';
import 'package:bedrock/src/engine/engine_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

String _activeConfigJson() => jsonEncode({
      'schemaVersion': 3,
      'schedule': {
        for (var day = 1; day <= 7; day++)
          '$day': {
            'bedtimeMinutes': 23 * 60,
            'wakeMinutes': 7 * 60,
            'enabled': true,
          },
      },
      'windDownMinutes': 60,
      'allowlist': <String>[],
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
            'windowOpen': null,
            'windowClose': null,
          },
        'getConfig' || 'updateConfig' => <Object?, Object?>{
            'active': _activeConfigJson(),
            'pending': '{}',
          },
        'getStats' => <Object?, Object?>{
            'currentStreak': 0,
            'windowsKept': 0,
            'totalWindows': 0,
            'recent': <Object?>[],
          },
        'getHardcorePassword' || 'regenerateHardcorePassword' =>
          <Object?, Object?>{'viewable': false, 'password': null},
        'isOnboarded' => true,
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
    expect(snapshot.windowClose, isNull);
  });

  test('getConfig decodes the active schedule', () async {
    final view = await EngineChannel().getConfig();
    expect(view.active.schedule.length, 7);
    expect(view.active.schedule[1]!.bedtimeMinutes, 23 * 60);
    expect(view.hasPendingChanges, isFalse);
  });

  test('updateConfig sends a sparse JSON patch', () async {
    await EngineChannel().updateConfig(
      ConfigPatch(
        schedule: {5: const NightPlan(bedtimeMinutes: 1410, wakeMinutes: 480)},
      ),
    );
    final call = calls.singleWhere((c) => c.method == 'updateConfig');
    final patch =
        jsonDecode((call.arguments as Map)['patch'] as String) as Map;
    expect(patch.keys.toSet(), {'schedule'});
    expect(patch['schedule'], {
      '5': {'bedtimeMinutes': 1410, 'wakeMinutes': 480, 'enabled': true},
    });
  });

  testWidgets('app boots into the single-page home', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: BedrockApp()));
    await tester.pumpAndSettle();
    // Today status reflects the IDLE session.
    expect(find.text('Apps unblocked'), findsOneWidget);
    // Schedule and Stats are collapsible section headers, not tabs.
    expect(find.text('Downtime'), findsOneWidget);
    expect(find.text('Stats'), findsOneWidget);
    // Settings lives behind a gear icon in the top-right.
    expect(find.byIcon(Icons.settings_outlined), findsOneWidget);
  });

  testWidgets('expanding Downtime shows the editor in Every Day mode',
      (tester) async {
    // Tall surface so the whole editor fits without scroll gymnastics.
    await tester.binding.setSurfaceSize(const Size(1000, 2400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const ProviderScope(child: BedrockApp()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Downtime'));
    await tester.pumpAndSettle();
    // Every Day/Customize selector, defaulting to the shared Start/End window
    // (all seven days share one window).
    expect(find.text('Every Day'), findsOneWidget);
    expect(find.text('Customize Days'), findsOneWidget);
    expect(find.text('Start'), findsOneWidget);
    expect(find.text('End'), findsOneWidget);
    // Per-day rows only appear after switching to Customize.
    expect(find.text('Monday'), findsNothing);
    await tester.tap(find.text('Customize Days'));
    await tester.pumpAndSettle();
    expect(find.text('Monday'), findsOneWidget);
    expect(find.text('Saturday'), findsOneWidget);
  });
}
