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
        RenderingRegistry.registerEntityRenderingHandler(EntityItem::class.java, {
            RenderLLEntityItem(it, Minecraft.getMinecraft().renderItem)
        })
    }
}

class ServerProxy: CommonProxy {
    override fun preInit(event: FMLPreInitializationEvent) {}
}