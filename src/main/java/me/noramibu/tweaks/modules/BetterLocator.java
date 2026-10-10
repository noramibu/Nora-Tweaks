/*
 * Implemented from Meteor Client PR #5928
 * https://github.com/MeteorDevelopment/meteor-client/pull/5928
 * This module will not be registered if Meteor Client already has better-locator.
 */

package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class BetterLocator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlayers = settings.createGroup("Players");
    private final SettingGroup sgMeteorWaypoints = settings.createGroup("Meteor Waypoints");

    public final Setting<Boolean> alwaysShowExperience = sgGeneral.add(new BoolSetting.Builder()
        .name("always-show-experience")
        .description("Always shows the experience bar when the locator bar is visible.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showDirections = sgGeneral.add(new BoolSetting.Builder()
        .name("show-directions")
        .description("Shows cardinal directions (N, S, E, W) on the locator bar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> displayHeads = sgPlayers.add(new BoolSetting.Builder()
        .name("display-heads")
        .description("Displays player heads instead of dots on the locator bar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> displayPlayerData = sgPlayers.add(new BoolSetting.Builder()
        .name("display-player-data")
        .description("Displays information about the player.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> displayPlayerName = sgPlayers.add(new BoolSetting.Builder()
        .name("display-name")
        .description("Displays the player's name.")
        .defaultValue(true)
        .visible(displayPlayerData::get)
        .build()
    );

    public final Setting<Boolean> displayPlayerDistance = sgPlayers.add(new BoolSetting.Builder()
        .name("display-distance")
        .description("Displays the player's distance.")
        .defaultValue(true)
        .visible(displayPlayerData::get)
        .build()
    );

    public final Setting<Boolean> respectPlayerColor = sgPlayers.add(new BoolSetting.Builder()
        .name("respect-color")
        .description("Respects the player's color.")
        .defaultValue(false)
        .visible(displayPlayerData::get)
        .build()
    );

    public final Setting<Boolean> displayPlayerOnlyOnTab = sgPlayers.add(new BoolSetting.Builder()
        .name("display-only-on-tab")
        .description("Only displays data when holding the player list key (Tab).")
        .defaultValue(true)
        .visible(displayPlayerData::get)
        .build()
    );

    public final Setting<Boolean> displayWaypoints = sgMeteorWaypoints.add(new BoolSetting.Builder()
        .name("display-waypoints")
        .description("Displays Meteor Client waypoints on the locator bar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> displayWaypointName = sgMeteorWaypoints.add(new BoolSetting.Builder()
        .name("display-name")
        .description("Displays the waypoint's name.")
        .defaultValue(true)
        .visible(displayWaypoints::get)
        .build()
    );

    public final Setting<Boolean> displayWaypointDistance = sgMeteorWaypoints.add(new BoolSetting.Builder()
        .name("display-distance")
        .description("Displays the waypoint's distance.")
        .defaultValue(true)
        .visible(displayWaypoints::get)
        .build()
    );

    public final Setting<Boolean> displayWaypointOnlyOnTab = sgMeteorWaypoints.add(new BoolSetting.Builder()
        .name("display-only-on-tab")
        .description("Only displays data when holding the player list key (Tab).")
        .defaultValue(false)
        .visible(displayWaypoints::get)
        .build()
    );

    public BetterLocator() {
        super(NoraTweaks.CATEGORY, "better-locator", "Enhances the vanilla Locator Bar HUD with additional features.");
    }
}
