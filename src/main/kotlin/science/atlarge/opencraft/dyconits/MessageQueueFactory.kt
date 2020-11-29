package science.atlarge.opencraft.dyconits

interface MessageQueueFactory<T> {
    fun newMessageQueue(): MessageQueue<T>
}

interface MessageQueue<T> : Iterable<T> {
    fun add(msg: T)
    fun clear()
}

class MessageListQueue<T> : MessageQueue<T> {
    private val list = ArrayList<T>()
    override fun add(msg: T) {
        list.add(msg)
    }

    override fun clear() {
        list.clear()
    }

    override fun iterator(): Iterator<T> = list.iterator()
}

class DefaultQueueFactory<T> : MessageQueueFactory<T> {
    override fun newMessageQueue(): MessageQueue<T> {
        return MessageListQueue()
    }
}
