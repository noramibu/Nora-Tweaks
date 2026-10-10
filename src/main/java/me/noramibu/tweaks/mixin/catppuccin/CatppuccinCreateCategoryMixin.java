package me.noramibu.tweaks.mixin.catppuccin;

import me.noramibu.tweaks.category.ModuleLayoutManager;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "me.pindour.catppuccin.gui.screens.CatppuccinModulesScreen", remap = false)
public abstract class CatppuccinCreateCategoryMixin {
    @Inject(method = "createCategory", at = @At("HEAD"), cancellable = true, require = 0)
    private void nora$skipHiddenCategory(WContainer c, Category category, List<Module> moduleList, CallbackInfoReturnable<WWindow> cir) {
        if (ModuleLayoutManager.isDefaultCategoryHidden(category)) {
            cir.setReturnValue(null);
        }
    }
}
