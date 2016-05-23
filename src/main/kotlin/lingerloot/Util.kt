package lingerloot

import net.minecraft.entity.item.EntityItem
import net.minecraftforge.fml.relauncher.ReflectionHelper

inline fun <T> MutableList<T>.filterInPlace(filter: (T)->Boolean) {
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
