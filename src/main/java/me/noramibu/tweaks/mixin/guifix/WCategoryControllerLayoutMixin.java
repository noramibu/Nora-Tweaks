package me.noramibu.tweaks.mixin.guifix;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meteordevelopment.meteorclient.gui.screens.ModulesScreen$WCategoryController")
public abstract class WCategoryControllerLayoutMixin {
    @Inject(method = "onCalculateWidgetPositions", at = @At("TAIL"), require = 0)
    private void noraGuiFix$preserveWindowCells(CallbackInfo ci) {
        for (Cell<?> cell : ((WContainer) (Object) this).cells) {
            if (cell.widget() instanceof WWindow window) {
                cell.x = window.x;
                cell.y = window.y;
                cell.width = window.width;
                cell.height = window.height;
            }
        }
    }
}
