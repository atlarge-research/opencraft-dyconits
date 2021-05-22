package science.atlarge.opencraft.dyconits

import com.google.common.collect.Maps

/**
 * A dyconit is similar to a _Topic_ in a Pub/Sub system,
 * and is the core of the bounded-inconsistency communication system in Opencraft.
 *
 * Outgoing messages are sent to a dyconit, which lazily forwards them to subscribers (players)
 * while preventing large inconsistency.
 */
class Dyconit<SubKey, Message>(
    val name: String,
    private val queueFactory: MessageQueueFactory<Message> = DefaultQueueFactory(),
) {

    private var subscriptions: MutableMap<SubKey, Subscription<Message>> = Maps.newConcurrentMap()

    fun addSubscription(sub: SubKey, bounds: Bounds, callback: MessageChannel<Message>) {
        when (val subscription = subscriptions[sub]) {
            null -> subscriptions[sub] =
                Subscription(bounds, callback, queueFactory.newMessageQueue())
            else -> subscription.update(bounds = bounds, callback = callback)
        }
    }

    fun removeSubscription(sub: SubKey) {
        // TODO this does not flush queued messages. Needed?
        subscriptions.remove(sub)
    }

    fun addMessage(message: DMessage<Message>) {
        subscriptions.values.forEach { it.addMessage(message) }
    }

    fun countSubscribers(): Int {
        return subscriptions.size
    }

    fun getSubscribers(): List<SubKey> {
        return subscriptions.keys.toList()
    }

    fun getSubscription(sub: SubKey): Subscription<Message>? {
        return subscriptions[sub]
    }

    fun synchronize() {
        subscriptions.values.forEach { it.synchronize() }
    }

    fun close() {
        // TODO prevent new subscriptions from being added
        subscriptions.values.parallelStream().forEach { it.close() }
    }
}
