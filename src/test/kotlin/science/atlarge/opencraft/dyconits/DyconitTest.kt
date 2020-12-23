package science.atlarge.opencraft.dyconits

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class DyconitTest {

    val dyconitName = "test"
    val dyconit = Dyconit<String, String>(dyconitName)
    val subscriber = "sub1"
    val boundsZeroNumerical = Bounds(Int.MAX_VALUE, 0)
    val boundsOneNumerical = Bounds(Int.MAX_VALUE, 1)
    val boundsZeroTime = Bounds(0, Int.MAX_VALUE)
    val boundsSecondTime = Bounds(1000, Int.MAX_VALUE)
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
    val msg = "hello world"

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun addSubscription() {
        dyconit.addSubscription(subscriber, boundsZeroNumerical, callback)
        assertEquals(1, dyconit.countSubscribers())
        assertEquals(subscriber, dyconit.getSubscribers()[0])
    }

    @Test
    fun removeSubscription() {
        dyconit.addSubscription(subscriber, boundsZeroNumerical, callback)
        assertEquals(1, dyconit.countSubscribers())
        dyconit.removeSubscription(subscriber)
        assertEquals(0, dyconit.countSubscribers())
    }

    @Test
    fun addMessageSend() {
        dyconit.addSubscription(subscriber, boundsZeroNumerical, callback)
        dyconit.addMessage(DMessage(msg, 1))
        Thread.sleep(1000)
        dyconit.synchronize()
        callback.flush()
        assertEquals(msg, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun addMessageNoSend() {
        dyconit.addSubscription(subscriber, boundsOneNumerical, callback)
        dyconit.addMessage(DMessage(msg, 0))
        Thread.sleep(1000)
        dyconit.synchronize()
        callback.flush()
        assertEquals(null, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun addMessageSendSecond() {
        dyconit.addSubscription(subscriber, boundsOneNumerical, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
        dyconit.addMessage(DMessage(msg, 2))
        Thread.sleep(1000)
        dyconit.synchronize()
        callback.flush()
        assertEquals(msg, sentMessages.poll(1, TimeUnit.SECONDS))
        assertEquals(msg, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun addMessageSendTime() {
        dyconit.addSubscription(subscriber, boundsZeroTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        Thread.sleep(1000)
        dyconit.synchronize()
        callback.flush()
        assertEquals(msg, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun addMessageNoSendTime() {
        dyconit.addSubscription(subscriber, boundsSecondTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        dyconit.synchronize()
        callback.flush()
        assertEquals(null, sentMessages.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun addMessageSendAfterSecond() {
        dyconit.addSubscription(subscriber, boundsSecondTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        dyconit.synchronize()
        callback.flush()
        assertEquals(0, sentMessages.size)
        dyconit.addMessage(DMessage(msg, 0))
        dyconit.synchronize()
        callback.flush()
        assertEquals(0, sentMessages.size)
        Thread.sleep(1100)
        dyconit.synchronize()
        callback.flush()
        assertEquals(msg, sentMessages.poll(10, TimeUnit.MILLISECONDS))
        assertEquals(msg, sentMessages.poll(10, TimeUnit.MILLISECONDS))
    }

    @Test
    fun getName() {
        assertEquals(dyconitName, dyconit.name)
    }
}
