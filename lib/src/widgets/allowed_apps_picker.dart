import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../engine/engine_models.dart';
import '../theme/app_theme.dart';
import 'section_card.dart';

/// The Always-Allowed picker body: the current allowlist (removable) above every
/// other launchable app (addable). Presentational - it reports the next set via
/// [onChanged]; the caller decides whether to write it to the engine now
/// (Settings) or hold it until onboarding finishes. Sits inside the caller's
/// own scroll view.
class AllowedAppsPicker extends StatelessWidget {
  const AllowedAppsPicker({
    super.key,
    required this.allowed,
    required this.apps,
    required this.onChanged,
    this.systemAllowed = const {},
  });

  final Set<String> allowed;
  final List<InstalledApp> apps;
  final ValueChanged<Set<String>> onChanged;

  /// Packages the blocker always allows by default (dialer, launcher, Settings,
  /// Play Store, ...). Shown non-removable atop the user's own list.
  final Set<String> systemAllowed;

  @override
  Widget build(BuildContext context) {
    final byPackage = {for (final a in apps) a.packageName: a};
    // Default-allowed apps, resolved to their launchable entries (non-launchable
    // system packages like SystemUI simply won't be in [apps] and are skipped).
    final systemApps = apps
        .where((a) => systemAllowed.contains(a.packageName))
        .toList()
      ..sort((a, b) => a.label.toLowerCase().compareTo(b.label.toLowerCase()));
    // The user's own allowlist, minus anything already covered by the defaults.
    final allowedApps = allowed
        .where((pkg) => !systemAllowed.contains(pkg))
        .map(
          (pkg) =>
              byPackage[pkg] ??
              InstalledApp(packageName: pkg, label: pkg, icon: Uint8List(0)),
        )
        .toList()
      ..sort((a, b) => a.label.toLowerCase().compareTo(b.label.toLowerCase()));
    final choosable = apps
        .where((a) =>
            !allowed.contains(a.packageName) &&
            !systemAllowed.contains(a.packageName))
        .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (systemApps.isNotEmpty) ...[
          const SectionLabel('Always allowed by default'),
          _AppCard(
            apps: systemApps,
            action: _RowAction.locked,
            onTap: (_) {},
          ),
          const SizedBox(height: 28),
        ],
        if (allowedApps.isNotEmpty) ...[
          const SectionLabel('Your allowed apps'),
          _AppCard(
            apps: allowedApps,
            action: _RowAction.remove,
            onTap: (a) => onChanged(allowed.difference({a.packageName})),
          ),
          const SizedBox(height: 28),
        ],
        const SectionLabel('Choose apps'),
        if (choosable.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Every installed app is already allowed.',
              style: TextStyle(
                fontSize: 13,
                height: 1.4,
                color: BedrockColors.onSurfaceMuted,
              ),
            ),
          )
        else
          _AppCard(
            apps: choosable,
            action: _RowAction.add,
            onTap: (a) => onChanged({...allowed, a.packageName}),
          ),
      ],
    );
  }
}

enum _RowAction { add, remove, locked }

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
    return DecoratedBox(
      decoration: const BoxDecoration(
        border: Border(
          top: BorderSide(color: BedrockColors.hairline),
          bottom: BorderSide(color: BedrockColors.hairline),
        ),
      ),
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
    final isLocked = action == _RowAction.locked;
    final isAdd = action == _RowAction.add;
    return InkWell(
      onTap: isLocked ? null : onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            if (isLocked)
              const SizedBox(
                width: 24,
                height: 24,
                child: Icon(Icons.lock_outline,
                    size: 18, color: BedrockColors.onSurfaceMuted),
              )
            else
              _CircleButton(
                icon: isAdd ? Icons.add : Icons.remove,
                color:
                    isAdd ? const Color(0xFF30A46C) : const Color(0xFFE5484D),
                onTap: onTap,
              ),
            const SizedBox(width: 12),
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
          : Image.memory(bytes,
              width: 34, height: 34, filterQuality: FilterQuality.medium),
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
