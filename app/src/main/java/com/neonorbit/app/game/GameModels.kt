package com.neonorbit.app.game

import kotlin.math.PI

enum class GameStatus { READY, RUNNING, PAUSED, GAME_OVER }
enum class OrbitLane { INNER, OUTER }
enum class ParticleKind { CYAN, VIOLET, WHITE, DANGER }

data class Hazard(
    val id: Long,
    val lane: OrbitLane,
    val distance: Float,
    val variant: Int,
)

data class EnergyShard(
    val id: Long,
    val lane: OrbitLane,
    val distance: Float,
)

data class Particle(
    val id: Long,
    val laneRadius: Float,
    val angle: Float,
    val radialVelocity: Float,
    val angularVelocity: Float,
    val age: Float,
    val ttl: Float,
    val size: Float,
    val kind: ParticleKind,
)

data class GameSnapshot(
    val status: GameStatus = GameStatus.READY,
    val lane: OrbitLane = OrbitLane.OUTER,
    val score: Int = 0,
    val bestScore: Int = 0,
    val elapsedSeconds: Float = 0f,
    val speed: Float = 1.35f,
    val energy: Float = 0f,
    val surgeRemaining: Float = 0f,
    val combo: Int = 0,
    val shake: Float = 0f,
    val hazards: List<Hazard> = emptyList(),
    val shards: List<EnergyShard> = emptyList(),
    val particles: List<Particle> = emptyList(),
) {
    val surgeActive: Boolean get() = surgeRemaining > 0f
}

sealed interface GameEvent {
    data object LaneSwitched : GameEvent
    data object ShardCollected : GameEvent
    data object SurgeStarted : GameEvent
    data object HazardDestroyed : GameEvent
    data object Crashed : GameEvent
    data object NewBest : GameEvent
}

internal const val PLAYER_ANGLE: Float = (-PI / 2.0).toFloat()
internal const val TAU: Float = (PI * 2.0).toFloat()
