package me.noramibu.tweaks.utils;

import net.minecraft.world.level.ChunkPos;

public final class ChunkPosUtil {
    private ChunkPosUtil() {}

    public static long pack(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    public static long pack(ChunkPos pos) {
        return pack(x(pos), z(pos));
    }

    public static int x(ChunkPos pos) {
        try {
            return (int) ChunkPos.class.getMethod("x").invoke(pos);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return ChunkPos.class.getField("x").getInt(pos);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    public static int z(ChunkPos pos) {
        try {
            return (int) ChunkPos.class.getMethod("z").invoke(pos);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return ChunkPos.class.getField("z").getInt(pos);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
