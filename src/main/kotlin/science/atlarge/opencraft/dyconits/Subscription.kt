package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

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
    private val messageChannel = Channel<ChannelItem<DMessage<Message>>>(Channel.UNLIMITED)
    private var timerSet = false
    private var stopped = false
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
        GlobalScope.launch(dispatcher) {
            kotlin.runCatching {
                while (!stopped) {
                    when (val msg = messageChannel.receive()) {
                        is ChannelMessage<DMessage<Message>> -> {
                            messageQueue.add(msg.msg.message)
                            numericalError += msg.msg.weight
                            if (boundsExceeded()) {
                                flush()
                            }
                        }
                        is FlushToken -> flush()
                    }
                }
            }.onFailure {
                logger.error("Failure in sync coroutine", it)
            }
        }
    }

    fun addMessage(msg: DMessage<Message>) {
        messageChannel.sendBlocking(ChannelMessage(msg))
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

    private fun boundsExceeded(): Boolean {
        val timeSinceLastFlush = staleness
        if (bounds.staleness in 0..timeSinceLastFlush.toMillis()) {
            // logger.trace("flush cause staleness $timeSinceLastFlush >= ${bounds.staleness}")
            return true
        } else if (bounds.numerical in 0 until numericalError) {
            // logger.trace("flush cause numerical $numericalError > ${bounds.numerical}")
            return true
        } else if (bounds.staleness >= 0 && !timerSet) {
            val delayMS = bounds.staleness - timeSinceLastFlush.toMillis()
            GlobalScope.launch {
                delay(delayMS)
//                logger.trace(
//                    "flush cause timer ${
//                        staleness.toMillis()
//                    } ~>= ${bounds.staleness}"
//                )
                messageChannel.send(FlushToken())
                timerSet = false
            }
            timerSet = true
        }
        return false
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

        messageQueue.forEach { m -> callback.send(m) }
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

    // FIXME fix close
    fun close() = runBlocking {
        // TODO prevent policy resetting bounds
        bounds = Bounds.ZERO
//            flush()
        stopped = true
    }
}
