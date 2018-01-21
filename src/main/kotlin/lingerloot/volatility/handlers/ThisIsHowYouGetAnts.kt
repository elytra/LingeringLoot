package lingerloot.volatility.handlers

import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.monster.EntitySilverfish
import net.minecraft.item.ItemFood
import net.minecraft.world.WorldServer

fun spontaneousGeneration(world: WorldServer, entityItem: EntityItem, type: ItemFood) {
    val pos = entityItem.position
    for (i in 1..(
            entityItem.item.count
            * ((type.getHealAmount(entityItem.item)+1)/2)
        )) {
            val entitysilverfish = EntitySilverfish(world)
            entitysilverfish.setLocationAndAngles(pos.getX().toDouble() + 0.5, pos.getY().toDouble(), pos.getZ().toDouble() + 0.5, 0.0f, 0.0f)
            world.spawnEntity(entitysilverfish)
            entitysilverfish.spawnExplosionParticle()
    }
}
