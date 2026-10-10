package me.noramibu.tweaks;

import me.noramibu.tweaks.category.CustomCategoryManager;
import me.noramibu.tweaks.category.ModuleLayoutManager;
import me.noramibu.tweaks.modules.AutoDirtPath;
import me.noramibu.tweaks.modules.AutoFarming;
import me.noramibu.tweaks.modules.AutoFarmLand;
import me.noramibu.tweaks.modules.AutoLogStrip;
import me.noramibu.tweaks.modules.CategoryManagerModule;
import me.noramibu.tweaks.modules.ChatUtility;
import me.noramibu.tweaks.modules.HotkeyUtility;
import me.noramibu.tweaks.modules.LegitMaceKill;
import me.noramibu.tweaks.modules.MaceCombo;
import me.noramibu.tweaks.modules.WindChargeJump;
import me.noramibu.tweaks.modules.AutoTrapPlus;
import me.noramibu.tweaks.modules.DeepslateESP;
import me.noramibu.tweaks.modules.OreSim;
import me.noramibu.tweaks.modules.PearlChecker;
import me.noramibu.tweaks.modules.BetterLocator;
import me.noramibu.tweaks.modules.AttributeSwapping;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import me.noramibu.tweaks.utils.ConfigModifier;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import meteordevelopment.meteorclient.commands.Commands;
import me.noramibu.tweaks.commands.CalculatorCommand;
import me.noramibu.tweaks.commands.MobCheckerCommand;
import me.noramibu.tweaks.commands.LocateCommand;
import me.noramibu.tweaks.commands.SeedCommand;
import me.noramibu.tweaks.commands.HostInfoCommand;
import me.noramibu.tweaks.utils.Seeds;
import me.noramibu.tweaks.utils.StartupDataCollector;

public class NoraTweaks extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Nora Tweaks");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Nora's Tweaks");

        CustomCategoryManager.init();
        ModuleLayoutManager.init();

        // Modules
        Modules.get().add(new AutoDirtPath());
        Modules.get().add(new AutoFarming());
        Modules.get().add(new AutoFarmLand());
        Modules.get().add(new AutoLogStrip());
        Modules.get().add(new CategoryManagerModule());
        Modules.get().add(new ChatUtility());
        Modules.get().add(new HotkeyUtility());
        Modules.get().add(new MaceCombo());
        Modules.get().add(new LegitMaceKill());
        Modules.get().add(new WindChargeJump());
        Modules.get().add(new AutoTrapPlus());
        Modules.get().add(new DeepslateESP());
        Modules.get().add(new PearlChecker());

        // Conditionally registering those modules that may exist in Meteor Client because PRs
        if (!isMeteorModulePresent("attribute-swap")) {
            Modules.get().add(new AttributeSwapping());
        } else {
            LOG.info("Meteor Client already has attribute-swap module, skipping AttributeSwapping.");
        }

        if (!isMeteorModulePresent("better-locator")) {
            Modules.get().add(new BetterLocator());
        } else {
            LOG.info("Meteor Client already has better-locator module, skipping BetterLocator.");
        }

        Modules.get().add(new OreSim());

        Commands.add(new MobCheckerCommand());
        Commands.add(new CalculatorCommand());
        Commands.add(new SeedCommand());
        Commands.add(new LocateCommand());
        Commands.add(new HostInfoCommand());

        Seeds.get();

        Systems.add(new StartupDataCollector());

        ConfigModifier.get();
        
        checkMeteorRejectsVersion();
    }
    
    private void checkMeteorRejectsVersion() {
        try {
            var rejectsMod = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("meteor-rejects")
                .orElse(null);
            
            if (rejectsMod != null) {
                String version = rejectsMod.getMetadata().getVersion().getFriendlyString();
                LOG.info("Detected Meteor Rejects version: {}", version);
                
                if (isVersionLowerThan(version, "0.4.0")) {
                    LOG.warn("=".repeat(60));
                    LOG.warn("Your Meteor Rejects version ({}) is outdated.", version);
                    LOG.warn("Please use the updated fork of Meteor Rejects for better compatibility:");
                    LOG.warn("https://github.com/MCDxAI/meteor-rejects-v2");
                    LOG.warn("=".repeat(60));
                }
            }
        } catch (Exception e) {
        }
    }
    
    private boolean isVersionLowerThan(String version, String target) {
        try {
            String[] vParts = version.split("[.-]");
            String[] tParts = target.split("[.-]");
            
            for (int i = 0; i < Math.min(3, Math.min(vParts.length, tParts.length)); i++) {
                int v = Integer.parseInt(vParts[i].replaceAll("[^0-9]", ""));
                int t = Integer.parseInt(tParts[i].replaceAll("[^0-9]", ""));
                if (v < t) return true;
                if (v > t) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.noramibu.tweaks";
    }

    public static boolean isBaritonePresent() {
        try {
            Class.forName("baritone.api.pathing.goals.Goal");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isMeteorModulePresent(String moduleName) {
        return Modules.get().getAll().stream()
                .anyMatch(module -> module.name.equalsIgnoreCase(moduleName));
    }
}
