package moe.ramon.cryostasis.render;

import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.modules.render.BlockOutlineModule;
import moe.ramon.cryostasis.modules.render.HitboxModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Binds the world-render modules to Fabric's world render events. Registered once at
 * client init. Each hook cheaply returns when its module is disabled, so a disabled
 * module costs one boolean check per frame and nothing more.
 *
 * Boxes are drawn in world space with the pose stack shifted by the negated camera
 * position, which is the standard way to place absolute-coordinate geometry during these
 * events.
 */
public final class WorldRenderHooks {
	private WorldRenderHooks() {
	}

	public static void register(ModuleManager modules) {
		HitboxModule hitbox = modules.get(HitboxModule.class);
		BlockOutlineModule blockOutline = modules.get(BlockOutlineModule.class);

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (hitbox == null || !hitbox.isEnabled()) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			PoseStack pose = context.matrixStack();
			if (mc.level == null || pose == null) {
				return;
			}
			Vec3 cam = context.camera().getPosition();
			VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
			int argb = HudColors.isRainbow() ? HudColors.rainbow(0.0f) : hitbox.getColor();
			float[] c = RenderUtil.argbToFloats(argb);
			double expand = hitbox.getExpand();

			pose.pushPose();
			pose.translate(-cam.x, -cam.y, -cam.z);
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity == mc.player) {
					continue;
				}
				AABB box = entity.getBoundingBox().inflate(expand);
				ShapeRenderer.renderLineBox(pose, lines, box, c[0], c[1], c[2], c[3]);
			}
			pose.popPose();
		});

		WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
			if (blockOutline == null || !blockOutline.isEnabled()) {
				return true; // let vanilla draw its outline
			}
			Minecraft mc = Minecraft.getInstance();
			PoseStack pose = context.matrixStack();
			if (mc.level == null || pose == null) {
				return true;
			}
			BlockPos pos = outline.blockPos();
			BlockState state = outline.blockState();
			VoxelShape shape = state.getShape(mc.level, pos);
			AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();

			Vec3 cam = context.camera().getPosition();
			VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
			int argb = HudColors.isRainbow() ? HudColors.rainbow(0.5f) : blockOutline.getColor();
			float[] c = RenderUtil.argbToFloats(argb);

			pose.pushPose();
			pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
			ShapeRenderer.renderLineBox(pose, lines, box, c[0], c[1], c[2], c[3]);
			pose.popPose();
			return false; // suppress the vanilla outline
		});
	}
}
