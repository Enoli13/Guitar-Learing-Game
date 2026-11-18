package com.enoli.perde;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Inflater;

/** Küçük zip okuyucu: merkez dizinden okur (veri tanımlayıcılı zip'lerde de çalışır). */
public final class Zip {
    private Zip() {}

    static int u16(byte[] b, int p) { return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8); }
    static int u32(byte[] b, int p) { return u16(b, p) | (u16(b, p + 2) << 16); }

    public static Map<String, byte[]> read(byte[] z) throws Exception {
        int eocd = -1;
        for (int i = z.length - 22; i >= Math.max(0, z.length - 66000); i--)
            if (z[i] == 0x50 && z[i + 1] == 0x4b && z[i + 2] == 5 && z[i + 3] == 6) { eocd = i; break; }
        if (eocd < 0) throw new IllegalArgumentException("Bozuk zip");
        int count = u16(z, eocd + 10), cd = u32(z, eocd + 16);
        Map<String, byte[]> out = new LinkedHashMap<>();
        int p = cd;
        for (int k = 0; k < count; k++) {
            if (u32(z, p) != 0x02014b50) break;
            int method = u16(z, p + 10), csize = u32(z, p + 20), usize = u32(z, p + 24);
            int nlen = u16(z, p + 28), xlen = u16(z, p + 30), clen = u16(z, p + 32), off = u32(z, p + 42);
            String name = new String(z, p + 46, nlen, "UTF-8");
            p += 46 + nlen + xlen + clen;
            if (name.endsWith("/")) continue;
            int data = off + 30 + u16(z, off + 26) + u16(z, off + 28);
            if (usize < 0 || usize > 64 * 1024 * 1024) continue;
            byte[] res;
            if (method == 0) { res = new byte[csize]; System.arraycopy(z, data, res, 0, csize); }
            else if (method == 8) {
                Inflater inf = new Inflater(true);
                inf.setInput(z, data, csize);
                res = new byte[usize];
                int n = 0;
                while (n < usize && !inf.finished()) { int r = inf.inflate(res, n, usize - n); if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break; n += r; }
                inf.end();
            } else continue;
            out.put(name, res);
        }
        return out;
    }
}
