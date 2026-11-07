package app.tessera.ui.editor.engine

import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fuzzer with real text measuring (native graphics: lines wrap, headings are taller): every 10 steps and at the end of a session the
 * live layout must equal a fresh one (line tops, numbers, the spacing the spans measured with). A separate class, as native graphics is
 * the one that can flake (`RenderNodeNatives`): re-run it alone. 120 sessions × 120 steps per class; ×4 in the deep mode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
abstract class EngineFuzzLayoutBase(private val seedFrom: Int) {
    @Test
    fun theLiveLayoutIsAFreshOneAfterEverySession() {
        val n = if (EngineFuzz.deep) SESSIONS * 4 else SESSIONS
        val failures = ArrayList<String>()
        val t0 = System.currentTimeMillis()
        for (seed in seedFrom until seedFrom + n) {
            val start = EngineFuzz.startOfLayout(seed)
            val steps = EngineFuzz.sequence(seed, STEPS)
            val f = EngineFuzz.runSteps(start, steps, layoutEvery = 10, label = "seed $seed") ?: continue
            failures += EngineFuzz.report(seed, start, steps, f, layoutEvery = 10)
            if (failures.size >= 3) break
        }
        println("EngineFuzz ${this::class.simpleName}: $n sessions from seed $seedFrom, ${System.currentTimeMillis() - t0} ms, ${failures.size} failing")
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    companion object {
        const val SESSIONS = 120
        const val STEPS = 120
    }
}

class EngineFuzzLayoutSeeds0 : EngineFuzzLayoutBase(100_000)
class EngineFuzzLayoutSeeds1 : EngineFuzzLayoutBase(110_000)
