package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class MaceCombo extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCombat = settings.createGroup("Combat");

    // Static flag to prevent Wind Charge Jump interference
    public static boolean isUsingWindCharge = false;

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Maximum range to auto-attack target.")
        .defaultValue(4.5)
        .min(2.0)
        .max(8.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum range to maintain target tracking.")
        .defaultValue(12.0)
        .min(5.0)
        .max(20.0)
        .sliderMax(15.0)
        .build()
    );

    private final Setting<Boolean> autoRotate = sgCombat.add(new BoolSetting.Builder()
        .name("auto-rotate")
        .description("Automatically rotate to face target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgCombat.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Show combo status messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxComboTime = sgCombat.add(new IntSetting.Builder()
        .name("max-combo-time")
        .description("Maximum combo duration in seconds.")
        .defaultValue(30)
        .min(3)
        .max(60)
        .sliderMax(45)
        .build()
    );

    private final Setting<Boolean> lockFirstMace = sgCombat.add(new BoolSetting.Builder()
        .name("lock-first-mace")
        .description("Lock to the first mace found, preventing manual switching during combo.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instantWindCharge = sgCombat.add(new BoolSetting.Builder()
        .name("instant-wind-charge")
        .description("Use wind charge immediately when landing without delay.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> windChargeDelay = sgCombat.add(new IntSetting.Builder()
        .name("wind-charge-delay")
        .description("Delay in ticks before jumping after wind charge (20 ticks = 1 second).")
        .defaultValue(1)
        .min(0)
        .max(10)
        .sliderMax(5)
        .visible(() -> !instantWindCharge.get())
        .build()
    );

    private final Setting<Boolean> optimizePitch = sgCombat.add(new BoolSetting.Builder()
        .name("optimize-pitch")
        .description("Set pitch to 85° instead of 90° for better wind charge trajectory and jump height.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> waitForAttackIndicator = sgCombat.add(new BoolSetting.Builder()
        .name("wait-for-attack-indicator")
        .description("Wait for attack indicator to be full before attacking, unless very close to landing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> closeAttackRange = sgCombat.add(new DoubleSetting.Builder()
        .name("close-attack-range")
        .description("Range where attack indicator is ignored and attack happens immediately.")
        .defaultValue(2.5)
        .min(1.0)
        .max(5.0)
        .sliderMax(4.0)
        .visible(() -> waitForAttackIndicator.get())
        .build()
    );

    // State
    private LivingEntity target = null;
    private boolean comboActive = false;
    private int comboTicks = 0;
    private int hits = 0;
    private int attackCooldown = 0;
    private boolean hasLaunched = false;
    private boolean awaitingGroundCombo = false;
    private boolean lastOnGround = false;
    private int windChargeJumpTicks = 0;
    private int savedMaceSlot = -1;
    private boolean awaitingWindChargeUse = false;
    private int firstUsedMaceSlot = -1;
    private int windChargeThrowDelay = 0;

    public MaceCombo() {
        super(NoraTweaks.CATEGORY, "mace-combo", "Auto-attacks targets and chains mace combos with wind charges.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        target = null;
        comboActive = false;
        comboTicks = 0;
        hits = 0;
        attackCooldown = 0;
        hasLaunched = false;
        awaitingGroundCombo = false;
        lastOnGround = false;
        windChargeJumpTicks = 0;
        savedMaceSlot = -1;
        awaitingWindChargeUse = false;
        firstUsedMaceSlot = -1;
        windChargeThrowDelay = 0;
        isUsingWindCharge = false;
    }

    @EventHandler
    private void onAttack(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;
        if (!(event.packet instanceof ServerboundAttackPacket(int entityId))) return;
        if (mc.player.getMainHandItem().getItem() != Items.MACE) return;

        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity livingTarget)) return;

        // Start combo on first manual attack
        if (!comboActive && mc.player.fallDistance >= 1.5) {
            // Record the mace slot that was used to start the combo
            firstUsedMaceSlot = mc.player.getInventory().getSelectedSlot();
            startCombo(livingTarget);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // Handle all timers efficiently
        handleTimers();

        if (!comboActive) return;

        comboTicks++;
        if (attackCooldown > 0) attackCooldown--;

        // Check timeout
        if (comboTicks > maxComboTime.get() * 20) {
            endCombo("Combo timed out!");
            return;
        }

        // Check target validity using horizontal distance (more efficient)
        if (!isTargetValid()) {
            return;
        }

        // Handle mace switching only when needed
        handleMaceSwitching();

        // Track launch state
        updateLaunchState();

        // Handle combat logic
        handleCombatLogic();
    }

    private void handleTimers() {
        // Wind charge jump timer
        if (windChargeJumpTicks > 0 && --windChargeJumpTicks == 0) {
            performWindChargeJump();
        }

        // Wind charge throw delay timer
        if (windChargeThrowDelay > 0 && --windChargeThrowDelay == 0) {
            switchBackToMace();
        }

        // Safety check: reset stuck wind charge states after 10 seconds
        if (isUsingWindCharge && windChargeThrowDelay == 0 && windChargeJumpTicks == 0) {
            // If we're using wind charge but no timers are active, something went wrong
            if (comboTicks % 200 == 0) { // Check every 10 seconds (200 ticks)
                if (chatFeedback.get()) {
                    mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §cResetting stuck wind charge state"));
                }
                isUsingWindCharge = false;
                awaitingWindChargeUse = false;
                savedMaceSlot = -1;
            }
        }
    }

    private boolean isTargetValid() {
        if (target == null || target.isDeadOrDying() || target.isRemoved()) {
            endCombo("Target lost!");
            return false;
        }

        // Use squared distance for better performance
        double dx = mc.player.getX() - target.getX();
        double dz = mc.player.getZ() - target.getZ();
        double horizontalDistanceSquared = dx * dx + dz * dz;

        if (horizontalDistanceSquared > targetRange.get() * targetRange.get()) {
            endCombo("Target too far!");
            return false;
        }

        return true;
    }

    private void handleMaceSwitching() {
        if (lockFirstMace.get() && mc.player.getMainHandItem().getItem() != Items.MACE &&
            !hasAnyMaceInHand() && !awaitingWindChargeUse) {
            switchToMace();
        }
    }

    private void updateLaunchState() {
        if (mc.player.getDeltaMovement().y > 0.5 || mc.player.fallDistance > 3.0) {
            hasLaunched = true;
        }
    }

    private void handleCombatLogic() {
        // Auto-attack when falling and close to target
        if (shouldAutoAttack()) {
            performAutoAttack();
        }

        // Ground-based wind charge usage
        if (shouldUseGroundWindCharge()) {
            initiateGroundWindCharge();
        }

        // Detect ground contact for wind charge + jump
        handleGroundContact();
    }

    private boolean shouldAutoAttack() {
        if (!mc.player.onGround() && mc.player.fallDistance >= 1.5 &&
            attackCooldown == 0 && hasLaunched && isTargetInRange(attackRange.get())) {

            // Check if we should wait for attack indicator
            if (waitForAttackIndicator.get()) {
                double distanceToTarget = mc.player.distanceTo(target);
                // Attack immediately if very close to landing, otherwise wait for full indicator
                return distanceToTarget <= closeAttackRange.get() || isAttackIndicatorFull();
            }

            return true;
        }
        return false;
    }

    private boolean isAttackIndicatorFull() {
        // Check if attack indicator is full (1.0 = full charge)
        return mc.player.getAttackStrengthScale(0.0f) >= 1.0f;
    }

    private boolean shouldUseGroundWindCharge() {
        if (mc.player.onGround() && attackCooldown == 0 && !awaitingGroundCombo &&
            !awaitingWindChargeUse && isTargetInRange(attackRange.get()) &&
            !isTargetInRange(2.0) && !isUsingWindCharge && hasWindCharges()) {

            // Check if we should wait for attack indicator
            if (waitForAttackIndicator.get()) {
                double distanceToTarget = mc.player.distanceTo(target);
                // Use wind charge immediately if very close, otherwise wait for full indicator
                return distanceToTarget <= closeAttackRange.get() || isAttackIndicatorFull();
            }

            return true;
        }
        return false;
    }

    private boolean isTargetInRange(double range) {
        if (target == null) return false;
        double distanceSquared = mc.player.distanceTo(target);
        return distanceSquared <= range;
    }

    private void performAutoAttack() {
        // Ensure we have a mace for attack if lock is enabled
        if (lockFirstMace.get() && mc.player.getMainHandItem().getItem() != Items.MACE && !hasAnyMaceInHand()) {
            switchToMace();
        }

        if (autoRotate.get()) rotateToTarget();
        attackTarget();
        attackCooldown = 20; // 1 second cooldown
        hasLaunched = false;
        awaitingGroundCombo = true;
    }

    private void initiateGroundWindCharge() {
        // Check if wind charge is in off-hand first (no slot switching needed)
        if (mc.player.getOffhandItem().getItem() == Items.WIND_CHARGE) {
            isUsingWindCharge = true;
            awaitingWindChargeUse = false; // Use immediately
            useWindCharge(); // Use wind charge immediately

            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bUsing wind charge (off-hand)"));
            }

            attackCooldown = 30; // Longer cooldown for ground-based usage
            return;
        }

        // Check hotbar slots
        int windChargeSlot = getWindChargeSlot();
        if (windChargeSlot != -1) {
            savedMaceSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(windChargeSlot);
            isUsingWindCharge = true;
            awaitingWindChargeUse = false; // Use immediately
            useWindCharge(); // Use wind charge immediately

            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bSwitched to wind charge (slot " + windChargeSlot + ")"));
            }

            attackCooldown = 30; // Longer cooldown for ground-based usage
        } else if (chatFeedback.get()) {
            mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §cNo wind charge found in hotbar!"));
        }
    }

    private void handleGroundContact() {
        boolean onGround = mc.player.onGround();
        if (awaitingGroundCombo && !lastOnGround && onGround && !awaitingWindChargeUse) {
            if (!isUsingWindCharge && hasWindCharges()) {
                // Check off-hand first (no slot switching needed)
                if (mc.player.getOffhandItem().getItem() == Items.WIND_CHARGE) {
                    isUsingWindCharge = true;
                    awaitingWindChargeUse = false; // Use immediately
                    useWindCharge(); // Use wind charge immediately

                    if (chatFeedback.get()) {
                        mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bUsing wind charge (off-hand)"));
                    }
                } else {
                    // Check hotbar slots
                    int windChargeSlot = getWindChargeSlot();
                    if (windChargeSlot != -1) {
                        savedMaceSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(windChargeSlot);
                        isUsingWindCharge = true;
                        awaitingWindChargeUse = false; // Use immediately
                        useWindCharge(); // Use wind charge immediately

                        if (chatFeedback.get()) {
                            mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bSwitched to wind charge (slot " + windChargeSlot + ")"));
                        }
                    } else if (chatFeedback.get()) {
                        mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §cNo wind charge found in hotbar!"));
                    }
                }
            } else if (chatFeedback.get() && !hasWindCharges()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §cNo wind charges available!"));
            }
            awaitingGroundCombo = false;
        }
        lastOnGround = onGround;
    }

    private void switchBackToMace() {
        // Only switch slots if we saved a slot (meaning we switched from a hotbar slot)
        if (savedMaceSlot != -1) {
            mc.player.getInventory().setSelectedSlot(savedMaceSlot);
            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §7Switched back to mace (slot " + savedMaceSlot + ")"));
            }
            savedMaceSlot = -1;
        }
        isUsingWindCharge = false;
        awaitingWindChargeUse = false;
    }

    private void useWindCharge() {
        if (mc.player == null) return;

        // Optimize pitch for maximum jump height
        if (optimizePitch.get()) {
            mc.player.setXRot(85.0f); // Slightly less than 90 for better trajectory
        } else {
            mc.player.setXRot(90.0f); // Straight down
        }

        // Use wind charge - prioritize off-hand, then main hand
        if (mc.player.getOffhandItem().getItem() == Items.WIND_CHARGE) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.player.swing(InteractionHand.OFF_HAND);
            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bWind Charge used (off-hand)!"));
            }
        } else if (mc.player.getMainHandItem().getItem() == Items.WIND_CHARGE) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bWind Charge used (main-hand)!"));
            }
        } else {
            if (chatFeedback.get()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §cError: No wind charge in hand!"));
            }
        }

        // Improved jump timing - wait a bit longer for better wind charge effect
        windChargeJumpTicks = instantWindCharge.get() ? 2 : windChargeDelay.get() + 2;

        // Set delay before switching back to mace (5 ticks = 0.25 seconds)
        windChargeThrowDelay = 5;
        awaitingWindChargeUse = false;
    }

    private void performWindChargeJump() {
        if (mc.player == null) return;

        // Enhanced jump with better velocity control and timing
        Vec3 currentVelocity = mc.player.getDeltaMovement();
        double jumpBoost = 0.6; // Increased jump strength for better height
        mc.player.setDeltaMovement(currentVelocity.x, jumpBoost, currentVelocity.z);

        if (chatFeedback.get()) {
            mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §bJump executed!"));
        }

        // Note: Switching back to mace is now handled by windChargeThrowDelay timer
    }

    private void startCombo(LivingEntity livingTarget) {
        target = livingTarget;
        comboActive = true;
        comboTicks = 0;
        hits = 1;
        String enchantStatus = " (Using wind charges)";
        if (chatFeedback.get()) {
            mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §aMace combo started!" + enchantStatus));
        }
    }

    private void endCombo(String reason) {
        if (chatFeedback.get()) {
            mc.player.sendSystemMessage(Component.literal("§8[§6Nora Tweaks§8] §7Combo ended: " + reason + " Hits: " + hits));
        }
        reset();
    }

    private void rotateToTarget() {
        Vec3 targetPos = target.position();
        Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
    }

    private void attackTarget() {
        if (mc.gameMode == null) return;
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        hits++;
    }

    private void switchToMace() {
        if (lockFirstMace.get()) {
            // Lock to the first mace that was used to start the combo
            if (firstUsedMaceSlot != -1 && mc.player.getInventory().getItem(firstUsedMaceSlot).getItem() == Items.MACE) {
                mc.player.getInventory().setSelectedSlot(firstUsedMaceSlot);
                return;
            }
            // Fallback to first mace found if the original slot is empty
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).getItem() == Items.MACE) {
                    mc.player.getInventory().setSelectedSlot(i);
                    return;
                }
            }
        } else {
            // Allow any mace - user can switch manually
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).getItem() == Items.MACE) {
                    mc.player.getInventory().setSelectedSlot(i);
                    return;
                }
            }
        }
    }

    private boolean hasAnyMaceInHand() {
        return mc.player.getMainHandItem().getItem() == Items.MACE ||
               mc.player.getOffhandItem().getItem() == Items.MACE;
    }

    private boolean hasWindCharges() {
        // Check main hand and off hand first (faster)
        if (mc.player.getMainHandItem().getItem() == Items.WIND_CHARGE ||
            mc.player.getOffhandItem().getItem() == Items.WIND_CHARGE) {
            return true;
        }

        // Check hotbar slots
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.WIND_CHARGE) {
                return true;
            }
        }
        return false;
    }

    // Returns the hotbar slot index of a wind charge, or -1 if not found
    private int getWindChargeSlot() {
        // Check hotbar slots only (off-hand is handled separately in useWindCharge)
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.WIND_CHARGE) {
                return i;
            }
        }
        return -1;
    }
}

