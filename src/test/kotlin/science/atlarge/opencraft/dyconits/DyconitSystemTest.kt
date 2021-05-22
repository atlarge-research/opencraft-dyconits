package science.atlarge.opencraft.dyconits

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.assertj.core.util.Lists
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import science.atlarge.opencraft.dyconits.policies.DyconitCommand
import science.atlarge.opencraft.dyconits.policies.DyconitPolicy
import science.atlarge.opencraft.dyconits.policies.DyconitSubscribeCommand
import science.atlarge.opencraft.messaging.Filter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class DyconitSystemTest {

    val subscriberName = "sub1"
    val sentMessages = LinkedBlockingQueue<String>()
    val callback = object : MessageChannel<String> {
        val messages = ArrayList<String>()
        override fun send(msg: String) {
            messages.add(msg)
        }

        override fun flush() {
            messages.forEach { sentMessages.put(it) }
            messages.clear()
        }
    }
    val subscriber = Subscriber(subscriberName, callback)
    val bounds = Bounds(0, 0)
    val dyconitName = "d1"
    val filter = Filter<String, String> { _, _ -> true }
    var policy = object : DyconitPolicy<String, String> {
        override fun update(sub: Subscriber<String, String>): List<DyconitCommand<String, String>> {
            return listOf(DyconitSubscribeCommand(subscriberName, callback, bounds, dyconitName))
        }

        override fun weigh(message: String): Int {
            return 1
        }

        override fun computeAffectedDyconit(any: Any): String {
            return dyconitName
        }

        override fun globalUpdate(): List<DyconitCommand<String, String>> {
            return Lists.emptyList()
        }

    }

    @RelaxedMockK
    lateinit var mockPolicy: DyconitPolicy<String, String>

    @RelaxedMockK
    lateinit var mockDyconitCommand: DyconitCommand<String, String>

    @RelaxedMockK
    lateinit var mockCallback: MessageChannel<String>

    var system: DyconitSystem<String, String>? = null

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        system = DyconitSystem(policy, filter)
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun update() {
        assertEquals(0, system?.countDyconits())
        system?.update(subscriber)
        assertEquals(1, system?.countDyconits())
    }

    @Test
    fun getDyconit() {
        system?.update(subscriber)
        assertEquals(dyconitName, system?.getDyconit(dyconitName)?.name)
    }

    @Test
    fun removeDyconit() {
        system?.update(subscriber)
        assertEquals(true, system?.removeDyconit(dyconitName))
    }

    @Test
    fun removeDyconitFails() {
        assertEquals(false, system?.removeDyconit(dyconitName))
    }

    @Test
    fun subscribe() {
        system?.subscribe(subscriberName, callback, bounds, dyconitName)
        assertEquals(1, system?.countDyconits())
        assertEquals(dyconitName, system?.getDyconits(subscriberName)?.toList()?.get(0)?.name)
    }

    @Test
    fun unsubscribe() {
        system?.subscribe(subscriberName, callback, bounds, dyconitName)
        system?.unsubscribe(subscriberName, dyconitName)
        assertEquals(0, system?.countDyconits())
    }

    @Test
    fun getDyconits() {
        assertEquals(0, system?.getDyconits(subscriberName)?.size)
        system?.update(subscriber)
        assertEquals(1, system?.getDyconits(subscriberName)?.size)
        assertEquals(dyconitName, system?.getDyconits(subscriberName)?.toList()?.get(0)?.name)
    }

    @Test
    fun publish() {
        val msg = "hello world"
        system?.update(subscriber)
        system?.publish(Unit, msg)
        system?.synchronize()
        assertEquals(msg, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun setBounds() {
        system?.subscribe(subscriberName, callback, bounds, dyconitName)
        val bounds2 = bounds.copy(numerical = bounds.numerical + 1)
        assertEquals(bounds, system?.getDyconit(dyconitName)?.getSubscription(subscriberName)?.bounds)
        assertNotEquals(bounds2, system?.getDyconit(dyconitName)?.getSubscription(subscriberName)?.bounds)
    }

    @Test
    fun updateBounds() {
        system?.subscribe(subscriberName, callback, bounds, dyconitName)
        val bounds2 = bounds.copy(numerical = bounds.numerical + 1)
        system?.subscribe(subscriberName, callback, bounds2, dyconitName)
        assertEquals(bounds2, system?.getDyconit(dyconitName)?.getSubscription(subscriberName)?.bounds)
    }

    @Test
    fun globalUpdate() {
        every { mockPolicy.globalUpdate() } returns listOf(mockDyconitCommand)
        system?.policy = mockPolicy
        system?.globalUpdate()
        verify { mockDyconitCommand.execute(system!!) }
    }

    @Test
    fun unsubScribeAll() {
        val dc1 = "dc1"
        val dc2 = "dc2"
        system?.subscribe(subscriberName, mockCallback, Bounds.ZERO, dc1)
        system?.subscribe(subscriberName, mockCallback, Bounds.ZERO, dc2)
        assertEquals(subscriberName, system?.getDyconit(dc1)?.getSubscribers()!![0])
        assertEquals(subscriberName, system?.getDyconit(dc2)?.getSubscribers()!![0])
        system?.unsubscribeAll(subscriberName)
        assertEquals(0, system?.getDyconit(dc1)?.getSubscribers()?.size)
        assertEquals(0, system?.getDyconit(dc2)?.getSubscribers()?.size)
    }

    @Test
    fun getAllDyconits() {
        system?.subscribe(subscriberName, mockCallback, bounds, dyconitName)
        assertEquals(1, system?.getDyconits()?.size)
        system?.getDyconits()?.forEach { dc ->
            run {
                assertEquals(subscriberName, dc.getSubscribers()[0])
                assertEquals(dyconitName, dc.name)
            }
        }
    }
}
