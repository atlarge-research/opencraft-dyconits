package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.concurrent.withLock

class Subscription<SubKey, Message>(val sub: SubKey, var bounds: Bounds, var callback: Consumer<Message>) {
    private val messageQueue: MutableList<DMessage<Message>> = ArrayList()
    private var timerSet = false
    private val lock = ReentrantLock()
    val staleness: Duration
        get() = Duration.between(bounds.timestampLastReset, Instant.now())
    var numericalError = 0
        private set
    var firstMessageQueued: Instant = Instant.now()
        private set

    private
    val logger = LoggerFactory.getLogger(javaClass)

    fun addMessage(msg: DMessage<Message>) {
        lock.withLock {
            if (messageQueue.isEmpty()) {
                firstMessageQueued = Instant.now()
            }
            messageQueue.add(msg)
            numericalError += msg.weight
            logger.trace("queue weight $numericalError")
            logger.trace("queue length ${messageQueue.size}")
            checkBounds()
        }
    }

    private fun checkBounds() {
        val timeSinceLastFlush = staleness
        if (bounds.staleness in 0..timeSinceLastFlush.toMillis()) {
            logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            flush()
        } else if (bounds.numerical in 0 until numericalError) {
            logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            flush()
        } else if (bounds.staleness >= 0 && !timerSet) {
            val delayMS = bounds.staleness - timeSinceLastFlush.toMillis()
            GlobalScope.launch {
                delay(delayMS)
                logger.trace(
                    "flush cause timer ${
                        Duration.between(
                            bounds.timestampLastReset,
                            Instant.now()
                        ).toMillis()
                    } ~>= ${bounds.staleness}"
                )
                flush()
                timerSet = false
            }
            timerSet = true
        }
    }

    private fun flush() {
        timerSet = false
        val instance = PerformanceCounterLogger.instance
        val sum = messageQueue.map { it.weight }.sum()
        val now = Instant.now()

        instance.messagesSent.addAndGet(messageQueue.size)
        instance.numericalErrorSent.addAndGet(sum)
        instance.removeNumericalError(sub!!, sum)
        if (messageQueue.isNotEmpty()) {
            instance.removeStaleness(sub, Duration.between(firstMessageQueued, now))
        }

        messageQueue.forEach { m -> callback.accept(m.message) }

        bounds.timestampLastReset = now
        messageQueue.clear()
        numericalError = 0
    }

    fun close() {
        lock.withLock {
            // TODO prevent policy resetting bounds
            bounds = Bounds.ZERO
            flush()
        }
    }
}
