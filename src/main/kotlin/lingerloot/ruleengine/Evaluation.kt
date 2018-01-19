package lingerloot.ruleengine

import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

class EvaluationContext(val item: EntityItem, val causeMask: Int) {
    val oreIds = OreDictionary.getOreIDs(item.item).toSet()
    val classMask = getClassMask(item.item.item)
    val tagCache = mutableMapOf<String, Boolean>()
    val tagRecursionStack = mutableSetOf<String>()

    var timer: Int? = null
    var volatile = false
    var transform: ItemStack? = null


}
