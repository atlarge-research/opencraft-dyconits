package science.atlarge.opencraft.dyconits

import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer

class Subscription<Message>(var bounds: Bounds, var callback: Consumer<Message>) {
    private val messageQueue: MutableList<DMessage<Message>> = ArrayList()
    private val timer = Timer()
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
        if (timeSinceLastFlush >= bounds.staleness) {
            logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            flush()
        } else if (numericalError > bounds.numerical) {
            logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            flush()
        } else if (!timerSet) {
            val delay = bounds.staleness - timeSinceLastFlush
            timer.schedule(kotlin.concurrent.timerTask {
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
            }, delay)
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
}
