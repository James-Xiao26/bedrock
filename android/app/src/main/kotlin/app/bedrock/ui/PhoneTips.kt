package app.bedrock.ui

/**
 * Rotating "put it down" nudges shown on the blocker's locked screen - a small
 * push toward leaving instead of unlocking. Each blends a bit of the science
 * with one concrete step, and is short enough to read at a glance.
 *
 * This is the whole content source: edit, reorder, or replace these lines
 * freely. One is picked at random each time the blocker appears.
 */
object PhoneTips {
    val ALL = listOf(
        "The urge to check peaks and fades in about 90 seconds. Set the phone down and let it pass.",
        "Every unlock trains the habit loop. Walking away this once makes next time easier.",
        "Put the phone in another room. Out of reach is out of mind - distance does the willpower for you.",
        "Name what you actually came here for. If it can wait until morning, it usually can.",
        "Boredom is the withdrawal, not the problem. Sit with it for a minute and it lifts on its own.",
        "Bright screens read as daylight to your brain and push sleep later. Give your eyes the dark.",
        "Trade the scroll for one small thing: a glass of water, a stretch, three slow breaths.",
        "If you keep landing here for an app you don't really need, delete it. The easiest habit to break is the one you can't open.",
        "Turn off notifications for apps that only ping to pull you back. Most alerts are a company buying your attention, not news you need.",
        "You don't need more time on this app - the day is just over. Go rest.",
    )

    fun random(): String = ALL.random()
}
