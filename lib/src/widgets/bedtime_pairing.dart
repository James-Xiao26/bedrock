import 'package:flutter/material.dart';

import '../theme/app_theme.dart';
import 'section_card.dart';

/// Guidance for pairing Bedrock with the phone's built-in Bedtime mode: use it
/// as a soft warning that starts before Bedrock's hard stop. There is no public
/// API to configure Bedtime mode for the user, so this is instructional only.
/// ponytail: text-only; a deep-link-to-settings button would need native intents
/// and still misfire across OEMs. Add one if testers ask for the shortcut.
void showBedtimePairingSheet(BuildContext context) {
  showModalBottomSheet<void>(
    context: context,
    backgroundColor: BedrockColors.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(BedrockRadii.hero)),
    ),
    builder: (ctx) => const SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(24, 20, 24, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Pair with Bedtime mode',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w700,
                color: BedrockColors.onSurface,
              ),
            ),
            SizedBox(height: 14),
            _Para('Your phone has a built-in Bedtime mode that dims and quiets '
                'the screen. On its own it only nudges - you can still open '
                'anything. Bedrock is the hard stop.'),
            SizedBox(height: 12),
            _Para('Set Bedtime mode to turn on about 30 minutes before your '
                'downtime starts. You get a gentle warning to wind down, then '
                'Bedrock locks in.'),
            SizedBox(height: 12),
            _Para('While you\'re in there, turn on Grayscale and Do Not Disturb '
                'for bedtime. A grey, silent screen is far easier to put down.'),
            SizedBox(height: 12),
            _Para('You will find it in Settings under Digital Wellbeing, or in '
                'the Clock app under Bedtime. The exact place varies by phone.'),
          ],
        ),
      ),
    ),
  );
}

/// A settings row that opens [showBedtimePairingSheet]. Reused on the schedule
/// screen and in Settings.
class BedtimePairingRow extends StatelessWidget {
  const BedtimePairingRow({super.key});

  @override
  Widget build(BuildContext context) {
    return SettingGroup(
      rows: [
        SettingRow(
          title: 'Pair with Bedtime mode',
          subtitle: 'Add a soft warning before downtime locks in.',
          trailing: const Icon(Icons.chevron_right,
              color: BedrockColors.onSurfaceMuted, size: 20),
          onTap: () => showBedtimePairingSheet(context),
        ),
      ],
    );
  }
}

class _Para extends StatelessWidget {
  const _Para(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 14,
        height: 1.45,
        color: BedrockColors.onSurfaceMuted,
      ),
    );
  }
}
