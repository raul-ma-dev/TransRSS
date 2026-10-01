package com.rama.rss.ui.rocket

import kotlin.math.floor
import kotlin.random.Random

/** Coordinates and velocities use dp and seconds, independent of screen density. */
internal data class RocketSpark(
    var x: Float,
    var y: Float,
    val velocityX: Float,
    var velocityY: Float,
    val gravity: Float,
    val lifetime: Float,
    val radius: Float,
    val warmth: Float,
    var age: Float = 0f
) {
    val remainingFraction: Float get() = (1f - age / lifetime).coerceIn(0f, 1f)
}

internal class RocketParticleSystem(
    private val random: Random = Random.Default,
    private val maxParticles: Int = 160
) {
    init { require(maxParticles > 0) }

    private val sparks = ArrayList<RocketSpark>(maxParticles)
    private var emissionCredit = 0f
    val particles: List<RocketSpark> get() = sparks

    fun emitBurst(count: Int, emitterX: Float, emitterY: Float, launching: Boolean) {
        repeat(count.coerceIn(0, maxParticles - sparks.size)) {
            sparks.add(
                RocketSpark(
                    x = emitterX + between(-8f, 8f),
                    y = emitterY + between(-4f, 4f),
                    velocityX = if (launching) between(-90f, 90f) else between(-180f, 180f),
                    velocityY = if (launching) between(100f, 260f) else between(-360f, -160f),
                    gravity = between(400f, 800f),
                    lifetime = between(0.4f, 1.1f),
                    radius = between(0.8f, 2.2f),
                    warmth = random.nextFloat()
                )
            )
        }
    }

    fun advance(
        deltaSeconds: Float,
        emitterX: Float = 0f,
        emitterY: Float = 0f,
        emissionRate: Float = 0f,
        launching: Boolean = false
    ) {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f)
        require(emissionRate.isFinite() && emissionRate >= 0f)
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val spark = iterator.next()
            spark.age += deltaSeconds
            if (spark.age >= spark.lifetime) {
                iterator.remove()
            } else {
                spark.x += spark.velocityX * deltaSeconds
                spark.y += spark.velocityY * deltaSeconds + 0.5f * spark.gravity * deltaSeconds * deltaSeconds
                spark.velocityY += spark.gravity * deltaSeconds
            }
        }
        if (emissionRate > 0f) {
            emissionCredit += emissionRate * deltaSeconds
            val count = floor(emissionCredit).toInt()
            emissionCredit -= count
            emitBurst(count, emitterX, emitterY, launching)
        } else {
            emissionCredit = 0f
        }
    }

    private fun between(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)
}

