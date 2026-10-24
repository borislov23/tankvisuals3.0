package com.tankvisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TankVisualsClient implements ClientModInitializer {
    public static KeyBinding guiKey;
    public static KeyBinding itemSwapKey;
    public static KeyBinding elytraSwapKey;

    public static boolean hudEnabled = true;
    public static boolean targetHudEnabled = true;
    public static boolean optimizationEnabled = true;
    public static boolean itemSwapEnabled = true;
    public static boolean elytraSwapEnabled = true;
    public static boolean itemRadiusEnabled = true;

    public static int swapMode = 0; 
    public static int swapDelayConfigTicks = 10;

    private static int currentDelayCounter = 0;
    private static boolean pendingSwap = false;
    public static boolean playerInRange = false;

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

        // 3D Рендер зон в мире
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!itemRadiusEnabled || client.player == null || client.world == null) return;

            ItemStack mainHand = client.player.getMainHandStack();
            
            boolean shouldRender = mainHand.isOf(Items.ENDER_EYE) || 
                                   mainHand.isOf(Items.SUGAR) || 
                                   mainHand.isOf(Items.SNOWBALL) || 
                                   mainHand.isOf(Items.FIRE_CHARGE) || 
                                   mainHand.isOf(Items.NETHERITE_SCRAP) || 
                                   mainHand.isOf(Items.DRIED_KELP);
            
            if (!shouldRender) return;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
            MatrixStack matrices = context.matrixStack();
            Vec3d cameraPos = context.camera().getPos();

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            // 1. Дезориентация / Явная пыль (10б)
            if (mainHand.isOf(Items.ENDER_EYE) || mainHand.isOf(Items.SUGAR)) {
                Vec3d pos = client.player.getPos();
                Box box = new Box(pos.x - 10, pos.y, pos.z - 10, pos.x + 10, pos.y + 3, pos.z + 10);
                int col = playerInRange ? 0xFFFF0000 : 0xFFFFFFFF;
                draw3dBox(buffer, matrices, box, col);
            }
            // 2. Снежок заморозки (7б)
            else if (mainHand.isOf(Items.SNOWBALL)) {
                HitResult hit = client.crosshairTarget;
                Vec3d targetPos = (hit != null && hit.getType() == HitResult.Type.BLOCK) ? hit.getPos() : client.player.getPos();
                Box box = new Box(targetPos.x - 7, targetPos.y, targetPos.z - 7, targetPos.x + 7, targetPos.y + 3, targetPos.z + 7);
                draw3dBox(buffer, matrices, box, 0xFF55FFFF);
            }
            // 3. Огненный шар (5б)
            else if (mainHand.isOf(Items.FIRE_CHARGE)) {
                HitResult hit = client.crosshairTarget;
                Vec3d targetPos = (hit != null && hit.getType() == HitResult.Type.BLOCK) ? hit.getPos() : client.player.getPos();
                Box box = new Box(targetPos.x - 5, targetPos.y, targetPos.z - 5, targetPos.x + 5, targetPos.y + 3, targetPos.z + 5);
                draw3dBox(buffer, matrices, box, 0xFFFF5555);
            }
            // 4. Трапка (Незеритовый лом) -> 5 блоков вверх, 2б в стороны
            else if (mainHand.isOf(Items.NETHERITE_SCRAP)) {
                Vec3d pos = client.player.getPos();
                Box box = new Box(pos.x - 2, pos.y, pos.z - 2, pos.x + 3, pos.y + 5, pos.z + 3);
                draw3dBox(buffer, matrices, box, 0xFFFFAAAA);
            }
            // 5. Пласт (Сушеная ламинария)
            else if (mainHand.isOf(Items.DRIED_KELP)) {
                HitResult hit = client.crosshairTarget;
                if (hit instanceof BlockHitResult blockHit) {
                    BlockPos bp = blockHit.getBlockPos();
                    Box box = new Box(bp.getX() - 2, bp.getY() - 2, bp.getZ() - 2, bp.getX() + 3, bp.getY() + 3, bp.getZ() + 3);
                    draw3dBox(buffer, matrices, box, 0xFFFFAA55);
                }
            }

            matrices.pop();
        });

        // 2D HUD интерфейс
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            if (itemRadiusEnabled) {
                ItemStack mainHand = client.player.getMainHandStack();
                if (mainHand.isOf(Items.ENDER_EYE) || mainHand.isOf(Items.SUGAR)) {
                    boolean foundPlayer = false;
                    List<AbstractClientPlayerEntity> players = client.world.getPlayers();
                    for (AbstractClientPlayerEntity p : players) {
                        if (p != client.player && !p.isSpectator()) {
                            if (client.player.distanceTo(p) <= 10.0) {
                                foundPlayer = true;
                                break;
                            }
                        }
                    }
                    playerInRange = foundPlayer;
                } else {
                    playerInRange = false;
                }
            }

            if (pendingSwap) {
                if (currentDelayCounter > 0) currentDelayCounter--;
                else {
                    performSmartSwap(client);
                    pendingSwap = false;
                }
            }

            if (guiKey.wasPressed()) client.setScreen(new SoupGuiScreen());

            while (itemSwapEnabled && itemSwapKey.wasPressed()) {
                if (!pendingSwap) {
                    currentDelayCounter = swapDelayConfigTicks;
                    pendingSwap = true;
                }
            }

            while (elytraSwapEnabled && elytraSwapKey.wasPressed()) {
                performElytraSwap(client);
            }
        });
    }

    private void draw3dBox(VertexConsumer buffer, MatrixStack matrices, Box box, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Нижний контур
        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Верхний контур
        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Вертикальные стойки
        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void line(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    private void performSmartSwap(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        int syncId = client.player.playerScreenHandler.syncId;
        ItemStack offhandStack = client.player.getOffHandStack();

        if (swapMode == 0) {
            int totemSlot = findAnyItemSlot(client, Items.TOTEM_OF_UNDYING);
            if (totemSlot != -1) swapSlotWithOffhand(client, syncId, totemSlot);
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
            if (elytraSlot != -1) swapArmorSlot(client, syncId, elytraSlot, 6);
        } else {
            int chestplateSlot = findChestplateSlot(client);
            if (chestplateSlot != -1) swapArmorSlot(client, syncId, chestplateSlot, 6);
        }
    }

    private int findAnyItemSlot(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 0; i < client.player.playerScreenHandler.slots.size(); i++) {
            if (i == 45) continue;
            if (client.player.playerScreenHandler.getSlot(i).getStack().isOf(item)) return i;
        }
        return -1;
    }

    private int findChestplateSlot(MinecraftClient client) {
        for (int i = 0; i < client.player.playerScreenHandler.slots.size(); i++) {
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
            int y = height / 2 - 140;

            context.fill(x, y, x + 260, y + 280, 0xEE1A1A1A);
            context.fill(x, y, x + 260, y + 25, 0xEE2A2A2A);
            context.drawCenteredTextWithShadow(textRenderer, "TankVisuals // SoupVisuals Style", width / 2, y + 8, 0xFF55FF55);

            context.drawTextWithShadow(textRenderer, "[0] Optimization: " + (optimizationEnabled ? "ON" : "OFF"), x + 15, y + 35, optimizationEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[1] Player HUD: " + (hudEnabled ? "ON" : "OFF"), x + 15, y + 60, hudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[2] Target HUD: " + (targetHudEnabled ? "ON" : "OFF"), x + 15, y + 85, targetHudEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[3] 3D World Render: " + (itemRadiusEnabled ? "ON" : "OFF"), x + 15, y + 110, itemRadiusEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[4] Item Swap (G): " + (itemSwapEnabled ? "ON" : "OFF"), x + 15, y + 135, itemSwapEnabled ? 0x55FF55 : 0xFF5555);
            context.drawTextWithShadow(textRenderer, "[5] Elytra Swap (V): " + (elytraSwapEnabled ? "ON" : "OFF"), x + 15, y + 160, elytraSwapEnabled ? 0x55FF55 : 0xFF5555);

            String modeStr = (swapMode == 0) ? "Тотем ⇄ Тотем" : "Сфера ⇄ Тотем";
            context.drawTextWithShadow(textRenderer, "[M] Режим свапа: " + modeStr, x + 15, y + 190, 0xFFFF55);

            double seconds = swapDelayConfigTicks * 0.05;
            context.drawTextWithShadow(textRenderer, "[6] Задержка: " + swapDelayConfigTicks + " тиков (" + seconds + " сек)", x + 15, y + 215, 0x55FFFF);

            context.drawTextWithShadow(textRenderer, "0-5, M (режим), 6 (задержка)", x + 15, y + 250, 0xAAAAAA);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_0) { optimizationEnabled = !optimizationEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_1) { hudEnabled = !hudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_2) { targetHudEnabled = !targetHudEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_3) { itemRadiusEnabled = !itemRadiusEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_4) { itemSwapEnabled = !itemSwapEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_5) { elytraSwapEnabled = !elytraSwapEnabled; return true; }
            if (keyCode == GLFW.GLFW_KEY_M) { swapMode = (swapMode == 0) ? 1 : 0; return true; }
            if (keyCode == GLFW.GLFW_KEY_6) {
                if (swapDelayConfigTicks == 2) swapDelayConfigTicks = 5;
                else if (swapDelayConfigTicks == 5) swapDelayConfigTicks = 10;
                else if (swapDelayConfigTicks == 10) swapDelayConfigTicks = 15;
                else if (swapDelayConfigTicks == 15) swapDelayConfigTicks = 20;
                else swapDelayConfigTicks = 2;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
