package science.atlarge.opencraft.dyconits

import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer

class Subscription<Message>(var bounds: Bounds, var callback: Consumer<Message>) {
    private val messageQueue: MutableList<DMessage<Message>> = Collections.synchronizedList(ArrayList())
    private val timer = Timer()
    private var timerSet = false
    private val lock = Any()

    fun addMessage(msg: DMessage<Message>) {
        messageQueue.add(msg)
        // TODO can be optimized by keeping a running total weight.
        checkBounds()
    }

    private fun checkBounds() {
        val timeSinceLastFlush = Duration.between(bounds.timestampLastReset, Instant.now()).toMillis()
        if (timeSinceLastFlush >= bounds.staleness) {
            flush()
        } else if (messageQueue.map { m -> m.weight }.sum() > bounds.numerical) {
            flush()
        } else if (!timerSet) {
            val delay = bounds.staleness - timeSinceLastFlush
            timer.schedule(kotlin.concurrent.timerTask { flush(); timerSet = false }, delay)
            timerSet = true
        }
    }

    private fun flush() {
        synchronized(lock) {
            timerSet = false
            bounds.timestampLastReset = Instant.now()
            messageQueue.forEach { m -> callback.accept(m.message) }
            messageQueue.clear()
        }
    }
}
