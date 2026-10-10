package me.noramibu.tweaks.mixin;

import me.noramibu.tweaks.category.ModuleDragController;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WidgetScreen.class, remap = false)
public abstract class WidgetScreenDragMixin {
    @Inject(method = "mouseMoved", at = @At("HEAD"), require = 0)
    private void nora$dragMouseMoved(double mouseX, double mouseY, CallbackInfo ci) {
        if (ModuleDragController.isPending() || ModuleDragController.isDragging()) {
            try {
                double s = meteordevelopment.meteorclient.MeteorClient.mc.getWindow().getGuiScale();
                ModuleDragController.updateMouse(mouseX * s, mouseY * s);
            } catch (Throwable t) {
                ModuleDragController.updateMouse(mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), require = 0)
    private void nora$dragMouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleDragController.isDragging() || ModuleDragController.isPending()) {
            try {
                double s = meteordevelopment.meteorclient.MeteorClient.mc.getWindow().getGuiScale();
                ModuleDragController.endDrag(click.x() * s, click.y() * s);
            } catch (Throwable t) {
                ModuleDragController.endDrag(click.x(), click.y());
            }
        }
    }
}
