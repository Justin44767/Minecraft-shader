package verity.voice;

import com.google.gson.JsonParser;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.vosk.Model;
import org.vosk.Recognizer;
import verity.VerityMod;
import verity.server.VerityServerRuntime.MicrophonePermit;

/** Optional official SVC plugin. Captures only explicitly opted-in speakers; never stores raw audio. */
public final class VerityVoiceChatBridge implements VoicechatPlugin {
    private record Speaker(UUID player, MicrophonePermit session) { }
    private static final class Input implements AutoCloseable {
        final OpusDecoder decoder;
        final Recognizer recognizer;
        int samples;
        Input(VoicechatServerApi api, Model model) throws IOException {
            decoder = api.createDecoder();
            try { recognizer = new Recognizer(model, 48000); }
            catch (IOException | RuntimeException e) { decoder.close(); throw e; }
        }
        @Override public void close() { try { recognizer.close(); } finally { decoder.close(); } }
    }
    private final Map<Speaker, Input> inputs = new HashMap<>();
    // Packet work is serialized to keep Opus and Vosk state ordered and out of the Minecraft tick.
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), Thread.ofPlatform().daemon(true).name("verity-stt").factory(), new ThreadPoolExecutor.AbortPolicy());
    private Model model;
    private boolean modelAttempted;
    private volatile boolean running;
    @Override public String getPluginId() { return "verity"; }
    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> running = true);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            running = false;
            worker.getQueue().clear();
            try { worker.execute(this::closeInputs); } catch (RejectedExecutionException ignored) { }
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            try { worker.execute(() -> {
                for (var it = inputs.entrySet().iterator(); it.hasNext();) {
                    var entry = it.next();
                    if (entry.getKey().player().equals(event.getPlayerUuid())) { entry.getValue().close(); it.remove(); }
                }
            }); } catch (RejectedExecutionException ignored) { }
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::microphone);
    }
    private void microphone(MicrophonePacketEvent event) {
        if (!running || event.getSenderConnection() == null) return;
        Object rawPlayer = event.getSenderConnection().getPlayer().getPlayer();
        if (!(rawPlayer instanceof ServerPlayer player)) return;
        var runtime = VerityMod.runtime(player.level().getServer());
        if (runtime == null) return;
        var session = runtime.microphoneSession(player.getUUID());
        if (session == null) return; // Check consent before copying or decoding microphone data.
        byte[] packet = event.getPacket().getOpusEncodedData();
        if (packet.length > 4096) return;
        byte[] encoded = packet.clone();
        Speaker speaker = new Speaker(player.getUUID(), session);
        try {
            worker.execute(() -> {
                if (!running || !session.equals(runtime.microphoneSession(speaker.player()))) { closeSpeaker(speaker); return; }
                try {
                    if (!loadModel()) { runtime.disableMicrophone(speaker.player()); return; }
                    // A replaced/disabled session releases its native recognizer before another is opened.
                    for (var it = inputs.entrySet().iterator(); it.hasNext();) {
                        var entry = it.next();
                        if (!entry.getKey().session().equals(runtime.microphoneSession(entry.getKey().player()))) {
                            entry.getValue().close(); it.remove();
                        }
                    }
                    Input input = inputs.get(speaker);
                    if (input == null) {
                        if (inputs.size() >= 8) { runtime.disableMicrophone(speaker.player()); return; }
                        input = new Input(event.getVoicechat(), model); inputs.put(speaker, input);
                    }
                    if (encoded.length == 0) {
                        transcript(input.recognizer.getFinalResult(), runtime, session);
                        input.recognizer.reset(); input.decoder.resetState(); input.samples = 0;
                    } else {
                        short[] pcm = input.decoder.decode(encoded);
                        input.samples += pcm.length;
                        if (input.recognizer.acceptWaveForm(pcm, pcm.length)) {
                            transcript(input.recognizer.getResult(), runtime, session); input.samples = 0;
                        }
                        if (input.samples > 48000 * 15) {
                            transcript(input.recognizer.getFinalResult(), runtime, session);
                            input.recognizer.reset(); input.samples = 0;
                        }
                    }
                } catch (IOException | RuntimeException | LinkageError e) {
                    closeSpeaker(speaker);
                    VerityMod.LOGGER.warn("VERITY offline speech failed; text chat remains available", e);
                    runtime.disableMicrophone(speaker.player());
                }
            });
        } catch (RejectedExecutionException full) {
            // Never block the UDP or Minecraft thread on an overloaded recognizer.
            runtime.disableMicrophone(speaker.player());
        }
    }
    private void transcript(String json, verity.server.VerityServerRuntime runtime, MicrophonePermit session) {
        String text = JsonParser.parseString(json).getAsJsonObject().get("text").getAsString();
        if (!text.isBlank() && text.length() <= 512) runtime.voiceTranscript(session, text);
    }
    private boolean loadModel() throws IOException {
        if (modelAttempted) return model != null;
        modelAttempted = true;
        Path path = modelPath();
        if (!Files.isDirectory(path.resolve("am")) || !Files.isDirectory(path.resolve("conf"))) {
            VerityMod.LOGGER.warn("VERITY Vosk model missing at {}. Microphone remains unavailable; see README.", path);
            return false;
        }
        model = new Model(path.toString());
        return true;
    }
    public static Path modelPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("verity/models/current");
    }
    private void closeSpeaker(Speaker speaker) {
        Input input = inputs.remove(speaker); if (input != null) input.close();
    }
    private void closeInputs() {
        inputs.values().forEach(Input::close); inputs.clear();
        if (model != null) { model.close(); model = null; }
        modelAttempted = false;
    }
}
