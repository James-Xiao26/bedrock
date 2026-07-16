import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'engine_channel.dart';
import 'engine_models.dart';

final engineChannelProvider = Provider<EngineChannel>((ref) => EngineChannel());

final engineEventsProvider = StreamProvider<EngineEvent>(
  (ref) => ref.watch(engineChannelProvider).events(),
);

/// Latest session snapshot; refreshed on engine events. Dart never assumes
/// the event stream was alive since boot - screens should also re-read on
/// resume.
final sessionStateProvider = FutureProvider<SessionSnapshot>((ref) async {
  ref.listen(engineEventsProvider, (_, next) {
    if (next.valueOrNull?.name == 'sessionStateChanged') {
      ref.invalidateSelf();
    }
  });
  return ref.watch(engineChannelProvider).getSessionState();
});

/// Active + pending config; refreshed whenever the engine reports a change
/// (including the morning merge).
final configProvider = FutureProvider<ConfigView>((ref) async {
  ref.listen(engineEventsProvider, (_, next) {
    if (next.valueOrNull?.name == 'configChanged') {
      ref.invalidateSelf();
    }
  });
  return ref.watch(engineChannelProvider).getConfig();
});
