package me.noramibu.tweaks.mixin.guifix;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WContainer.class)
public abstract class WContainerHitOrderMixin {
    private static final Field DROPDOWN_ROOT;
    private static final Field DROPDOWN_EXPANDED;

    static {
        Field root = null;
        Field expanded = null;
        try {
            root = WDropdown.class.getDeclaredField("root");
            root.setAccessible(true);
            expanded = WDropdown.class.getDeclaredField("expanded");
            expanded.setAccessible(true);
        } catch (Throwable ignored) {
        }
        DROPDOWN_ROOT = root;
        DROPDOWN_EXPANDED = expanded;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void noraGuiFix$prioritizeDropdown(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        WDropdown<?> dropdown = findOpenDropdown((WContainer) (Object) this);
        if (dropdown != null && dropdown.mouseClicked(click, doubled)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void noraGuiFix$prioritizeDropdownRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        WDropdown<?> dropdown = findOpenDropdown((WContainer) (Object) this);
        if (dropdown != null && dropdown.mouseReleased(click)) cir.setReturnValue(true);
    }

    @Redirect(
        method = "mouseClicked",
        at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<?> noraGuiFix$reverseClickOrder(List<?> list) {
        return reverse(list);
    }

    @Redirect(
        method = "mouseReleased",
        at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<?> noraGuiFix$reverseReleaseOrder(List<?> list) {
        return reverse(list);
    }

    private static Iterator<?> reverse(List<?> list) {
        if (list == null || list.isEmpty()) return Collections.emptyIterator();
        ListIterator<?> it = list.listIterator(list.size());
        return new Iterator<>() {
            public boolean hasNext() {
                return it.hasPrevious();
            }
            public Object next() {
                return it.previous();
            }
        };
    }

    private static WDropdown<?> findOpenDropdown(WContainer container) {
        for (int i = container.cells.size() - 1; i >= 0; i--) {
            WWidget widget = container.cells.get(i).widget();
            WDropdown<?> dropdown = findOpenDropdown(widget);
            if (dropdown != null) return dropdown;
        }
        return null;
    }

    private static WDropdown<?> findOpenDropdown(WWidget widget) {
        if (widget instanceof WDropdown<?> dropdown && isOpenOverPopup(dropdown)) return dropdown;
        if (widget instanceof WContainer container) return findOpenDropdown(container);
        return null;
    }

    private static boolean isOpenOverPopup(WDropdown<?> dropdown) {
        try {
            if (DROPDOWN_EXPANDED == null || DROPDOWN_ROOT == null) return false;
            if (!DROPDOWN_EXPANDED.getBoolean(dropdown)) return false;
            Object root = DROPDOWN_ROOT.get(dropdown);
            return root instanceof WWidget widget && widget.mouseOver;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
