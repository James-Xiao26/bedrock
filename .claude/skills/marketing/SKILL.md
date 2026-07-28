---
name: marketing
description: Bedrock's marketing positioning - the "angles" for how the app should look and feel to users (store listing, screenshots, onboarding copy, landing page, tester pitch). Use when writing user-facing copy, deciding tone/visual direction, or pitching Bedrock to prospective users/testers.
---

# Bedrock marketing angles

How Bedrock should come across to users. Reference this whenever writing
anything a user reads: Play listing, screenshots, onboarding copy, the tester
recruitment pitch, a landing page.

## Store listing (rewritten 2026-07-27 for the feed-blocking pivot)

The bedtime framing was replaced when feed blocking became the core feature.
The title is live in Play Console as `Bedrock: Social Media Blocker` (changed 2026-07-28); the descriptions and category still need updating there.

- **Play title (30-char max):** `Bedrock: Social Media Blocker` (29 chars) - live. Keywords: *social media blocker* is the head term with real search volume; *feed blocker* has almost none. Alternates if that reads too generic: `Bedrock: Block Reels & Shorts` (29) leans hard on the differentiator, `Bedrock: Feed & App Blocker` (27) splits the difference.
- **Short description (80-char max):** `Block Reels, Shorts and feeds. Keep your DMs. No ads, no tracking, no account.` (78 chars)
- **Full description:** see `full-description.txt` in this skill dir - ASO terms (social media blocker / app blocker / block social media / screen time / Reels / Shorts / feed) worked in naturally, calm voice, no clinical claims, $1 framed as the top rung of a friction ladder rather than a paywall.
- **Category:** move from Health & Fitness to **Productivity**. The sleep angle is gone, so Health & Fitness no longer fits, and Productivity is where people search for blockers. Editable later.
- **Tags:** Productivity, Social, Digital Wellbeing.
- **Developer name:** Grounded Labs.
- **Brand display font:** PT Serif Bold (700) - use for the app name "Bedrock" and other important/hero words in graphics (feature graphic, screenshots, landing page). Free Google Font, OFL license. Body/UI text stays in the app's existing sans; PT Serif is for display/wordmark only.

## What Bedrock is (one line)

A minimalist social media blocker that takes the feed and leaves the messages - it collects nothing about you, and shuts the endless scroll without cutting you off from people.

## Angles

0. **The feed is the problem, not the app.** The lead angle since the pivot, and the one thing no competitor does. Every other blocker is all-or-nothing: block Instagram and you also lose your DMs, so people turn it off. Bedrock blocks Reels, Shorts and the home feed while messaging keeps working. Say this first, in the title, the first screenshot, and the first line of every pitch.

1. **Zero data, zero surveillance.** Unlike every other screentime blocker, Bedrock collects no information whatsoever. Nothing is saved, nothing is uploaded, and it does not even look at your screentime. There are no charts, no "insights," no account. The whole category is built on watching you; Bedrock's pitch is that it doesn't.

2. **Radically minimalist - no stimuli.** The app itself is calm by design: everything is monotone, nothing competes for attention. It is the opposite of the apps it blocks. A blocker meant to get you off the feed shouldn't itself be another bright, engaging thing on your phone.

3. **For people who are serious.** Not a casual "screentime awareness" tool. It's for people who have already decided the scroll is costing them something and are ready to take a hard line with it.

4. **Friction, not a wall.** Getting back in is deliberately hard, but never impossible.
   During downtime hours an app has three ways out, in rising order of friction: a password you set (stop and type it), a free unblock that's buried and slow to reach, and the $1 emergency unblock.
   A blocked feed is stricter - the password does nothing there, because nobody has a Reels emergency. It's the free copy-out note or the $1, nothing cheaper.
   Turning feed blocking off entirely is allowed but takes a day to land, so it can never be an impulse.
   It's a friction ladder, not a single hardcore lock. Speak to people who want that discipline, and frame it as friction that respects them, never as being trapped.

5. **The $1 emergency unblock.** The top rung of the ladder, for when you genuinely need an app right now. The point isn't the money - it's friction. A small, deliberate cost kills the mindless reach without ever leaving you truly locked out. Because the free unblock also exists, no one is ever forced to pay to get into their own phone.

## Voice & tone

- Calm, plain, honest. Match the monotone visual design - no hype, no exclamation marks, no growth-hacky urgency.
- Confident and a little uncompromising - the friction is the default behavior, not an optional mode. Speak to people who already want discipline.
- Privacy stated as plain fact, not a feature bullet with a lock icon.

## Don'ts

- No medical or clinical claims about sleep, mental health, or "addiction" outcomes (Play policy + honesty).
- Don't claim feed blocking on apps that have no rule in `FeedRules.kt`. It currently covers **Instagram and YouTube only** - no TikTok. Naming TikTok or "For You" in copy, screenshots, or the accessibility demo video is a claim the build doesn't honor.
- Don't frame the $1 unblock as pay-to-unlock/ransomware - it's the top rung of a friction ladder, and a free unblock always exists, so no one is ever forced to pay to get into their own phone. (See `play-policy-risk-analysis` memory.)
- No manufactured urgency, streaks, badges, gamification, or engagement mechanics - they contradict the whole "no stimuli" premise.
- Don't promise or imply any data feature ("track your progress," "see your stats") - there is none, by design.
