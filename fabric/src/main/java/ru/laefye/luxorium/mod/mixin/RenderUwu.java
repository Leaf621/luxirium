package ru.laefye.luxorium.mod.mixin;

import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.laefye.luxorium.mod.client.LuxoriumClient;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class RenderUwu {
    @Shadow protected abstract SpriteIdentifier getTextureId(WoodType woodType);

    @Shadow protected abstract void applyTextTransforms(MatrixStack matrices, boolean front, Vec3d textOffset);

    @Shadow protected abstract Vec3d getTextOffset();

    @Shadow protected abstract void applyTransforms(MatrixStack matrices, float blockRotationDegrees, BlockState state);

    @Shadow @Final private TextRenderer textRenderer;

    @Inject(at = @At("HEAD"), method = "Lnet/minecraft/client/render/block/entity/AbstractSignBlockEntityRenderer;render(Lnet/minecraft/block/entity/SignBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V", cancellable = true)
    private void render(SignBlockEntity signBlockEntity, float f, MatrixStack matrices, VertexConsumerProvider vertexConsumerProvider, int light, int overlay, Vec3d vec3d, CallbackInfo ci) {
        matrices.push();

        BlockState state = signBlockEntity.getCachedState();

        // Используем кастомную текстуру для экрана (можно менять динамически)
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LuxoriumClient.createScreenLayer(LuxoriumClient.getInstance().getScreenTexture()));

        // Пример координат quada (1x1, в центре)
        float x1 = -0.5f, y1 = -0.5f;
        float x2 = 0.5f, y2 = 0.5f;
        float z = 0.0f;

        // Цвет (белый, полностью непрозрачный)
        int r = 255, g = 255, b = 255, a = 255;

        applyTransforms(matrices, -((AbstractSignBlock)state.getBlock()).getRotationDegrees(state), state);
        matrices.translate(0, 5F/16F, 0);

        // Максимальное освещение (15) для fullbright эффекта
        int fullbrightLight = 15728880; // LightmapTextureManager.pack(15, 15)

        MatrixStack.Entry entry = matrices.peek();
        var model = matrices.peek();

        // Добавляем 4 вершины для экрана с полным освещением
        vertexConsumer.vertex(model, x1, y1, z).color(r, g, b, a).texture(0.0f, 0.0f).overlay(overlay).light(fullbrightLight).normal(entry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(model, x2, y1, z).color(r, g, b, a).texture(1.0f, 0.0f).overlay(overlay).light(fullbrightLight).normal(entry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(model, x2, y2, z).color(r, g, b, a).texture(1.0f, 1.0f).overlay(overlay).light(fullbrightLight).normal(entry, 0.0f, 0.0f, 1.0f);
        vertexConsumer.vertex(model, x1, y2, z).color(r, g, b, a).texture(0.0f, 1.0f).overlay(overlay).light(fullbrightLight).normal(entry, 0.0f, 0.0f, 1.0f);

        matrices.pop();
        ci.cancel();
    }
}
