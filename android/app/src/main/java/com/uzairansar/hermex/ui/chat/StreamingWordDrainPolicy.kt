package com.uzairansar.hermex.ui.chat

internal object StreamingWordDrainPolicy {
    fun unitCount(text: String): Int {
        var count = 0
        var hasSeenNonWhitespace = false
        var previousWasWhitespace = false
        text.forEach { character ->
            val isWhitespace = character.isWhitespace()
            if (count == 0) {
                count = 1
            } else if (previousWasWhitespace && !isWhitespace && hasSeenNonWhitespace) {
                count += 1
            }
            if (!isWhitespace) hasSeenNonWhitespace = true
            previousWasWhitespace = isWhitespace
        }
        return count
    }

    fun splitAtUnitBoundary(text: String, unitCount: Int): Pair<String, String> {
        if (unitCount <= 0 || text.isEmpty()) return "" to text

        var unitsSeen = 0
        var hasSeenNonWhitespace = false
        var previousWasWhitespace = false
        text.forEachIndexed { index, character ->
            val isWhitespace = character.isWhitespace()
            if (unitsSeen == 0) {
                unitsSeen = 1
            } else if (previousWasWhitespace && !isWhitespace && hasSeenNonWhitespace) {
                unitsSeen += 1
                if (unitsSeen > unitCount) return text.substring(0, index) to text.substring(index)
            }
            if (!isWhitespace) hasSeenNonWhitespace = true
            previousWasWhitespace = isWhitespace
        }
        return text to ""
    }

    fun drainQuota(backlogUnitCount: Int, cadenceMillis: Long, maximumLagMillis: Long): Int {
        if (backlogUnitCount <= 1) return 1
        if (cadenceMillis <= 0 || maximumLagMillis <= 0) return backlogUnitCount
        val quota = kotlin.math.ceil(
            backlogUnitCount.toDouble() * cadenceMillis.toDouble() / maximumLagMillis.toDouble(),
        ).toInt()
        return quota.coerceIn(1, backlogUnitCount)
    }
}
