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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class TankVisualsClient implements ClientModInitializer {
    private static KeyBinding guiKey;
    private static KeyBinding itemSwapKey;

    public static boolean hudEnabled = true;
    public static boolean optimizationEnabled = true;

    @Override
    public void onInitializeClient() {
        // Открытие ClickGUI на Right Shift
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.tankvisuals"
        ));

        // Свап Голова ⇄ Тотем на клавишу G
        itemSwapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tankvisuals.swap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.tankvisuals"
        ));

        // Визуал HUD (FPS/Ping)
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || !hudEnabled) return;

            int fps = client.getCurrentFps();
            int ping = 0;
            if (client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) != null) {
                ping = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency();
            }

            String infoText = "FPS: " + fps + " | Ping: " + ping + "ms";
            int screenWidth = client.getWindow().getScaledWidth();
            context.drawTextWithShadow(client.textRenderer, infoText, screenWidth - 110, 10, 0x00FF66);
        });

        // Обработка тиков (Нажатия клавиш)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (guiKey.wasPressed()) {
                client.setScreen(new SoupGuiScreen());
            }

            while (itemSwapKey.wasPressed()) {
                performTotemHeadSwap(client);
            }
        });
    }

    // Свап предмета из левой руки на Голову игрока из инвентаря
    private void performTotemHeadSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;

        int syncId = client.player.playerScreenHandler.syncId;
        ItemStack offhandStack = client.player.getOffHandStack();
        boolean isTotemInOffhand = offhandStack.isOf(Items.TOTEM_OF_UNDYING);

        if (!isTotemInOffhand) {
            // Если в левой руке не тотем — ищем тотем и ставим в левую руку
            int totemSlot = findItemSlot(client, Items.TOTEM_OF_UNDYING);
            if (totemSlot != -1) {
                swapSlotWithOffhand(client, syncId, totemSlot);
            }
        } else {
            // Если в левой руке тотем — ищем ГОЛОВУ ИГРОКА и меняем местами
            int headSlot = findItemSlot(client, Items.PLAYER_HEAD);
            if (headSlot != -1) {
                swapSlotWithOffhand(client, syncId, headSlot);
            }
        }
    }

    private int findItemSlot(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 9; i < 45; i++) {
            if (client.player.playerScreenHandler.getSlot(i).getStack().isOf(item)) {
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

    // Простая GUI-панель в стиле SoupVisuals
    private static class SoupGuiScreen extends Screen {
        protected SoupGuiScreen() {
            super(Text.literal("SoupVisuals ClickGUI"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.fill(width / 2 - 120, height / 2 - 80, width / 2 + 120, height / 2 + 80, 0xCC000000);
            context.drawCenteredTextWithShadow(textRenderer, "TankVisuals Menu", width / 2, height / 2 - 60, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, "[1] HUD Display: " + (hudEnabled ? "ON" : "OFF"), width / 2, height / 2 - 20, hudEnabled ? 0x00FF00 : 0xFF0000);
            context.drawCenteredTextWithShadow(textRenderer, "[2] Optimization: " + (optimizationEnabled ? "ON" : "OFF"), width / 2, height / 2 + 10, optimizationEnabled ? 0x00FF00 : 0xFF0000);
            context.drawCenteredTextWithShadow(textRenderer, "Press 1 or 2 to Toggle Options", width / 2, height / 2 + 50, 0xAAAAAA);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_1) {
                hudEnabled = !hudEnabled;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_2) {
                optimizationEnabled = !optimizationEnabled;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
