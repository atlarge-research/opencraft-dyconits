package science.atlarge.opencraft.dyconits

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.LongAdder

class Subscription<Message>(
    bounds: Bounds,
    callback: MessageChannel<Message>,
    private val messageQueue: MessageQueue<Message>,
) {
    var bounds: Bounds = bounds
        private set
    var callback: MessageChannel<Message> = callback
        private set
    private var timerSet = false
    var timestampLastReset: Instant = Instant.now()
        private set
    val staleness: Duration
        get() = Duration.between(timestampLastReset, Instant.now())
    var numericalError = LongAdder()
        private set

    fun addMessage(msg: DMessage<Message>) {
        messageQueue.add(msg.message)
        numericalError.add(msg.weight.toLong())
    }

    private fun boundsExceeded(): Error {
        val timeSinceLastFlush = staleness
        val numError = numericalError.toInt()
        if (bounds.staleness in 0..timeSinceLastFlush.toMillis()) {
            return Error(timeSinceLastFlush, numError, true)
        } else if (bounds.numerical in 0 until numError) {
            return Error(timeSinceLastFlush, numError, true)
        }
        return Error(timeSinceLastFlush, numError, false)
    }

    private fun send() {
        timerSet = false
        val now = Instant.now()

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
        this.bounds = bounds
        this.callback = callback
    }
}
