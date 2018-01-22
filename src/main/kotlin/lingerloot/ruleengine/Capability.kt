package lingerloot.ruleengine

import lingerloot.volatility.DespawnHandlerSet
import lingerloot.volatility.despawnHandlerSetsByShort
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagShort
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityInject
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.INBTSerializable


@CapabilityInject(TouchedByLingeringLewd::class)
var TOUCHED_CAP: Capability<TouchedByLingeringLewd>? = null
val VOLTAG = "volatile"
val TAGTYPE_ID_SHORT = NBTTagShort().id

fun registerCapabilities() {
    CapabilityManager.INSTANCE.register(TouchedByLingeringLewd::class.java, TouchedStorage, {TouchedByLingeringLewd})
}

object TouchedByLingeringLewd: ICapabilityProvider, INBTSerializable<NBTTagCompound> {
    var despawnHandler: DespawnHandlerSet? = null

    override fun serializeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        despawnHandler?.let{
            tag.setShort(VOLTAG, it.code.toShort())
        }
        return tag
    }

    override fun deserializeNBT(nbt: NBTTagCompound?) {
        if (nbt != null && nbt.hasKey(VOLTAG, TAGTYPE_ID_SHORT.toInt()))
            despawnHandler = despawnHandlerSetsByShort[nbt.getShort(VOLTAG)]
    }


    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?) = capability == TOUCHED_CAP

    override fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T? =
            if (capability === TOUCHED_CAP) this as T else null

}

private object TouchedStorage: Capability.IStorage<TouchedByLingeringLewd> {
    override fun writeNBT(capability: Capability<TouchedByLingeringLewd>?, instance: TouchedByLingeringLewd?,
                          side: EnumFacing?) = instance?.serializeNBT()?:NBTTagCompound()
    override fun readNBT(capability: Capability<TouchedByLingeringLewd>?, instance: TouchedByLingeringLewd?,
                         side: EnumFacing?, nbt: NBTBase?) {
        if (nbt is NBTTagCompound)
            instance?.deserializeNBT(nbt)
    }
}