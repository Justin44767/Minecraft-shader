package verity.client;

import com.mojang.text2speech.Narrator;
import verity.VerityMod;

/** Separate OS narrator instance, using Minecraft's supported library. No cloud, recordings, or borrowed voice. */
final class VerityVoiceOutput {
    private Narrator narrator;
    private boolean attempted;
    void speak(String text) {
        if (!attempted) {
            attempted = true;
            try { narrator = Narrator.getNarrator(); }
            catch (RuntimeException | LinkageError error) { VerityMod.LOGGER.warn("VERITY OS speech unavailable; subtitles remain available", error); }
        }
        if (narrator != null && narrator.active()) narrator.say(text, false, 0.8f);
    }
    void clear() { if (narrator != null) narrator.clear(); }
    void destroy() { if (narrator != null) narrator.destroy(); }
}
