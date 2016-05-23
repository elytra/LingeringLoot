package lingerloot

import net.minecraft.entity.item.EntityItem
import java.lang.ref.WeakReference
import java.util.*

class JitterNotificationQueue() {
    private val q = PriorityQueue<ExpectJitter>()
    private var tick = -1

    fun prepareToDie(item: EntityItem): Boolean {
        return q.add(ExpectJitter(tick - JITTER_TIME, item))
    }

    fun tick() {
        tick++

        val readyToJitters = ArrayList<EntityItem>(64)

        while (q.isNotEmpty() && tick >= q.peek().expectToJitter) {
            q.poll().ref.get().ifAlive()?.let { item ->
                if (item.lifespan - serversideAge(item) > JITTER_TIME + 10)
                    prepareToDie(item) // not even close, throw it back in
                else
                    readyToJitters.add(item)
            }
        }

        readyToJitters.forEach { LAMBDA_NETWORK.send().packet(GONNA_DESPAWN).with("id", it.entityId).toAllWatching(it) }
    }
}

class ExpectJitter(tick: Int, item: EntityItem) : Comparable<ExpectJitter> {
    val ref = WeakReference(item)
    val expectToJitter = tick + item.lifespan - serversideAge(item)

    override fun compareTo(other: ExpectJitter) = expectToJitter - other.expectToJitter
}
