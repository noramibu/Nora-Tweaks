package me.noramibu.tweaks.category;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.events.CustomCategoriesChangedEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CustomCategoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(MeteorClient.FOLDER, "nora-tweaks-categories.json");

    private static final List<CustomCategory> categories = new ArrayList<>();
    private static final ConcurrentHashMap<String, List<ModuleConfig>> assignments = new ConcurrentHashMap<>();

    public static void init() {
        load();
        if (categories.isEmpty()) {
            addCategory("Example Category");
        }
    }

    public static void addCategory(String name) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmedName = name.trim();
        if (categories.stream().noneMatch(c -> c.name.equalsIgnoreCase(trimmedName))) {
            categories.add(new CustomCategory(trimmedName));
            assignments.put(trimmedName, new ArrayList<>());
            save();
        }
    }

    public static void deleteCategory(CustomCategory category) {
        if (category == null) return;
        categories.remove(category);
        assignments.remove(category.name);
        save();
    }

    public static void renameCategory(CustomCategory category, String newName) {
        if (category == null || newName == null || newName.trim().isEmpty()) return;
        String oldName = category.name;
        String finalNewName = newName.trim();

        List<ModuleConfig> moduleConfigs = assignments.remove(oldName);
        if (moduleConfigs == null) {
            moduleConfigs = new ArrayList<>();
        }
        assignments.put(finalNewName, moduleConfigs);

        category.name = finalNewName;
        save();
    }

    public static List<CustomCategory> getCategories() {
        return categories;
    }

    private static void toggleModuleAssignmentInternal(Module module, CustomCategory category) {
        if (module == null || category == null) return;
        List<ModuleConfig> configs = assignments.computeIfAbsent(category.name, k -> new ArrayList<>());

        Optional<ModuleConfig> existing = configs.stream().filter(mc -> mc.moduleName.equals(module.name)).findFirst();

        if (existing.isPresent()) {
            configs.remove(existing.get());
        } else {
            configs.add(new ModuleConfig(module.name));
        }
    }

    public static void toggleModuleAssignment(Module module, CustomCategory category) {
        toggleModuleAssignmentInternal(module, category);
        save();
    }

    public static void assignAll(List<Module> modules, CustomCategory category) {
        if (modules == null || category == null) return;
        List<ModuleConfig> configs = assignments.computeIfAbsent(category.name, k -> new ArrayList<>());
        for (Module module : modules) {
            Optional<ModuleConfig> existing = configs.stream().filter(mc -> mc.moduleName.equals(module.name)).findFirst();
            if (existing.isEmpty()) {
                configs.add(new ModuleConfig(module.name));
            }
        }
        save();
    }

    public static void unassignAll(List<Module> modules, CustomCategory category) {
        if (modules == null || category == null) return;
        List<ModuleConfig> configs = assignments.computeIfAbsent(category.name, k -> new ArrayList<>());
        List<String> moduleNames = modules.stream().map(m -> m.name).collect(Collectors.toList());
        configs.removeIf(mc -> moduleNames.contains(mc.moduleName));
        save();
    }

    public static boolean isModuleInCategory(Module module, CustomCategory category) {
        if (module == null || category == null) return false;
        List<ModuleConfig> configs = assignments.get(category.name);
        return configs != null && configs.stream().anyMatch(mc -> mc.moduleName.equals(module.name));
    }

    public static List<ModuleConfig> getOrCreateConfigs(CustomCategory category) {
        if (category == null) return new ArrayList<>();
        return assignments.computeIfAbsent(category.name, k -> new ArrayList<>());
    }

    public static List<Module> getModules(CustomCategory category) {
        if (category == null) return new ArrayList<>();
        List<ModuleConfig> configs = assignments.get(category.name);
        if (configs == null) return new ArrayList<>();

        return configs.stream()
            .map(mc -> Modules.get().get(mc.moduleName))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static int getModuleWeight(Module module, CustomCategory category) {
        if (module == null || category == null) return 100;
        List<ModuleConfig> configs = assignments.get(category.name);
        if (configs == null) return 100;

        return configs.stream()
            .filter(mc -> mc.moduleName.equals(module.name))
            .findFirst()
            .map(mc -> mc.weight)
            .orElse(100);
    }

    public static void setModuleWeight(Module module, CustomCategory category, int weight) {
        if (module == null || category == null) return;
        List<ModuleConfig> configs = assignments.get(category.name);
        if (configs == null) return;

        configs.stream()
            .filter(mc -> mc.moduleName.equals(module.name))
            .findFirst()
            .ifPresent(mc -> {
                mc.weight = weight;
                save();
            });
    }


    public static void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            CustomCategoryManagerData data = new CustomCategoryManagerData(categories, assignments);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            NoraTweaks.LOG.error("Failed to save custom categories", e);
        }

        if (MeteorClient.EVENT_BUS != null) {
            MeteorClient.EVENT_BUS.post(CustomCategoriesChangedEvent.INSTANCE);
        }
    }

    private static void load() {
        try {
            if (FILE.exists()) {
                String json = new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8);
                CustomCategoryManagerData data = GSON.fromJson(json, CustomCategoryManagerData.class);
                if (data != null) {
                    if (data.categories != null) categories.addAll(data.categories);
                    if (data.assignments != null) assignments.putAll(data.assignments);
                }
            }
        } catch (IOException e) {
            NoraTweaks.LOG.error("Failed to load custom categories", e);
        }
    }

    private static class CustomCategoryManagerData {
        List<CustomCategory> categories;
        ConcurrentHashMap<String, List<ModuleConfig>> assignments;

        public CustomCategoryManagerData(List<CustomCategory> categories, ConcurrentHashMap<String, List<ModuleConfig>> assignments) {
            this.categories = categories;
            this.assignments = assignments;
        }
    }
}
