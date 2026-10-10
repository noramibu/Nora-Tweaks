package me.noramibu.tweaks.mixin.guifix;

import me.noramibu.tweaks.guifix.WindowPositionMemory;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "meteordevelopment.meteorclient.gui.widgets.containers.WWindow$WHeader")
public abstract class WHeaderReleaseMixin {
    private static final Field OUTER;
    private static final Field DRAGGED;
    private static final Field DRAGGING;

    static {
        Field outer = null;
        Field dragged = null;
        Field dragging = null;
        try {
            Class<?> headerClass = Class.forName("meteordevelopment.meteorclient.gui.widgets.containers.WWindow$WHeader");
            outer = headerClass.getDeclaredField("this$0");
            outer.setAccessible(true);
            dragged = WWindow.class.getDeclaredField("dragged");
            dragged.setAccessible(true);
            dragging = WWindow.class.getDeclaredField("dragging");
            dragging.setAccessible(true);
        } catch (Throwable ignored) {
        }
        OUTER = outer;
        DRAGGED = dragged;
        DRAGGING = dragging;
    }

    @Inject(method = "onMouseReleased", at = @At("HEAD"), require = 0)
    private void noraGuiFix$captureRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (OUTER == null || DRAGGED == null || DRAGGING == null) return;
            WWindow window = (WWindow) OUTER.get(this);
            if (DRAGGING.getBoolean(window) && DRAGGED.getBoolean(window)) WindowPositionMemory.afterDragEnd(window);
        } catch (Throwable ignored) {
        }
    }
}
