package io.simplematter.mqtt.load.utils

import java.lang.AssertionError
import java.util.concurrent.TimeoutException
import kotlin.random.Random


object RandomUtils {
    private val random = Random(System.currentTimeMillis())

    /**
     * Generates n unique random numbers from 0 (inclusive) to max (exclusive)
     */
    fun uniqueRandomNumbers(max: Int, n: Int): Set<Int> {
        if(max < n) {
            throw AssertionError("Can't generate ${n} unique random numbers from ${max}")
        }
        val res = mutableSetOf<Int>()
        val maxAttempts = n * 100
        var attempt = 0
        while(res.size < n) {
            res.add(random.nextInt(max))
            attempt++
            if(attempt > maxAttempts) {
                throw AssertionError("Unable to generate ${n} unique random numbers from ${max} after ${attempt} iterations")
            }
        }
        return res
    }

    fun <T>pickRandomElements(elements: List<T>, n: Int): List<T> {
        val indexes = uniqueRandomNumbers(elements.size, n)
        return elements.filterIndexed { i, v -> indexes.contains(i) }
    }
}