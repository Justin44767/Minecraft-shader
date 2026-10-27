package verity.voice;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import verity.VerityMod;

/** Original prerecorded clips: public positional audio, available without OS TTS or SVC. */
public final class VerityVoiceClips {
    public record Clip(int id, String name, String text, int ticks) { }
    private static final List<Clip> CLIPS = load();
    private static final Map<Integer, SoundEvent> SOUNDS = new HashMap<>();
    private static List<Clip> load() {
        try (var input = VerityVoiceClips.class.getResourceAsStream("/assets/verity/voice_clips.json")) {
            if (input == null) throw new IOException("Missing VERITY speech assets");
            var clips = List.of(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Clip[].class));
            if (clips.size() != 7 || clips.stream().anyMatch(c -> c.ticks() < 1 || c.ticks() > 1200)) throw new IOException("Invalid speech manifest");
            return clips;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static void register() {
        for (var clip : CLIPS) SOUNDS.put(clip.id(), Registry.register(BuiltInRegistries.SOUND_EVENT,
                VerityMod.id("voice." + clip.name()), SoundEvent.createFixedRangeEvent(VerityMod.id("voice." + clip.name()), 48)));
    }
    public static Clip clip(int id) { return CLIPS.stream().filter(c -> c.id() == id).findFirst().orElseThrow(); }
    public static SoundEvent sound(int id) { return SOUNDS.get(id); }
}
