package verity.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VeritySettingsScreen extends Screen {
    private final Screen parent;
    private final VerityClientSettings settings;
    public VeritySettingsScreen(Screen parent, VerityClientSettings settings) { super(Component.literal("VERITY · SETTINGS")); this.parent = parent; this.settings = settings; }
    private void button(String label, int row, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> { action.run(); rebuildWidgets(); })
                .bounds(width / 2 - 110, 40 + row * 22, 220, 20).build());
    }
    @Override protected void init() {
        button("Mood HUD: " + (settings.hud ? "ON" : "OFF"), 0, () -> settings.hud = !settings.hud);
        button("Position: " + (settings.left ? "LEFT" : "RIGHT"), 1, () -> settings.left = !settings.left);
        button("Layout: " + (settings.horizontal ? "HORIZONTAL" : "VERTICAL"), 2, () -> settings.horizontal = !settings.horizontal);
        button("Scale: " + settings.scale + "×", 3, () -> settings.scale = settings.scale == 1 ? 2 : 1);
        button("Opacity: " + settings.opacity + "%", 4, () -> settings.opacity = settings.opacity == 100 ? 25 : Math.min(100, settings.opacity + 15));
        button("OS narrator output: " + (settings.voice ? "ON" : "OFF"), 5, () -> settings.voice = !settings.voice);
        button("Percentage: " + settings.percentage + " · Trust: " + settings.trust, 6, () -> {
            if (settings.percentage && settings.trust) settings.trust = false;
            else if (settings.percentage) settings.percentage = false;
            else { settings.percentage = true; settings.trust = true; }
        });
        button("Save and close", 7, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xF00B0812); g.fill(0, 0, width, 3, 0xFF9A63DA);
        g.text(font, title, width / 2 - 70, 20, 0xFFCCB5EC);
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, "Installed OS voice · non-spatial", 10, height - 26, 0xFFA99DB6);
        g.text(font, "Mic: /verity microphone enable (SVC + model)", 10, height - 14, 0xFFA99DB6);
    }
    @Override public void onClose() { settings.save(); minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
