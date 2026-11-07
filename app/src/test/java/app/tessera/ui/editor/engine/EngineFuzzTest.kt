package app.tessera.ui.editor.engine

import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fuzzer of Chunk 4c: [SESSIONS] sessions × [STEPS] steps per class, the seeds are fixed (`seedFrom until seedFrom + SESSIONS`). The
 * default run is 5 classes = 500 sessions × 200 steps = 100 000 steps; the deep mode (`ENGINE_FUZZ_DEEP=1`) runs 4 times as many seeds in
 * each class. A failing seed is printed with the minimized sequence (see [EngineFuzz.report]); every such sequence gets its own test in
 * [EngineFuzzRegressionTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class EngineFuzzBase(private val seedFrom: Int) {
    @Test
    fun sessionsKeepEveryInvariant() {
        val n = if (EngineFuzz.deep) SESSIONS * 4 else SESSIONS
        val failures = ArrayList<String>()
        val t0 = System.currentTimeMillis()
        for (seed in seedFrom until seedFrom + n) {
            val start = EngineFuzz.startOf(seed)
            val big = start.length > 20_000
            val steps = EngineFuzz.sequence(seed, if (big) 40 else STEPS)
            val f = EngineFuzz.runSteps(start, steps, label = "seed $seed") ?: continue
            failures += EngineFuzz.report(seed, start, steps, f)
            if (failures.size >= 3) break
        }
        println("EngineFuzz ${this::class.simpleName}: $n sessions from seed $seedFrom, ${System.currentTimeMillis() - t0} ms, ${failures.size} failing")
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    companion object {
        const val SESSIONS = 300
        const val STEPS = 200
    }
}

class EngineFuzzSeeds0 : EngineFuzzBase(0)
class EngineFuzzSeeds1 : EngineFuzzBase(1_000)
class EngineFuzzSeeds2 : EngineFuzzBase(2_000)
class EngineFuzzSeeds3 : EngineFuzzBase(3_000)
class EngineFuzzSeeds4 : EngineFuzzBase(4_000)
