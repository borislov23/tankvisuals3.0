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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class TankVisualsClient implements ClientModInitializer {
    // Клавиши (можно менять в настройках управления Minecraft)
    public static KeyBinding guiKey;
    public static KeyBinding itemSwapKey;
    public static KeyBinding elytraSwapKey;

    // Переключатели модулей
    public static boolean hudEnabled = true;
    public static boolean targetHudEnabled = true;
    public static boolean optimizationEnabled = true;

    @Override
    public void onInitializeClient() {
        // Регистрация горячих клавиш
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.tankvisuals"
        ));
        itemSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.swap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.tankvisuals"
        ));
        elytraSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.elytra", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.tankvisuals"
        ));

        // Рендер HUD и Target HUD
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            // 1. Обычный HUD (FPS и Пинг)
            if (hudEnabled) {
                int fps = client.getCurrentFps();
                int ping = 0;
                if (client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) != null) {
                    ping = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency();
                }
                String infoText = "TankVisuals | FPS: " + fps + " | Ping: " + ping + "ms";
                context.drawTextWithShadow(client.textRenderer, infoText, 10, 10, 0x00FF66);
            }

            // 2. Target HUD (Цель, на которую смотришь)
            if (targetHudEnabled) {
                Entity targetedEntity = client.targetedEntity;
                if (targetedEntity instanceof LivingEntity living) {
                    int screenWidth = client.getWindow().getScaledWidth();
                    int screenHeight = client.getWindow().getScaledHeight();
                    int x = screenWidth / 2 - 60;
                    int y = screenHeight / 2 + 20;

                    // Фон плашки Target HUD
                    context.fill(x, y, x + 120, y + 35, 0xAA000000);
                    String name = living.getName().getString();
                    if (name.length() > 15) name = name.substring(0, 12) + "...";
                    
                    context.drawTextWithShadow(client.textRenderer, name, x + 5, y + 5, 0xFFFFFF);
                    
                    // Полоска здоровья
                    float health = living.getHealth();
                    float maxHealth = living.getMaxHealth();
                    int healthBarWidth = (int) ((health / maxHealth) * 110);
                    context.fill(x + 5, y + 20, x + 115, y + 28, 0xFF555555);
                    context.fill(x + 5, y + 20, x + 5 + Math.max(0, healthBarWidth), y + 28, 0xFFFF5555);
                }
            }
        });

        // Обработка нажатий в игре
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Открытие ClickGUI
            if (guiKey.wasPressed()) {
                client.setScreen(new SoupGuiScreen());
            }

            // Свап Голова ⇄ Тотем
            while (itemSwapKey.wasPressed()) {
                performTotemHeadSwap(client);
            }

            // Элитра-свап (нагрудник ⇄ элитра)
            while (elytraSwapKey.wasPressed()) {
                performElytraSwap(client);
            }
        });
    }

    // Логика свапа: Тотем в левую руку / Голова игрока
    private void performTotemHeadSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        int syncId = client.player.playerScreenHandler.syncId;
        ItemStack offhandStack = client.player.getOffHandStack();

        if (!offhandStack.isOf(Items.TOTEM_OF_UNDYING)) {
            int totemSlot = findItemSlot(client, Items.TOTEM_OF_UNDYING);
            if (totemSlot != -1) swapSlotWithOffhand(client, syncId, totemSlot);
        } else {
            int headSlot = findItemSlot(client, Items.PLAYER_HEAD);
            if (headSlot != -1) swapSlotWithOffhand(client, syncId, headSlot);
        }
    }

    // Логика элитра-свапа (слот нагрудника — 6)
    private void performElytraSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        int syncId = client.player.playerScreenHandler.syncId;
        ItemStack chestStack = client.player.getInventory().getStack(38); // Слот нагрудника

        if (!chestStack.isOf(Items.ELYTRA)) {
            int elytraSlot = findItemSlot(client, Items.ELYTRA);
            if (elytraSlot != -1) {
                client.interactionManager.clickSlot(syncId, elytraSlot, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(syncId, elytraSlot, 0, SlotActionType.PICKUP, client.player);
            }
        } else {
            int chestplateSlot = findBestChestplateSlot(client);
            if (chestplateSlot != -1) {
                client.interactionManager.clickSlot(syncId, chestplateSlot, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(syncId, chestplateSlot, 0, SlotActionType.PICKUP, client.player);
            }
        }
    }

    private int findItemSlot(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 9; i < 45; i++) {
            if (client.player.playerScreenHandler.getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }

    private int findBestChestplateSlot(MinecraftClient client) {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = client.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.getItem().toString().contains("chestplate")) return i;
        }
        return -1;
    }

    private void swapSlotWithOffhand(MinecraftClient client, int syncId, int slot) {
        client.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, client.player);
    }

    // Красивое ClickGUI в стиле SoupVisuals
    private static class SoupGuiScreen extends Screen {
        protected SoupGuiScreen() {
            super(Text.literal("SoupVisuals ClickGUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            int x = width / 2 - 130;
            int y = height / 2 - 90;

            // Окно меню
            context.fill(x, y, x + 260, y + 180, 0xEE1A1A1A);
            context.fill(x, y, x + 260, y + 25, 0xEE2A2A2A);
            context.drawCenteredTextWithShadow(textRenderer, "TankVisuals // SoupVisuals Style", width / 2, y + 8, 0xFF55FF55);

            // Опции
            context.drawTextWithShadow(textRenderer, "[1] Player HUD: " + (hudEnabled ? "ON" : "OFF"), x + 15, y + 40, hudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[2] Target HUD: " + (targetHudEnabled ? "ON" : "OFF"), x + 15, y + 70, targetHudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[3] Optimization: " + (optimizationEnabled ? "ON" : "OFF"), x + 15, y + 100, optimizationEnabled ? 0x55FF55 : 0xFF5555);

            context.drawTextWithShadow(textRenderer, "Нажми 1, 2 или 3 для переключения", x + 15, y + 145, 0xAAAAAA);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_1) { hudEnabled = !hudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_2) { targetHudEnabled = !targetHudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_3) { optimizationEnabled = !optimizationEnabled; return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
