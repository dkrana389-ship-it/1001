package com.deepinnovations.reelcounter

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

/**
 * Reel ginne ke 3 tarike (BrainPal jaise apps ki tarah), sab ek saath:
 * 1) Reel ki pehchaan badalna  2) Reels viewer ka position badalna  3) Backup: scroll rukna
 * Dono ek hi swipe ko double count na karein, isliye beech mein dedupe hai.
 */
class ReelAccessibilityService : AccessibilityService() {

    private class Scan {
        var pager = false
        var suppressed = false
        var containerDesc = ""
        var longestText = ""
        var nodes = 0
        val candidates = LinkedHashSet<String>()
    }

    private val igPackages = setOf("com.instagram.android", "com.instagram.lite")
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: CounterOverlay? = null

    private var inInstagram = false
    private var lastTick = 0L

    private var evalPending = false
    private var pagerVisible = false
    private var identityAvailable = false
    private var suppressedNow = false
    private var lastIdentity = ""
    private var pendingIdentity = ""
    private val seenIdentities = LinkedHashSet<String>()
    private val seenIndexes = HashSet<Int>()
    private var firstInSession = true
    private var lastPagerSeenAt = 0L
    private var lastIndexOkAt = 0L
    private var lastCountAt = 0L
    private var lastCountedBy = "-"
    private var lastPkg = "-"
    private var candidates = ""
    private var lastDiagAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = leaveInstagram()
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!inInstagram) return
            flushTime()
            handler.postDelayed(this, 1000)
        }
    }

    private val evalRunnable = Runnable { evaluate() }
    private val settle = Runnable { onScrollSettled() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = CounterOverlay(this)
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        writeDiag(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastPkg = pkg
            if (pkg in igPackages) enterInstagram()
            else if (!isIgnored(pkg)) leaveInstagram()
            writeDiag(true)
        }

        if (pkg !in igPackages) return

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastPkg = pkg
            enterInstagram()
            onScrollEvent(e)
        }
        if (inInstagram) scheduleEval(250)
    }

    private fun isIgnored(pkg: String) =
        pkg == "com.android.systemui" || pkg == "android" ||
            pkg.contains("inputmethod") || pkg.contains("keyboard")

    private fun enterInstagram() {
        if (inInstagram) return
        inInstagram = true
        lastTick = SystemClock.elapsedRealtime()
        handler.post(ticker)
        overlay?.show(Store.reels(this))
    }

    private fun leaveInstagram() {
        if (!inInstagram) return
        flushTime()
        inInstagram = false
        pagerVisible = false
        identityAvailable = false
        firstInSession = true
        pendingIdentity = ""
        evalPending = false
        seenIndexes.clear()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(settle)
        handler.removeCallbacks(evalRunnable)
        overlay?.hide()
        writeDiag(true)
    }

    private fun flushTime() {
        val now = SystemClock.elapsedRealtime()
        Store.addMillis(this, now - lastTick)
        lastTick = now
    }

    private fun scheduleEval(delayMs: Long) {
        if (evalPending) return
        evalPending = true
        handler.postDelayed(evalRunnable, delayMs)
    }

    // Tarika 2: reels viewer ka position
    private fun onScrollEvent(e: AccessibilityEvent) {
        val sid = e.source?.viewIdResourceName ?: ""
        if (sid.contains("clips_viewer") && sid.contains("pager")) {
            val idx = if (e.fromIndex >= 0 && e.fromIndex == e.toIndex) e.fromIndex else -1
            if (idx >= 0) {
                lastIndexOkAt = SystemClock.elapsedRealtime()
                if (!suppressedNow && seenIndexes.add(idx)) tryCount("position")
                return
            }
        }
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, 450)
    }

    // Tarika 1: reel ki pehchaan
    private fun evaluate() {
        evalPending = false
        if (!inInstagram) return
        val now = SystemClock.elapsedRealtime()
        val s = Scan()
        rootInActiveWindow?.let { scan(it, 0, false, s) }
        pagerVisible = s.pager
        suppressedNow = s.suppressed
        candidates = s.candidates.joinToString(", ")
        val identity = if (s.containerDesc.isNotBlank() || s.longestText.isNotBlank())
            s.containerDesc + "|" + s.longestText.take(40) else ""
        identityAvailable = pagerVisible && identity.isNotEmpty()

        if (pagerVisible) {
            lastPagerSeenAt = now
        } else if (now - lastPagerSeenAt > 1500) {
            firstInSession = true
            pendingIdentity = ""
            seenIndexes.clear()
        }

        if (pagerVisible && !suppressedNow && identityAvailable) handleIdentity(identity)
        writeDiag(false)
        if (pagerVisible && pendingIdentity.isNotEmpty()) scheduleEval(250)
    }

    private fun handleIdentity(id: String) {
        if (id == lastIdentity) {
            pendingIdentity = ""
            return
        }
        if (firstInSession || id == pendingIdentity) {
            lastIdentity = id
            pendingIdentity = ""
            firstInSession = false
            if (seenIdentities.size > 300) seenIdentities.clear()
            if (seenIdentities.add(id)) tryCount("pehchaan")
        } else {
            pendingIdentity = id
        }
    }

    // Tarika 3: backup, jab upar ke dono kaam na karein
    private fun onScrollSettled() {
        if (!inInstagram) return
        val now = SystemClock.elapsedRealtime()
        if (identityAvailable || now - lastIndexOkAt < 5000) return
        if (suppressedNow) return
        if (Store.strict(this) && !pagerVisible) return
        tryCount("scroll (backup)")
    }

    private fun tryCount(by: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val window = if (by == lastCountedBy) 250L else 1800L
        if (now - lastCountAt < window) return false
        lastCountAt = now
        lastCountedBy = by
        overlay?.update(Store.addReel(this))
        writeDiag(true)
        return true
    }

    private fun scan(node: AccessibilityNodeInfo?, depth: Int, inPager: Boolean, s: Scan) {
        if (node == null || depth > 30 || s.nodes > 1500) return
        s.nodes++
        val id = node.viewIdResourceName?.substringAfter(":id/", "") ?: ""
        var pagerHere = inPager
        if (id.isNotEmpty()) {
            if (id.contains("clips_viewer") && id.contains("pager")) {
                s.pager = true
                pagerHere = true
            }
            if (id == "clips_video_container" && s.containerDesc.isEmpty() && node.isVisibleToUser) {
                s.containerDesc = node.contentDescription?.toString() ?: ""
            }
            if (id == "layout_comment_thread_edittext" || id.contains("igds_snackbar")) {
                s.suppressed = true
            }
            if ((id.contains("clips") || id.contains("reel")) && s.candidates.size < 8) {
                s.candidates.add(id)
            }
        }
        if (pagerHere && node.isVisibleToUser) {
            val t = node.text?.toString()
            if (t != null && t.length >= 4 && t.length > s.longestText.length) s.longestText = t
        }
        for (i in 0 until node.childCount) {
            scan(node.getChild(i), depth + 1, pagerHere, s)
        }
    }

    private fun writeDiag(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastDiagAt < 400) return
        lastDiagAt = now
        val posOk = now - lastIndexOkAt < 5000
        val text = "Service: LIVE ✓\n" +
            "Last app: $lastPkg\n" +
            "Instagram open: ${if (inInstagram) "haan" else "nahi"}\n" +
            "Reels viewer: ${if (pagerVisible) "mil gaya ✓" else "nahi mila"}\n" +
            "Reel pehchaan: ${if (identityAvailable) "kaam kar rahi ✓" else "nahi mili"}\n" +
            "Position events: ${if (posOk) "kaam kar rahe ✓" else "nahi aaye"}\n" +
            "Aakhri ginti: $lastCountedBy\n" +
            "IDs: ${candidates.ifEmpty { "-" }}"
        Store.setDiag(this, text)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        leaveInstagram()
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
        super.onDestroy()
    }
}
