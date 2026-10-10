package me.noramibu.tweaks.gui.screens;

import me.noramibu.tweaks.modules.HotkeyUtility;
import me.noramibu.tweaks.utils.Hotkey;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.WKeybind;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class EditHotkeyScreen extends WindowScreen {
    private final Hotkey hotkey;
    private final HotkeyUtility module;
    private final List<WKeybind> keybindWidgets = new ArrayList<>();
    private WTable actionSettingsTable;
    private WIntEdit pressesWidget;
    private WIntEdit pressDelayWidget;
    private WDropdown<Hotkey.Action> actionDropdown;
    private WKeybind keybindWidget;

    public EditHotkeyScreen(GuiTheme theme, HotkeyUtility module, Hotkey hotkey) {
        super(theme, "Edit Hotkey");
        this.hotkey = hotkey;
        this.module = module;
        if (this.parent == null) {
            this.parent = new HotkeysScreen(theme, module);
        }
    }

    @Override
    public void initWidgets() {
        keybindWidgets.clear();
        WTable table = add(theme.table()).expandX().widget();

        // Keybind, Presses, and Delay are always visible
        table.add(theme.label("Keybind:"));
        keybindWidget = theme.keybind(hotkey.keybind);
        keybindWidget.action = this::save;
        table.add(keybindWidget).expandX();
        keybindWidgets.add(keybindWidget);
        table.row();

        table.add(theme.label("Presses:"));
        pressesWidget = theme.intEdit(hotkey.presses, 1, 100, false);
        pressesWidget.action = this::save;
        table.add(pressesWidget).expandX();
        table.row();

        table.add(theme.label("Press Delay:"));
        pressDelayWidget = theme.intEdit(hotkey.pressDelay, 0, 200, false);
        pressDelayWidget.action = this::save;
        table.add(pressDelayWidget).expandX();
        table.row();

        // Action dropdown
        table.add(theme.label("Action:"));
        actionDropdown = theme.dropdown(hotkey.action);
        actionDropdown.action = () -> {
            hotkey.action = actionDropdown.get();
            buildActionSettings();
            save();
        };
        table.add(actionDropdown).expandX();
        table.row();

        // Table for action-specific settings
        actionSettingsTable = add(theme.table()).expandX().widget();
        buildActionSettings();

        // Back button
        WButton backButton = add(theme.button("Back")).expandX().widget();
        backButton.action = () -> {
            save();
            if (this.parent instanceof HotkeysScreen s) {
                s.reload();
            }
            onClose();
        };
    }

    private void buildActionSettings() {
        actionSettingsTable.clear();

        switch (hotkey.action) {
            case SwitchSlot:
                actionSettingsTable.add(theme.label("Slot:"));
                WIntEdit slotWidget = theme.intEdit(hotkey.slot, 1, 9, true);
                slotWidget.action = () -> {
                    hotkey.slot = slotWidget.get();
                    save();
                };
                actionSettingsTable.add(slotWidget).expandX();
                actionSettingsTable.row();
                break;
            case HoldItem:
                // Item
                actionSettingsTable.add(theme.label("Item:"));
                WItem itemWidget = actionSettingsTable.add(theme.item(hotkey.toItemStack())).widget();
                actionSettingsTable.row();

                // Set button
                WButton getInfoButton = theme.button("Get from Main Hand");
                getInfoButton.action = () -> {
                    if (mc.player != null) {
                        ItemStack stack = mc.player.getMainHandItem();
                        if (!stack.isEmpty()) {
                            hotkey.fromItemStack(stack);
                            itemWidget.set(hotkey.toItemStack());
                            save();
                        }
                    }
                };
                actionSettingsTable.add(getInfoButton).expandX();
                actionSettingsTable.row();

                // Clear button
                WButton clearButton = theme.button("Clear");
                clearButton.action = () -> {
                    hotkey.fromItemStack(new ItemStack(Items.AIR));
                    itemWidget.set(hotkey.toItemStack());
                    save();
                };
                actionSettingsTable.add(clearButton).expandX();
                actionSettingsTable.row();
                break;
        }
    }

    private void save() {
        // This is the crucial part. We must update the hotkey object
        // with the state from all widgets before saving.
        hotkey.presses = pressesWidget.get();
        hotkey.pressDelay = pressDelayWidget.get();
        hotkey.action = actionDropdown.get();

        module.saveHotkeys();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        for (WKeybind w : keybindWidgets) {
            if (w.onAction(false, click.buttonInfo().button(), 0)) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        for (WKeybind w : keybindWidgets) {
            if (w.onAction(true, input.key(), input.modifiers())) return true;
        }
        return super.keyPressed(input);
    }
}
