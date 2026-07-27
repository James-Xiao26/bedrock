# r/SideProject - launch post draft

Status: READY TO POST. Real links below are live.
Sub welcomes self-promo but wants a story + a real, un-gated product. Rules: show working screenshots/recording (not a store badge), say you built it, engage every comment.
Before posting: attach 2-3 real screenshots (the night/blocked screen, the schedule editor, the friction ladder). Subreddit: https://www.reddit.com/r/SideProject/

---

## Title

I built a screen-time blocker that collects nothing about you - no account, no charts, no tracking

## Body

I'm a solo dev. I built Bedrock because my own phone kept me up at night, and every screentime app I tried made it worse - they threw analytics and streaks at me I never asked for, and half of them nagged me to buy the paid version. So this one does none of that. No account, no uploads, no tracking, no charts, no streaks. It doesn't even look at your screentime. It just locks away the apps you choose during the hours you choose, and it's calm and monotone on purpose instead of being one more bright thing on your phone.

The interesting design problem was: how do you make a blocker hard to defeat without ever truly locking someone out of their own phone. The answer is a friction ladder with three ways out, in rising order of effort: a password you set (you have to stop and type it), a free unblock that's deliberately buried and slow to reach, and a $1 emergency unblock for when you genuinely need an app right now. The money isn't the point - it's just enough friction to kill the mindless reach.

Stack is Flutter for the awake-time UI (onboarding, schedule/settings) and native Kotlin for everything that has to be true at 3am - the state machine, alarms, and the actual blocking. Android-only for now; iOS later via Screen Time API.

Feedback I'm after: does the friction ladder feel fair or annoying, and would you actually keep it installed past week one. It's free, no signup - install link and a note on how to join is in the first comment.
