package me.noramibu.tweaks.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.gui.screens.HotkeysScreen;
import me.noramibu.tweaks.utils.Hotkey;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ContainerInput;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.utils.misc.input.KeyAction.Press;

public class HotkeyUtility extends Module {
    public final List<Hotkey> hotkeys = new ArrayList<>();

    public HotkeyUtility() {
        super(NoraTweaks.CATEGORY, "hotkey-utility", "Allows you to set key combinations to switch to a specific hotbar slot.");
        loadHotkeys();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("Configure Hotkeys");
        button.action = () -> mc.setScreenAndShow(new HotkeysScreen(theme, this));
        return button;
    }

    public void saveHotkeys() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray array = new JsonArray();
        for (Hotkey hotkey : hotkeys) {
            array.add(hotkey.toJson());
        }

        try (FileWriter writer = new FileWriter(getHotkeysFile())) {
            gson.toJson(array, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadHotkeys() {
        hotkeys.clear();
        File file = getHotkeysFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonArray array = new Gson().fromJson(reader, JsonArray.class);
                if (array != null) {
                    for (int i = 0; i < array.size(); i++) {
                        hotkeys.add(Hotkey.fromJson(array.get(i).getAsJsonObject()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private File getHotkeysFile() {
        File folder = new File(MeteorClient.FOLDER, "nora-tweaks");
        if (!folder.exists()) folder.mkdirs();
        return new File(folder, "hotkeys.json");
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
        saveHotkeys();
        for (Hotkey hotkey : hotkeys) {
            hotkey.resetState();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        saveHotkeys();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Hotkey hotkey : hotkeys) {
            if (hotkey.delayLeft > 0) {
                hotkey.delayLeft--;
            } else {
                hotkey.pressCount = 0;
            }
        }
    }

    @EventHandler
    private void onKeyPress(KeyInputEvent event) {
        if (event.action != Press) return;

        for (Hotkey hotkey : hotkeys) {
            //? if >=1.21.9 {
            if (hotkey.keybind.matches(true, event.key(), event.modifiers())) {
            //?} else
            /*if (hotkey.keybind.matches(true, event.key, event.modifiers)) {
            */
                if (hotkey.delayLeft > 0) {
                    hotkey.pressCount++;
                } else {
                    hotkey.pressCount = 1;
                }

                hotkey.delayLeft = hotkey.pressDelay;

                if (hotkey.pressCount >= hotkey.presses) {
                    switch (hotkey.action) {
                        case SwitchSlot:
                            int targetSlot = hotkey.slot - 1;
                            //? if >=1.21.5 {
                            if (mc.player != null && mc.player.getInventory().getSelectedSlot() != targetSlot) {
                            //?} else
                            /*if (mc.player != null && mc.player.getInventory().selectedSlot != targetSlot) {
                            */
                                mc.player.getInventory().setSelectedSlot(targetSlot);
                            }
                            break;
                        case HoldItem:
                            if (mc.player != null) {
                                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                                    if (hotkey.matches(mc.player.getInventory().getItem(i))) {
                                        if (i < 9) { // In hotbar
                                            mc.player.getInventory().setSelectedSlot(i);
                                        } else { // In main inventory
                                            int emptyHotbarSlot = -1;
                                            for (int j = 0; j < 9; j++) {
                                                if (mc.player.getInventory().getItem(j).isEmpty()) {
                                                    emptyHotbarSlot = j;
                                                    break;
                                                }
                                            }

                                            if (emptyHotbarSlot != -1) {
                                                // Move to empty hotbar slot
                                                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, i, emptyHotbarSlot, ContainerInput.SWAP, mc.player);
                                                mc.player.getInventory().setSelectedSlot(emptyHotbarSlot);
                                            } else {
                                                // Hotbar is full, swap with selected slot
                                                //? if >=1.21.5 {
                                                int selectedSlot = mc.player.getInventory().getSelectedSlot();
                                                //?} else
                                                /*int selectedSlot = mc.player.getInventory().selectedSlot;
                                                */
                                                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, i, selectedSlot, ContainerInput.SWAP, mc.player);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                            break;
                    }
                    hotkey.resetState();
                }
            }
        }
    }
}
