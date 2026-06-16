package com.lowdragmc.kilagraphdemo.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a work package into network-sized byte chunks and reassembles them, so transfers can exceed
 * the single-packet caps (C2S ~32 KiB, S2C ~1 MiB, and the ~2 MiB wire frame) — important once works
 * embed models / textures. The package is gzip-compressed NBT; each chunk is sent as its own packet and
 * collected by a {@link Accumulator} keyed by transfer.
 */
public final class Chunks {

    /** Per-chunk payload size. Kept under the C2S 32 KiB cap (with packet overhead headroom). */
    public static final int CHUNK_SIZE = 24 * 1024;
    /** Safety cap on a transfer's chunk count (~48 MiB) to bound memory against malformed/abusive headers. */
    public static final int MAX_CHUNKS = 2048;

    private Chunks() {
    }

    public static byte[] toBytes(CompoundTag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static CompoundTag toTag(byte[] data) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }

    public static List<byte[]> split(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        for (int off = 0; off < data.length; off += CHUNK_SIZE) {
            int len = Math.min(CHUNK_SIZE, data.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(data, off, chunk, 0, len);
            out.add(chunk);
        }
        if (out.isEmpty()) out.add(new byte[0]); // always at least one chunk
        return out;
    }

    /** Collects the chunks of one transfer (idempotent on duplicates), tracking progress. */
    public static final class Accumulator {
        private final byte[][] chunks;
        private int received;

        public Accumulator(int totalChunks) {
            this.chunks = new byte[Math.max(0, totalChunks)][];
        }

        /** Stores a chunk; returns true once all chunks have arrived. */
        public boolean put(int index, byte[] data) {
            if (index < 0 || index >= chunks.length || chunks[index] != null) return isComplete();
            chunks[index] = data;
            received++;
            return isComplete();
        }

        public boolean isComplete() {
            return chunks.length > 0 && received == chunks.length;
        }

        public int total() {
            return chunks.length;
        }

        public int received() {
            return received;
        }

        public float progress() {
            return chunks.length == 0 ? 1f : (float) received / chunks.length;
        }

        @Nullable
        public byte[] assemble() {
            if (!isComplete()) return null;
            int size = 0;
            for (byte[] c : chunks) size += c.length;
            byte[] out = new byte[size];
            int off = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, off, c.length);
                off += c.length;
            }
            return out;
        }
    }
}
