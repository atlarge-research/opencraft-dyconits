package science.atlarge.opencraft.dyconits

import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PerformanceCounterLogger private constructor(val logFilePath: String = "dyconits.log") {

    var dyconitsCreated = AtomicInteger(0)
    var dyconitsRemoved = AtomicInteger(0)
    var messagesQueued = AtomicInteger(0)
    var messagesSent = AtomicInteger(0)
    var numericalErrorQueued = AtomicInteger(0)
    var numericalErrorSent = AtomicInteger(0)

    private val numericalErrorAdded = HashMap<Any, Int>()
    private val numericalErrorRemoved = HashMap<Any, Int>()
    private val stalenessRemoved = HashMap<Any, Duration>()

    private val queue: BlockingQueue<String> = LinkedBlockingDeque()
    private var firstLog = true
    private var numericalBoundsTotal = 0L
    private var numericalBoundsMax = 0
    private var stalenessBoundsTotal = 0L
    private var stalenessBoundsMax = 0

    private val lock = ReentrantLock()

    private val file = File(logFilePath)

    companion object Factory {
        val instance = PerformanceCounterLogger()
    }

    fun addNumericalError(sub: Any, error: Int) {
        lock.withLock {
            when (val count = numericalErrorAdded[sub]) {
                null -> numericalErrorAdded[sub] = error
                else -> numericalErrorAdded[sub] = count + error
            }
        }
    }

    fun removeNumericalError(sub: Any, error: Int) {
        lock.withLock {
            when (val count = numericalErrorRemoved[sub]) {
                null -> numericalErrorRemoved[sub] = error
                else -> numericalErrorRemoved[sub] = count + error
            }
        }
    }

    fun removeStaleness(sub: Any, delay: Duration) {
        lock.withLock {
            val prevDelay = stalenessRemoved[sub]
            when {
                prevDelay == null -> stalenessRemoved[sub] = delay
                delay > prevDelay -> stalenessRemoved[sub] = delay
            }
        }
    }

    fun log() {
        val now = Instant.now().toEpochMilli()
        val builder = StringBuilder()
        lock.withLock {
            for (entry in numericalErrorAdded) {
                builder.appendLine("$now numericalErrorAdded ${entry.key} ${entry.value}")
            }
            numericalErrorAdded.clear()
            for (entry in numericalErrorRemoved) {
                builder.appendLine("$now numericalErrorRemoved ${entry.key} ${entry.value}")
            }
            numericalErrorRemoved.clear()
            for (entry in stalenessRemoved) {
                builder.appendLine("$now stalenessRemoved ${entry.key} ${entry.value.toMillis()}")
            }
            stalenessRemoved.clear()
        }
        builder.appendLine("$now dyconitsCreated $dyconitsCreated")
        builder.appendLine("$now dyconitsRemoved $dyconitsRemoved")
        builder.appendLine("$now messagesQueued $messagesQueued")
        builder.appendLine("$now messagesSent $messagesSent")
        builder.appendLine("$now numericalErrorQueued $numericalErrorQueued")
        builder.appendLine("$now numericalErrorSent $numericalErrorSent")
        repeat(queue.size) {
            builder.appendLine(queue.take())
        }
        file.appendText(builder.toString())
    }

    /**
     * Update global bounds information (inconsistency bounds across all dyconits/subscriptions) based on
     * a bounds update on a single subscription.
     *
     * @param bounds The subscription's new bounds.
     * @param previous If the subscription is not new but updated: the old subscription bounds.
     */
    fun updateBounds(bounds: Bounds, previous: Bounds = Bounds.ZERO) {
        if (!firstLog && bounds == previous) {
            return
        }
        firstLog = false

        val now = Instant.now().toEpochMilli()

        (bounds.numerical - previous.numerical).takeIf { it != 0 }?.let {
            numericalBoundsTotal += it
            queue.put("$now numericalBoundsTotal $numericalBoundsTotal")
        }

        bounds.numerical.takeIf { it > numericalBoundsMax }?.let {
            numericalBoundsMax = it
            queue.put("$now numericalBoundsMax $numericalBoundsMax")
        }

        (bounds.staleness - previous.staleness).takeIf { it != 0 }?.let {
            stalenessBoundsTotal += it
            queue.put("$now stalenessBoundsTotal $stalenessBoundsTotal")
        }

        bounds.staleness.takeIf { it > stalenessBoundsMax }?.let {
            stalenessBoundsMax = it
            queue.put("$now stalenessBoundsMax $stalenessBoundsMax")
        }
    }
}
