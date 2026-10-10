package me.noramibu.tweaks.mixin.guifix;

import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WWindow.class)
public interface WWindowDragStateAccessor {
    @Accessor("moved")
    boolean noraGuiFix$getMoved();
    @Accessor("moved")
    void noraGuiFix$setMoved(boolean value);
    @Accessor("movedX")
    void noraGuiFix$setMovedX(double value);
    @Accessor("movedY")
    void noraGuiFix$setMovedY(double value);
}
