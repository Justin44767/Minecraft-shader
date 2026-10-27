package verity.client;

import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import verity.VerityMod;
import verity.core.Mood;
import verity.net.VerityPayload;

public final class VerityClient implements ClientModInitializer {
    private static final VerityClientSettings SETTINGS = new VerityClientSettings();
    private final LinkedHashSet<UUID> spoken = new LinkedHashSet<>();
    private final VerityVoiceOutput voice = new VerityVoiceOutput();
    private int mood = 70, trust = 50;
    private float displayedMood = 70;
    private boolean received;
    private boolean microphone;
    @Override public void onInitializeClient() {
        SETTINGS.load();
        ModelLayerRegistry.registerModelLayer(VerityModel.LAYER, VerityModel::layer);
        EntityRendererRegistry.register(VerityMod.ENTITY, VerityEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(VerityPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            received = true; mood = Mood.clamp(payload.mood()); trust = Mood.clamp(payload.trust()); microphone = payload.microphone();
            if (!payload.speech().isEmpty() && spoken.add(payload.requestId())) {
                while (spoken.size() > 256) spoken.removeFirst();
                context.client().gui.hud.getChat().addClientSystemMessage(Component.literal("<VERITY> " + payload.speech()));
                if (SETTINGS.voice) voice.speak(payload.speech());
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            received = false; microphone = false; spoken.clear(); displayedMood = 70; voice.clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> voice.destroy());
        KeyMapping settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.verity.settings", 344, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SETTINGS.voice) voice.clear();
            displayedMood += (mood - displayedMood) * 0.25f;
            while (settingsKey.consumeClick()) client.setScreenAndShow(new VeritySettingsScreen(client.gui.screen(), SETTINGS));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof OptionsScreen) Screens.getWidgets(screen).add(Button.builder(Component.literal("VERITY"),
                    b -> client.setScreenAndShow(new VeritySettingsScreen(screen, SETTINGS))).bounds(8, 8, 76, 20).build());
        });
        HudElementRegistry.addLast(VerityMod.id("mood"), (graphics, delta) -> hud(graphics));
    }
    private void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!received || mc.player == null) return;
        // Consent indicator stays visible even if the optional mood bar or vanilla HUD is hidden.
        if (microphone) g.text(mc.font, "VERITY MIC ON · /verity microphone disable", 8, 8, 0xFFFF7777);
        if (!SETTINGS.hud || mc.gui.hud.isHidden()) return;
        int w = 116, h = SETTINGS.horizontal ? 87 : 178;
        float scale = Math.min(SETTINGS.scale, Math.min((g.guiWidth() - 12f) / w, (g.guiHeight() - 12f) / h));
        int x = SETTINGS.left ? 6 : g.guiWidth() - (int)(w * scale) - 6;
        int y = Math.max(6, (g.guiHeight() - (int)(h * scale)) / 2);
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(scale);
        g.fill(0, 0, w, h, ((int)(SETTINGS.opacity * 2.55) << 24) | 0x110D1B);
        g.fill(0, 0, 2, h, 0xFFA778DD);
        g.text(mc.font, "VERITY MOOD", 9, 8, 0xFFE0CEF4);
        int color = 0xFF000000 | Mood.of(mood).color;
        g.text(mc.font, Mood.of(mood).name().replace('_', ' '), 9, 23, color);
        if (SETTINGS.percentage) g.text(mc.font, Math.round(displayedMood) + "%", 9, 36, 0xFFFFFFFF);
        if (SETTINGS.horizontal) {
            g.fill(9, 51, 107, 60, 0xFF2E243B);
            for (int i = 0; i < Math.round(98 * displayedMood / 100); i++)
                g.fill(9 + i, 51, 10 + i, 60, 0xFF000000 | Mood.of(i * 100 / 98).color);
        } else {
            g.fill(15, 53, 35, 151, 0xFF2E243B);
            int fill = Math.round(98 * displayedMood / 100);
            for (int i = 0; i < fill; i++) g.fill(15, 150 - i, 35, 151 - i, 0xFF000000 | Mood.of(i * 100 / 98).color);
            // Small animated face mirrors the same authoritative global mood.
            g.fill(52, 76, 100, 116, 0xFFF3C752);
            boolean blink = (System.nanoTime() / 50_000_000L) % 90 < 3;
            g.fill(60, 85, 67, blink ? 87 : 96, 0xFF151122); g.fill(84, 85, 91, blink ? 87 : 96, 0xFF151122);
            g.fill(68, 104, 84, mood >= 60 ? 108 : 106, 0xFF151122);
        }
        if (SETTINGS.trust) g.text(mc.font, "Trust: " + trust + "%", 9, h - 16, 0xFFCDB8E4);
        g.pose().popMatrix();
    }
}
