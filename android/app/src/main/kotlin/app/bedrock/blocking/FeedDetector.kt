package app.bedrock.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import app.bedrock.BuildConfig
import app.bedrock.engine.FeedDecision
import app.bedrock.engine.FeedRules

/**
 * In-app feed blocking: decides whether the screen showing inside a watched
 * social app is a feed, and bounces to the blocker if it is.
 *
 * Everything here touches Android types; the decision table itself is pure and
 * lives in [FeedRules] so it can be unit-tested.
 *
 * Three guards keep this cheap and calm, in order:
 *  1. Packages with no rules return before window content is ever requested.
 *     Content-changed events arrive for every app, and this is the line that
 *     makes "we only inspect three apps" true.
 *  2. A scan throttle, because content-changed fires near frame rate while
 *     scrolling and a tree walk per event would be a battery problem.
 *  3. A dwell timer, because Instagram opens onto its (blocked) home feed and
 *     the user needs a moment to reach DMs without being bounced first. It also
 *     absorbs the transient misreads that happen mid-navigation.
 */
object FeedDetector {

    private const val TAG = "FeedDetector"
    private const val SCAN_THROTTLE_MS = 300L
    private const val DWELL_MS = 2_000L

    /** Beat between the Back press and the blocker, so Back lands first. */
    private const val EXIT_SETTLE_MS = 200L
    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 4_000

    /**
     * Longest content description treated as a UI label. Litho screens (all of
     * Instagram, much of TikTok) expose almost no view IDs, so labels like
     * "Reels" or "Direct messaging" are the only usable fingerprint. Anything
     * longer than this is user content - a caption, a message body - and is
     * dropped: it is not a stable signal and it does not belong in a log.
     */
    private const val MAX_LABEL_CHARS = 40

    private val handler = Handler(Looper.getMainLooper())

    /** Package whose block is armed and waiting out the dwell, if any. */
    private var pendingPackage: String? = null
    private var pendingSurface: String = ""
    private var lastScanElapsedMs = 0L
    private var lastLoggedIds: Set<String> = emptySet()

    /** Nodes walked by the last scan; debug-only, tells a thin tree from a hit cap. */
    private var lastNodeCount = 0

    private val fire = Runnable {
        val pkg = pendingPackage ?: return@Runnable
        val surface = pendingSurface
        pendingPackage = null
        val service = AppBlockerAccessibilityService.instance ?: return@Runnable
        // Re-check on the way out: a grant may have landed, or the toggle flipped,
        // during the dwell.
        if (!BlockingController.shouldBlockFeed(service, pkg)) return@Runnable
        Log.i(TAG, "blocking feed in $pkg")

        if (FeedRules.rulesFor(pkg)?.exitSurfaceFirst == true) {
            // Covering the surface is not enough for an app that can float over
            // the cover; see AppRules.exitSurfaceFirst. Leave first, then block,
            // with a beat in between so Back lands before the blocker steals focus.
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            handler.postDelayed(
                { BlockingController.bounceToBlocker(service, pkg, surface = surface) },
                EXIT_SETTLE_MS,
            )
        } else {
            BlockingController.bounceToBlocker(service, pkg, surface = surface)
        }
    }

    /** A watched app's content changed. Cheap for everything else. */
    fun onContentChanged(service: AccessibilityService, packageName: String) {
        val rules = FeedRules.rulesFor(packageName) ?: return
        if (!BlockingController.shouldBlockFeed(service, packageName)) {
            cancel()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastScanElapsedMs < SCAN_THROTTLE_MS) return
        lastScanElapsedMs = now

        val root = service.rootInActiveWindow ?: return
        val ids = collectSignals(root)
        val decision = FeedRules.decide(rules, ids)
        logForDiscovery(packageName, decision, ids)

        when (decision) {
            FeedDecision.BLOCK -> arm(packageName, rules.blockedLabel.ifEmpty { rules.label })
            // STALE means our fingerprints stopped matching this app version, so
            // we allow: a blocker that mistakenly locks a whole app is far worse
            // than one that quietly stops working until the rules are refreshed.
            FeedDecision.ALLOW, FeedDecision.STALE -> cancel()
        }
    }

    /** The foreground app changed; drop a dwell armed for a different package. */
    fun onForegroundChanged(packageName: String) {
        if (pendingPackage != null && pendingPackage != packageName) cancel()
    }

    fun cancel() {
        if (pendingPackage == null) return
        pendingPackage = null
        handler.removeCallbacks(fire)
    }

    /**
     * Debug-only: the view IDs on screen right now, for capturing real
     * fingerprints from installed app versions. See `dumpWindowIds`.
     */
    fun dumpIds(): List<String> {
        val root = AppBlockerAccessibilityService.instance?.rootInActiveWindow ?: return emptyList()
        return collectSignals(root).sorted()
    }

    /**
     * Debug builds: print each new screen's view IDs so real fingerprints can be
     * read off `adb logcat -s FeedDetector` while walking through the apps.
     * Only fires when the ID set actually changes, so scrolling stays quiet.
     */
    private fun logForDiscovery(packageName: String, decision: FeedDecision, ids: Set<String>) {
        if (!BuildConfig.DEBUG) return
        if (ids == lastLoggedIds) return
        lastLoggedIds = ids
        val (labels, viewIds) = ids.partition { it.startsWith("d:") }
        Log.d(TAG, "$packageName -> $decision  nodes=$lastNodeCount")
        Log.d(TAG, "  ids: ${viewIds.sorted().joinToString(",")}")
        Log.d(TAG, "  labels: ${labels.sorted().joinToString(",")}")
    }

    private fun arm(packageName: String, surface: String) {
        if (pendingPackage == packageName) return // already counting down
        cancel()
        pendingPackage = packageName
        pendingSurface = surface
        handler.postDelayed(fire, DWELL_MS)
    }

    /**
     * Fingerprint signals reachable from [root], breadth first and capped on
     * both depth and node count so a pathological tree can't stall the
     * accessibility thread.
     *
     * Two kinds of signal share one set:
     *  - bare view-ID names, the part after `:id/`
     *  - content descriptions, lowercased and prefixed `d:`
     *
     * The descriptions are not redundant. Instagram renders through Litho, which
     * flattens the hierarchy into virtual accessibility nodes that carry no view
     * ID at all, so labels are the only thing left to match on there.
     *
     * Only nodes reporting [AccessibilityNodeInfo.isVisibleToUser] contribute.
     * Instagram parks the screens you have already visited in the hierarchy
     * rather than tearing them down, so an unfiltered walk reports the home feed
     * as present while you are sitting on your profile. Everything is still
     * traversed, because an offscreen container can hold visible children.
     */
    private fun collectSignals(root: AccessibilityNodeInfo): Set<String> {
        val signals = HashSet<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                node.viewIdResourceName?.let { signals.add(it.substringAfter(":id/", it)) }
                node.contentDescription?.toString()?.trim()?.let {
                    if (it.isNotEmpty() && it.length <= MAX_LABEL_CHARS) {
                        signals.add("d:" + it.lowercase())
                    }
                }
            }
            if (depth >= MAX_DEPTH) continue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it to depth + 1) }
            }
        }
        lastNodeCount = visited
        return signals
    }
}
