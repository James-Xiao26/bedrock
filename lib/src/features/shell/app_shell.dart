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
