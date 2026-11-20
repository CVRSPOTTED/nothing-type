package dev.nothingtype.ime

import dev.nothingtype.ime.GlyphSync.Companion.MATRIX_SIZE
import dev.nothingtype.settings.GlyphEffect
import kotlin.math.abs
import kotlin.math.hypot

/** Typing events carry only their timing here. The latest character is rendered separately. */
internal class GlyphEffects {
    private data class Hit(val time: Long, val center: Pair<Int, Int>)
    private data class Spark(val delay: Int, val x: Int, val y: Int)

    private val hits = ArrayDeque<Hit>()
    private val center = MATRIX_SIZE / 2
    private val distances = DoubleArray(MATRIX_SIZE * MATRIX_SIZE) { index ->
        hypot((index % MATRIX_SIZE - center).toDouble(), (index / MATRIX_SIZE - center).toDouble())
    }
    private var nextCenter = 0

    fun tap(now: Long) {
        if (hits.size == MAX_HITS) hits.removeFirst()
        hits.addLast(Hit(now, centers[nextCenter]))
        nextCenter = (nextCenter + 1) % centers.size
    }

    fun clear() {
        hits.clear()
        nextCenter = 0
    }

    /** One byte per pixel: 0 is off, 1 and 2 are the effect levels. */
    fun levels(effect: GlyphEffect, now: Long): ByteArray? {
        while (hits.firstOrNull()?.let { now - it.time >= effect.durationMs } == true) hits.removeFirst()
        if (hits.isEmpty()) return null

        val pixels = ByteArray(distances.size)
        for (hit in hits) {
            val age = (now - hit.time).coerceAtLeast(0L)
            when (effect) {
                GlyphEffect.PULSE -> pulse(pixels, age)
                GlyphEffect.SCANNER -> scan(pixels, age)
                GlyphEffect.SPARKS -> scatter(pixels, age, hit.center)
            }
        }
        return pixels
    }

    private fun pulse(pixels: ByteArray, age: Long) {
        val radius = age * 0.025
        val level = if (age < 280) 2 else 1
        for (index in pixels.indices) {
            val distance = distances[index]
            if (distance <= MATRIX_RADIUS && abs(distance - radius) < 0.9) {
                pixels[index] = maxOf(pixels[index].toInt(), level).toByte()
            }
        }
    }

    private fun scan(pixels: ByteArray, age: Long) {
        val stripe = -center + age * 0.048
        for (y in -9..9) {
            for (x in -center..center) {
                val index = (y + center) * MATRIX_SIZE + x + center
                val distance = abs(x - stripe)
                if (distance < 2 && distances[index] <= MATRIX_RADIUS) {
                    val level = if (distance < 0.9) 2 else 1
                    pixels[index] = maxOf(pixels[index].toInt(), level).toByte()
                }
            }
        }
    }

    private fun scatter(pixels: ByteArray, age: Long, origin: Pair<Int, Int>) {
        for (spark in sparks) {
            val elapsed = age - spark.delay
            if (elapsed !in 0L..234L) continue
            val spread = if (elapsed < 120) 1 else 2
            val x = origin.first + spark.x * spread + center
            val y = origin.second + spark.y * spread + center
            if (x !in 0 until MATRIX_SIZE || y !in 0 until MATRIX_SIZE) continue
            val index = y * MATRIX_SIZE + x
            if (distances[index] <= MATRIX_RADIUS) {
                val level = if (elapsed < 100) 2 else 1
                pixels[index] = maxOf(pixels[index].toInt(), level).toByte()
            }
        }
    }

    private companion object {
        const val MAX_HITS = 8
        const val MATRIX_RADIUS = 12.4
        val centers = listOf(-4 to 2, 5 to -3, -1 to -5, 4 to 5, -6 to -1, 2 to 2)
        val sparks = listOf(Spark(0, -1, -1), Spark(27, 2, 0), Spark(48, -1, 2),
            Spark(64, 2, 2), Spark(80, -3, -1), Spark(108, 0, -3))
    }
}
