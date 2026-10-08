package com.example.baseui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {

    private fun sample() = listOf(
        ReorderSection("To do", listOf("A", "B", "C")),
        ReorderSection("Doing", listOf("D")),
        ReorderSection("Done", emptyList()),
    )

    @Test fun moveWithinSection() {
        val result = Reorder.moveItem(sample(), ItemPosition(0, 0), ItemPosition(0, 2))
        assertEquals(listOf("B", "C", "A"), result[0].items)
    }

    @Test fun moveAcrossSectionsAndIntoEmpty() {
        var result = Reorder.moveItem(sample(), ItemPosition(0, 1), ItemPosition(1, 0))
        assertEquals(listOf("A", "C"), result[0].items)
        assertEquals(listOf("B", "D"), result[1].items)
        result = Reorder.moveItem(result, ItemPosition(1, 1), ItemPosition(2, 0))
        assertEquals(listOf("D"), result[2].items)
    }

    @Test fun pinnedSectionsStayPut() {
        val pinned = listOf(true, false, false, true, false)
        assertEquals(1, Reorder.clampedSectionDestination(2, 0, pinned))
        assertEquals(2, Reorder.clampedSectionDestination(1, 4, pinned))
        assertEquals(0, Reorder.clampedSectionDestination(0, 3, pinned))
    }

    @Test fun swapIntoSwapSection() {
        val sections = sample().toMutableList().also {
            it[1] = ReorderSection("Doing", listOf("D", "E"), swapsOnDrop = true)
        }
        assertTrue(Reorder.swaps(sections, ItemPosition(0, 1), ItemPosition(1, 1)))
        val result = Reorder.swapItem(sections, ItemPosition(0, 1), ItemPosition(1, 1))
        assertEquals(listOf("A", "E", "C"), result[0].items)
        assertEquals(listOf("D", "B"), result[1].items)
        assertFalse(Reorder.swaps(sections, ItemPosition(1, 0), ItemPosition(1, 1)))
    }

    @Test fun lockedSectionRefusesDrops() {
        val sections = sample().toMutableList().also { it[2] = it[2].copy(allowsItemMoves = false) }
        assertFalse(Reorder.canDrop(sections, ItemPosition(0, 0), 2, allowsCrossSection = true))
        assertFalse(Reorder.canDrop(sections, ItemPosition(0, 0), 1, allowsCrossSection = false))
    }

    @Test fun layoutAxes() {
        assertTrue(ReorderLayout.Carousel.scrollsSideways)
        assertTrue(ReorderLayout.HorizontalBanner.scrollsSideways)
        assertFalse(ReorderLayout.Banner.scrollsSideways)
    }
}

class ZoomDragTest {
    private val all = setOf(ZoomDismissGesture.Down, ZoomDismissGesture.Right)

    @Test fun axisNeedsSlopAndEnabledDirection() {
        assertNull(ZoomDrag.axis(3f, 3f, 8f, all))
        assertEquals(ZoomDrag.Axis.Right, ZoomDrag.axis(20f, 2f, 8f, all))
        assertEquals(ZoomDrag.Axis.Down, ZoomDrag.axis(2f, 20f, 8f, all))
        assertNull(ZoomDrag.axis(-20f, 2f, 8f, all))
        assertNull(ZoomDrag.axis(20f, 2f, 8f, setOf(ZoomDismissGesture.Down)))
    }

    @Test fun frameShrinksTowardsMinimumScale() {
        val frame = ZoomDrag.frame(ZoomDrag.Axis.Down, 0f, 400f, density = 1f, minimumScale = 0.7f)
        assertEquals(1f, frame.progress, 0.001f)
        assertEquals(0.7f, frame.scale, 0.001f)
    }

    @Test fun dismissByDistanceOrFlick() {
        assertTrue(ZoomDrag.shouldDismiss(ZoomDrag.Axis.Down, 0f, 130f, 0f, 1f, 120f))
        assertFalse(ZoomDrag.shouldDismiss(ZoomDrag.Axis.Down, 0f, 60f, 0f, 1f, 120f))
        assertTrue(ZoomDrag.shouldDismiss(ZoomDrag.Axis.Right, 60f, 0f, 1500f, 1f, 120f))
    }
}

class DeviceScreenTest {
    @Test fun classifiesCutouts() {
        assertEquals(CutoutType.None, DeviceScreen.classify(null, 1080))
        assertEquals(CutoutType.None, DeviceScreen.classify(0, 0, 1080))
        assertEquals(CutoutType.PunchHole, DeviceScreen.classify(80, 80, 1080))
        assertEquals(CutoutType.Notch, DeviceScreen.classify(480, 90, 1080))
    }
}
