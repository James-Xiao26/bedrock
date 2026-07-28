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
- [x] App created in console: name `Bedrock: Social Media Blocker`, package `app.bedrock`, free app.
- [ ] **App content** declarations:
  - [x] Privacy policy — live at `https://bedrock-app.github.io/legal/privacy-policy.html` (hosted via free GitHub org `bedrock-app`, repo `legal`, Pages from root).
  - [x] App access / sign-in — "no restrictions" (Bedrock has no login).
  - [x] Ads — declared "No, contains no ads".
  - [x] Content rating questionnaire.
  - [x] Target audience & content.
  - [x] Data safety form — says **collects/shares NO data**, matching the privacy policy.
  - [ ] **Accessibility (Permissions) declaration** — rewritten for feed blocking, see below. This is the review risk.
- [ ] Main store listing (short + full description, icon, screenshots, feature graphic). Title updated to `Bedrock: Social Media Blocker` on 2026-07-28; descriptions/category still need the post-pivot copy from the `marketing` skill.
- [ ] Upload AAB to a closed-testing track
- [ ] Closed-testing track with **12 testers held 14 continuous days** (the launch bottleneck — see below)
- [ ] Apply for production access
- [ ] Submit for review

## Accessibility declaration (paste into Play Console)

As of versionCode 5 the release build sets `canRetrieveWindowContent="true"` and listens for
`typeWindowContentChanged`, because in-app feed blocking has to tell a feed apart from DMs.
That makes this declaration load-bearing: it is the single most likely reason a submission gets rejected.

Play Console → App content → **Permissions** (the `AccessibilityService` / IsAccessibilityTool section).

**Which app functionality requires the AccessibilityService API:**

> Bedrock is a screen-time blocker. During a downtime window the user has scheduled, it blocks apps
> they have chosen to stay away from, and shows a full-screen block screen instead. Two things need
> the accessibility API. First, detecting which app has come to the foreground, so a blocked app can
> be interrupted the moment it opens. Second, for a small fixed list of social apps, reading the
> on-screen view hierarchy to determine whether the visible screen is an infinite-scroll feed
> (Instagram Reels and the home feed, the YouTube Shorts player) as opposed to direct
> messages, search, or a profile. This lets Bedrock block only the feed and leave the rest of the app
> usable, which is the core of the product: users want to keep talking to people without falling into
> the feed.

**Why other APIs are not sufficient:**

> UsageStatsManager only reports app usage after the fact and at coarse granularity, so it cannot
> interrupt a blocked app at the moment it is opened. It also reports nothing about which screen
> inside an app is showing, so it cannot distinguish a feed from a direct-message inbox. There is no
> other Android API that exposes the current in-app screen to a third-party app.

**What user data is accessed, and how it is handled:**

> Window content is only ever requested for the small fixed list of packages that have a matching
> rule in the app (currently `com.instagram.android` and `com.google.android.youtube` only). For
> every other package the service returns
> before requesting the window, so their content is never read. Within those apps, Bedrock collects
> only view-ID resource names and short content descriptions (40 characters or less) used as UI
> fingerprints, such as "reels_tab". Text content — messages, captions, posts, anything typed — is
> not collected. Nothing read from any window is stored, logged, or transmitted. The app has no
> network code of its own; the INTERNET permission comes from the Google Play Billing library and is
> used only to talk to Google Play. All state stays on the device.

**Prominent disclosure:** shown in-app before the user is sent to Accessibility settings
(`_PrivacyNote` in `lib/src/features/onboarding/onboarding_flow.dart`) and in the service description
(`accessibility_service_description` in `android/app/src/main/res/values/strings.xml`).
**These two, the XML config, and this declaration must all say the same thing** — a mismatch between
the declared behavior and the in-app disclosure is a standard rejection reason.

**Video demo:** Play usually asks for a link to a short video showing the accessibility feature in
use. Record the flow: set a downtime window → open Instagram → DMs stay usable → open Reels → block
screen appears. Unlisted YouTube link is fine. Only demo apps that are actually in `FeedRules` —
showing a feature on TikTok when TikTok has no rule is a mismatch reviewers will catch.

**IsAccessibilityTool:** leave the `isAccessibilityTool` attribute **unset/false**. Bedrock is not an
assistive tool for users with disabilities; claiming otherwise is its own policy violation.

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

### Tester links (live)

- **Tester Google Group (join to opt in):** https://groups.google.com/g/bedrock-testers
- **Play opt-in URL ("Become a tester"):** https://play.google.com/apps/testing/app.bedrock
- **Play Store install link:** https://play.google.com/store/apps/details?id=app.bedrock

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
