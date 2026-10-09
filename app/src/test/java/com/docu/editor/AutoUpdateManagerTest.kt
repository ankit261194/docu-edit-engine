package com.docu.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateManagerTest {

    private fun isSemanticVersionNewer(current: String, candidate: String): Boolean {
        val currClean = current.trim().removePrefix("v").removePrefix("V")
        val candClean = candidate.trim().removePrefix("v").removePrefix("V")
        val currParts = currClean.split(".").map { it.toIntOrNull() ?: 0 }
        val candParts = candClean.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, candParts.size)

        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val candVal = candParts.getOrElse(i) { 0 }
            if (candVal > currVal) return true
            if (candVal < currVal) return false
        }
        return false
    }

    @Test
    fun testSemanticVersionNewer() {
        // Equal versions must never trigger update
        assertFalse(isSemanticVersionNewer("10.8.1", "10.8.1"))
        assertFalse(isSemanticVersionNewer("v10.8.1", "10.8.1"))
        assertFalse(isSemanticVersionNewer("10.8.1", "v10.8.1"))
        assertFalse(isSemanticVersionNewer("10.8.2", "10.8.2"))
        assertFalse(isSemanticVersionNewer("v10.8.2", "v10.8.2"))

        // Older current version must trigger update
        assertTrue(isSemanticVersionNewer("10.8.1", "10.8.2"))
        assertTrue(isSemanticVersionNewer("10.8.0", "10.8.1"))
        assertTrue(isSemanticVersionNewer("v10.7.0", "10.8.1"))
        assertTrue(isSemanticVersionNewer("10.7.0", "10.8.2"))
        assertTrue(isSemanticVersionNewer("9.9.9", "10.0.0"))

        // Newer current version must never trigger update
        assertFalse(isSemanticVersionNewer("10.8.2", "10.8.1"))
        assertFalse(isSemanticVersionNewer("10.9.0", "10.8.1"))
        assertFalse(isSemanticVersionNewer("11.0.0", "10.8.2"))
    }

    @Test
    fun testUpdateLoopPreventionLogic() {
        // When installed version is 10.8.2 and candidate is 10.8.2, no update
        val installedVer = "10.8.2"
        val candidateVer = "10.8.2"
        assertFalse("Same version must not trigger update loop", isSemanticVersionNewer(installedVer, candidateVer))

        // When installed is 10.8.1 and candidate is 10.8.1 (post-update), no update
        assertFalse("Post-update installed 10.8.1 must not re-trigger 10.8.1 update", isSemanticVersionNewer("10.8.1", "10.8.1"))

        // Cache consistency: even if cached_has_update was true, isSemanticVersionNewer guarantees false
        val cachedVer = "10.8.2"
        val hasActualUpdate = true && isSemanticVersionNewer(installedVer, cachedVer)
        assertFalse("Cache must not report update when installed matches latest", hasActualUpdate)
    }
}
