---
name: competitors
description: Notes on Bedrock's competitors - which apps block short-form feeds, on which platform, and how they charge. Use when positioning Bedrock against other blockers, writing comparison copy, checking whether a differentiator is actually unique, or when the user reports finding a new competing app.
---

# Competitor notes

Running log of other apps in the blocker space.
Add an entry whenever the user mentions a new one; keep each entry short and date the observation.
Unverified means "the user noticed it", not "we checked the store listing".

## SocialLite (iOS + Android)

- Observed 2026-07-27 by the user, who installed it and walked the whole flow. Prices and store facts are the user's reading, not scraped.
- Blocks short-form content (the feed-level block, not whole-app), via a wrapper client rather than by modifying the real app.
- $6.99/mo or $47.90/yr. No lifetime tier. The 7-day trial converts to yearly.
- Highest-ranked screen time blocker in iOS Productivity.
- **Ships on Android too, 100k+ downloads.**
- Has a parent/child setup mode: you set it up for a child by entering a code.

Why it matters: feed-blocking-not-app-blocking is Bedrock's lead angle (see the `marketing` skill, angle 0).
That angle is claimed on both platforms, by the same company, with a head start and a rank.
An iOS port was already a second-mover story; Android is now one too.
Bedrock's remaining differentiators have to be mechanism and price, not the idea - see "Where Bedrock still differs" below.

### Onboarding flow (walked through by the user 2026-07-27, app installed)

In order:

1. Title page. Motto "same apps. No algorithm." typed out slowly as an animation. Otherwise clean/empty.
2. "Setting this up for yourself or a child?"
3. "How much time do you spend scrolling per day?" - slider, annotated with 4.8 hours as the average.
4. Age question.
5. Life-in-weeks animation: a grid of boxes, one per week of your life, color-coded into past, sleep, work, hygiene, free time.
   Then it fills your remaining free-time boxes with scrolling time, computed from the hours you gave in step 3.
   Button at the bottom: "Take it back."
6. Typed-text manifesto screen: social media companies don't want you to know this exists, so we engineered our own way in.
   Small print at the bottom: they don't see your password and don't collect data.
7. Social proof: 4.8 star App Store rating, quotes from users in different countries, stats (500k+ lives changed, 45+ countries, 86% screen time reduction).
   Scroll for more reviews, continue button at the bottom.
8. Join / sign in.

Post-signup, same flow continues:

9. "We built it to be free for everyone."
10. Another slow-typed text screen, this one the Pro pitch:
    "The social media apps were designed to be addicting. It's not your fault, but they will try to pull you back. The first three days are the hardest when going cold turkey. Pro makes the transition easier."
    Pro features named here: no ads, no suggested posts.
11. Tutorial: screen recording plus small caption text.
    Shows app blocking and settings tuning - "tune everything. granular control per platform - flip on what helps, leave the rest."
12. Sleep mode, with a looping animation. Pro feature.
    "Set a bedtime that locks your phone overnight and a short morning buffer that holds the apps off until you've actually started your day."
13. Paywall: free vs Pro checklist, big "Unlock Pro" button, "Continue for free" underneath.
    Tapping free pops a 7-day free trial offer anyway.
14. Another text screen, button "Set everything up."
15. Screen Time permission request (iOS).
16. Ad/suggested-post blocking for Instagram and YouTube, with a toggle to turn it on.
17. Add widgets.
18. Welcome screen/animation.

### App layout (5 swipeable pages, left to right)

1. Sleep mode. Pro.
2. Settings: grayscale, haptics, theme.
3. Main tab - this is the product. Horizontal scroller of social apps; you pick one and open SocialLite's stripped version of it.
   Each app has its own block toggles (stories, posts, feed, DMs-only, etc.).
   On Instagram, Reels and Explore are always blocked and can't be turned off; the rest of the toggles need Pro.
   Covered apps: Instagram, YouTube, X, TikTok, Facebook, LinkedIn, Snapchat, Reddit.
4. App blocking. Entire page is Pro.
   "Keep Instagram on your phone but use SocialLite to block it. When you tap a blocked app, you're redirected to SocialLite's version, which strips out the addictive parts."
5. Profile: today's usage per social app; referral rewards (5 friends = 1 month Pro, 15 = 1 year, redeeming someone's code = 3 days); share, help and support, family features, settings, about.

### Pricing model

Free: the stripped in-app browser versions, with Instagram Reels/Explore blocked and nothing else configurable.
Pro: everything that makes it yours - per-app block toggles, sleep mode, app blocking/redirect, ad and suggested-post removal.

So free is a demo, not a product. The free tier gets you one opinionated config; every knob is paid.
Referrals are the growth loop, and the 3-day code redemption doubles as the acquisition hook.

### Does the wrapper actually work? Yes

Tested on Instagram 2026-07-27. It is a real client, not a degraded shell.
DMs readable, posting works, notifications come through. The Reels button is gone from the bottom bar entirely.
Ad blocking is wrapper-only - open YouTube outside SocialLite and the ads are back.

The gap: on free you still see posts and stories, so it is still a distracting feed.
Removing those costs Pro. Free removes the single worst surface and leaves the rest as the sales pitch.

### Reviews

Very few below 4 stars. The complaints that exist: the paywall, some glitches, some trouble during setup.
No pattern of "the wrapper broke" or "login failed" - the failure mode I'd have expected from a rebuilt client isn't showing up.

### Where Bedrock still differs

The idea is taken on both platforms. What's left:

- **Mechanism.** They redirect you out of Instagram into their copy of it; page 4 exists to funnel you there.
  Bedrock blocks inside the real app - no second client to maintain, no per-platform rebuild, and it degrades better when the platform ships a UI change.
  Cost: accessibility permission and the Play review that comes with it.
- **Price.** $6.99/mo with everything meaningful behind it. Bedrock's per-app feed control being free is a real line, not a footnote.
- **No account.** They gate the app behind signup; Bedrock has nothing to sign into.

All of these are "we're the better-built cheaper one," not "we invented this." Copy should stop implying otherwise.

What's worth stealing: the whole flow is a guilt-and-arithmetic funnel.
It gets a number out of you (step 3) before it can use it against you (step 5), and it earns the ask (step 8) only after the payoff and the social proof.
The password/data disclaimer sits exactly where the manifesto sounds sketchiest.
The Pro pitch lands right after "free for everyone" and frames paying as relapse insurance, not as a feature purchase - permissions come after the paywall, so you're bought in before you're asked for anything hard.

What Bedrock shouldn't copy: the sign-in gate (Bedrock has no accounts), the invented-looking stats, and the free-trial popup that fires after you already tapped "continue for free."

## Open questions

- **Their Android build: wrapper, or accessibility-based like Bedrock?** Highest-value unknown - it decides whether we're differentiated on mechanism where it actually counts.
- Android pricing and store rank - unknown, and more relevant to us than the iOS numbers.
- What the child setup code actually does (MDM profile? shared account? supervision?) - unknown.
- Any *other* Android app doing feed-level blocking? None found yet.
