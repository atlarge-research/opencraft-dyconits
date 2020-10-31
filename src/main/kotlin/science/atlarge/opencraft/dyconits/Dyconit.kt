package science.atlarge.opencraft.dyconits

import com.google.common.collect.Maps
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * A dyconit is similar to a _Topic_ in a Pub/Sub system,
 * and is the core of the bounded-inconsistency communication system in Opencraft.
 *
 * Outgoing messages are sent to a dyconit, which lazily forwards them to subscribers (players)
 * while preventing large inconsistency.
 */
class Dyconit<SubKey, Message>(val name: String) {

    private var subscriptions: MutableMap<SubKey, Subscription<SubKey, Message>> = Maps.newConcurrentMap()

    private val logger = LoggerFactory.getLogger(javaClass)

    fun addSubscription(sub: SubKey, bounds: Bounds, callback: Consumer<Message>) {
        when (val subscription = subscriptions[sub]) {
            null -> subscriptions[sub] = Subscription(sub, bounds, callback)
            else -> subscription.update(bounds = bounds, callback = callback)
        }
        logger.trace("dyconit $name subscribers ${subscriptions.size}")
    }

    fun removeSubscription(sub: SubKey) {
        // TODO this does not flush queued messages. Needed?
        subscriptions.remove(sub)
            ?.let { PerformanceCounterLogger.instance.updateBounds(Bounds.ZERO, previous = it.bounds) }
        logger.trace("dyconit $name subscribers ${subscriptions.size}")
    }

    fun addMessage(message: DMessage<Message>) {
        subscriptions.entries.parallelStream()
            .map { it.value }
            .forEach { it.addMessage(message) }
        val count = subscriptions.entries.count()
        val instance = PerformanceCounterLogger.instance
        val error = count * message.weight
        instance.messagesQueued.addAndGet(count)
        instance.numericalErrorQueued.addAndGet(error)
        subscriptions.values.forEach { instance.addNumericalError(it.sub!!, message.weight) }
    }

    fun countSubscribers(): Int {
        return subscriptions.size
    }

    fun getSubscribers(): List<SubKey> {
        return subscriptions.keys.toList()
    }

    fun getSubscription(sub: SubKey): Subscription<SubKey, Message>? {
        return subscriptions[sub]
    }

    fun close() {
        // TODO prevent new subscriptions from being added
        subscriptions.values.parallelStream().forEach { it.close() }
    }
}
