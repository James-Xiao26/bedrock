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

/// The user's display name (null if unset). Invalidated after a Settings edit.
final displayNameProvider = FutureProvider<String?>(
  (ref) => ref.watch(engineChannelProvider).getDisplayName(),
);

/// Installed launchable apps for the Always Allowed picker. Loaded once - the
/// set of installed apps rarely changes within a session.
final installedAppsProvider = FutureProvider<List<InstalledApp>>(
  (ref) => ref.watch(engineChannelProvider).getInstalledApps(),
);

/// Local sleep stats; refreshed after every night-end. RecordNight runs in the
/// same effect batch that fires 'sessionStateChanged', so no separate event is
/// needed. Screens also re-read on resume, per the convention above.
final statsProvider = FutureProvider<StatsView>((ref) async {
  ref.listen(engineEventsProvider, (_, next) {
    if (next.valueOrNull?.name == 'sessionStateChanged') {
      ref.invalidateSelf();
    }
  });
  return ref.watch(engineChannelProvider).getStats();
});

/// The hardcore escape code as the engine will reveal it right now. Visibility
/// depends on wall-clock time and session state, so screens re-read on resume
/// and after any config/session change rather than trusting a cached value.
final hardcorePasswordProvider =
    FutureProvider<HardcorePasswordView>((ref) async {
  ref.listen(engineEventsProvider, (_, next) {
    final name = next.valueOrNull?.name;
    if (name == 'configChanged' || name == 'sessionStateChanged') {
      ref.invalidateSelf();
    }
  });
  return ref.watch(engineChannelProvider).getHardcorePassword();
});
