package lingerloot

import net.minecraft.client.Minecraft
import net.minecraft.entity.item.EntityItem
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

interface CommonProxy {
    fun preInit(event: FMLPreInitializationEvent)
}

class ClientProxy: CommonProxy {
    override fun preInit(event: FMLPreInitializationEvent) {
        println("ACTIVATEIIIING THINGAR DOOT")

        RenderingRegistry.registerEntityRenderingHandler(EntityItem::class.java, ::RenderLLEntityItem)
        println("ACTIVATED OT")
    }
}

class ServerProxy: CommonProxy {
    override fun preInit(event: FMLPreInitializationEvent) {}
}

