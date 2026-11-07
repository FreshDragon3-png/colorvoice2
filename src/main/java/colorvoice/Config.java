package colorvoice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, gespeichert in config/colorvoice.json */
public class Config {
    /** Radius in Bloecken (Kugel um den Spieler). */
    public int radius = 100;
    /** Sprache der Spracherkennung: "de" oder "en". Muss zum Vosk-Modell passen. */
    public String language = "de";
    /** Ordner des Vosk-Modells, relativ zum config-Ordner. */
    public String modelPath = "colorvoice/model";
    /** Wie viele Millisekunden pro Server-Tick fuer das Verwandeln verwendet werden duerfen. */
    public int msBudgetPerTick = 15;
    /** Kisten, Ofen usw. nicht ersetzen (sonst geht der Inhalt verloren). */
    public boolean skipBlockEntities = true;
    /** Wasser/Lava (und wassergefuellte Bloecke) nicht ersetzen. */
    public boolean skipFluids = true;
    /** Pause zwischen zwei Verwandlungen pro Spieler. */
    public int cooldownSeconds = 5;
    /** Testbefehl /colorvoice <farbe>. Auf oeffentlichen Servern besser auf false stellen! */
    public boolean enableCommand = true;

    public Path modelDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(modelPath);
    }

    public static Config load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("colorvoice.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Config c = gson.fromJson(r, Config.class);
                    if (c != null) return c;
                }
            }
            Config c = new Config();
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(c));
            return c;
        } catch (Exception e) {
            ColorVoiceMod.LOGGER.error("Konnte Config nicht laden, nutze Standardwerte", e);
            return new Config();
        }
    }
}
