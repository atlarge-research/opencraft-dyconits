package science.atlarge.opencraft.dyconits

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.LoggerFactory
import science.atlarge.opencraft.dyconits.policies.DyconitPolicy
import science.atlarge.opencraft.messaging.Filter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

class DyconitSystem<SubKey, Message>(
    policy: DyconitPolicy<SubKey, Message>,
    val filter: Filter<SubKey, Message>,
    private val messageQueueFactory: MessageQueueFactory<Message> = DefaultQueueFactory(),
    private val executorService: Supplier<ExecutorService>? = null,
    log: Boolean = false
) {
    var policy = policy
        set(value) {
            clear()
            field = value
        }
    private val dyconits = HashMap<String, Dyconit<SubKey, Message>>()
    private val subs = HashMap<SubKey, MutableSet<Dyconit<SubKey, Message>>>()
    private val callbackMap = HashMap<SubKey, MessageChannel<Message>>()
//    private val perfCounterLogger = PerformanceCounterLogger.instance

    private val logger = LoggerFactory.getLogger(javaClass)

//    init {
//        if (log) {
//            timer("dyconit-system-timer", true, 0, 1000) { perfCounterLogger.log() }
//        }
//    }

    init {
        System.setProperty("kotlinx.coroutines.scheduler", "off")
    }

    fun update(sub: Subscriber<SubKey, Message>) {
        val commands = policy.update(sub)
        commands.forEach { it.execute(this) }
    }

    fun getDyconit(name: String): Dyconit<SubKey, Message> {
//        if (!dyconits.containsKey(name)) {
//            perfCounterLogger.dyconitsCreated.incrementAndGet()
//        }
        val dyconit =
            dyconits.getOrPut(
                name,
                {
                    Dyconit(
                        name,
                        messageQueueFactory,
                        executorService?.get()?.asCoroutineDispatcher() ?: Dispatchers.Default
                    )
                })
        // logger.trace("dyconits total ${dyconits.size}")
        return dyconit
    }

    fun removeDyconit(name: String): Boolean {
        val deleted = dyconits.remove(name) != null
        // logger.trace("dyconits total ${dyconits.size}")
//        deleted.takeIf { it }?.run { perfCounterLogger.dyconitsRemoved.incrementAndGet() }
        return deleted
    }

    fun removeDyconit(dyconit: Dyconit<SubKey, Message>): Boolean {
        return removeDyconit(dyconit.name)
    }

    fun subscribe(sub: SubKey, callback: MessageChannel<Message>, bounds: Bounds, name: String) {
        val filteredCallback = callbackMap.getOrPut(sub, {
            object : MessageChannel<Message> {
                override fun send(msg: Message) {
                    if (filter.filter(sub, msg)) {
                        callback.send(msg)
                    }
                }

                override fun flush() {
                    callback.flush()
                }
            }
        })
        subscribe(sub, filteredCallback, bounds, getDyconit(name))
    }

    fun subscribe(sub: SubKey, callback: MessageChannel<Message>, bounds: Bounds, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addSubscription(sub, bounds, callback)
        subs.getOrPut(sub, { HashSet() }).add(dyconit)
        // logger.trace("dyconit ${dyconit.name} subscribers ${dyconit.countSubscribers()}")
    }

    fun unsubscribeAll(sub: SubKey) {
        subs[sub]?.toList()?.forEach { d -> unsubscribe(sub, d) }
    }

    fun unsubscribe(sub: SubKey, dyconitName: String) {
        val dyconit = dyconits[dyconitName]
        if (dyconit != null) {
            unsubscribe(sub, dyconit)
        }
    }

    fun unsubscribe(sub: SubKey, dyconit: Dyconit<SubKey, Message>) {
        dyconit.removeSubscription(sub)
        subs.getOrPut(sub, { HashSet() }).remove(dyconit)
        dyconit.takeIf { it.countSubscribers() <= 0 }?.let { removeDyconit(it) }
        // logger.trace("dyconit ${dyconit.name} subscribers ${dyconit.countSubscribers()}")
    }

    fun getDyconits(): Collection<Dyconit<SubKey, Message>> {
        return ArrayList(dyconits.values)
    }

    fun getDyconits(subscriber: SubKey): Collection<Dyconit<SubKey, Message>> {
        return subs.getOrDefault(subscriber, HashSet())
    }

    fun publish(publisher: Any, message: Message) {
        val name = policy.computeAffectedDyconit(publisher)
        dyconits[name]?.let { publish(message, it) }
    }

    fun publish(message: Message, dyconit: Dyconit<SubKey, Message>) {
        dyconit.addMessage(DMessage(message, policy.weigh(message)))
    }

    fun countDyconits(): Int {
        return dyconits.size
    }

    private fun clear() {
        // TODO prevent concurrent modifications.
        dyconits.forEach { (_, v) -> v.close() }
        dyconits.clear()
        subs.clear()
    }
}
