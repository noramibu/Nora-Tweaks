package me.noramibu.tweaks.mixin.guifix;

import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.pindour.catppuccin.gui.screens.CatppuccinModulesScreen$WCategoryController", remap = false)
public abstract class CatppuccinCategoryControllerLayoutMixin {
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
