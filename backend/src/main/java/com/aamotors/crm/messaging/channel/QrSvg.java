package com.aamotors.crm.messaging.channel;

/**
 * Minimal QR code (model 2, byte mode, error correction L, versions 1–10) rendered
 * as inline SVG, so onboarding links can be shown without an external library or
 * a call to a third-party QR service.
 */
public final class QrSvg {

    private QrSvg() {}

    public static String render(String text) {
        boolean[][] m = QrEncoder.encode(text);
        int n = m.length;
        int scale = 4, quiet = 4;
        int size = (n + 2 * quiet) * scale;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(size).append(' ').append(size)
          .append("\" width=\"").append(size).append("\" height=\"").append(size).append("\" shape-rendering=\"crispEdges\">")
          .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"");
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (m[y][x]) {
                    sb.append('M').append((x + quiet) * scale).append(' ').append((y + quiet) * scale)
                      .append('h').append(scale).append('v').append(scale).append('h').append(-scale).append('z');
                }
            }
        }
        sb.append("\"/></svg>");
        return sb.toString();
    }

    /** Compact QR encoder (byte mode, ECL L). Based on the public-domain algorithm description in ISO/IEC 18004. */
    static final class QrEncoder {
        private static final int[] EC_CODEWORDS_L = {0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
        private static final int[] BLOCKS_L = {0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2};
        private static final int[][] ALIGN = {{}, {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50}};

        static boolean[][] encode(String text) {
            byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int version = 1;
            while (version <= 10 && dataCapacity(version) < data.length) version++;
            if (version > 10) throw new IllegalArgumentException("Text too long for QR v1-10");
            int size = version * 4 + 17;
            int totalCodewords = totalCodewords(version);
            int ecTotal = EC_CODEWORDS_L[version] * BLOCKS_L[version];
            int dataCodewords = totalCodewords - ecTotal;

            BitBuffer bb = new BitBuffer();
            bb.append(0b0100, 4);
            bb.append(data.length, version < 10 ? 8 : 16);
            for (byte b : data) bb.append(b & 0xFF, 8);
            int terminator = Math.min(4, dataCodewords * 8 - bb.length);
            bb.append(0, terminator);
            while (bb.length % 8 != 0) bb.append(0, 1);
            for (int pad = 0xEC; bb.length < dataCodewords * 8; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
            byte[] dataBytes = bb.toBytes();

            // split into blocks, compute EC
            int blocks = BLOCKS_L[version];
            int shortLen = dataCodewords / blocks;
            int longBlocks = dataCodewords % blocks;
            byte[][] dataBlocks = new byte[blocks][];
            byte[][] ecBlocks = new byte[blocks][];
            int off = 0;
            for (int b = 0; b < blocks; b++) {
                int len = shortLen + (b >= blocks - longBlocks ? 1 : 0);
                dataBlocks[b] = java.util.Arrays.copyOfRange(dataBytes, off, off + len);
                ecBlocks[b] = ReedSolomon.compute(dataBlocks[b], EC_CODEWORDS_L[version]);
                off += len;
            }
            byte[] all = new byte[totalCodewords];
            int k = 0;
            for (int i = 0; i < shortLen + 1; i++)
                for (int b = 0; b < blocks; b++) if (i < dataBlocks[b].length) all[k++] = dataBlocks[b][i];
            for (int i = 0; i < EC_CODEWORDS_L[version]; i++)
                for (int b = 0; b < blocks; b++) all[k++] = ecBlocks[b][i];

            boolean[][] modules = new boolean[size][size];
            boolean[][] reserved = new boolean[size][size];
            drawFunctionPatterns(modules, reserved, version, size);
            placeData(modules, reserved, all, size);
            int mask = chooseMask(modules, reserved, size, version);
            applyMask(modules, reserved, size, mask);
            drawFormat(modules, size, mask);
            return modules;
        }

        private static int dataCapacity(int v) { return totalCodewords(v) - EC_CODEWORDS_L[v] * BLOCKS_L[v] - 2 - (v >= 10 ? 1 : 0); }

        private static int totalCodewords(int v) {
            int size = v * 4 + 17;
            int result = size * size - 3 * 64 - 2 * (size - 16) - 31;
            if (v >= 2) { int a = ALIGN[v].length; result -= (a * a - 3) * 25; result -= (a - 2) * 10; }
            if (v >= 7) result -= 36;
            return result / 8;
        }

        private static void drawFunctionPatterns(boolean[][] m, boolean[][] r, int v, int n) {
            for (int i = 0; i < n; i++) { set(m, r, 6, i, i % 2 == 0); set(m, r, i, 6, i % 2 == 0); }
            finder(m, r, 3, 3); finder(m, r, n - 4, 3); finder(m, r, 3, n - 4);
            int[] a = ALIGN[v];
            for (int i = 0; i < a.length; i++) for (int j = 0; j < a.length; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == a.length - 1) || (i == a.length - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
                    set(m, r, a[i] + dx, a[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
            for (int i = 0; i < 8; i++) { reserve(r, 8, i, n); reserve(r, i, 8, n); reserve(r, n - 1 - i, 8, n); reserve(r, 8, n - 1 - i, n); }
            reserve(r, 8, 8, n);
            set(m, r, 8, n - 8, true);
            if (v >= 7) {
                int bits = v << 12; int rem = v;
                for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
                bits |= rem;
                for (int i = 0; i < 18; i++) {
                    boolean bit = ((bits >>> i) & 1) != 0;
                    int aa = n - 11 + i % 3, b = i / 3;
                    set(m, r, aa, b, bit); set(m, r, b, aa, bit);
                }
            }
        }

        private static void finder(boolean[][] m, boolean[][] r, int cx, int cy) {
            for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= m.length || y >= m.length) continue;
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                set(m, r, x, y, d != 2 && d != 4);
            }
        }

        private static void placeData(boolean[][] m, boolean[][] r, byte[] data, int n) {
            int i = 0;
            for (int right = n - 1; right >= 1; right -= 2) {
                if (right == 6) right = 5;
                for (int vert = 0; vert < n; vert++) {
                    for (int j = 0; j < 2; j++) {
                        int x = right - j;
                        boolean upward = ((right + 1) & 2) == 0;
                        int y = upward ? n - 1 - vert : vert;
                        if (!r[y][x] && i < data.length * 8) {
                            m[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                            i++;
                        }
                    }
                }
            }
        }

        private static boolean maskBit(int mask, int x, int y) {
            return switch (mask) {
                case 0 -> (x + y) % 2 == 0;
                case 1 -> y % 2 == 0;
                case 2 -> x % 3 == 0;
                case 3 -> (x + y) % 3 == 0;
                case 4 -> (x / 3 + y / 2) % 2 == 0;
                case 5 -> x * y % 2 + x * y % 3 == 0;
                case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
            };
        }

        private static void applyMask(boolean[][] m, boolean[][] r, int n, int mask) {
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (!r[y][x] && maskBit(mask, x, y)) m[y][x] = !m[y][x];
        }

        private static int chooseMask(boolean[][] m, boolean[][] r, int n, int v) {
            int best = 0; long bestScore = Long.MAX_VALUE;
            for (int mask = 0; mask < 8; mask++) {
                applyMask(m, r, n, mask);
                drawFormat(m, n, mask);
                long score = penalty(m, n);
                applyMask(m, r, n, mask);
                if (score < bestScore) { bestScore = score; best = mask; }
            }
            return best;
        }

        private static long penalty(boolean[][] m, int n) {
            long p = 0;
            for (int y = 0; y < n; y++) { int run = 1; for (int x = 1; x < n; x++) { if (m[y][x] == m[y][x - 1]) { run++; if (run == 5) p += 3; else if (run > 5) p++; } else run = 1; } }
            for (int x = 0; x < n; x++) { int run = 1; for (int y = 1; y < n; y++) { if (m[y][x] == m[y - 1][x]) { run++; if (run == 5) p += 3; else if (run > 5) p++; } else run = 1; } }
            for (int y = 0; y < n - 1; y++) for (int x = 0; x < n - 1; x++) if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) p += 3;
            long dark = 0; for (boolean[] row : m) for (boolean b : row) if (b) dark++;
            long total = (long) n * n;
            p += Math.abs(dark * 20 - total * 10) / total * 10;
            return p;
        }

        private static void drawFormat(boolean[][] m, int n, int mask) {
            int data = (1 << 3) | mask; // ECL L = 01
            int rem = data;
            for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
            int bits = ((data << 10) | rem) ^ 0x5412;
            for (int i = 0; i <= 5; i++) m[i][8] = bit(bits, i);
            m[7][8] = bit(bits, 6); m[8][8] = bit(bits, 7); m[8][7] = bit(bits, 8);
            for (int i = 9; i < 15; i++) m[8][14 - i] = bit(bits, i);
            for (int i = 0; i < 8; i++) m[8][n - 1 - i] = bit(bits, i);
            for (int i = 8; i < 15; i++) m[n - 15 + i][8] = bit(bits, i);
            m[n - 8][8] = true;
        }

        private static boolean bit(int v, int i) { return ((v >>> i) & 1) != 0; }
        private static void set(boolean[][] m, boolean[][] r, int x, int y, boolean v) { if (x >= 0 && y >= 0 && x < m.length && y < m.length) { m[y][x] = v; r[y][x] = true; } }
        private static void reserve(boolean[][] r, int x, int y, int n) { if (x >= 0 && y >= 0 && x < n && y < n) r[y][x] = true; }

        static final class BitBuffer {
            private final java.util.BitSet bits = new java.util.BitSet();
            int length;
            void append(int val, int len) { for (int i = len - 1; i >= 0; i--) bits.set(length++, ((val >>> i) & 1) != 0); }
            byte[] toBytes() { byte[] out = new byte[(length + 7) / 8]; for (int i = 0; i < length; i++) if (bits.get(i)) out[i / 8] |= (byte) (0x80 >>> (i % 8)); return out; }
        }

        static final class ReedSolomon {
            static byte[] compute(byte[] data, int ecLen) {
                byte[] gen = new byte[ecLen];
                gen[ecLen - 1] = 1;
                int root = 1;
                for (int i = 0; i < ecLen; i++) {
                    for (int j = 0; j < ecLen; j++) {
                        gen[j] = (byte) mul(gen[j] & 0xFF, root);
                        if (j + 1 < ecLen) gen[j] ^= gen[j + 1];
                    }
                    root = mul(root, 2);
                }
                byte[] res = new byte[ecLen];
                for (byte b : data) {
                    int factor = (b ^ res[0]) & 0xFF;
                    System.arraycopy(res, 1, res, 0, ecLen - 1);
                    res[ecLen - 1] = 0;
                    for (int j = 0; j < ecLen; j++) res[j] ^= (byte) mul(gen[j] & 0xFF, factor);
                }
                return res;
            }
            private static int mul(int x, int y) {
                int z = 0;
                for (int i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((y >>> i) & 1) * x; }
                return z & 0xFF;
            }
        }
    }
}
