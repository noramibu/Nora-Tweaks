package me.noramibu.tweaks.gui.screens;

import me.noramibu.tweaks.category.CustomCategory;
import me.noramibu.tweaks.category.CustomCategoryManager;
import me.noramibu.tweaks.events.CustomCategoriesChangedEvent;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ManageCategoryModulesScreen extends WindowScreen {
    private final CustomCategory category;
    private WTable modulesTable;
    private String filter = "";

    public ManageCategoryModulesScreen(GuiTheme theme, CustomCategory category) {
        super(theme, "Manage Category - " + category.name);
        this.category = category;
    }

    @Override
    protected void init() {
        super.init();
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void initWidgets() {
        // Search bar
        WTextBox searchBox = add(theme.textBox(filter)).expandX().widget();
        searchBox.setFocused(true);
        searchBox.action = () -> {
            filter = searchBox.get();
            buildTable();
        };

        // Buttons
        WTable buttonTable = add(new WTable()).expandX().widget();

        WButton sortButton = buttonTable.add(theme.button("Sort: " + getSortModeName(category.sortOrder))).expandX().widget();
        sortButton.action = () -> {
            category.cycleSortOrder();
            CustomCategoryManager.save();
            sortButton.set("Sort: " + getSortModeName(category.sortOrder));
        };

        WButton addAllButton = buttonTable.add(theme.button("Add All")).expandX().widget();
        addAllButton.action = this::addAll;

        WButton removeAllButton = buttonTable.add(theme.button("Remove All")).expandX().widget();
        removeAllButton.action = this::removeAll;

        add(theme.horizontalSeparator()).expandX();

        // Modules list
        modulesTable = add(new WTable()).expandX().widget();
        buildTable();
    }

    private void buildTable() {
        modulesTable.clear();
        List<Module> moduleList = Modules.get().getAll().stream()
            .filter(m -> filter.isEmpty() || m.title.toLowerCase().contains(filter.toLowerCase()))
            .sorted(Comparator.comparing(m -> m.title))
            .collect(Collectors.toList());

        for (Module module : moduleList) {
            boolean inCategory = CustomCategoryManager.isModuleInCategory(module, category);

            WCheckbox checkbox = modulesTable.add(theme.checkbox(inCategory)).widget();
            checkbox.action = () -> CustomCategoryManager.toggleModuleAssignment(module, category);

            modulesTable.add(theme.label(module.title));

            if (inCategory) {
                WIntEdit weightEdit = modulesTable.add(theme.intEdit(CustomCategoryManager.getModuleWeight(module, category), 0, 999, false)).expandX().right().widget();
                weightEdit.action = () -> CustomCategoryManager.setModuleWeight(module, category, weightEdit.get());
            } else {
                modulesTable.add(theme.label("")).expandX(); // Placeholder to keep alignment
            }
            modulesTable.row();
        }
    }

    private List<Module> getFilteredModules() {
        return Modules.get().getAll().stream()
            .filter(m -> filter.isEmpty() || m.title.toLowerCase().contains(filter.toLowerCase()))
            .collect(Collectors.toList());
    }

    private void addAll() {
        CustomCategoryManager.assignAll(getFilteredModules(), category);
    }

    private void removeAll() {
        CustomCategoryManager.unassignAll(getFilteredModules(), category);
    }

    private String getSortModeName(me.noramibu.tweaks.category.SortOrder order) {
        if (order == null) return "A-Z";
        switch (order) {
            case A_TO_Z: return "A-Z";
            case Z_TO_A: return "Z-A";
            case WEIGHT: return "Weight";
            default:     return "A-Z";
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onCategoriesChanged(CustomCategoriesChangedEvent event) {
        Minecraft.getInstance().execute(() -> {
            if (!CustomCategoryManager.getCategories().contains(category)) {
                onClose();
                return;
            }

            // If the name changed, reopen the screen with the new title
            if (!this.getTitle().getString().equals("Manage Category - " + category.name)) {
                this.onClose();
                Minecraft.getInstance().setScreenAndShow(new ManageCategoryModulesScreen(theme, category));
                return;
            }

            buildTable();
        });
    }

    @Override
    public void onClose() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        super.onClose();
    }
}
