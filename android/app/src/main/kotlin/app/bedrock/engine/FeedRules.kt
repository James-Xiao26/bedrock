package app.bedrock.engine

/** How a package's [AppRules.surfaceIds] should be read. */
enum class SurfaceMode {
    /** Listed surfaces are blocked, everything else in the app is allowed. */
    BLOCKLIST,

    /** Only listed surfaces are allowed, everything else in the app is blocked. */
    ALLOWLIST,
}

/** What to do about the screen currently showing inside a watched app. */
enum class FeedDecision {
    ALLOW,
    BLOCK,

    /** Fingerprints no longer match this app version; callers must fail open. */
    STALE,
}

/**
 * View-ID fingerprints for one watched app.
 *
 * [anchorIds] are IDs that should be present anywhere in the app's main UI
 * (a bottom nav bar, a root container). They exist to tell "the user is on a
 * screen we don't recognise" apart from "our fingerprints stopped matching
 * because the app updated and renamed its views".
 */
data class AppRules(
    val packageName: String,
    val label: String,
    val mode: SurfaceMode,
    val anchorIds: Set<String>,
    val surfaceIds: Set<String>,
    /**
     * Substrings that force a block regardless of [mode] or [surfaceIds].
     *
     * Needed because surfaces share signals. Instagram's story viewer carries
     * the same composer send button as an open DM thread, so allowing threads
     * by that signal alone lets stories through the inbox. Matched as
     * substrings, not exact values, because the giveaway labels embed usernames
     * and counts ("halifax.mp4's story, 0 of 13, unseen.").
     */
    /**
     * What the blocker says is blocked, as a noun phrase: "YouTube Shorts",
     * "Instagram's feed". Distinct from [label], which names the app itself,
     * because what gets blocked is rarely the whole app. Falls back to [label]
     * when unset.
     */
    val blockedLabel: String = "",
    val vetoSubstrings: Set<String> = emptySet(),
    /**
     * Press Back to leave the surface before showing the blocker.
     *
     * Needed where the app can escape being covered. Launching the blocker over
     * YouTube reads as leaving the app, so it drops the Short into
     * picture-in-picture and carries on playing above us. Backing out first ends
     * playback, leaving nothing to float.
     *
     * Off by default because it is not free: where the feed is the app's root
     * screen, Back exits the app entirely rather than popping one level.
     */
    val exitSurfaceFirst: Boolean = false,
)

/**
 * Decides whether the screen showing inside a social app is a feed.
 *
 * Pure (no Android types) so the decision table is exhaustively unit-testable;
 * the tree-walking half lives in `blocking/FeedDetector`.
 *
 * A signal is either a bare view-ID name (the part after `:id/`, not the
 * fully-qualified `pkg:id/name`, since the prefix varies between an app's own
 * views and library views it embeds) or a lowercased content description
 * prefixed `d:`. Both live in one set. Labels matter because Instagram renders
 * through Litho, which exposes virtual accessibility nodes carrying no view ID
 * at all, leaving descriptions as the only thing to match on.
 *
 * Rule shape differs per app because the apps differ:
 *  - Instagram is ALLOWLIST (only messaging and your own profile are reachable),
 *    the faithful reading of "feeds off", and it does not need re-enumerating
 *    every time they add a surface.
 *  - YouTube is BLOCKLIST and targets Shorts alone, so the home feed, search,
 *    subscriptions, and long-form playback all keep working. Long video is not
 *    the thing being quit here; the endless vertical feed is.
 *
 * Apps with no salvageable half belong in the allowlist blocker instead, blocked
 * whole. Splitting feed from messaging only earns its keep where something worth
 * keeping survives the split.
 *
 * ALLOWLIST fails open via [FeedDecision.STALE] when no anchor is visible.
 * Without that, an app update that renamed its views would brick the app
 * entirely rather than merely stop blocking it, and bricking is much worse.
 */
object FeedRules {

    /**
     * Both entries were captured from the apps installed on a real device on
     * 2026-07-27, not guessed. Signals are version-specific and will rot: when
     * an app update breaks one, recapture with `FeedDetector`'s debug logging
     * rather than reasoning about what the IDs ought to be.
     *
     * Rot degrades safely. ALLOWLIST entries lose their anchor and go STALE
     * (allow), BLOCKLIST entries simply stop matching (allow). The feature
     * quietly stops working; it never locks anyone out of an app.
     */
    val defaults: List<AppRules> = listOf(
        // Captured 2026-07-27 from the installed app. Instagram exposes no view
        // IDs worth matching (Litho), so every signal here is a label.
        AppRules(
            packageName = "com.instagram.android",
            label = "Instagram",
            blockedLabel = "Instagram's feed",
            mode = SurfaceMode.ALLOWLIST,
            // No single label survives every screen: the feed tabs carry the
            // bottom nav, while DMs open in their own window without it. Between
            // them these cover all observed screens, and losing all of them at
            // once is the signal that a redesign broke us.
            anchorIds = setOf("d:home", "d:back", "d:profile", "d:send", "d:search"),
            surfaceIds = setOf(
                // Inbox.
                "d:create group chat",
                "d:new message",
                "d:select multiple messages",
                "d:message requests",
                "d:meta ai",
                // An open thread. The composer's send button is the stable one;
                // it survived both the empty and the just-sent states.
                "d:send",
                "d:tag",
                // Your own profile, allowed by choice. These three are absent
                // from other people's profiles, which stay blocked - those are a
                // feed by another name. Do not add "d:grid view" here: it is on
                // every profile and would open all of them.
                "d:edit profile",
                "d:share profile",
                "d:professional dashboard entry point",
            ),
            // Story-viewer labels embed a username ("<name>'s story, 0 of 13,
            // unseen."), so match the stable tail. Needed because the story
            // viewer carries the same composer send button as a DM thread and
            // would otherwise ride in on the allowlist. Confirmed on device:
            // stories opened from the DM list block, the inbox still opens.
            vetoSubstrings = setOf("'s story"),
        ),
        AppRules(
            packageName = "com.google.android.youtube",
            label = "YouTube",
            blockedLabel = "YouTube Shorts",
            mode = SurfaceMode.BLOCKLIST,
            anchorIds = emptySet(), // BLOCKLIST never needs an anchor: it fails open by construction.
            // Captured 2026-07-27. YouTube exposes no view IDs to accessibility
            // either, so these are all labels found on the Shorts player and
            // absent from the watch page, which must keep working.
            //
            // Do NOT add "d:shorts": that is the bottom-nav button and it is
            // present on the home feed, subscriptions, and search. Matching it
            // would block the entire app.
            surfaceIds = setOf(
                "d:remix",
                "d:see more videos using this sound",
                // The watch page says plain "d:share" for the same action.
                "d:share this video",
            ),
            // Survives rewording of the surrounding label. Safe as a substring
            // because it is UI chrome; a bare word like "remix" would not be,
            // since video titles arrive as labels too.
            vetoSubstrings = setOf("using this sound"),
            // Shorts is never YouTube's root screen, so Back lands on the feed
            // or the previous video rather than closing the app.
            exitSurfaceFirst = true,
        ),
    )

    fun rulesFor(packageName: String): AppRules? =
        defaults.firstOrNull { it.packageName == packageName }

    /** Whether [packageName] is watched at all - the detector's cheap short-circuit. */
    fun isWatched(packageName: String): Boolean = rulesFor(packageName) != null

    /**
     * Classify the screen described by [visibleIds] (bare view-ID names).
     *
     * An empty [visibleIds] never yields [FeedDecision.BLOCK]: it means the tree
     * walk came back with nothing, which is a detector failure, not a feed.
     */
    fun decide(rules: AppRules, visibleIds: Set<String>): FeedDecision {
        // Veto first. A match proves the fingerprints still work, so blocking is
        // safe, and it must outrank the allowlist to close signal collisions.
        if (visibleIds.any { signal -> rules.vetoSubstrings.any { signal.contains(it) } }) {
            return FeedDecision.BLOCK
        }
        return when (rules.mode) {
            SurfaceMode.BLOCKLIST ->
                if (visibleIds.any { it in rules.surfaceIds }) FeedDecision.BLOCK else FeedDecision.ALLOW

            SurfaceMode.ALLOWLIST -> when {
                rules.anchorIds.none { it in visibleIds } -> FeedDecision.STALE
                visibleIds.any { it in rules.surfaceIds } -> FeedDecision.ALLOW
                else -> FeedDecision.BLOCK
            }
        }
    }
}
