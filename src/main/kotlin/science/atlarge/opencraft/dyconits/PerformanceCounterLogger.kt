package science.atlarge.opencraft.dyconits

import java.io.File
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PerformanceCounterLogger(val logFilePath: String = "dyconits.log") {

    var dyconitsCreated = AtomicInteger(0)
    var dyconitsRemoved = AtomicInteger(0)
    var messagesQueued = AtomicInteger(0)
    var messagesSent = AtomicInteger(0)
    var numericalErrorQueued = AtomicInteger(0)
    var numericalErrorSent = AtomicInteger(0)
    private val numericalErrorAdded = LinkedList<NumericalErrorEntry>()
    private val numericalErrorRemoved = LinkedList<NumericalErrorEntry>()
    private val stalenessRemoved = LinkedList<StalenessEntry>()
    private val lock = ReentrantLock()

    private val file = File(logFilePath)

    companion object Factory {
        val instance = PerformanceCounterLogger()
    }

    data class NumericalErrorEntry(
        val timestamp: Instant,
        val sub: String,
        val error: Int,
    )

    data class StalenessEntry(
        val timestamp: Instant,
        val sub: String,
        val error: Instant,
    )

    fun addNumericalError(sub: String, error: Int) {
        lock.withLock {
            numericalErrorAdded.add(NumericalErrorEntry(Instant.now(), sub, error))
        }
    }

    fun removeNumericalError(sub: String, error: Int) {
        lock.withLock {
            numericalErrorRemoved.add(NumericalErrorEntry(Instant.now(), sub, error))
        }
    }

    fun removeStaleness(sub: String, lastReset: Instant) {
        lock.withLock {
            stalenessRemoved.add(StalenessEntry(Instant.now(), sub, lastReset))
        }
    }

    fun log() {
        val now = Instant.now()
        val builder = StringBuilder()
        lock.withLock {
            for (entry in numericalErrorAdded) {
                builder.appendLine("${entry.timestamp} numericalErrorAdded ${entry.sub} ${entry.error}")
            }
            numericalErrorAdded.clear()
            for (entry in numericalErrorRemoved) {
                builder.appendLine("${entry.timestamp} numericalErrorAdded ${entry.sub} ${entry.error}")
            }
            numericalErrorRemoved.clear()
            for (entry in stalenessRemoved) {
                builder.appendLine("${entry.timestamp} numericalErrorAdded ${entry.sub} ${entry.error}")
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
