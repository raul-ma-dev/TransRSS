package com.rama.rss.ui.rocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RocketParticleSystemTest {
    @Test
    fun sparksHaveIndependentVelocitiesGravityAndLifetimes() {
        val system = RocketParticleSystem(Random(42))
        system.emitBurst(30, 100f, 200f, launching = false)
        assertEquals(30, system.particles.size)
        assertTrue(system.particles.map { it.velocityX }.distinct().size > 1)
        assertTrue(system.particles.map { it.velocityY }.distinct().size > 1)
        assertTrue(system.particles.map { it.gravity }.distinct().size > 1)
        assertTrue(system.particles.map { it.lifetime }.distinct().size > 1)
        assertTrue(system.particles.all { it.velocityY < 0f && it.gravity > 0f })
    }

    @Test
    fun motionUsesIndividualVelocityAndGravity() {
        val system = RocketParticleSystem(Random(7))
        system.emitBurst(1, 100f, 200f, launching = true)
        val before = system.particles.single().copy()
        system.advance(0.1f)
        val after = system.particles.single()
        assertEquals(before.x + before.velocityX * 0.1f, after.x, 0.001f)
        assertEquals(before.y + before.velocityY * 0.1f + before.gravity * 0.005f, after.y, 0.001f)
        assertEquals(before.velocityY + before.gravity * 0.1f, after.velocityY, 0.001f)
        assertEquals(0.1f, after.age, 0.0001f)
        assertTrue(after.remainingFraction in 0f..1f)
    }

    @Test
    fun trajectoriesDoNotDependOnFrameRate() {
        fun seeded() = RocketParticleSystem(Random(9)).apply { emitBurst(12, 0f, 0f, launching = false) }
        val singleStep = seeded()
        val multipleSteps = seeded()
        singleStep.advance(0.3f)
        repeat(3) { multipleSteps.advance(0.1f) }
        singleStep.particles.zip(multipleSteps.particles).forEach { (first, second) ->
            assertEquals(first.x, second.x, 0.001f)
            assertEquals(first.y, second.y, 0.001f)
            assertEquals(first.velocityY, second.velocityY, 0.001f)
        }
    }

    @Test
    fun sparksExpireIndependentlyAndEventuallyDisappear() {
        val system = RocketParticleSystem(Random(1))
        system.emitBurst(60, 0f, 0f, launching = false)
        system.advance(0.7f)
        assertTrue(system.particles.size in 1..59)
        assertTrue(system.particles.all { it.age < it.lifetime && it.remainingFraction < 1f })
        system.advance(2f)
        assertTrue(system.particles.isEmpty())
    }

    @Test
    fun emissionAccumulatesFractionsWithoutEmittingAfterStop() {
        val system = RocketParticleSystem(Random(2))
        repeat(5) { system.advance(0.01f, emissionRate = 10f) }
        assertTrue(system.particles.isEmpty())
        system.advance(0.06f, emissionRate = 10f)
        assertEquals(1, system.particles.size)
        system.advance(0.01f)
        assertEquals(1, system.particles.size)
        system.advance(0.05f, emissionRate = 10f)
        assertEquals(1, system.particles.size)
    }

    @Test
    fun particleCountIsBoundedAndSlotsAreReusedAfterExpiry() {
        val system = RocketParticleSystem(Random(3), maxParticles = 12)
        repeat(20) { system.advance(0.01f, emissionRate = 20_000f, launching = true) }
        assertEquals(12, system.particles.size)
        system.advance(2f)
        system.emitBurst(5, 0f, 0f, launching = false)
        assertEquals(5, system.particles.size)
    }

    @Test
    fun movingEmitterOnlyChangesNewSparksOrigin() {
        val system = RocketParticleSystem(Random(4))
        system.emitBurst(1, 100f, 200f, launching = false)
        val firstPosition = system.particles.first().let { it.x to it.y }
        system.emitBurst(1, 300f, -100f, launching = true)
        assertEquals(firstPosition, system.particles.first().let { it.x to it.y })
        val newSpark = system.particles.last()
        assertTrue(newSpark.x in 292f..308f)
        assertTrue(newSpark.y in -104f..-96f)
        assertTrue(newSpark.velocityY > 0f)
    }
}

