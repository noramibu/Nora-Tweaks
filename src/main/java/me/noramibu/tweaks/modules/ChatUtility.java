package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.gui.screens.KeywordsScreen;
import me.noramibu.tweaks.utils.Keyword;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class ChatUtility extends Module {
    private final SettingGroup sgChatNotify = settings.createGroup("Chat Notify");
    private final SettingGroup sgAutoMessage = settings.createGroup("Auto Message");

    // Chat Notify Settings
    private final Setting<Boolean> chatNotifyEnabled = sgChatNotify.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Plays a sound when a chat message contains specific keywords.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toastNotify = sgChatNotify.add(new BoolSetting.Builder()
        .name("toast-notify")
        .description("Shows a toast notification when a message is received.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<SoundEvent>> sound = sgChatNotify.add(new SoundEventListSetting.Builder()
        .name("sound")
        .description("The sound to play on notification.")
        .defaultValue(List.of(SoundEvents.EXPERIENCE_ORB_PICKUP))
        .build()
    );

    // Auto Message Settings
    private final Setting<Boolean> autoMessageEnabled = sgAutoMessage.add(new BoolSetting.Builder()
        .name("auto-message")
        .description("Sends a custom message to chat periodically.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> message = sgAutoMessage.add(new StringSetting.Builder()
        .name("message")
        .description("The message to send.")
        .defaultValue("Hello, world!")
        .build()
    );

    private final Setting<Integer> delay = sgAutoMessage.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay in seconds between messages.")
        .defaultValue(60)
        .min(1)
        .build()
    );

    public final List<Keyword> keywords = new ArrayList<>();
    private int timer;
    private TrayIcon trayIcon;

    public ChatUtility() {
        super(NoraTweaks.CATEGORY, "chat-utility", "Various chat-related utilities.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        WButton button = table.add(theme.button("Keywords to notify")).expandX().widget();
        button.action = () -> mc.setScreenAndShow(new KeywordsScreen(theme));
        table.row();

        if (!keywords.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
            table.add(theme.label("Active Keywords:")).expandX();
            table.row();

            for (Keyword keyword : keywords) {
                table.add(theme.label(keyword.name));
                table.row();
            }
        }

        return table;
    }

    @Override
    public void onActivate() {
        timer = 0;
        setupTrayIcon();
        for (Keyword keyword : keywords) {
            keyword.compilePattern();
        }
    }

    @Override
    public void onDeactivate() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();

        ListTag keywordsTag = new ListTag();
        for (Keyword keyword : keywords) {
            keywordsTag.add(keyword.toNbt());
        }
        tag.put("keywords", keywordsTag);

        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        super.fromTag(tag);

        keywords.clear();
        if (tag.contains("keywords") && tag.get("keywords") != null && tag.get("keywords").getId() == Tag.TAG_LIST) { // 9 = List
            ListTag keywordsTag = (ListTag) tag.get("keywords");
            for (Tag keywordTag : keywordsTag) {
                if (keywordTag.getId() == 10) { // 10 = Compound
                    keywords.add(Keyword.fromNbt((CompoundTag) keywordTag));
                }
            }
        }

        return this;
    }

    private void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            return;
        }

        try (InputStream is = NoraTweaks.class.getResourceAsStream("/assets/nora-tweaks/icon.png")) {
            if (is == null) {
                NoraTweaks.LOG.warn("Could not find icon for toast notification.");
                return;
            }
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                NoraTweaks.LOG.warn("Failed to read icon for toast notification.");
                return;
            }
            Image scaledImage = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            trayIcon = new TrayIcon(scaledImage, "Nora Tweaks");
            trayIcon.setImageAutoSize(true);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                NoraTweaks.LOG.error("Failed to add system tray icon.", e);
            }
        } catch (Exception e) {
            NoraTweaks.LOG.error("Failed to set up system tray icon.", e);
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!chatNotifyEnabled.get()) return;

        String fullMessage = event.getMessage().getString();
        String messageContent = fullMessage;

        int separatorIndex = Math.max(fullMessage.lastIndexOf('>'), fullMessage.lastIndexOf(':'));
        if (separatorIndex != -1 && separatorIndex + 1 < fullMessage.length()) {
            messageContent = fullMessage.substring(separatorIndex + 1).trim();
        }

        for (Keyword keyword : keywords) {
            if (checkMatch(messageContent, keyword)) {
                if (!sound.get().isEmpty()) mc.player.playSound(sound.get().get(0), 1, 1);
                if (toastNotify.get()) sendToastNotification("Minecraft", fullMessage);
                break;
            }
        }
    }

    private boolean checkMatch(String message, Keyword keyword) {
        if (keyword.pattern != null) {
            return keyword.pattern.matcher(message).find();
        }
        return false;
    }

    private void sendToastNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!autoMessageEnabled.get()) return;

        if (timer >= delay.get() * 20) {
            mc.player.connection.sendChat(message.get());
            timer = 0;
        } else {
            timer++;
        }
    }
}
