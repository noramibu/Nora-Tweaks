package me.noramibu.tweaks.mixin;

import me.noramibu.tweaks.gui.screens.AddToCategoryScreen;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModuleScreen.class, remap = false)
public abstract class SingleModuleScreenMixin extends WidgetScreen {
    @Shadow @Final private Module module;

    private SingleModuleScreenMixin(GuiTheme theme, String title) {
        super(theme, title);
    }

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void onInitWidgets(CallbackInfo ci) {
        add(theme.horizontalSeparator()).expandX();
        WButton addToCategoryButton = add(theme.button("Add to Custom Category")).expandX().widget();
        addToCategoryButton.action = () -> {
            Minecraft.getInstance().setScreenAndShow(new AddToCategoryScreen(theme, module));
        };
    }
}
