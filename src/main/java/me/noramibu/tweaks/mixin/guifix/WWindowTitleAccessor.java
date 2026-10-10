package me.noramibu.tweaks.mixin.guifix;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WWindow.class)
public interface WWindowTitleAccessor {
    @Accessor("title")
    String noraGuiFix$getTitle();
}
