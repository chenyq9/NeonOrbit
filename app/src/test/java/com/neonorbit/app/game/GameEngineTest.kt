package com.neonorbit.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun start_resetsRoundAndRuns() {
        val engine = GameEngine(initialBestScore = 123, seed = 1)
        engine.start()
        val state = engine.snapshot()
        assertEquals(GameStatus.RUNNING, state.status)
        assertEquals(0, state.score)
        assertEquals(123, state.bestScore)
        assertEquals(OrbitLane.OUTER, state.lane)
    }

    @Test
    fun switchLane_onlyWorksWhileRunning() {
        val engine = GameEngine(seed = 2)
        engine.switchLane()
        assertEquals(OrbitLane.OUTER, engine.snapshot().lane)
        engine.start()
        engine.switchLane()
        assertEquals(OrbitLane.INNER, engine.snapshot().lane)
    }

    @Test
    fun simulation_speedIncreasesOverTime() {
        val engine = GameEngine(seed = 3)
        engine.start()
        val startSpeed = engine.snapshot().speed
        repeat(1000) { engine.tick(0.05f) }
        assertTrue(engine.snapshot().speed > startSpeed)
    }

    @Test
    fun tick_isClampedToAvoidHugeFrameJumps() {
        val engine = GameEngine(seed = 4)
        engine.start()
        engine.tick(5f)
        assertTrue(engine.snapshot().elapsedSeconds <= 0.051f)
    }
}
