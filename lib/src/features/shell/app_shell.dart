import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/collapsible_section.dart';
import '../schedule/schedule_screen.dart';
import '../settings/settings_screen.dart';
import '../stats/stats_screen.dart';
import '../tonight/tonight_screen.dart';

/// The whole app on one calm surface: Today at the top, then the Schedule and
/// Stats editors as collapsible blocks, with Settings tucked behind a gear in
/// the top-right. No bottom nav; the engine providers are shared via Riverpod.
class AppShell extends ConsumerWidget {
  const AppShell({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;
    final stats = ref.watch(statsProvider).valueOrNull;

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 48),
          children: [
            Align(
              alignment: Alignment.centerRight,
              child: IconButton(
                icon: const Icon(Icons.settings_outlined),
                color: BedrockColors.onSurfaceMuted,
                tooltip: 'Settings',
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => Scaffold(
                      appBar: AppBar(title: const Text('Settings')),
                      body: const SettingsScreen(),
                    ),
                  ),
                ),
              ),
            ),
            const _PermissionWarning(),
            const TonightContent(),
            const SizedBox(height: 48),
            CollapsibleSection(
              title: 'Schedule',
              summary: config == null ? null : scheduleSummary(context, config),
              child: const ScheduleContent(),
            ),
            CollapsibleSection(
              title: 'Stats',
              summary: stats == null ? null : statsSummary(stats),
              child: const StatsContent(),
            ),
          ],
        ),
      ),
    );
  }
}

/// Blocking is dead without the accessibility service, and Android turns it off
/// on its own - a reinstall, a sideload over the top, some OEM battery cleaner.
/// Nothing tells the app when that happens, so re-read the grant on every
/// resume and say so loudly while it's off.
class _PermissionWarning extends ConsumerStatefulWidget {
  const _PermissionWarning();

  @override
  ConsumerState<_PermissionWarning> createState() => _PermissionWarningState();
}

class _PermissionWarningState extends ConsumerState<_PermissionWarning>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.invalidate(permissionsProvider);
    }
  }

  @override
  Widget build(BuildContext context) {
    final granted =
        ref.watch(permissionsProvider).valueOrNull?.accessibility ?? true;
    if (granted) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: BedrockColors.surfaceHigh,
        borderRadius: BorderRadius.circular(BedrockRadii.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Blocking is off',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          const Text(
            'Android switched off Bedrock\'s accessibility permission, so apps '
            'will not be blocked. This happens after reinstalling the app.',
            style: TextStyle(color: BedrockColors.onSurfaceMuted),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () =>
                ref.read(engineChannelProvider).openAccessibilitySettings(),
            child: const Text('Turn it back on'),
          ),
        ],
      ),
    );
  }
}
