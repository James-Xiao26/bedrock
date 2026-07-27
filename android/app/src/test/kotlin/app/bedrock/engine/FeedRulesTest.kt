package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedRulesTest {

    private val blocklist = AppRules(
        packageName = "com.example.tube",
        label = "Tube",
        mode = SurfaceMode.BLOCKLIST,
        anchorIds = emptySet(),
        surfaceIds = setOf("reel_recycler", "shorts_container"),
    )

    private val allowlist = AppRules(
        packageName = "com.example.gram",
        label = "Gram",
        mode = SurfaceMode.ALLOWLIST,
        anchorIds = setOf("tab_bar"),
        surfaceIds = setOf("direct_inbox", "message_list"),
    )

    @Test
    fun `blocklist blocks a listed surface`() {
        assertEquals(
            FeedDecision.BLOCK,
            FeedRules.decide(blocklist, setOf("toolbar", "reel_recycler")),
        )
    }

    @Test
    fun `blocklist allows everything else`() {
        assertEquals(
            FeedDecision.ALLOW,
            FeedRules.decide(blocklist, setOf("search_box", "results_list")),
        )
    }

    @Test
    fun `allowlist allows only listed surfaces`() {
        assertEquals(
            FeedDecision.ALLOW,
            FeedRules.decide(allowlist, setOf("tab_bar", "message_list")),
        )
    }

    @Test
    fun `allowlist blocks a recognised app screen that is not allowed`() {
        assertEquals(
            FeedDecision.BLOCK,
            FeedRules.decide(allowlist, setOf("tab_bar", "feed_recycler")),
        )
    }

    @Test
    fun `allowlist goes stale when no anchor is visible`() {
        // The app updated and renamed its views: fail open rather than brick it.
        assertEquals(
            FeedDecision.STALE,
            FeedRules.decide(allowlist, setOf("brand_new_id", "another_new_id")),
        )
    }

    @Test
    fun `an empty screen never blocks`() {
        // No IDs means the tree walk failed, which is a detector bug, not a feed.
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(blocklist, emptySet()))
        assertEquals(FeedDecision.STALE, FeedRules.decide(allowlist, emptySet()))
    }

    @Test
    fun `rulesFor matches only watched packages`() {
        assertNotNull(FeedRules.rulesFor("com.instagram.android"))
        assertNull(FeedRules.rulesFor("com.example.notwatched"))
        assertTrue(FeedRules.isWatched("com.google.android.youtube"))
        assertTrue(!FeedRules.isWatched("com.example.notwatched"))
    }

    @Test
    fun `every allowlist default carries an anchor`() {
        // Without an anchor an ALLOWLIST app can never reach STALE, so a rename
        // in that app would block all of it. Guard the invariant here.
        for (rules in FeedRules.defaults.filter { it.mode == SurfaceMode.ALLOWLIST }) {
            assertTrue(
                rules.anchorIds.isNotEmpty(),
                "${rules.packageName} is ALLOWLIST but has no anchor to fail open on",
            )
        }
    }

    /**
     * Real signal sets captured from Instagram on 2026-07-27, trimmed to the
     * labels that mattered. These are the regression guard: if a rules edit
     * starts blocking DMs or letting the feed through, it fails here rather
     * than on someone's phone.
     */
    @Test
    fun `real Instagram captures classify correctly`() {
        val instagram = FeedRules.rulesFor("com.instagram.android")!!

        val homeFeed = setOf(
            "d:home", "d:instagram home feed", "d:reels tray container",
            "d:message", "d:profile", "d:reels", "d:search and explore",
        )
        val otherProfile = setOf(
            "d:back", "d:follow back", "d:grid view", "d:message", "d:options",
        )
        val ownProfile = setOf(
            "d:home", "d:profile", "d:reels", "d:search and explore", "d:message",
            "d:edit profile", "d:share profile", "d:professional dashboard entry point",
            "d:grid view", "d:settings", "d:photos of you",
        )
        val dmInbox = setOf(
            "d:back", "d:search", "d:create group chat", "d:meta ai", "d:assistant",
        )
        val dmThread = setOf("d:back", "d:search", "d:send")
        val dmThreadAfterSending = setOf("d:back", "d:send", "d:tag")

        assertEquals(FeedDecision.BLOCK, FeedRules.decide(instagram, homeFeed))
        // Someone else's profile is a feed by another name; your own is not.
        assertEquals(FeedDecision.BLOCK, FeedRules.decide(instagram, otherProfile))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(instagram, ownProfile))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(instagram, dmInbox))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(instagram, dmThread))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(instagram, dmThreadAfterSending))
    }

    /**
     * Real signal sets captured from YouTube on 2026-07-27. The load-bearing
     * case is the last one: "d:shorts" is the bottom-nav button and rides along
     * on nearly every screen, so a careless rule edit blocks the whole app.
     */
    @Test
    fun `real YouTube captures classify correctly`() {
        val youtube = FeedRules.rulesFor("com.google.android.youtube")!!

        val shortsPlayer = setOf(
            "d:home", "d:shorts", "d:you", "d:create", "d:subscribe",
            "d:remix", "d:see more videos using this sound", "d:share this video",
            "d:view 202 comments", "d:go to channel @ryleycr1",
        )
        val longVideo = setOf(
            "d:video player", "d:comments", "d:share", "d:save to playlist",
            "d:enter fullscreen", "d:autoplay is on", "d:subscribe",
            "d:dislike this video", "d:pause video", "d:minimize",
        )
        val homeFeed = setOf(
            "d:home", "d:shorts", "d:you", "d:create", "d:search",
            "d:notifications", "d:youtube", "d:filters", "d:explore menu",
        )
        val searchSuggestions = setOf(
            "d:clear", "d:navigate up", "d:edit suggestion test",
        )
        val subscriptions = setOf(
            "d:subscriptions", "d:all", "d:videos", "d:live", "d:podcasts",
            "d:home", "d:shorts", "d:you",
        )

        assertEquals(FeedDecision.BLOCK, FeedRules.decide(youtube, shortsPlayer))
        // Long video is explicitly still allowed: the vertical feed is the
        // target, not YouTube itself.
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(youtube, longVideo))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(youtube, homeFeed))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(youtube, searchSuggestions))
        assertEquals(FeedDecision.ALLOW, FeedRules.decide(youtube, subscriptions))
    }

    @Test
    fun `a veto outranks the allowlist`() {
        val instagram = FeedRules.rulesFor("com.instagram.android")!!
        // The story viewer carries the composer's send button, same as a DM
        // thread. Without the veto this reads as an allowed thread.
        val storyFromInbox = setOf(
            "d:back", "d:send", "d:halifax.mp4's story, 0 of 13, unseen.",
        )
        assertEquals(FeedDecision.BLOCK, FeedRules.decide(instagram, storyFromInbox))
    }

    @Test
    fun `a veto substring does not fire on unrelated signals`() {
        val vetoed = allowlist.copy(vetoSubstrings = setOf("'s story"))
        assertEquals(
            FeedDecision.ALLOW,
            FeedRules.decide(vetoed, setOf("tab_bar", "message_list", "d:story archive")),
        )
    }

    @Test
    fun `defaults have no duplicate packages`() {
        val packages = FeedRules.defaults.map { it.packageName }
        assertEquals(packages.size, packages.toSet().size, "duplicate package in defaults")
    }
}
