package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.LongAdder

class Subscription<SubKey, Message>(
    val sub: SubKey,
    bounds: Bounds,
    callback: MessageChannel<Message>,
    private val messageQueue: MessageQueue<Message>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    var bounds: Bounds = bounds
        private set
    var callback: MessageChannel<Message> = callback
        private set
    private var timerSet = false
    private var stopped = false
    var timestampLastReset = Instant.now()
        private set
    val staleness: Duration
        get() = Duration.between(timestampLastReset, Instant.now())
    var numericalError = LongAdder()
        private set
    var firstMessageQueued: Instant = Instant.now()
        private set

    private val logger = LoggerFactory.getLogger(javaClass)

    fun addMessage(msg: DMessage<Message>) {
        messageQueue.add(msg.message)
        numericalError.add(msg.weight.toLong())
//        lock.withLock {
//            if (messageQueue.isEmpty()) {
//                firstMessageQueued = Instant.now()
//            }
//            messageQueue.add(msg)
//            numericalError += msg.weight
//            // logger.trace("queue weight $numericalError")
//            // logger.trace("queue length ${messageQueue.size}")
//            checkBounds()
//        }
    }

    private fun boundsExceeded(): Error {
        val timeSinceLastFlush = staleness
        val numError = numericalError.toInt()
        if (bounds.staleness in 0..timeSinceLastFlush.toMillis()) {
            // logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            return Error(timeSinceLastFlush, numError, true)
        } else if (bounds.numerical in 0 until numError) {
            // logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            return Error(timeSinceLastFlush, numError, true)
        }
        return Error(timeSinceLastFlush, numError, false)
    }

    private fun send() {
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

        while (!messageQueue.isEmpty()) {
            callback.send(messageQueue.remove())
        }
        timestampLastReset = now
        numericalError.reset()
    }

    fun synchronize(): Error {
        val error = boundsExceeded()
        if (error.exceedsBounds) {
            send()
        }
        return error
    }

    fun update(bounds: Bounds = this.bounds, callback: MessageChannel<Message> = this.callback) {
//        PerformanceCounterLogger.instance.updateBounds(bounds, previous = this.bounds)
        this.bounds = bounds
        this.callback = callback
    }

    // FIXME fix close
    fun close() = runBlocking {
        // TODO prevent policy resetting bounds
        bounds = Bounds.ZERO
//            flush()
        stopped = true
    }
}
