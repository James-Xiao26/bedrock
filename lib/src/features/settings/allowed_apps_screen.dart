import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../engine/engine_models.dart';
import '../../engine/engine_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/section_card.dart';

/// iOS Downtime "Always Allowed" screen: apps that stay reachable during a
/// window. The top card is the current allowlist (removable); below it, every
/// other launchable app (addable).
class AllowedAppsScreen extends ConsumerWidget {
  const AllowedAppsScreen({super.key});

  Future<void> _setAllowlist(WidgetRef ref, Set<String> next) =>
      ref.read(engineChannelProvider).updateConfig(ConfigPatch(allowlist: next));

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(configProvider).valueOrNull;
    final apps = ref.watch(installedAppsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Always Allowed')),
      body: switch ((config, apps)) {
        (final c?, AsyncData(:final value)) => _body(ref, c.active.allowlist, value),
        (_, AsyncError(:final error)) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text('Could not load apps: $error'),
            ),
          ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }

  Widget _body(WidgetRef ref, Set<String> allowed, List<InstalledApp> apps) {
    final byPackage = {for (final a in apps) a.packageName: a};
    // Allowed entries, keeping any allowlisted package we can't resolve to an
    // installed app (shown by package name) so it stays removable.
    final allowedApps = allowed.map(
      (pkg) =>
          byPackage[pkg] ??
          InstalledApp(packageName: pkg, label: pkg, icon: Uint8List(0)),
    ).toList()
      ..sort((a, b) => a.label.toLowerCase().compareTo(b.label.toLowerCase()));
    final choosable =
        apps.where((a) => !allowed.contains(a.packageName)).toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
      children: [
        const _Caption(
          'Always allowed apps stay available during downtime.',
        ),
        const SizedBox(height: 20),
        if (allowedApps.isNotEmpty) ...[
          _AppCard(
            apps: allowedApps,
            action: _RowAction.remove,
            onTap: (a) =>
                _setAllowlist(ref, allowed.difference({a.packageName})),
          ),
          const SizedBox(height: 28),
        ],
        const SectionLabel('Choose apps'),
        if (choosable.isEmpty)
          const _Caption('Every installed app is already allowed.')
        else
          _AppCard(
            apps: choosable,
            action: _RowAction.add,
            onTap: (a) => _setAllowlist(ref, {...allowed, a.packageName}),
          ),
      ],
    );
  }
}

enum _RowAction { add, remove }

class _AppCard extends StatelessWidget {
  const _AppCard({
    required this.apps,
    required this.action,
    required this.onTap,
  });

  final List<InstalledApp> apps;
  final _RowAction action;
  final ValueChanged<InstalledApp> onTap;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < apps.length; i++) {
      rows.add(_AppRow(app: apps[i], action: action, onTap: () => onTap(apps[i])));
      if (i != apps.length - 1) rows.add(const Divider(indent: 52));
    }
    return SectionCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Column(children: rows),
    );
  }
}

class _AppRow extends StatelessWidget {
  const _AppRow({required this.app, required this.action, required this.onTap});

  final InstalledApp app;
  final _RowAction action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final isAdd = action == _RowAction.add;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            if (!isAdd) ...[
              _CircleButton(
                icon: Icons.remove,
                color: const Color(0xFFE5484D),
                onTap: onTap,
              ),
              const SizedBox(width: 12),
            ] else ...[
              _CircleButton(
                icon: Icons.add,
                color: const Color(0xFF30A46C),
                onTap: onTap,
              ),
              const SizedBox(width: 12),
            ],
            _AppIcon(app.icon),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                app.label,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: BedrockColors.onSurface,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  const _AppIcon(this.bytes);

  final Uint8List bytes;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: bytes.isEmpty
          ? Container(
              width: 34,
              height: 34,
              color: BedrockColors.surfaceHigh,
              child: const Icon(Icons.apps,
                  size: 20, color: BedrockColors.onSurfaceMuted),
            )
          : Image.memory(bytes, width: 34, height: 34, filterQuality: FilterQuality.medium),
    );
  }
}

class _CircleButton extends StatelessWidget {
  const _CircleButton({
    required this.icon,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 24,
        height: 24,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        child: Icon(icon, size: 18, color: Colors.white),
      ),
    );
  }
}

class _Caption extends StatelessWidget {
  const _Caption(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 13,
          height: 1.4,
          color: BedrockColors.onSurfaceMuted,
        ),
      ),
    );
  }
}
