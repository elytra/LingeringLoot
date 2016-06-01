package lingerloot

import cpw.mods.fml.relauncher.ReflectionHelper
import net.minecraft.entity.item.EntityItem

inline fun <T> MutableIterable<T>.filterInPlace(filter: (T)->Boolean) {
    val it = iterator()
    while (it.hasNext())
        if (!filter(it.next()))
            it.remove()
}

fun EntityItem?.ifAlive(): EntityItem? {
    return if (this != null && !this.isDead) this else null
}

val ageField by lazy { ReflectionHelper.findField(EntityItem::class.java, "age", "field_70292_b") }
fun serversideAge(item: EntityItem): Int { return ageField.get(item) as Int }
