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
@Mixin(targets = "baritone.process.MineProcess", remap = false, priority = 2000)
public class MineProcessMixin {

    @Unique
    private static Field cachedField;

    @Unique
    private static boolean fieldLookupAttempted;

    @Inject(method = "rescan", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onRescanNamed(CallbackInfo ci) {
        handleRescan(ci);
    }

    @Inject(method = "a", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onRescanObf(CallbackInfo ci) {
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
