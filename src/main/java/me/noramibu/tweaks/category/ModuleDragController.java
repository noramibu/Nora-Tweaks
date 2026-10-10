package me.noramibu.tweaks.category;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.lang.reflect.Field;

public class ModuleDragController {
    private static final double DRAG_THRESHOLD = 6.0;
    private static final Color HIGHLIGHT = new Color(100, 200, 140, 100);

    private static Module module;
    private static String sourceCategoryKey;
    private static WWidget sourceWidget;
    private static double startX, startY;
    private static double mouseX, mouseY;
    private static boolean dragging;
    private static boolean pending;
    private static boolean suppressToggle;
    private static int insertIndex = -1;
    private static String targetCategoryKey;
    private static WWindow targetWindow;
    private static boolean validDrop;
    private static WWidget hoverTargetWidget;

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean isPending() {
        return pending;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isHoverTarget(WWidget widget) {
        return dragging && validDrop && widget != null && widget == hoverTargetWidget;
    }

    public static void renderHoverHighlight(GuiRenderer renderer, WWidget widget) {
        if (!isHoverTarget(widget) || renderer == null) return;
        renderer.quad(widget.x, widget.y, widget.width, widget.height, HIGHLIGHT);
    }

    public static boolean beginPotentialDrag(Module mod, WWidget widget, double mx, double my) {
        if (mod == null || widget == null) return false;
        module = mod;
        sourceWidget = widget;
        startX = mx;
        startY = my;
        mouseX = mx;
        mouseY = my;
        pending = true;
        dragging = false;
        sourceCategoryKey = resolveCategoryKey(widget);
        insertIndex = -1;
        targetCategoryKey = null;
        targetWindow = null;
        validDrop = false;
        hoverTargetWidget = null;
        return true;
    }

    public static void updateMouse(double mx, double my) {
        mouseX = mx;
        mouseY = my;
        if (!pending && !dragging) return;

        if (pending && !dragging) {
            double dx = mx - startX;
            double dy = my - startY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                dragging = true;
                pending = false;
            }
        }

        if (dragging) {
            resolveDropTarget(mx, my);
        }
    }

    public static boolean cancelClickToggle() {
        return dragging || (pending && (Math.abs(mouseX - startX) > 1 || Math.abs(mouseY - startY) > 1));
    }

    public static void endDrag(double mx, double my) {
        boolean didDrag = dragging;
        WWidget source = sourceWidget;
        if (dragging && module != null) {
            resolveDropTarget(mx, my);
            if (validDrop && targetCategoryKey != null && targetWindow != null) {
                if (targetCategoryKey.equals(sourceCategoryKey)) {
                    ModuleLayoutManager.reorderWithin(sourceCategoryKey, module, insertIndex);
                } else {
                    ModuleLayoutManager.moveModule(module, sourceCategoryKey, targetCategoryKey, insertIndex);
                }
            }
        }
        if (didDrag) {
            suppressToggle = true;
            if (source instanceof meteordevelopment.meteorclient.gui.widgets.pressable.WPressable pressable) {
                try {
                    java.lang.reflect.Field pressedField = meteordevelopment.meteorclient.gui.widgets.pressable.WPressable.class.getDeclaredField("pressed");
                    pressedField.setAccessible(true);
                    pressedField.setBoolean(pressable, false);
                } catch (Throwable ignored) {
                }
            }
        }
        reset();
    }

    public static boolean consumeSuppressToggle() {
        if (suppressToggle) {
            suppressToggle = false;
            return true;
        }
        return false;
    }

    public static boolean shouldSuppressToggle(WWidget widget) {
        if (suppressToggle) return true;
        if (sourceWidget != null && sourceWidget == widget) {
            return dragging || (pending && (Math.abs(mouseX - startX) > 1 || Math.abs(mouseY - startY) > 1));
        }
        return false;
    }

    public static boolean isSourceWidget(WWidget widget) {
        return sourceWidget != null && sourceWidget == widget;
    }

    public static void cancel() {
        reset();
    }

    public static boolean removeFromCustomCategory(Module mod, WWidget widget) {
        if (mod == null || widget == null) return false;
        String key = resolveCategoryKey(widget);
        if (key == null || !ModuleLayoutManager.isCustomKey(key)) return false;
        String name = ModuleLayoutManager.stripCustomKey(key);
        CustomCategory category = null;
        for (CustomCategory c : CustomCategoryManager.getCategories()) {
            if (c.name.equals(name)) {
                category = c;
                break;
            }
        }
        if (category == null) return false;
        if (!CustomCategoryManager.isModuleInCategory(mod, category)) return false;
        CustomCategoryManager.toggleModuleAssignment(mod, category);
        ModuleLayoutManager.removeModuleFromCustomOrder(key, mod.name);
        return true;
    }

    private static void reset() {
        module = null;
        sourceWidget = null;
        sourceCategoryKey = null;
        pending = false;
        dragging = false;
        insertIndex = -1;
        targetCategoryKey = null;
        targetWindow = null;
        validDrop = false;
        hoverTargetWidget = null;
    }

    private static void resolveDropTarget(double mx, double my) {
        targetCategoryKey = null;
        targetWindow = null;
        insertIndex = -1;
        validDrop = false;
        hoverTargetWidget = null;

        WWidget root = sourceWidget;
        while (root != null && root.parent != null) root = root.parent;

        if (root instanceof WContainer container) {
            findWindowAt(container, mx, my);
        }
    }

    private static void findWindowAt(WContainer container, double mx, double my) {
        for (int i = container.cells.size() - 1; i >= 0; i--) {
            WWidget child = container.cells.get(i).widget();
            if (child instanceof WWindow window) {
                if (isPointInWindow(window, mx, my)) {
                    String key = categoryKeyFromWindow(window);
                    if (key == null) continue;
                    if ("search".equals(key) || "favorites".equals(key)) continue;
                    if (!ModuleLayoutManager.isCustomKey(key)) continue;

                    targetWindow = window;
                    targetCategoryKey = key;
                    validDrop = true;
                    resolveHoverModule(window, my);
                    return;
                }
            }
            if (child instanceof WContainer nested) {
                findWindowAt(nested, mx, my);
                if (targetWindow != null) return;
            }
        }
    }

    private static void resolveHoverModule(WWindow window, double my) {
        WView view = window.view;
        if (view == null || view.cells.isEmpty()) {
            insertIndex = 0;
            return;
        }

        WWidget lastModuleWidget = null;
        int lastModuleIndex = -1;
        WWidget chosen = null;
        int index = 0;

        for (int i = 0; i < view.cells.size(); i++) {
            WWidget w = view.cells.get(i).widget();
            if (w == null || extractModule(w) == null) continue;
            lastModuleWidget = w;
            lastModuleIndex = i;

            if (my >= w.y && my <= w.y + w.height) {
                chosen = w;
                if (my < w.y + w.height * 0.5) {
                    index = i;
                } else {
                    index = i + 1;
                }
                break;
            }

            if (my < w.y) {
                chosen = w;
                index = i;
                break;
            }

            index = i + 1;
            chosen = w;
        }

        if (chosen == null && lastModuleWidget != null) {
            chosen = lastModuleWidget;
            index = lastModuleIndex + 1;
        }

        hoverTargetWidget = chosen;

        if (sourceCategoryKey != null && sourceCategoryKey.equals(targetCategoryKey) && module != null) {
            int sourceIdx = indexOfModuleInView(view, module);
            if (sourceIdx >= 0 && index > sourceIdx) index--;
        }

        insertIndex = Math.max(0, index);
    }

    private static boolean isPointInWindow(WWindow window, double mx, double my) {
        return mx >= window.x && mx <= window.x + window.width
            && my >= window.y && my <= window.y + window.height;
    }

    private static int indexOfModuleInView(WView view, Module mod) {
        for (int i = 0; i < view.cells.size(); i++) {
            WWidget w = view.cells.get(i).widget();
            Module m = extractModule(w);
            if (m != null && m.name.equals(mod.name)) return i;
        }
        return -1;
    }

    public static Module extractModule(WWidget widget) {
        if (widget == null) return null;
        try {
            Field f = widget.getClass().getDeclaredField("module");
            f.setAccessible(true);
            Object val = f.get(widget);
            return val instanceof Module m ? m : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveCategoryKey(WWidget widget) {
        WWidget current = widget;
        while (current != null) {
            if (current instanceof WWindow window) {
                return categoryKeyFromWindow(window);
            }
            current = current.parent;
        }
        return null;
    }

    private static String categoryKeyFromWindow(WWindow window) {
        if (window.id == null || window.id.isEmpty()) {
            try {
                Field titleField = WWindow.class.getDeclaredField("title");
                titleField.setAccessible(true);
                Object t = titleField.get(window);
                if (t instanceof String s && !s.isEmpty()) {
                    for (CustomCategory cat : CustomCategoryManager.getCategories()) {
                        if (cat.name.equals(s)) return ModuleLayoutManager.customKey(cat.name);
                    }
                    return s;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
        if (window.id.startsWith("custom-")) return window.id;
        return window.id;
    }
}
