package me.noramibu.tweaks.gui.screens;

import me.noramibu.tweaks.modules.ChatUtility;
import me.noramibu.tweaks.utils.Keyword;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class KeywordsScreen extends WindowScreen {
    private final ChatUtility module;
    private WTable table;

    public KeywordsScreen(GuiTheme theme) {
        super(theme, "Keywords");
        this.module = Modules.get().get(ChatUtility.class);
    }

    @Override
    public void initWidgets() {
        // Add button
        WButton addButton = add(theme.button("Add")).expandX().widget();
        addButton.action = () -> minecraft.setScreenAndShow(new EditKeywordScreen(theme, null, keyword -> {
            if (!module.keywords.contains(keyword)) {
                module.keywords.add(keyword);
            }
            refresh();
        }));

        this.table = add(theme.table()).expandX().widget();

        refresh();
    }

    private void refresh() {
        table.clear();

        for (Keyword keyword : module.keywords) {
            table.add(theme.label(keyword.name));

            WButton editButton = table.add(theme.button("Edit")).widget();
            editButton.action = () -> minecraft.setScreenAndShow(new EditKeywordScreen(theme, keyword, k -> refresh()));

            WButton deleteButton = table.add(theme.button("Remove")).widget();
            deleteButton.action = () -> {
                module.keywords.remove(keyword);
                refresh();
            };

            table.row();
        }
    }
}
