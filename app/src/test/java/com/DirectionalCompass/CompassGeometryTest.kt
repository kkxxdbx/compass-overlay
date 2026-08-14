package com.DirectionalCompass

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassGeometryTest {

    @Test
    fun labelSize_scalesWithDensity() {
        assertEquals(24, CompassGeometry.labelSize(16, 1.0f))
        assertEquals(48, CompassGeometry.labelSize(16, 2.0f))
        assertEquals(30, CompassGeometry.labelSize(20, 1.0f))
    }

    @Test
    fun defaultCrossPos_cardinalDirs() {
        val gap = 80
        val diag = 56
        val size = 24
        val north = CompassGeometry.defaultCrossPos(Prefs.DIR_NORTH, 1080, 2400, gap, diag, size)
        val south = CompassGeometry.defaultCrossPos(Prefs.DIR_SOUTH, 1080, 2400, gap, diag, size)
        val west = CompassGeometry.defaultCrossPos(Prefs.DIR_WEST, 1080, 2400, gap, diag, size)
        val east = CompassGeometry.defaultCrossPos(Prefs.DIR_EAST, 1080, 2400, gap, diag, size)
        val cx = (1080 - size) / 2
        val cy = (2400 - size) / 2
        assertEquals(cx, north.first)
        assertEquals(cy - gap, north.second)
        assertEquals(cx, south.first)
        assertEquals(cy + gap, south.second)
        assertEquals(cx - gap, west.first)
        assertEquals(cy, west.second)
        assertEquals(cx + gap, east.first)
        assertEquals(cy, east.second)
    }

    @Test
    fun defaultCrossPos_diagonalDirs_useDiagOffset() {
        val gap = 100
        val diag = 70
        val size = 20
        val ne = CompassGeometry.defaultCrossPos(Prefs.DIR_NORTHEAST, 800, 1200, gap, diag, size)
        val sw = CompassGeometry.defaultCrossPos(Prefs.DIR_SOUTHWEST, 800, 1200, gap, diag, size)
        val cx = (800 - size) / 2
        val cy = (1200 - size) / 2
        assertEquals(cx + diag, ne.first)
        assertEquals(cy - diag, ne.second)
        assertEquals(cx - diag, sw.first)
        assertEquals(cy + diag, sw.second)
    }

    @Test
    fun defaultCrossPos_unknownDir_returnsCenter() {
        val size = 20
        val (x, y) = CompassGeometry.defaultCrossPos("unknown", 800, 1200, 100, 70, size)
        assertEquals((800 - size) / 2, x)
        assertEquals((1200 - size) / 2, y)
    }

    @Test
    fun scaleSpacing_identity() {
        val pts = listOf(100 to 100, 200 to 150)
        assertEquals(pts, CompassGeometry.scaleSpacing(pts, 1f))
    }

    @Test
    fun scaleSpacing_scalesAroundBoundingBoxCenter() {
        val pts = listOf(0 to 0, 100 to 100)
        val scaled = CompassGeometry.scaleSpacing(pts, 2f)
        // 包围盒中心 (50,50)，放大 2 倍
        assertEquals(listOf(-50 to -50, 150 to 150), scaled)
    }

    @Test
    fun scaleSpacing_singlePoint_isStable() {
        val pts = listOf(42 to 24)
        assertEquals(pts, CompassGeometry.scaleSpacing(pts, 3f))
    }

    @Test
    fun scaleSpacing_emptyOrInvalidK_returnsInput() {
        assertEquals(emptyList<Pair<Int, Int>>(), CompassGeometry.scaleSpacing(emptyList(), 2f))
        val pts = listOf(1 to 2)
        assertEquals(pts, CompassGeometry.scaleSpacing(pts, 0f))
        assertEquals(pts, CompassGeometry.scaleSpacing(pts, -1f))
    }
}
