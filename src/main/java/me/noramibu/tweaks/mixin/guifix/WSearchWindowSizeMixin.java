package me.noramibu.tweaks.mixin.guifix;

import java.util.Map;
import java.util.WeakHashMap;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WWidget.class)
public abstract class WSearchWindowSizeMixin {
    private static final Map<WWindow, double[]> SEARCH_SIZES = new WeakHashMap<>();

    @Inject(method = "calculateSize", at = @At("RETURN"), require = 0)
    private void noraGuiFix$lockSearchSize(CallbackInfo ci) {
        if (!((Object) this instanceof WWindow window) || !"search".equals(window.id)) return;

        double[] size = SEARCH_SIZES.get(window);
        if (size == null) {
            double width = Math.max(window.width, 220);
            double availableHeight = Math.max(120, Utils.getWindowHeight() - window.y - 20);
            double height = Math.min(Math.max(window.height, 180), availableHeight);
            size = new double[] {width, height};
            SEARCH_SIZES.put(window, size);
        }

        window.width = size[0];
        window.height = size[1];
    }
}
