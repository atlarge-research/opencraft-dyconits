package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer

class Subscription<Message>(var bounds: Bounds, var callback: Consumer<Message>) {
    private val messageQueue: MutableList<DMessage<Message>> = ArrayList()
    private var timerSet = false
    private val lock = Any()
    val staleness: Long
        get() = Duration.between(bounds.timestampLastReset, Instant.now()).toMillis()
    var numericalError = 0
        private set

    private val logger = LoggerFactory.getLogger(javaClass)

    fun addMessage(msg: DMessage<Message>) {
        synchronized(lock) {
            messageQueue.add(msg)
            numericalError += msg.weight
            logger.trace("queue weight $numericalError")
            logger.trace("queue length ${messageQueue.size}")
            checkBounds()
        }
    }

    private fun checkBounds() {
        val timeSinceLastFlush = staleness
        if (bounds.staleness in 0..timeSinceLastFlush) {
            logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            flush()
        } else if (bounds.numerical in 0 until numericalError) {
            logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            flush()
        } else if (bounds.staleness >= 0 && !timerSet) {
            val delayMS = bounds.staleness - timeSinceLastFlush
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
        bounds.timestampLastReset = Instant.now()
        messageQueue.forEach { m -> callback.accept(m.message) }
        messageQueue.clear()
        numericalError = 0
    }

    fun countQueuedMessages(): Int {
        return messageQueue.size
    }

    fun close() {
        synchronized(lock) {
            // TODO prevent policy resetting bounds
            bounds = Bounds.ZERO
            flush()
        }
    }
}
