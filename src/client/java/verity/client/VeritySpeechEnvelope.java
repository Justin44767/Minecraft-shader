package verity.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Mouth motion is sampled from the decoded shipped audio, never a pretend microphone signal. */
final class VeritySpeechEnvelope {
    private static final Map<Integer, float[]> CLIPS = load();
    private static Map<Integer, float[]> load() {
        Map<Integer, float[]> result = new HashMap<>();
        try (var input = VeritySpeechEnvelope.class.getResourceAsStream("/assets/verity/voice_envelopes.json")) {
            if (input == null) return result;
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            json.entrySet().forEach(entry -> result.put(Integer.parseInt(entry.getKey()), new Gson().fromJson(entry.getValue(), float[].class)));
        } catch (IOException | RuntimeException error) { verity.VerityMod.LOGGER.warn("Unable to load speech animation envelopes", error); }
        return result;
    }
    static float amplitude(int clip, float elapsedTicks) {
        float[] values = CLIPS.get(clip);
        int frame = (int)(elapsedTicks * 2.5f);
        return values == null || frame < 0 || frame >= values.length ? 0 : values[frame];
    }
}
