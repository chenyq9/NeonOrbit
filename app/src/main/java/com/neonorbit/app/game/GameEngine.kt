package com.neonorbit.app.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Deterministic, rendering-agnostic game simulation.
 * Distances are angular radians ahead of the player. Rendering simply maps
 * `PLAYER_ANGLE + distance` onto the chosen orbit radius.
 */
class GameEngine(
    initialBestScore: Int = 0,
    seed: Int = Random.nextInt(),
) {
    private val random = Random(seed)
    private var bestScore = initialBestScore
    private var status = GameStatus.READY
    private var lane = OrbitLane.OUTER
    private var elapsed = 0f
    private var scoreFloat = 0f
    private var energy = 0f
    private var surge = 0f
    private var combo = 0
    private var shake = 0f
    private var spawnTimer = 0.55f
    private var nextId = 1L

    private val hazards = mutableListOf<Hazard>()
    private val shards = mutableListOf<EnergyShard>()
    private val particles = mutableListOf<Particle>()

    fun snapshot(): GameSnapshot = GameSnapshot(
        status = status,
        lane = lane,
        score = scoreFloat.toInt(),
        bestScore = bestScore,
        elapsedSeconds = elapsed,
        speed = speedFor(elapsed),
        energy = energy,
        surgeRemaining = surge,
        combo = combo,
        shake = shake,
        hazards = hazards.toList(),
        shards = shards.toList(),
        particles = particles.toList(),
    )

    fun start(): List<GameEvent> {
        resetRound()
        status = GameStatus.RUNNING
        return emptyList()
    }

    fun restart(): List<GameEvent> = start()

    fun goHome() {
        resetRound()
        status = GameStatus.READY
    }

    fun pause() {
        if (status == GameStatus.RUNNING) status = GameStatus.PAUSED
    }

    fun resume() {
        if (status == GameStatus.PAUSED) status = GameStatus.RUNNING
    }

    fun switchLane(): List<GameEvent> {
        if (status != GameStatus.RUNNING) return emptyList()
        lane = if (lane == OrbitLane.INNER) OrbitLane.OUTER else OrbitLane.INNER
        return listOf(GameEvent.LaneSwitched)
    }

    fun tick(rawDt: Float): List<GameEvent> {
        if (status != GameStatus.RUNNING) return emptyList()
        val dt = rawDt.coerceIn(0f, 0.05f)
        if (dt <= 0f) return emptyList()

        val events = mutableListOf<GameEvent>()
        elapsed += dt
        scoreFloat += dt * (9f + speedFor(elapsed) * 1.8f)
        surge = max(0f, surge - dt)
        shake = max(0f, shake - dt * 3.2f)

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnWave()
            val difficulty = (elapsed / 100f).coerceIn(0f, 1f)
            spawnTimer += lerp(1.18f, 0.72f, difficulty) * lerp(0.90f, 1.10f, random.nextFloat())
        }

        val angularSpeed = speedFor(elapsed)
        updateHazards(dt, angularSpeed, events)
        if (status != GameStatus.RUNNING) {
            updateParticles(dt)
            return events
        }
        updateShards(dt, angularSpeed, events)
        updateParticles(dt)
        return events
    }

    private fun updateHazards(dt: Float, speed: Float, events: MutableList<GameEvent>) {
        val iterator = hazards.listIterator()
        while (iterator.hasNext()) {
            val old = iterator.next()
            val updated = old.copy(distance = old.distance - speed * dt)
            if (old.distance > 0f && updated.distance <= 0f) {
                iterator.remove()
                if (old.lane == lane) {
                    if (surge > 0f) {
                        scoreFloat += 30f
                        shake = max(shake, 0.45f)
                        burst(old.lane, ParticleKind.VIOLET, 16)
                        events += GameEvent.HazardDestroyed
                    } else {
                        shake = 1f
                        burst(old.lane, ParticleKind.DANGER, 26)
                        status = GameStatus.GAME_OVER
                        val finalScore = scoreFloat.toInt()
                        if (finalScore > bestScore) {
                            bestScore = finalScore
                            events += GameEvent.NewBest
                        }
                        events += GameEvent.Crashed
                        return
                    }
                } else {
                    combo = max(0, combo - 1)
                    scoreFloat += 4f
                }
            } else if (updated.distance < -0.25f) {
                iterator.remove()
            } else {
                iterator.set(updated)
            }
        }
    }

    private fun updateShards(dt: Float, speed: Float, events: MutableList<GameEvent>) {
        val iterator = shards.listIterator()
        while (iterator.hasNext()) {
            val old = iterator.next()
            val updated = old.copy(distance = old.distance - speed * dt)
            if (old.distance > 0f && updated.distance <= 0f) {
                iterator.remove()
                if (old.lane == lane) {
                    combo += 1
                    val comboBonus = min(combo, 8) * 3
                    scoreFloat += 24f + comboBonus
                    energy = min(100f, energy + 19f)
                    burst(old.lane, ParticleKind.CYAN, 12)
                    events += GameEvent.ShardCollected
                    if (energy >= 100f) {
                        energy = 0f
                        surge = 3.6f
                        shake = max(shake, 0.35f)
                        burst(old.lane, ParticleKind.WHITE, 24)
                        events += GameEvent.SurgeStarted
                    }
                } else {
                    combo = 0
                }
            } else if (updated.distance < -0.25f) {
                iterator.remove()
            } else {
                iterator.set(updated)
            }
        }
    }

    private fun spawnWave() {
        val baseDistance = TAU * 0.76f
        val hazardLane = if (random.nextBoolean()) OrbitLane.INNER else OrbitLane.OUTER
        hazards += Hazard(
            id = nextId++,
            lane = hazardLane,
            distance = baseDistance,
            variant = random.nextInt(3),
        )

        val safeLane = if (hazardLane == OrbitLane.INNER) OrbitLane.OUTER else OrbitLane.INNER
        val shardChance = 0.83f
        if (random.nextFloat() < shardChance) {
            // Place the reward slightly before or after the hazard. The offset is small
            // enough to create a readable decision, never a frame-perfect input.
            val offset = if (random.nextBoolean()) -0.42f else 0.38f
            shards += EnergyShard(nextId++, safeLane, baseDistance + offset)
        }

        if (elapsed > 22f && random.nextFloat() < 0.26f) {
            // A second safe-lane shard rewards staying composed after the first pass.
            shards += EnergyShard(nextId++, hazardLane, baseDistance + 1.05f)
        }
    }

    private fun burst(lane: OrbitLane, kind: ParticleKind, count: Int) {
        val radius = if (lane == OrbitLane.INNER) 0.64f else 0.86f
        repeat(count) {
            particles += Particle(
                id = nextId++,
                laneRadius = radius,
                angle = PLAYER_ANGLE + lerp(-0.05f, 0.05f, random.nextFloat()),
                radialVelocity = lerp(-0.18f, 0.26f, random.nextFloat()),
                angularVelocity = lerp(-2.3f, 2.3f, random.nextFloat()),
                age = 0f,
                ttl = lerp(0.28f, 0.72f, random.nextFloat()),
                size = lerp(2f, 5.5f, random.nextFloat()),
                kind = kind,
            )
        }
        if (particles.size > 110) {
            particles.subList(0, particles.size - 110).clear()
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.listIterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            val age = p.age + dt
            if (age >= p.ttl) {
                iterator.remove()
            } else {
                iterator.set(
                    p.copy(
                        age = age,
                        laneRadius = p.laneRadius + p.radialVelocity * dt,
                        angle = p.angle + p.angularVelocity * dt,
                        radialVelocity = p.radialVelocity * 0.97f,
                        angularVelocity = p.angularVelocity * 0.96f,
                    )
                )
            }
        }
    }

    private fun resetRound() {
        lane = OrbitLane.OUTER
        elapsed = 0f
        scoreFloat = 0f
        energy = 0f
        surge = 0f
        combo = 0
        shake = 0f
        spawnTimer = 0.55f
        hazards.clear()
        shards.clear()
        particles.clear()
    }

    private fun speedFor(seconds: Float): Float {
        val ramp = (seconds / 95f).coerceIn(0f, 1f)
        return 1.35f + 1.65f * smoothStep(ramp)
    }

    private fun smoothStep(x: Float): Float = x * x * (3f - 2f * x)

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
