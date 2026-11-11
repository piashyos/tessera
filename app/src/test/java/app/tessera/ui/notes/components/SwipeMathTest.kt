package app.tessera.ui.notes.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipeMathTest {
    private val width = 1000f

    @Test
    fun `inside the limits the card follows the finger exactly`() {
        assertEquals(60f, resistedDrag(40f, 20f, min = -200f, max = 200f, dimension = width))
        assertEquals(-150f, resistedDrag(-100f, -50f, min = -200f, max = 200f, dimension = width))
    }

    @Test
    fun `past a limit only a share of the drag is followed`() {
        val next = resistedDrag(200f, 100f, min = -200f, max = 200f, dimension = width)
        assertTrue(next > 200f && next < 300f, "stretched $next")
    }

    @Test
    fun `a side with no actions stretches from zero`() {
        val next = resistedDrag(0f, -100f, min = 0f, max = 200f, dimension = width)
        assertTrue(next < 0f && next > -100f, "stretched $next")
    }

    @Test
    fun `no amount of dragging stretches past the card's dimension`() {
        var x = 0f
        repeat(2000) { x = resistedDrag(x, 40f, min = 0f, max = 0f, dimension = width) }
        assertTrue(x < width, "stretched $x")
        assertTrue(x > width * 0.2f, "stretched only $x")
    }

    @Test
    fun `resistance grows the further the card is stretched`() {
        val near = resistedDrag(10f, 10f, min = 0f, max = 0f, dimension = width) - 10f
        val far = resistedDrag(300f, 10f, min = 0f, max = 0f, dimension = width) - 300f
        assertTrue(far < near, "near $near, far $far")
    }

    @Test
    fun `dragging back out of a stretch never overshoots the limit`() {
        var x = 250f
        var steps = 0
        while (x > 200f && steps++ < 500) x = resistedDrag(x, -20f, min = -200f, max = 200f, dimension = width)
        assertEquals(200f, x) // it lands on the limit, not past it on the other side
    }

    @Test
    fun `a slow release past the middle opens, before it closes`() {
        val anchors = listOf(0f, 300f, -300f)
        assertEquals(300f, settleTarget(200f, 0f, anchors))
        assertEquals(0f, settleTarget(100f, 0f, anchors))
        assertEquals(-300f, settleTarget(-200f, 0f, anchors))
    }

    @Test
    fun `a flick decides even a short drag`() {
        val anchors = listOf(0f, 300f, -300f)
        assertEquals(300f, settleTarget(60f, 1500f, anchors))
        assertEquals(0f, settleTarget(250f, -1500f, anchors))
    }

    @Test
    fun `a stretched release springs back to the nearest stop`() {
        assertEquals(0f, settleTarget(-40f, 0f, listOf(0f, 300f)))
        assertEquals(300f, settleTarget(340f, 0f, listOf(0f, 300f)))
    }
}
