package me.noramibu.tweaks.mixin.guifix;

import me.noramibu.tweaks.guifix.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WWindow.class)
public abstract class WWindowClampMixin {
    @Redirect(
        method = "onCalculateWidgetPositions",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WWindow;height:D",
            opcode = 180
        ),
        require = 0
    )
    private double noraGuiFix$useVisibleHeightForClamp(WWindow window) {
        try {
            return WindowPositionMemory.effectiveHeight(window);
        } catch (Throwable ignored) {
            return window.height;
        }
    }
}
