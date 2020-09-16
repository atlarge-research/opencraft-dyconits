package science.atlarge.opencraft.dyconits

import com.google.common.collect.Maps
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/**
 * A dyconit is similar to a _Topics_ in a Pub/Sub system,
 * and is the core of the bounded-inconsistency communication system in Opencraft.
 *
 * Outgoing messages are sent to a dyconit, which lazily forwards them to subscribers (players)
 * while preventing large inconsistency.
 */
class Dyconit<SubKey, Message>(val name: String) {
    private var subscriptions: MutableMap<SubKey, Subscription<Message>> = Maps.newConcurrentMap()

    private val logger = LoggerFactory.getLogger(javaClass)

    fun addSubscription(sub: SubKey, bounds: Bounds, callback: Consumer<Message>) {
        subscriptions.putIfAbsent(sub, Subscription(bounds, callback))
        logger.trace("dyconit $name subscribers ${subscriptions.size}")
    }

    fun removeSubscription(sub: SubKey) {
        subscriptions.remove(sub)
        logger.trace("dyconit $name subscribers ${subscriptions.size}")
    }

    fun addMessage(message: DMessage<Message>) {
        subscriptions.entries.parallelStream()
            .map { e -> e.value }
            .forEach { s -> s.addMessage(message) }
    }

    fun countSubscribers(): Int {
        return subscriptions.size
    }

    fun getSubscribers(): List<SubKey> {
        return subscriptions.keys.toList()
    }

    fun countQueuedMessages(): Int {
        return subscriptions.map { e -> e.value.countQueuedMessages() }.sum()
    }

    fun calculateNumericalError(): Int {
        return subscriptions.map { e -> e.value.numericalError }.sum()
    }

    fun calculateStaleness(): Long {
        return subscriptions.map { e -> e.value.staleness }.sum()
    }
}
