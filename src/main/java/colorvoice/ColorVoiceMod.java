package colorvoice;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ColorVoiceMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("colorvoice");

    private static Config config;
    /** Nur vom Server-Thread benutzt. */
    private static final Deque<ConversionJob> JOBS = new ArrayDeque<>();
    private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();

    public static synchronized Config config() {
        if (config == null) config = Config.load();
        return config;
    }

    @Override
    public void onInitialize() {
        Config cfg = config();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ConversionJob job = JOBS.peek();
            if (job == null) return;
            long deadline = System.nanoTime() + cfg.msBudgetPerTick * 1_000_000L;
            if (job.run(deadline)) JOBS.poll();
        });

        if (cfg.enableCommand) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    dispatcher.register(Commands.literal("colorvoice")
                            .then(Commands.argument("farbe", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                                        VoiceColor c = VoiceColor.fromText(StringArgumentType.getString(ctx, "farbe"));
                                        if (c == null) {
                                            ctx.getSource().sendFailure(Component.literal("Unbekannte Farbe."));
                                            return 0;
                                        }
                                        trigger(p, c);
                                        return 1;
                                    }))));
        }
        LOGGER.info("Color Voice geladen (Radius {}, Sprache {}).", cfg.radius, cfg.language);
    }

    /** Muss auf dem Server-Thread aufgerufen werden. */
    public static void trigger(ServerPlayer player, VoiceColor color) {
        Config cfg = config();
        long now = System.currentTimeMillis();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < cfg.cooldownSeconds * 1000L) return;
        LAST_USE.put(player.getUUID(), now);

        JOBS.add(new ConversionJob(player, (ServerLevel) player.level(), color, cfg));
        player.sendSystemMessage(Component.literal(
                "Farbe erkannt: " + color.displayName + " – verwandle Blöcke im Umkreis von " + cfg.radius + "."));
    }
}
