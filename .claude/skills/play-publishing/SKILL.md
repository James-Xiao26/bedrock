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
- [x] **Identity verification** — gov ID verified by Google
- [x] **Device verification** — done on physical phone
- [x] Phone number verified. Developer email still to confirm (public on store listing — use a dedicated email, not personal)
- [x] Payments profile set up (legal name, bank, tax info) — bank account verified.
- [x] Signed release AAB built — upload keystore at `C:/Users/Jimmy/bedrock-upload.jks` (alias `upload`), Gradle reads `android/key.properties` (gitignored). Output: `build/app/outputs/bundle/release/app-release.aab`. BACK UP THE KEYSTORE.
- [x] App created in console: name `Bedrock: Bedtime App Blocker`, package `app.bedrock`, free app.
- [ ] **App content** declarations:
  - [x] Privacy policy — live at `https://bedrock-app.github.io/legal/privacy-policy.html` (hosted via free GitHub org `bedrock-app`, repo `legal`, Pages from root).
  - [x] App access / sign-in — "no restrictions" (Bedrock has no login).
  - [ ] Ads — declare "No, contains no ads".
  - [ ] Content rating questionnaire.
  - [ ] Target audience & content.
  - [ ] Data safety form — must say **collects/shares NO data** to match the privacy policy.
- [ ] Main store listing (short + full description, icon, screenshots, feature graphic).
- [ ] Upload AAB to a closed-testing track
- [ ] Closed-testing track with **12 testers held 14 continuous days** (the launch bottleneck — see below)
- [ ] Apply for production access
- [ ] Submit for review

## Device verification — needs a PHYSICAL phone (emulator does NOT work)

Google requires proof you have access to an Android device: sign into the **Play Store app** with the developer account.
- **Emulators fail**, even Google Play images. They're signed with `dev-keys` (`.../Pixel_9` reports `user/dev-keys`, SDK 35), so they are **not Play Protect certified**. Verification requires a certified device and rejects uncertified ones with a misleading *"Need Android 10 (SDK 29) or newer"* error — the SDK level isn't the real issue, certification is.
- **Use any physical Android 10+ phone** (Android 10 = 2019, so almost anything recent). One-time step; after it, the emulator is fine for all dev/testing.

## The real bottleneck: 12 testers / 14 days

Personal accounts created after Nov 2023 must run a closed test before production unlocks:
- **12 testers** opted in (real Google accounts, added by email or Google Group)
- held **14 continuous days**
- only then does "apply for production" unlock

Start recruiting the 12 testers early — this gates launch the way the D-U-N-S wait would have on the org path. (Google dropped this from 20 to 12; recruit a couple extra as buffer since a tester dropping out can reset the clock.)

### How testers actually sign up

You invite them; they opt in. There is no self-serve signup.
1. Play Console → Testing → Closed testing: create a track, upload the AAB.
2. Add testers by **email list** or, easier, a **Google Group** (people join the group instead of you editing the list each time).
3. Console gives an **opt-in URL** and a **Play Store install link**. Send both to testers.
4. Each tester, on the phone signed into the **same Google account** you added: open the opt-in URL → "Become a tester" → open the install link → install normally.

Gotcha: the phone's active Google account must exactly match the added email / joined group, or the tester just sees "not available."

## Gotchas

- Developer email is **public** (in the store listing's "Developer contact" section) and gets scraped — use a dedicated address. It's changeable later in Play Console.
- Payments-profile legal name/address must match gov ID and bank account, or payouts get blocked.
- Play policy constraints for the app itself (accessibility-service disclosure, don't block Settings/Play Store) live in `CLAUDE.md` — those bite at review time, not account time.
