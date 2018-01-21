package lingerloot.ruleengine

import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityInject
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.ICapabilityProvider


@CapabilityInject(TouchedByLingeringLewd::class)
var TOUCHED_CAP: Capability<TouchedByLingeringLewd>? = null

fun registerCapabilities() {
    CapabilityManager.INSTANCE.register(TouchedByLingeringLewd::class.java, TouchedStorage(), {TouchedByLingeringLewd()})
}

class TouchedByLingeringLewd: ICapabilityProvider {


    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?) = capability == TOUCHED_CAP

    override fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T? =
            if (capability === TOUCHED_CAP) this as T else null

}

private class TouchedStorage: Capability.IStorage<TouchedByLingeringLewd> {
    override fun writeNBT(capability: Capability<TouchedByLingeringLewd>?, instance: TouchedByLingeringLewd?, side: EnumFacing?): NBTBase? = NBTTagCompound()
    override fun readNBT(capability: Capability<TouchedByLingeringLewd>?, instance: TouchedByLingeringLewd?, side: EnumFacing?, nbt: NBTBase?) {}
}