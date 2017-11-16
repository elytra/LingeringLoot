package lingerloot

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.entity.RenderEntityItem
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.entity.item.EntityItem
import net.minecraft.util.math.MathHelper

class RenderLLEntityItem(renderManager: RenderManager):
        RenderEntityItem(renderManager, Minecraft.getMinecraft().renderItem) {
    override fun transformModelCount(itemIn: EntityItem, x: Double, y: Double, z: Double, partialTicks: Float, model: IBakedModel): Int {
        val itemstack = itemIn.item
        if (itemstack.item == null) return 0
        val progress = despawnNotificationProgress(itemIn, partialTicks)
        val prog_squared = if (progress < 1) progress*progress else 1f // clamp bob height if item despawns late
        val age = itemIn.age.toFloat() + partialTicks

        val flag = model.isGui3d
        val i = this.getModelCount(itemstack)
        val yOffset = if (shouldBob())
                Math.max(.3f*prog_squared, .1f) + MathHelper.sin((age + ageOffsetBob(progress)) / 10.0f + itemIn.hoverStart) *
                    (.1f + .5f*prog_squared)
            else 0f
        val f2 = model.itemCameraTransforms.getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y
        GlStateManager.translate(x.toFloat(), y.toFloat() + yOffset + 0.25f * f2, z.toFloat())

        if (flag || this.renderManager.options != null) {
            val f3 = ((age + ageOffsetSpin(progress)) / 20.0f + itemIn.hoverStart) * (180f / Math.PI.toFloat())
            GlStateManager.rotate(f3, 0.0f, 1.0f, 0.0f)
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        return i
    }

    fun despawnNotificationProgress(entity: EntityItem, partialTicks: Float) = if (entity in jitteringItems)
        (Math.max(0f, JITTER_TIME - entity.lifespan + entity.age + partialTicks
        ).toDouble() / JITTER_TIME).toFloat()
    else
        0f

    private fun ageOffsetBob(progress: Float): Float = if (progress < 1)
        400*progress*progress*progress
    else
        400 + 1200*progress // continue at fixed slope if item despawns late

    private fun ageOffsetSpin(progress: Float): Float = if (progress < 1)
        -1400*progress*progress*progress*progress
    else
        -1400 - 5600*progress
}