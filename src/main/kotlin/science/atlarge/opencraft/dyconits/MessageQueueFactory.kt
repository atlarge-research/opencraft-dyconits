package science.atlarge.opencraft.dyconits

interface MessageQueueFactory<T> {
    fun newMessageQueue(): MessageQueue<T>
}

interface MessageQueue<T> {
    fun add(msg: T)
    fun remove(): T
    fun isEmpty(): Boolean
}

class MessageListQueue<T> : MessageQueue<T> {
    private val list = ArrayDeque<T>()
    override fun add(msg: T) {
        list.add(msg)
    }

    override fun remove(): T {
        return list.removeFirst()
    }

    override fun isEmpty(): Boolean {
        return list.isEmpty()
    }
}

class DefaultQueueFactory<T> : MessageQueueFactory<T> {
    override fun newMessageQueue(): MessageQueue<T> {
        return MessageListQueue()
    }
}
