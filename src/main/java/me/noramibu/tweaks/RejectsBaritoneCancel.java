package me.noramibu.tweaks;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class RejectsBaritoneCancel implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return "anticope.rejects.mixin.baritone.MineProcessMixin".equals(mixinClassName);
    }
}
