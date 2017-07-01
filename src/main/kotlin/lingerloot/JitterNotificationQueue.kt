package lingerloot

import net.minecraft.entity.item.EntityItem
import java.lang.ref.WeakReference
import java.util.*

class JitterNotificationQueue {
    private val q = PriorityQueue<ExpectJitter>()
    private var tick = -1

    fun prepareToDie(item: EntityItem): Boolean {
        return q.add(ExpectJitter(tick - JITTER_TIME, item))
    }

    fun tick() {
        tick++

        while (q.isNotEmpty() && tick >= q.peek().expectToJitter) {
            q.poll().ref.get().ifAlive()?.let { item ->
                if (item.lifespan - item.extractAge() > JITTER_TIME + 10)
                    prepareToDie(item) // not even close, throw it back in
                else
                    LAMBDA_NETWORK.send().packet(GONNA_DESPAWN).with("id", item.entityId).toAllWatching(item)
            }
        }
    }
}

class ExpectJitter(tick: Int, item: EntityItem) : Comparable<ExpectJitter> {
    val ref = WeakReference(item)
    val expectToJitter = tick + item.lifespan - item.extractAge()

    override fun compareTo(other: ExpectJitter) = expectToJitter - other.expectToJitter
}
