package com.uwbcompass.core

/**
 * Client-side runtime degradation policy (Phase 5). Watches the ranging sample stream
 * and decides when to recommend a downgrade to a lower-priority technology. A downgrade
 * is only ever recommended DOWN the ladder UWB -> BLE -> GPS (never up), matching the
 * server's `isLegalDowngrade` rule.
 *
 * The client emits `technology.report` to the backend when [observe] returns a non-null
 * target; the backend validates it and relays `technology.downgrade` to both peers.
 */
class DegradationPolicy(
    private val maxConsecutiveLost: Int = 5,
    private val available: Set<Technology> = setOf(Technology.UWB, Technology.BLE, Technology.GPS),
) {
    private var consecutiveLost = 0

    /** @return the technology to downgrade to, or null if the current one is fine. */
    fun observe(sample: RangingSample): Technology? {
        if (sample.quality == SignalQuality.LOST) {
            consecutiveLost++
        } else {
            consecutiveLost = 0
            return null
        }
        if (consecutiveLost < maxConsecutiveLost) return null
        return nextBelow(sample.technology)
    }

    fun reset() {
        consecutiveLost = 0
    }

    private fun nextBelow(current: Technology): Technology? {
        val ladder = listOf(Technology.UWB, Technology.BLE, Technology.GPS)
        val idx = ladder.indexOf(current)
        for (i in idx + 1 until ladder.size) {
            if (ladder[i] in available) return ladder[i]
        }
        return null
    }
}
