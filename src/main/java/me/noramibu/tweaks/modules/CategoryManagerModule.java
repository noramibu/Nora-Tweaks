package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.gui.screens.CategoryManagerScreen;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Module;

public class CategoryManagerModule extends Module {
    public CategoryManagerModule() {
        super(NoraTweaks.CATEGORY, "category-manager", "Opens a screen to manage custom module categories.");
    }

    @Override
    public void onActivate() {
        mc.setScreenAndShow(new CategoryManagerScreen(GuiThemes.get()));
        toggle();
    }
}
