package colorvoice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple-Voice-Chat-Plugin: nimmt das Mikrofon-Audio jedes Spielers ab, dekodiert es und
 * schickt es an die Offline-Spracherkennung (Vosk). Wird eine Farbe erkannt, startet die Verwandlung.
 */
public class ColorVoicePlugin implements VoicechatPlugin {

    private static final class Session {
        final OpusDecoder decoder;
        Recognizer recognizer; // nur im STT-Thread benutzt

        Session(OpusDecoder decoder) {
            this.decoder = decoder;
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService stt = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "colorvoice-stt");
        t.setDaemon(true);
        return t;
    });

    private Model model;
    private boolean modelTried = false;

    @Override
    public String getPluginId() {
        return "colorvoice";
    }

    @Override
    public void initialize(VoicechatApi api) {
        ColorVoiceMod.LOGGER.info("Color Voice: Simple-Voice-Chat-Plugin registriert.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onDisconnect);
    }

    // ------------------------------------------------------------ Voice-Thread

    private void onMic(MicrophonePacketEvent event) {
        VoicechatConnection con = event.getSenderConnection();
        if (con == null) return;
        Object raw = con.getPlayer().getPlayer();
        if (!(raw instanceof ServerPlayer player)) return;

        byte[] opus = event.getPacket().getOpusEncodedData();
        Session s = sessions.computeIfAbsent(player.getUUID(), id -> new Session(event.getVoicechat().createDecoder()));

        if (opus == null || opus.length == 0) { // leeres Paket = Spieler hat aufgehoert zu sprechen
            s.decoder.resetState();
            stt.execute(() -> finish(player, s));
            return;
        }
        short[] pcm48 = s.decoder.decode(opus);
        stt.execute(() -> feed(player, s, pcm48));
    }

    private void onDisconnect(PlayerDisconnectedEvent event) {
        Session s = sessions.remove(event.getPlayerUuid());
        if (s != null) {
            s.decoder.close();
            stt.execute(() -> {
                if (s.recognizer != null) s.recognizer.close();
            });
        }
    }

    // ------------------------------------------------------------ STT-Thread

    private Model model() {
        if (modelTried) return model;
        modelTried = true;
        try {
            Path dir = ColorVoiceMod.config().modelDir();
            if (!Files.isDirectory(dir)) {
                ColorVoiceMod.LOGGER.error("Kein Vosk-Modell gefunden in: {} - siehe README.", dir);
                return null;
            }
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(dir.toString());
            ColorVoiceMod.LOGGER.info("Vosk-Modell geladen: {}", dir);
        } catch (Throwable t) {
            ColorVoiceMod.LOGGER.error("Vosk-Modell konnte nicht geladen werden", t);
        }
        return model;
    }

    private Recognizer recognizer(Session s) throws Exception {
        if (s.recognizer == null) {
            Model m = model();
            if (m == null) return null;
            s.recognizer = new Recognizer(m, 16000f, VoiceColor.grammar(ColorVoiceMod.config().language));
        }
        return s.recognizer;
    }

    private void feed(ServerPlayer player, Session s, short[] pcm48) {
        try {
            Recognizer rec = recognizer(s);
            if (rec == null) return;
            short[] pcm16 = downsample(pcm48);
            if (rec.acceptWaveForm(pcm16, pcm16.length)) {
                handle(player, rec.getResult());
            }
        } catch (Throwable t) {
            ColorVoiceMod.LOGGER.error("Fehler in der Spracherkennung", t);
        }
    }

    private void finish(ServerPlayer player, Session s) {
        try {
            if (s.recognizer == null) return;
            handle(player, s.recognizer.getFinalResult());
            s.recognizer.reset();
        } catch (Throwable t) {
            ColorVoiceMod.LOGGER.error("Fehler in der Spracherkennung", t);
        }
    }

    /** 48 kHz -> 16 kHz (Mittelwert aus je 3 Samples). */
    private static short[] downsample(short[] in) {
        short[] out = new short[in.length / 3];
        for (int i = 0; i < out.length; i++) {
            int j = i * 3;
            out[i] = (short) ((in[j] + in[j + 1] + in[j + 2]) / 3);
        }
        return out;
    }

    private void handle(ServerPlayer player, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("text")) return;
        String text = obj.get("text").getAsString().trim();
        if (text.isEmpty()) return;
        ColorVoiceMod.LOGGER.debug("Verstanden von {}: {}", player.getName().getString(), text);

        VoiceColor color = VoiceColor.fromText(text);
        if (color == null) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.execute(() -> ColorVoiceMod.trigger(player, color));
    }
}
