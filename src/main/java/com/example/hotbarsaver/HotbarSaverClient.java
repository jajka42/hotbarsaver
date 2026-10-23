package com.example.hotbarsaver;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HotbarSaverClient implements ClientModInitializer {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("hotbarsaver.txt");

    private boolean saveWasDown = false;
    private boolean loadWasDown = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            return;
        }
        long window = client.getWindow().getHandle();

        boolean saveDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean loadDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_P);

        if (saveDown && !saveWasDown) {
            saveHotbar(client.player);
        }
        if (loadDown && !loadWasDown) {
            loadHotbar(client);
        }
        saveWasDown = saveDown;
        loadWasDown = loadDown;
    }

    private void saveHotbar(ClientPlayerEntity player) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Item item = player.getInventory().getStack(i).getItem();
            ids.add(Registries.ITEM.getId(item).toString());
        }
        try {
            Files.write(FILE, ids);
            player.sendMessage(Text.literal("§aZapisano układ hotbara."), false);
        } catch (IOException e) {
            player.sendMessage(Text.literal("§cNie udało się zapisać układu."), false);
        }
    }

    private void loadHotbar(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!Files.exists(FILE)) {
            player.sendMessage(Text.literal("§cNie masz zapisanego układu (Prawy Alt)."), false);
            return;
        }

        List<String> ids;
        try {
            ids = Files.readAllLines(FILE);
        } catch (IOException e) {
            player.sendMessage(Text.literal("§cNie udało się wczytać układu."), false);
            return;
        }
        if (ids.size() < 9) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        List<Item> wanted = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Identifier id = Identifier.tryParse(ids.get(i).trim());
            wanted.add(id == null ? Items.AIR : Registries.ITEM.get(id));
        }

        int moved = 0;
        for (int i = 0; i < 9; i++) {
            Item item = wanted.get(i);
            if (item == Items.AIR || inv.getStack(i).isOf(item)) {
                continue;
            }
            int source = findSource(inv, item, wanted);
            if (source == -1) {
                continue;
            }
            int slotId = source < 9 ? 36 + source : source;
            client.interactionManager.clickSlot(
                    player.playerScreenHandler.syncId, slotId, i,
                    SlotActionType.SWAP, player);
            moved++;
        }
        player.sendMessage(Text.literal("§aWczytano układ hotbara (przeniesiono: " + moved + ")."), false);
    }

    private int findSource(PlayerInventory inv, Item item, List<Item> wanted) {
        for (int s = 9; s < 36; s++) {
            if (inv.getStack(s).isOf(item)) {
                return s;
            }
        }
        for (int s = 0; s < 9; s++) {
            if (inv.getStack(s).isOf(item) && wanted.get(s) != item) {
                return s;
            }
        }
        return -1;
    }
}
