package com.tankvisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class TankVisualsClient implements ClientModInitializer {
    public static KeyBinding guiKey;
    public static KeyBinding itemSwapKey;
    public static KeyBinding elytraSwapKey;

    public static boolean hudEnabled = true;
    public static boolean targetHudEnabled = true;
    public static boolean optimizationEnabled = true;
    public static boolean itemSwapEnabled = true;
    public static boolean elytraSwapEnabled = true;

    // Режим свапа: 0 = "Тотем ⇄ Тотем", 1 = "Сфера/Голова ⇄ Тотем"
    public static int swapMode = 0; 

    // Таймер для задержки свапа (15 тиков)
    private static int swapDelayTicks = 0;
    private static boolean pendingSwap = false;

    @Override
    public void onInitializeClient() {
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.tankvisuals"
        ));
        itemSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.swap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.tankvisuals"
        ));
        elytraSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.elytra", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.tankvisuals"
        ));

        // Отрисовка HUD
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            if (hudEnabled) {
                int fps = client.getCurrentFps();
                int ping = 0;
                if (client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) != null) {
                    ping = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency();
                }
                String infoText = "TankVisuals | FPS: " + fps + " | Ping: " + ping + "ms";
                context.drawTextWithShadow(client.textRenderer, infoText, 10, 10, 0x00FF66);
            }

            if (targetHudEnabled) {
                Entity targetedEntity = client.targetedEntity;
                if (targetedEntity instanceof LivingEntity living) {
                    int screenWidth = client.getWindow().getScaledWidth();
                    int screenHeight = client.getWindow().getScaledHeight();
                    int x = screenWidth / 2 - 60;
                    int y = screenHeight / 2 + 20;

                    context.fill(x, y, x + 120, y + 35, 0xAA000000);
                    String name = living.getName().getString();
                    if (name.length() > 15) name = name.substring(0, 12) + "...";
                    
                    context.drawTextWithShadow(client.textRenderer, name, x + 5, y + 5, 0xFFFFFF);
                    
                    float health = living.getHealth();
                    float maxHealth = living.getMaxHealth();
                    int healthBarWidth = (int) ((health / maxHealth) * 110);
                    context.fill(x + 5, y + 20, x + 115, y + 28, 0xFF555555);
                    context.fill(x + 5, y + 20, x + 5 + Math.max(0, healthBarWidth), y + 28, 0xFFFF5555);
                }
            }
        });

        // Слушатель тиков с задержкой 15 тиков
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Обработка таймера задержки свапа
            if (pendingSwap) {
                if (swapDelayTicks > 0) {
                    swapDelayTicks--;
                } else {
                    performSmartSwap(client);
                    pendingSwap = false;
                }
            }

            if (guiKey.wasPressed()) {
                client.setScreen(new SoupGuiScreen());
            }

            while (itemSwapEnabled && itemSwapKey.wasPressed()) {
                if (!pendingSwap) {
                    swapDelayTicks = 15; // Задержка ровно 15 тиков перед выполнением
                    pendingSwap = true;
                }
            }

            while (elytraSwapEnabled && elytraSwapKey.wasPressed()) {
                performElytraSwap(client);
            }
        });
    }

    private void performSmartSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        int syncId = client.player.playerScreenHandler.syncId;
        ItemStack offhandStack = client.player.getOffHandStack();

        if (swapMode == 0) {
            int totemSlot = findAnyItemSlot(client, Items.TOTEM_OF_UNDYING);
            if (totemSlot != -1) {
                swapSlotWithOffhand(client, syncId, totemSlot);
            }
        } else if (swapMode == 1) {
            if (!offhandStack.isOf(Items.TOTEM_OF_UNDYING)) {
                int totemSlot = findAnyItemSlot(client, Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) swapSlotWithOffhand(client, syncId, totemSlot);
            } else {
                int headSlot = findAnyItemSlot(client, Items.PLAYER_HEAD);
                if (headSlot != -1) swapSlotWithOffhand(client, syncId, headSlot);
            }
        }
    }

    private void performElytraSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        int syncId = client.player.playerScreenHandler.syncId;
        
        ItemStack chestStack = client.player.playerScreenHandler.getSlot(6).getStack();

        if (!chestStack.isOf(Items.ELYTRA)) {
            int elytraSlot = findAnyItemSlot(client, Items.ELYTRA);
            if (elytraSlot != -1) {
                swapArmorSlot(client, syncId, elytraSlot, 6);
            }
        } else {
            int chestplateSlot = findChestplateSlot(client);
            if (chestplateSlot != -1) {
                swapArmorSlot(client, syncId, chestplateSlot, 6);
            }
        }
    }

    private int findAnyItemSlot(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 0; i < client.player.playerScreenHandler.slots.size(); i++) {
            if (i == 45) continue;
            if (client.player.playerScreenHandler.getSlot(i).getStack().isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findChestplateSlot(MinecraftClient client) {
        for (int i = 0; i < client.player.playerScreenHandler.slots.size(); i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem().toString().contains("chestplate")) {
                return i;
            }
        }
        return -1;
    }

    private void swapSlotWithOffhand(MinecraftClient client, int syncId, int slot) {
        client.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, client.player);
    }

    private void swapArmorSlot(MinecraftClient client, int syncId, int fromSlot, int armorSlot) {
        client.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, client.player);
    }

    private static class SoupGuiScreen extends Screen {
        protected SoupGuiScreen() {
            super(Text.literal("SoupVisuals ClickGUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            int x = width / 2 - 130;
            int y = height / 2 - 110;

            context.fill(x, y, x + 260, y + 220, 0xEE1A1A1A);
            context.fill(x, y, x + 260, y + 25, 0xEE2A2A2A);
            context.drawCenteredTextWithShadow(textRenderer, "TankVisuals // SoupVisuals Style", width / 2, y + 8, 0xFF55FF55);

            context.drawTextWithShadow(textRenderer, "[1] Player HUD: " + (hudEnabled ? "ON" : "OFF"), x + 15, y + 35, hudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[2] Target HUD: " + (targetHudEnabled ? "ON" : "OFF"), x + 15, y + 60, targetHudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[3] Optimization: " + (optimizationEnabled ? "ON" : "OFF"), x + 15, y + 85, optimizationEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[4] Item Swap (G): " + (itemSwapEnabled ? "ON" : "OFF"), x + 15, y + 110, itemSwapEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[5] Elytra Swap (V): " + (elytraSwapEnabled ? "ON" : "OFF"), x + 15, y + 135, elytraSwapEnabled ? 0x55FF55 : 0xFF5555);

            String modeStr = (swapMode == 0) ? "Тотем ⇄ Тотем" : "Сфера ⇄ Тотем";
            context.drawTextWithShadow(textRenderer, "[M] Режим свапа: " + modeStr, x + 15, y + 165, 0xFFFF55);

            context.drawTextWithShadow(textRenderer, "Нажми 1-5 или M для выбора", x + 15, y + 195, 0xAAAAAA);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_1) { hudEnabled = !hudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_2) { targetHudEnabled = !targetHudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_3) { optimizationEnabled = !optimizationEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_4) { itemSwapEnabled = !itemSwapEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_5) { elytraSwapEnabled = !elytraSwapEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_M) { swapMode = (swapMode == 0) ? 1 : 0; return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
