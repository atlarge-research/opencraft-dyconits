package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

class Subscription<SubKey, Message>(
    val sub: SubKey,
    bounds: Bounds,
    callback: MessageChannel<Message>
) {
    var bounds: Bounds = bounds
        private set
    var callback: MessageChannel<Message> = callback
        private set
    private val messageQueue: MutableList<DMessage<Message>> = ArrayList()
    private var timerSet = false
    private val lock = Mutex()
    var timestampLastReset = Instant.now()
        private set
    val staleness: Duration
        get() = Duration.between(timestampLastReset, Instant.now())
    var numericalError = 0
        private set
    var firstMessageQueued: Instant = Instant.now()
        private set

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
//        PerformanceCounterLogger.instance.updateBounds(bounds)
    }

    fun addMessage(msg: DMessage<Message>) = runBlocking {
        lock.withLock {
            if (messageQueue.isEmpty()) {
                firstMessageQueued = Instant.now()
            }
            messageQueue.add(msg)
            numericalError += msg.weight
            // logger.trace("queue weight $numericalError")
            // logger.trace("queue length ${messageQueue.size}")
            checkBounds()
        }
    }

    private fun checkBounds() {
        val timeSinceLastFlush = staleness
        if (bounds.staleness in 0..timeSinceLastFlush.toMillis()) {
            // logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            flush()
        } else if (bounds.numerical in 0 until numericalError) {
            // logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            flush()
        } else if (bounds.staleness >= 0 && !timerSet) {
            val delayMS = bounds.staleness - timeSinceLastFlush.toMillis()
            GlobalScope.launch {
                delay(delayMS)
//                logger.trace(
//                    "flush cause timer ${
//                        staleness.toMillis()
//                    } ~>= ${bounds.staleness}"
//                )
                lock.withLock { flush() }
                timerSet = false
            }
            timerSet = true
        }
    }

    private fun flush() {
        timerSet = false
//        val instance = PerformanceCounterLogger.instance
//        val sum = messageQueue.map { it.weight }.sum()
        val now = Instant.now()

//        instance.messagesSent.addAndGet(messageQueue.size)
//        instance.numericalErrorSent.addAndGet(sum)
//        instance.removeNumericalError(sub!!, sum)
//        if (messageQueue.isNotEmpty()) {
//            instance.removeStaleness(sub, Duration.between(firstMessageQueued, now))
//        }

        messageQueue.forEach { m -> callback.send(m.message) }
        callback.flush()

        timestampLastReset = now
        messageQueue.clear()
        numericalError = 0
    }

    fun update(bounds: Bounds = this.bounds, callback: MessageChannel<Message> = this.callback) {
//        PerformanceCounterLogger.instance.updateBounds(bounds, previous = this.bounds)
        this.bounds = bounds
        this.callback = callback
    }

    fun close() = runBlocking {
        lock.withLock {
            // TODO prevent policy resetting bounds
            bounds = Bounds.ZERO
            flush()
        }
    }
}
