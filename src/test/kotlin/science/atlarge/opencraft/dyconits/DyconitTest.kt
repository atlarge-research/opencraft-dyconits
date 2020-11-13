package science.atlarge.opencraft.dyconits

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DyconitTest {

    val dyconitName = "test"
    val dyconit = Dyconit<String, String>(dyconitName)
    val subscriber = "sub1"
    val boundsZeroNumerical = Bounds(Int.MAX_VALUE, 0)
    val boundsOneNumerical = Bounds(Int.MAX_VALUE, 1)
    val boundsZeroTime = Bounds(0, Int.MAX_VALUE)
    val boundsSecondTime = Bounds(1000, Int.MAX_VALUE)
    val sentMessages = ArrayList<String>()
    val callback = object : MessageChannel<String> {
        val messages = ArrayList<String>()
        override fun send(msg: String) {
            messages.add(msg)
        }

        override fun flush() {
            sentMessages.addAll(messages)
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
        assertEquals(msg, sentMessages[0])
    }

    @Test
    fun addMessageNoSend() {
        dyconit.addSubscription(subscriber, boundsOneNumerical, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
    }

    @Test
    fun addMessageSendSecond() {
        dyconit.addSubscription(subscriber, boundsOneNumerical, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
        dyconit.addMessage(DMessage(msg, 2))
        assertEquals(2, sentMessages.size)
    }

    @Test
    fun addMessageSendTime() {
        dyconit.addSubscription(subscriber, boundsZeroTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(1, sentMessages.size)
        assertEquals(msg, sentMessages[0])
    }

    @Test
    fun addMessageNoSendTime() {
        dyconit.addSubscription(subscriber, boundsSecondTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
    }

    @Test
    fun addMessageSendAfterSecond() {
        dyconit.addSubscription(subscriber, boundsSecondTime, callback)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
        dyconit.addMessage(DMessage(msg, 0))
        assertEquals(0, sentMessages.size)
        Thread.sleep(1100)
        assertEquals(2, sentMessages.size)
    }

    @Test
    fun getName() {
        assertEquals(dyconitName, dyconit.name)
    }
}
