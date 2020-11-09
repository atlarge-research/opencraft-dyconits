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
    private var headerWritten = false
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
        val builder = StringBuilder()
        if (!headerWritten) {
            builder.appendLine("timestamp\tscope\tkey\tvalue")
            headerWritten = true
        }
        val now = Instant.now().toEpochMilli()
        lock.withLock {
            for (entry in numericalErrorAdded) {
                builder.appendLine("$now\t${entry.key}\tnumericalErrorAdded\t${entry.value}")
            }
            numericalErrorAdded.clear()
            for (entry in numericalErrorRemoved) {
                builder.appendLine("$now\t${entry.key}\tnumericalErrorRemoved\t${entry.value}")
            }
            numericalErrorRemoved.clear()
            for (entry in stalenessRemoved) {
                builder.appendLine("$now\t${entry.key}\tstalenessRemoved\t${entry.value.toMillis()}")
            }
            stalenessRemoved.clear()
        }
        builder.appendLine("$now\tglobal\tdyconitsCreated\t$dyconitsCreated")
        builder.appendLine("$now\tglobal\tdyconitsRemoved\t$dyconitsRemoved")
        builder.appendLine("$now\tglobal\tmessagesQueued\t$messagesQueued")
        builder.appendLine("$now\tglobal\tmessagesSent\t$messagesSent")
        builder.appendLine("$now\tglobal\tnumericalErrorQueued\t$numericalErrorQueued")
        builder.appendLine("$now\tglobal\tnumericalErrorSent\t$numericalErrorSent")
        repeat(queue.size) {
            builder.appendLine(queue.take())
        }
        file.writeText(builder.toString())
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
            queue.put("$now\tglobal\tnumericalBoundsTotal\t$numericalBoundsTotal")
        }

        bounds.numerical.takeIf { it > numericalBoundsMax }?.let {
            numericalBoundsMax = it
            queue.put("$now\tglobal\tnumericalBoundsMax\t$numericalBoundsMax")
        }

        (bounds.staleness - previous.staleness).takeIf { it != 0 }?.let {
            stalenessBoundsTotal += it
            queue.put("$now\tglobal\tstalenessBoundsTotal\t$stalenessBoundsTotal")
        }

        bounds.staleness.takeIf { it > stalenessBoundsMax }?.let {
            stalenessBoundsMax = it
            queue.put("$now\tglobal\tstalenessBoundsMax\t$stalenessBoundsMax")
        }
    }
}
