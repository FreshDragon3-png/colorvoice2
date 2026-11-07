package colorvoice;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Verwandelt alle Bloecke in einer Kugel um den Spieler. Laeuft ueber mehrere Ticks verteilt
 * (Zeitbudget pro Tick), damit der Server nicht einfriert. Nur geladene Chunks werden bearbeitet.
 */
public class ConversionJob {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos center;
    private final long radiusSq;
    private final List<BlockState> palette = new ArrayList<>();
    private final boolean skipBlockEntities;
    private final boolean skipFluids;
    private final int minY;
    private final int maxY;
    private final List<int[]> chunks = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private int chunkIdx = 0;
    private int lx = 0;
    private int lz = 0;
    private int y;
    private boolean chunkStarted = false;
    private LevelChunk chunk;
    private long changed = 0;

    public ConversionJob(ServerPlayer player, ServerLevel level, VoiceColor color, Config cfg) {
        this.player = player;
        this.level = level;
        this.center = player.blockPosition();
        int r = Math.max(1, cfg.radius);
        this.radiusSq = (long) r * r;
        this.skipBlockEntities = cfg.skipBlockEntities;
        this.skipFluids = cfg.skipFluids;
        for (Block b : color.blocks()) palette.add(b.defaultBlockState());

        this.minY = Math.max(level.getMinY(), center.getY() - r);
        this.maxY = Math.min(level.getMaxY(), center.getY() + r);
        this.y = minY;

        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        int minCx = (center.getX() - r) >> 4;
        int maxCx = (center.getX() + r) >> 4;
        int minCz = (center.getZ() - r) >> 4;
        int maxCz = (center.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        // Naechste Chunks zuerst, dann sieht man die Verwandlung von innen nach aussen.
        chunks.sort((a, b) -> Long.compare(dist(a, ccx, ccz), dist(b, ccx, ccz)));
    }

    private static long dist(int[] c, int ccx, int ccz) {
        long dx = c[0] - ccx;
        long dz = c[1] - ccz;
        return dx * dx + dz * dz;
    }

    /** Arbeitet bis zur Deadline. Gibt true zurueck, wenn der Job fertig ist. */
    public boolean run(long deadlineNanos) {
        int counter = 0;
        while (chunkIdx < chunks.size()) {
            int[] c = chunks.get(chunkIdx);
            if (!chunkStarted) {
                chunk = level.getChunkSource().getChunkNow(c[0], c[1]);
                if (chunk == null) { // Chunk nicht geladen -> ueberspringen
                    chunkIdx++;
                    continue;
                }
                chunkStarted = true;
                lx = 0;
                lz = 0;
                y = minY;
            }
            while (lx < 16) {
                while (lz < 16) {
                    int wx = (c[0] << 4) + lx;
                    int wz = (c[1] << 4) + lz;
                    long dx = wx - center.getX();
                    long dz = wz - center.getZ();
                    long dh = dx * dx + dz * dz;
                    if (dh <= radiusSq) {
                        while (y <= maxY) {
                            long dy = y - center.getY();
                            if (dh + dy * dy <= radiusSq) {
                                convert(wx, y, wz);
                            }
                            y++;
                            if ((++counter & 255) == 0 && System.nanoTime() >= deadlineNanos) {
                                return false;
                            }
                        }
                    }
                    y = minY;
                    lz++;
                }
                lz = 0;
                lx++;
            }
            chunkStarted = false;
            chunkIdx++;
        }
        player.sendSystemMessage(Component.literal("Fertig: " + changed + " Blöcke verwandelt."));
        return true;
    }

    private void convert(int wx, int wy, int wz) {
        pos.set(wx, wy, wz);
        BlockState old = chunk.getBlockState(pos);
        if (old.isAir()) return;
        if (skipFluids && !old.getFluidState().isEmpty()) return;
        if (skipBlockEntities && old.hasBlockEntity()) return;
        BlockState neu = palette.get(ThreadLocalRandom.current().nextInt(palette.size()));
        if (level.setBlock(pos, neu, FLAGS)) changed++;
    }
}
