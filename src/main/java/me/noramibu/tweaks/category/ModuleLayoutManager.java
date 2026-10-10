package me.noramibu.tweaks.category;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.events.CustomCategoriesChangedEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleLayoutManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(MeteorClient.FOLDER, "nora-tweaks-module-layout.json");

    private static final Map<String, String> overrides = new HashMap<>();
    private static final Map<String, List<String>> orders = new LinkedHashMap<>();
    private static final java.util.Set<String> hiddenDefaultCategories = new java.util.HashSet<>();

    public static void init() {
        load();
    }

    public static boolean isDefaultCategoryHidden(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) return false;
        if (hiddenDefaultCategories.contains(categoryName)) return true;
        for (String hidden : hiddenDefaultCategories) {
            if (hidden != null && hidden.equalsIgnoreCase(categoryName)) return true;
        }
        return false;
    }

    public static boolean isDefaultCategoryHidden(Category category) {
        return category != null && isDefaultCategoryHidden(category.name);
    }

    public static void setDefaultCategoryHidden(String categoryName, boolean hidden) {
        if (categoryName == null || categoryName.isEmpty()) return;
        hiddenDefaultCategories.removeIf(s -> s != null && s.equalsIgnoreCase(categoryName));
        if (hidden) hiddenDefaultCategories.add(categoryName);
        save();
        postChange();
    }

    public static void toggleDefaultCategoryHidden(String categoryName) {
        setDefaultCategoryHidden(categoryName, !isDefaultCategoryHidden(categoryName));
    }

    public static java.util.Set<String> getHiddenDefaultCategories() {
        return java.util.Collections.unmodifiableSet(hiddenDefaultCategories);
    }

    public static List<Category> getAllDefaultCategories() {
        List<Category> list = new ArrayList<>();
        try {
            for (Category category : Modules.loopCategories()) {
                list.add(category);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    public static void stripHiddenCategoryWindows(meteordevelopment.meteorclient.gui.widgets.containers.WContainer container, List<meteordevelopment.meteorclient.gui.widgets.containers.WWindow> windows) {
        if (container == null) return;
        try {
            if (windows != null) {
                windows.removeIf(w -> w == null || isHiddenWindow(w));
            }
            java.util.List<meteordevelopment.meteorclient.gui.utils.Cell<?>> toRemove = new java.util.ArrayList<>();
            for (meteordevelopment.meteorclient.gui.utils.Cell<?> cell : container.cells) {
                if (cell == null || cell.widget() == null) {
                    toRemove.add(cell);
                    continue;
                }
                if (cell.widget() instanceof meteordevelopment.meteorclient.gui.widgets.containers.WWindow window && isHiddenWindow(window)) {
                    toRemove.add(cell);
                }
            }
            for (meteordevelopment.meteorclient.gui.utils.Cell<?> cell : toRemove) {
                container.remove(cell);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isHiddenWindow(meteordevelopment.meteorclient.gui.widgets.containers.WWindow window) {
        if (window == null) return true;
        if (window.id != null && !window.id.isEmpty()) {
            if (window.id.startsWith("custom-")) return false;
            if ("search".equalsIgnoreCase(window.id) || "favorites".equalsIgnoreCase(window.id)) return false;
            if (isDefaultCategoryHidden(window.id)) return true;
        }
        try {
            java.lang.reflect.Field titleField = meteordevelopment.meteorclient.gui.widgets.containers.WWindow.class.getDeclaredField("title");
            titleField.setAccessible(true);
            Object title = titleField.get(window);
            if (title instanceof String s && isDefaultCategoryHidden(s)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String getDisplayCategory(Module module) {
        if (module == null) return null;
        String override = overrides.get(module.name);
        if (override != null) return override;
        return module.category != null ? module.category.name : null;
    }

    public static boolean hasOverride(Module module) {
        return module != null && overrides.containsKey(module.name);
    }

    public static List<Module> getModulesForDefaultCategory(Category category) {
        if (category == null) return new ArrayList<>();
        if (isDefaultCategoryHidden(category)) return new ArrayList<>();
        String catName = category.name;

        List<Module> result = new ArrayList<>();
        for (Module module : Modules.get().getGroup(category)) {
            try {
                if (meteordevelopment.meteorclient.systems.config.Config.get().hiddenModules.get().contains(module)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            String display = getDisplayCategory(module);
            if (catName.equals(display)) {
                result.add(module);
            }
        }

        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (!catName.equals(entry.getValue())) continue;
            Module module = Modules.get().get(entry.getKey());
            if (module == null) continue;
            try {
                if (meteordevelopment.meteorclient.systems.config.Config.get().hiddenModules.get().contains(module)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            if (module.category != null && module.category.name.equals(catName)) continue;
            if (!result.contains(module)) result.add(module);
        }

        return result;
    }

    public static List<Module> orderCustomModules(CustomCategory category, List<Module> modules) {
        if (modules == null || modules.isEmpty()) return modules;
        String key = customKey(category.name);
        List<String> order = orders.get(key);
        boolean useSavedOrder = order != null && !order.isEmpty() && category.sortOrder == SortOrder.WEIGHT;
        if (!useSavedOrder) {
            modules.sort(switch (category.sortOrder) {
                case WEIGHT -> java.util.Comparator
                    .comparingInt((Module m) -> CustomCategoryManager.getModuleWeight(m, category))
                    .thenComparing(m -> m.title);
                case Z_TO_A -> java.util.Comparator.comparing((Module m) -> m.title).reversed();
                default -> java.util.Comparator.comparing(m -> m.title);
            });
            return modules;
        }

        modules.sort((a, b) -> {
            int ia = order.indexOf(a.name);
            int ib = order.indexOf(b.name);
            if (ia < 0) ia = Integer.MAX_VALUE;
            if (ib < 0) ib = Integer.MAX_VALUE;
            if (ia != ib) return Integer.compare(ia, ib);
            return a.title.compareToIgnoreCase(b.title);
        });
        return modules;
    }

    public static void moveModule(Module module, String fromCategoryKey, String toCategoryKey, int insertIndex) {
        if (module == null || toCategoryKey == null) return;
        if (!isCustomKey(toCategoryKey)) return;

        boolean fromCustom = fromCategoryKey != null && isCustomKey(fromCategoryKey);
        String toName = stripCustomKey(toCategoryKey);
        String fromName = fromCategoryKey != null ? stripCustomKey(fromCategoryKey) : null;

        if (fromCustom && fromName != null) {
            CustomCategory fromCat = findCustom(fromName);
            if (fromCat != null) {
                List<ModuleConfig> configs = getConfigs(fromCat);
                configs.removeIf(mc -> mc.moduleName.equals(module.name));
            }
            removeFromOrder(fromCategoryKey, module.name);
        }

        CustomCategory toCat = findCustom(toName);
        if (toCat != null) {
            List<ModuleConfig> configs = getConfigs(toCat);
            if (configs.stream().noneMatch(mc -> mc.moduleName.equals(module.name))) {
                configs.add(new ModuleConfig(module.name));
            }
        }
        insertIntoOrder(toCategoryKey, module.name, insertIndex);

        save();
        CustomCategoryManager.save();
        postChange();
    }

    public static void reorderWithin(String categoryKey, Module module, int insertIndex) {
        if (module == null || categoryKey == null) return;
        if (!isCustomKey(categoryKey)) return;

        insertIntoOrder(categoryKey, module.name, insertIndex);

        CustomCategory cat = findCustom(stripCustomKey(categoryKey));
        if (cat != null) {
            List<String> order = orders.get(categoryKey);
            if (order != null) {
                List<ModuleConfig> configs = getConfigs(cat);
                for (int i = 0; i < order.size(); i++) {
                    String name = order.get(i);
                    for (ModuleConfig mc : configs) {
                        if (mc.moduleName.equals(name)) {
                            mc.weight = i * 10;
                            break;
                        }
                    }
                }
                cat.sortOrder = SortOrder.WEIGHT;
            }
        }

        save();
        CustomCategoryManager.save();
        postChange();
    }

    public static void removeModuleFromCustomOrder(String categoryKey, String moduleName) {
        if (categoryKey == null || moduleName == null) return;
        removeFromOrder(categoryKey, moduleName);
        save();
    }

    public static void clearOrder(String categoryKey) {
        if (categoryKey == null) return;
        orders.remove(categoryKey);
        save();
        postChange();
    }

    public static String categoryKeyForWindow(String windowId) {
        if (windowId == null) return null;
        if (windowId.startsWith("custom-")) return windowId;
        return windowId;
    }

    public static boolean isCustomKey(String key) {
        return key != null && key.startsWith("custom-");
    }

    public static String stripCustomKey(String key) {
        if (key == null) return null;
        if (key.startsWith("custom-")) return key.substring("custom-".length());
        return key;
    }

    public static String customKey(String name) {
        return "custom-" + name;
    }

    private static void insertIntoOrder(String key, String moduleName, int insertIndex) {
        List<String> order = orders.computeIfAbsent(key, k -> new ArrayList<>());
        if (order.isEmpty()) {
            CustomCategory cat = findCustom(stripCustomKey(key));
            if (cat != null) {
                List<ModuleConfig> configs = getConfigs(cat);
                List<Module> current = new ArrayList<>();
                Modules modules = Modules.get();
                if (modules != null) {
                    for (ModuleConfig mc : configs) {
                        Module m = modules.get(mc.moduleName);
                        if (m != null) current.add(m);
                    }
                }
                if (!current.isEmpty()) {
                    orderCustomModules(cat, current);
                    for (Module m : current) {
                        if (!m.name.equals(moduleName)) order.add(m.name);
                    }
                }
            }
        }
        order.remove(moduleName);
        if (insertIndex < 0 || insertIndex > order.size()) {
            order.add(moduleName);
        } else {
            order.add(insertIndex, moduleName);
        }
    }

    private static void removeFromOrder(String key, String moduleName) {
        List<String> order = orders.get(key);
        if (order != null) order.remove(moduleName);
    }

    private static CustomCategory findCustom(String name) {
        if (name == null) return null;
        return CustomCategoryManager.getCategories().stream()
            .filter(c -> c.name.equals(name))
            .findFirst()
            .orElse(null);
    }

    private static List<ModuleConfig> getConfigs(CustomCategory category) {
        return CustomCategoryManager.getOrCreateConfigs(category);
    }

    private static void postChange() {
        if (MeteorClient.EVENT_BUS != null) {
            MeteorClient.EVENT_BUS.post(CustomCategoriesChangedEvent.INSTANCE);
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) return;
            mc.execute(() -> refreshOpenModulesScreens(mc));
        } catch (Throwable ignored) {
        }
    }

    private static boolean refreshingModulesScreen;

    public static void refreshOpenModulesScreens(net.minecraft.client.Minecraft mc) {
        if (refreshingModulesScreen) return;
        try {
            meteordevelopment.meteorclient.gui.GuiTheme theme =
                meteordevelopment.meteorclient.gui.GuiThemes.get();
            if (theme == null) return;

            Object current = currentScreen(mc);
            if (current == null) return;

            java.lang.reflect.Field parentField =
                meteordevelopment.meteorclient.gui.WidgetScreen.class.getField("parent");

            String name = current.getClass().getName();
            if (name.contains("ModulesScreen") && current instanceof meteordevelopment.meteorclient.gui.WidgetScreen currentWs) {
                refreshingModulesScreen = true;
                try {
                    net.minecraft.client.gui.screens.Screen fresh = theme.modulesScreen();
                    if (fresh == null) return;
                    if (fresh instanceof meteordevelopment.meteorclient.gui.WidgetScreen freshWs) {
                        freshWs.parent = currentWs.parent;
                    }
                    mc.setScreenAndShow(fresh);
                } finally {
                    refreshingModulesScreen = false;
                }
                return;
            }

            if (current instanceof meteordevelopment.meteorclient.gui.WidgetScreen widgetScreen) {
                Object parent = parentField.get(widgetScreen);
                if (parent != null && parent.getClass().getName().contains("ModulesScreen")
                    && parent instanceof meteordevelopment.meteorclient.gui.WidgetScreen parentWs) {
                    net.minecraft.client.gui.screens.Screen fresh = theme.modulesScreen();
                    if (fresh == null) return;
                    if (fresh instanceof meteordevelopment.meteorclient.gui.WidgetScreen freshWs) {
                        freshWs.parent = parentWs.parent;
                    }
                    parentField.set(widgetScreen, fresh);
                }
            }
        } catch (Throwable ignored) {
            refreshingModulesScreen = false;
        }
    }

    private static Object currentScreen(net.minecraft.client.Minecraft mc) {
        try {
            Object gui = mc.getClass().getField("gui").get(mc);
            if (gui != null) {
                try {
                    return gui.getClass().getMethod("screen").invoke(gui);
                } catch (NoSuchMethodException e) {
                    try {
                        return gui.getClass().getMethod("getScreen").invoke(gui);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return mc.getClass().getMethod("getScreen").invoke(mc);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = mc.getClass().getField("screen");
            return f.get(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            LayoutData data = new LayoutData(overrides, orders, hiddenDefaultCategories);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            NoraTweaks.LOG.error("Failed to save module layout", e);
        }
    }

    private static void load() {
        try {
            if (!FILE.exists()) return;
            String json = new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
            LayoutData data = GSON.fromJson(json, LayoutData.class);
            if (data == null) return;
            if (data.overrides != null) overrides.putAll(data.overrides);
            if (data.orders != null) orders.putAll(data.orders);
            if (data.hiddenDefaultCategories != null) {
                hiddenDefaultCategories.clear();
                hiddenDefaultCategories.addAll(data.hiddenDefaultCategories);
            }
        } catch (IOException e) {
            NoraTweaks.LOG.error("Failed to load module layout", e);
        }
    }

    private static class LayoutData {
        Map<String, String> overrides;
        Map<String, List<String>> orders;
        java.util.Set<String> hiddenDefaultCategories;

        LayoutData(Map<String, String> overrides, Map<String, List<String>> orders, java.util.Set<String> hiddenDefaultCategories) {
            this.overrides = overrides;
            this.orders = orders;
            this.hiddenDefaultCategories = hiddenDefaultCategories;
        }
    }
}
