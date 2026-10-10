package me.noramibu.tweaks.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class NoraTweaksMixinPlugin implements IMixinConfigPlugin {
    private static final boolean CATPUCCIN_OLD_LOADED = FabricLoader.getInstance().isModLoaded("catpuccin-addon");
    private static final boolean CATPPUCCIN_LOADED = FabricLoader.getInstance().isModLoaded("catppuccin-addon");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".catpuccin.")) {
            return CATPUCCIN_OLD_LOADED;
        }
        if (mixinClassName.contains(".catppuccin.")) {
            return CATPPUCCIN_LOADED;
        }
        if (mixinClassName.contains(".baritone.")) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return List.of("LocatorBarMixin");
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
