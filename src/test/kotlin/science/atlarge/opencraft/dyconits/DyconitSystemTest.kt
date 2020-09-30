package science.atlarge.opencraft.dyconits

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import science.atlarge.opencraft.dyconits.policies.DyconitCommand
import science.atlarge.opencraft.dyconits.policies.DyconitPolicy
import science.atlarge.opencraft.dyconits.policies.DyconitSubscribeCommand
import science.atlarge.opencraft.messaging.Filter
import java.io.File
import java.util.function.Consumer
import kotlin.test.assertEquals

internal class DyconitSystemTest {

    val subscriberName = "sub1"
    val sentMessages = ArrayList<String>()
    val callback = Consumer<String> { t -> sentMessages.add(t) }
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

    }
    var system: DyconitSystem<String, String>? = null

    @BeforeEach
    fun setUp() {
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
        assertEquals(msg, sentMessages[0])
    }

    @Test
    fun testLog() {
        system = DyconitSystem(policy, filter, log = true)
        Thread.sleep(2000)
        assertEquals(true, system != null)
        assertEquals(true, File(system!!.perfCounterLogger.logFilePath).isFile)
        assertEquals(true, File(system!!.perfCounterLogger.logFilePath).delete())
    }
}
