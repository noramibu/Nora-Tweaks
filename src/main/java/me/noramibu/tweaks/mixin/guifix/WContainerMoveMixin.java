package me.noramibu.tweaks.mixin.guifix;

import me.noramibu.tweaks.guifix.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WContainer.class)
public abstract class WContainerMoveMixin {
    @Inject(method = "move", at = @At("TAIL"), require = 0)
    private void noraGuiFix$onMove(double deltaX, double deltaY, CallbackInfo ci) {
        try {
            if (WindowPositionMemory.isClamping()) return;
            if ((Object) this instanceof WWindow window) WindowPositionMemory.afterMove(window);
        } catch (Throwable ignored) {
        }
    }
}
