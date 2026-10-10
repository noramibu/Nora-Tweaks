package me.noramibu.tweaks.utils;

import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;

public class Keyword {
    public String name;
    public boolean caseSensitive;
    public boolean wholeWord;
    public boolean useRegex;
    public transient Pattern pattern;

    public Keyword(String name, boolean caseSensitive, boolean wholeWord, boolean useRegex) {
        this.name = name;
        this.caseSensitive = caseSensitive;
        this.wholeWord = wholeWord;
        this.useRegex = useRegex;
        compilePattern();
    }

    public void compilePattern() {
        if (name == null || name.isEmpty()) {
            pattern = null;
            return;
        }

        try {
            if (useRegex) {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(name, flags);
            } else {
                String patternString = Pattern.quote(name);
                if (wholeWord) {
                    patternString = "\\b" + patternString + "\\b";
                }
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = Pattern.compile(patternString, flags);
            }
        } catch (Exception e) {
            pattern = null;
        }
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("name", name);
        nbt.putBoolean("caseSensitive", caseSensitive);
        nbt.putBoolean("wholeWord", wholeWord);
        nbt.putBoolean("useRegex", useRegex);
        return nbt;
    }

    public static Keyword fromNbt(CompoundTag nbt) {
        Keyword keyword = new Keyword(
            nbt.getString("name").orElse(""),
            nbt.getBoolean("caseSensitive").orElse(false),
            nbt.getBoolean("wholeWord").orElse(false),
            nbt.getBoolean("useRegex").orElse(false)
        );
        keyword.compilePattern();
        return keyword;
    }
}
