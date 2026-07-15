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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;

public class CategoryManagerScreen extends WindowScreen {
    private WTable categoriesTable;

    public CategoryManagerScreen(GuiTheme theme) {
        super(theme, "Category Manager");
    }

    @Override
    protected void init() {
        super.init();
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void initWidgets() {
        WTable table = add(new WTable()).expandX().widget();
        table.add(theme.label("Manage Categories")).expandX();
        table.row();

        categoriesTable = table.add(new WTable()).expandX().widget();
        buildCategoriesTable();
        table.row();

        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        table.add(theme.label("Create New Category")).expandX();
        table.row();

        WTable createTable = table.add(new WTable()).expandX().widget();
        WTextBox newCategoryBox = createTable.add(theme.textBox("")).expandX().widget();
        WButton addButton = createTable.add(theme.button("Add")).widget();
        addButton.action = () -> {
            if (!newCategoryBox.get().trim().isEmpty()) {
                CustomCategoryManager.addCategory(newCategoryBox.get());
                newCategoryBox.set("");
            }
        };
    }

    private void buildCategoriesTable() {
        categoriesTable.clear();
        for (CustomCategory category : CustomCategoryManager.getCategories()) {
            categoriesTable.add(theme.label(category.name)).expandX();

            WButton manageButton = categoriesTable.add(theme.button("Manage")).widget();
            manageButton.action = () -> Minecraft.getInstance().setScreenAndShow(new ManageCategoryModulesScreen(theme, category));

            WButton renameButton = categoriesTable.add(theme.button("Rename")).widget();
            renameButton.action = () -> Minecraft.getInstance().setScreenAndShow(new RenameCategoryScreen(theme, category));

            WButton activateButton = categoriesTable.add(theme.button("Activate All")).widget();
            activateButton.action = () -> {
                for (meteordevelopment.meteorclient.systems.modules.Module module : CustomCategoryManager.getModules(category)) {
                    if (!module.isActive()) module.toggle();
                }
            };

            WButton deactivateButton = categoriesTable.add(theme.button("Deactivate All")).widget();
            deactivateButton.action = () -> {
                for (meteordevelopment.meteorclient.systems.modules.Module module : CustomCategoryManager.getModules(category)) {
                    if (module.isActive()) module.toggle();
                }
            };

            WButton deleteButton = categoriesTable.add(theme.button("Delete")).widget();
            deleteButton.action = () -> {
                CustomCategoryManager.deleteCategory(category);
            };
            categoriesTable.row();
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onCategoriesChanged(CustomCategoriesChangedEvent event) {
        Minecraft.getInstance().execute(this::buildCategoriesTable);
    }

    @Override
    public void onClose() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        super.onClose();
    }
}
