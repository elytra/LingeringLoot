package lingerloot

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderItem
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.entity.RenderEntityItem
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.entity.item.EntityItem
import net.minecraft.util.math.MathHelper

class RenderLLEntityItem(renderManager: RenderManager, item: RenderItem): RenderEntityItem(renderManager, item) {
//    override fun transformModelCount(itemIn: EntityItem, x: Double, y: Double, z: Double, partialTicks: Float, model: IBakedModel): Int {
//        itemIn
//
//        val itemstack = itemIn.item
//        val item = itemstack.item ?: return 0
//
//        val flag = model.isGui3d
//        val i = this.getModelCount(itemstack)
//        val yOffset = if (shouldBob())
//                MathHelper.sin((itemIn.age.toFloat() + partialTicks) / 10.0f + itemIn.hoverStart) * 0.1f + 0.1f
//            else 0f
//        val f2 = model.itemCameraTransforms.getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y
//        GlStateManager.translate(x.toFloat(), y.toFloat() + yOffset + 0.25f * f2, z.toFloat())
//
//        if (flag || this.renderManager.options != null) {
//            val f3 = ((itemIn.age.toFloat() + partialTicks) / 20.0f + itemIn.hoverStart) * (180f / Math.PI.toFloat())
//            GlStateManager.rotate(f3, 0.0f, 1.0f, 0.0f)
//        }
//
//        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
//        return i
//    }
//
//    fun despawnNotificationProgress() {
//
//    }
}