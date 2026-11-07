package colorvoice;

import com.google.gson.JsonArray;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.function.Supplier;

/** Farben mit Sprachwoertern (de/en) und den Bloecken, in die verwandelt wird. */
public enum VoiceColor {
    RED("Rot", w("rot"), w("red"),
            () -> l(Blocks.RED_WOOL, Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA, Blocks.NETHERRACK,
                    Blocks.RED_NETHER_BRICKS, Blocks.NETHER_WART_BLOCK)),
    BLUE("Blau", w("blau"), w("blue"),
            () -> l(Blocks.BLUE_WOOL, Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA, Blocks.LAPIS_BLOCK,
                    Blocks.BLUE_ICE)),
    LIGHT_BLUE("Hellblau", w("hellblau"), w("light blue"),
            () -> l(Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_TERRACOTTA)),
    GREEN("Gruen", w("grün", "gruen"), w("green"),
            () -> l(Blocks.GREEN_WOOL, Blocks.GREEN_CONCRETE, Blocks.GREEN_TERRACOTTA, Blocks.MOSS_BLOCK,
                    Blocks.EMERALD_BLOCK)),
    LIME("Hellgruen", w("hellgrün", "hellgruen"), w("lime"),
            () -> l(Blocks.LIME_WOOL, Blocks.LIME_CONCRETE, Blocks.LIME_TERRACOTTA)),
    YELLOW("Gelb", w("gelb"), w("yellow"),
            () -> l(Blocks.YELLOW_WOOL, Blocks.YELLOW_CONCRETE, Blocks.YELLOW_TERRACOTTA, Blocks.HAY_BLOCK,
                    Blocks.GOLD_BLOCK)),
    ORANGE("Orange", w("orange"), w("orange"),
            () -> l(Blocks.ORANGE_WOOL, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_TERRACOTTA, Blocks.PUMPKIN)),
    PURPLE("Lila", w("lila", "violett"), w("purple", "violet"),
            () -> l(Blocks.PURPLE_WOOL, Blocks.PURPLE_CONCRETE, Blocks.PURPLE_TERRACOTTA,
                    Blocks.AMETHYST_BLOCK, Blocks.PURPUR_BLOCK)),
    MAGENTA("Magenta", w("magenta"), w("magenta"),
            () -> l(Blocks.MAGENTA_WOOL, Blocks.MAGENTA_CONCRETE, Blocks.MAGENTA_TERRACOTTA)),
    PINK("Pink", w("rosa", "pink"), w("pink"),
            () -> l(Blocks.PINK_WOOL, Blocks.PINK_CONCRETE, Blocks.PINK_TERRACOTTA)),
    CYAN("Tuerkis", w("türkis", "tuerkis", "cyan"), w("cyan", "turquoise"),
            () -> l(Blocks.CYAN_WOOL, Blocks.CYAN_CONCRETE, Blocks.CYAN_TERRACOTTA, Blocks.PRISMARINE,
                    Blocks.WARPED_WART_BLOCK)),
    BLACK("Schwarz", w("schwarz"), w("black"),
            () -> l(Blocks.BLACK_WOOL, Blocks.BLACK_CONCRETE, Blocks.BLACK_TERRACOTTA, Blocks.COAL_BLOCK,
                    Blocks.OBSIDIAN, Blocks.BLACKSTONE)),
    WHITE("Weiss", w("weiß", "weiss"), w("white"),
            () -> l(Blocks.WHITE_WOOL, Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA, Blocks.QUARTZ_BLOCK,
                    Blocks.SNOW_BLOCK)),
    GRAY("Grau", w("grau"), w("gray", "grey"),
            () -> l(Blocks.GRAY_WOOL, Blocks.GRAY_CONCRETE, Blocks.GRAY_TERRACOTTA, Blocks.STONE,
                    Blocks.ANDESITE)),
    BROWN("Braun", w("braun"), w("brown"),
            () -> l(Blocks.BROWN_WOOL, Blocks.BROWN_CONCRETE, Blocks.BROWN_TERRACOTTA, Blocks.SPRUCE_PLANKS,
                    Blocks.DARK_OAK_PLANKS));

    public final String displayName;
    private final String[] de;
    private final String[] en;
    private final Supplier<List<Block>> blocks;

    VoiceColor(String displayName, String[] de, String[] en, Supplier<List<Block>> blocks) {
        this.displayName = displayName;
        this.de = de;
        this.en = en;
        this.blocks = blocks;
    }

    public List<Block> blocks() {
        return blocks.get();
    }

    private static String[] w(String... words) {
        return words;
    }

    private static List<Block> l(Block... b) {
        return List.of(b);
    }

    // ---------------------------------------------------------------- Erkennung

    /** Wort/Phrase -> Farbe, laengste Phrasen zuerst ("light blue" vor "blue"). */
    private static final List<Map.Entry<String, VoiceColor>> LOOKUP = new ArrayList<>();

    static {
        Map<String, VoiceColor> all = new HashMap<>();
        for (VoiceColor c : values()) {
            for (String s : c.de) all.put(s, c);
            for (String s : c.en) all.put(s, c);
        }
        LOOKUP.addAll(all.entrySet());
        LOOKUP.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
    }

    /** Sucht eine Farbe im erkannten Text. Gibt null zurueck, wenn keine gefunden wurde. */
    public static VoiceColor fromText(String text) {
        if (text == null) return null;
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L} ]", " ").trim() + " ";
        for (Map.Entry<String, VoiceColor> e : LOOKUP) {
            if (t.contains(" " + e.getKey() + " ")) return e.getValue();
        }
        return null;
    }

    /** JSON-Grammatik fuer Vosk: erlaubt nur Farbwoerter (macht die Erkennung viel genauer). */
    public static String grammar(String language) {
        boolean german = !"en".equalsIgnoreCase(language);
        JsonArray arr = new JsonArray();
        for (VoiceColor c : values()) {
            for (String s : german ? c.de : c.en) arr.add(s);
        }
        arr.add("[unk]");
        return arr.toString();
    }
}
