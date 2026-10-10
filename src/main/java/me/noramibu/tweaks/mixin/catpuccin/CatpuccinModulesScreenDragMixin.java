package me.noramibu.tweaks.mixin.catpuccin;

import me.noramibu.tweaks.category.ModuleLayoutManager;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "me.pindour.catpuccin.gui.screens.CatpuccinModulesScreen$WCategoryController", remap = false)
public abstract class CatpuccinModulesScreenDragMixin extends WContainer {
    @Shadow public List<WWindow> windows;

    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lmeteordevelopment/meteorclient/systems/modules/Modules;getGroup(Lmeteordevelopment/meteorclient/systems/modules/Category;)Ljava/util/List;"
        ),
        require = 0
    )
    private List<Module> nora$orderedGroup(Modules modules, Category category) {
        return ModuleLayoutManager.getModulesForDefaultCategory(category);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void nora$removeHiddenCategories(CallbackInfo ci) {
        ModuleLayoutManager.stripHiddenCategoryWindows(this, windows);
    }
}
