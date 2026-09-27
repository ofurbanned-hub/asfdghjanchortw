package com.anchorbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnchorRtpBotClient implements ClientModInitializer {
    private static final int SCAN_RADIUS = 100;
    private static final double PLAYER_RADIUS = 32.0;
    private static final int HUNGER_THRESHOLD = 10;
    private static final int LOW_DURABILITY_PERCENT = 15;

    private static boolean enabled = false;
    private static State state = State.IDLE;
    private static BlockPos target = null;
    private static long nextAction = 0;
    private static long lastScan = 0;

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.anchorbot.toggle",
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.MISC
    );

    enum State { IDLE, RTP, WAITING, SCANNING, MOVING, MINING, EATING, REPAIRING }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.consumeClick()) {
                enabled = !enabled;
                state = enabled ? State.RTP : State.IDLE;
                target = null;
                message(client, enabled ? "Anchor RTP Bot: ON" : "Anchor RTP Bot: OFF");
            }

            if (!enabled || client.player == null || client.level == null) return;
            tick(client);
        });
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();

        if (now < nextAction) return;

        if (nearbyPlayer(mc)) {
            target = null;
            sendCommand(mc, "rtp");
            state = State.WAITING;
            nextAction = now + 3500;
            return;
        }

        if (needsFood(mc)) {
            eat(mc);
            return;
        }

        if (needsRepair(mc)) {
            state = State.REPAIRING;
            repairWithNearbyXp(mc);
            if (needsRepair(mc)) {
                nextAction = now + 1000;
                return;
            }
        }

        switch (state) {
            case RTP -> {
                sendCommand(mc, "rtp");
                state = State.WAITING;
                nextAction = now + 4000;
            }
            case WAITING -> {
                if (mc.player.tickCount % 10 == 0) {
                    state = State.SCANNING;
                }
                nextAction = now + 250;
            }
            case SCANNING -> {
                target = findNearestAnchor(mc);
                if (target == null) {
                    sendCommand(mc, "rtp");
                    state = State.WAITING;
                    nextAction = now + 4000;
                } else {
                    state = State.MOVING;
                    nextAction = now + 100;
                }
            }
            case MOVING -> {
                if (target == null || mc.level.getBlockState(target).getBlock() != Blocks.RESPAWN_ANCHOR) {
                    state = State.SCANNING;
                    nextAction = now + 100;
                    return;
                }
                faceAndMove(mc, target);
                if (mc.player.distanceToSqr(target.getX() + .5, target.getY() + .5, target.getZ() + .5) < 9.0) {
                    state = State.MINING;
                }
                nextAction = now + 50;
            }
            case MINING -> {
                if (target == null || mc.level.getBlockState(target).getBlock() != Blocks.RESPAWN_ANCHOR) {
                    state = State.SCANNING;
                    nextAction = now + 100;
                    return;
                }
                faceAndMine(mc, target);
                nextAction = now + 50;
            }
            case REPAIRING, EATING, IDLE -> {
                nextAction = now + 100;
            }
        }

        if (now - lastScan > 1500 && state != State.RTP && state != State.WAITING) {
            lastScan = now;
            BlockPos found = findNearestAnchor(mc);
            if (found != null) target = found;
            else if (state == State.MINING || state == State.MOVING) state = State.RTP;
        }
    }

    private static boolean nearbyPlayer(Minecraft mc) {
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            if (p.distanceToSqr(mc.player) <= PLAYER_RADIUS * PLAYER_RADIUS) return true;
        }
        return false;
    }

    private static boolean needsFood(Minecraft mc) {
        return mc.player.getFoodData().getFoodLevel() <= HUNGER_THRESHOLD
                && findFoodSlot(mc) >= 0;
    }

    private static int findFoodSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.has(DataComponents.FOOD)) return i;
        }
        return -1;
    }

    private static void eat(Minecraft mc) {
        int slot = findFoodSlot(mc);
        if (slot < 0) return;
        mc.player.getInventory().setSelectedSlot(slot);
        state = State.EATING;
        mc.options.keyUse.setDown(true);
        nextAction = System.currentTimeMillis() + 1400;
        if (mc.player.getFoodData().getFoodLevel() >= 18) {
            mc.options.keyUse.setDown(false);
            state = State.SCANNING;
        }
    }

    private static boolean needsRepair(Minecraft mc) {
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || !held.isDamageableItem()) return false;
        int max = held.getMaxDamage();
        int remaining = max - held.getDamageValue();
        return remaining <= Math.max(1, max * LOW_DURABILITY_PERCENT / 100);
    }

    private static void repairWithNearbyXp(Minecraft mc) {
        mc.player.getInventory().setSelectedSlot(mc.player.getInventory().getSelectedSlot());
        // Mending repairs automatically when XP orbs are collected while the damaged
        // item is held. This routine simply keeps the pickaxe equipped.
    }

    private static BlockPos findNearestAnchor(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        int r = SCAN_RADIUS;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x*x + y*y + z*z > r*r) continue;
                    BlockPos p = origin.offset(x, y, z);
                    if (mc.level.getBlockState(p).getBlock() == Blocks.RESPAWN_ANCHOR) {
                        found.add(p.immutable());
                    }
                }
            }
        }

        return found.stream()
                .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5)))
                .orElse(null);
    }

    private static void faceAndMove(Minecraft mc, BlockPos pos) {
        double dx = pos.getX() + .5 - mc.player.getX();
        double dy = pos.getY() + .5 - (mc.player.getY() + mc.player.getEyeHeight());
        double dz = pos.getZ() + .5 - mc.player.getZ();

        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.options.keyUp.setDown(true);
    }

    private static void faceAndMine(Minecraft mc, BlockPos pos) {
        faceAndMove(mc, pos);
        mc.options.keyUp.setDown(false);
        mc.options.keyAttack.setDown(true);
    }

    private static void sendCommand(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
        mc.options.keyUp.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }
}
