package me.noramibu.tweaks.mixin;

import me.noramibu.tweaks.category.ModuleDragController;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WWidget.class, remap = false)
public abstract class WWidgetDragHighlightMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void nora$dragHoverHighlight(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleDragController.isDragging()) return;
        ModuleDragController.renderHoverHighlight(renderer, (WWidget) (Object) this);
    }
}
