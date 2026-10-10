package me.noramibu.tweaks.mixin.baritone;

import me.noramibu.tweaks.modules.OreSim;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

@Pseudo
@Mixin(targets = {"baritone.em", "baritone.ek"}, remap = false, priority = 2000)
public class MineProcessMixinApi {

    @Unique
    private static Field cachedField;

    @Unique
    private static boolean fieldLookupAttempted;

    @Unique
    private static boolean isMineProcess;

    @Unique
    private static boolean isMineProcessChecked;

    @Inject(method = "a", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onRescanObf(CallbackInfo ci) {
        if (!checkIsMineProcess()) {
            return;
        }
        handleRescan(ci);
    }

    @Inject(method = "rescan", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onRescanNamed(CallbackInfo ci) {
        if (!checkIsMineProcess()) {
            return;
        }
        handleRescan(ci);
    }

    @Unique
    private void handleRescan(CallbackInfo ci) {
        OreSim oreSim = Modules.get().get(OreSim.class);
        if (oreSim == null || !oreSim.baritone()) {
            return;
        }
        List<BlockPos> simulatedGoals = oreSim.getBaritoneGoals();
        if (simulatedGoals == null || simulatedGoals.isEmpty()) {
            return;
        }
        if (setKnownOreLocations(this, simulatedGoals)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean checkIsMineProcess() {
        if (isMineProcessChecked) {
            return isMineProcess;
        }
        isMineProcessChecked = true;
        for (Class<?> iface : this.getClass().getInterfaces()) {
            if (iface.getName().contains("MineProcess") || "cw".equals(iface.getSimpleName())) {
                isMineProcess = true;
                return true;
            }
        }
        Class<?> parent = this.getClass().getSuperclass();
        while (parent != null && parent != Object.class) {
            if (parent.getName().contains("MineProcess")) {
                isMineProcess = true;
                return true;
            }
            parent = parent.getSuperclass();
        }
        String simple = this.getClass().getSimpleName();
        if (simple.contains("MineProcess") || "em".equals(simple) || "ek".equals(simple)) {
            isMineProcess = true;
            return true;
        }
        return false;
    }

    @Unique
    private static boolean setKnownOreLocations(Object instance, List<BlockPos> locations) {
        try {
            Field field = getKnownOreLocationsField(instance.getClass());
            if (field != null) {
                field.set(instance, locations);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Unique
    private static Field getKnownOreLocationsField(Class<?> clazz) {
        if (fieldLookupAttempted) {
            return cachedField;
        }
        fieldLookupAttempted = true;
        try {
            cachedField = clazz.getDeclaredField("knownOreLocations");
            cachedField.setAccessible(true);
            return cachedField;
        } catch (NoSuchFieldException ignored) {
        }
        Field firstListField = null;
        Field firstListBlockPosField = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (firstListField == null) {
                firstListField = field;
                firstListField.setAccessible(true);
            }
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    String typeName = typeArgs[0].getTypeName();
                    if (typeName.contains("BlockPos") || typeName.contains("class_2338")) {
                        firstListBlockPosField = field;
                        firstListBlockPosField.setAccessible(true);
                        break;
                    }
                }
            }
        }
        cachedField = firstListBlockPosField != null ? firstListBlockPosField : firstListField;
        return cachedField;
    }
}
