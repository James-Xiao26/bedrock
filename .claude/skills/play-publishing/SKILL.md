---
name: play-publishing
description: Track Bedrock's Google Play Store publishing progress and requirements (personal developer account "Grounded Labs"). Use when the user asks about Play Store publishing status, next steps, account/identity verification, the closed-testing gate, or what's left before launch.
---

# Publishing Bedrock to Google Play

Living checklist of getting Bedrock onto the Play Store. Update the status marks as steps complete.

## Account facts (locked)

- **Account type:** Personal (individual), not organization — no D-U-N-S / legal entity needed.
- **Developer name (public):** Grounded Labs.
- **Legal name:** used only in the payments profile (private; must match gov ID + bank). Distinct from the public developer name.
- **Fee:** $25 one-time — **paid**.

## Progress

- [x] Sign up at [play.google.com/console](https://play.google.com/console), chose personal account
- [x] Pay $25 one-time fee
- [ ] **Identity verification** — gov ID submitted, *Google is verifying* (in progress)
- [ ] **Device verification** — needs a PHYSICAL Android 10+ phone (emulator does NOT work — see below); sign into the Play Store with the Grounded Labs account
- [ ] Verify developer email + phone (public on store listing — use a dedicated email, not personal)
- [ ] Payments profile set up (legal name, bank, tax info) — needed because Bedrock has billing
- [ ] Closed-testing track with **20 testers held 14 continuous days** (the launch bottleneck — see below)
- [ ] Apply for production access
- [ ] Store listing (screenshots, description, privacy policy, data-safety form)
- [ ] Upload signed release AAB
- [ ] Submit for review

## Device verification — needs a PHYSICAL phone (emulator does NOT work)

Google requires proof you have access to an Android device: sign into the **Play Store app** with the developer account.
- **Emulators fail**, even Google Play images. They're signed with `dev-keys` (`.../Pixel_9` reports `user/dev-keys`, SDK 35), so they are **not Play Protect certified**. Verification requires a certified device and rejects uncertified ones with a misleading *"Need Android 10 (SDK 29) or newer"* error — the SDK level isn't the real issue, certification is.
- **Use any physical Android 10+ phone** (Android 10 = 2019, so almost anything recent). One-time step; after it, the emulator is fine for all dev/testing.

## The real bottleneck: 20 testers / 14 days

Personal accounts created after Nov 2023 must run a closed test before production unlocks:
- **20 testers** opted in (real Google accounts, added by email or Google Group)
- held **14 continuous days**
- only then does "apply for production" unlock

Start recruiting the 20 testers early — this gates launch the way the D-U-N-S wait would have on the org path.

## Gotchas

- Developer email is **public** (in the store listing's "Developer contact" section) and gets scraped — use a dedicated address. It's changeable later in Play Console.
- Payments-profile legal name/address must match gov ID and bank account, or payouts get blocked.
- Play policy constraints for the app itself (accessibility-service disclosure, don't block Settings/Play Store) live in `CLAUDE.md` — those bite at review time, not account time.
