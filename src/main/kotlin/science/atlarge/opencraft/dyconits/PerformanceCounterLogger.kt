package science.atlarge.opencraft.dyconits

import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
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
        val now = Instant.now()
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
        file.appendText(builder.toString())
    }
}
