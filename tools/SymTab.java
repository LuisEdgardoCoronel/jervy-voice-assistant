import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Extrae la tabla de símbolos (palabras) embebida en un FST de OpenFst: int32 largo + UTF-8 + int64 clave.
 *
 * Uso (herramienta de desarrollo, no del build): java tools/SymTab.java <Gr.fst> <salida.tsv>
 * Ver "Herramientas" en docs/spike-wake-word.md.
 */
public class SymTab {
    public static void main(String[] a) throws Exception {
        byte[] b = Files.readAllBytes(Path.of(a[0]));
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
        TreeMap<Long, String> out = new TreeMap<>();
        for (int i = 0; i + 4 < b.length; i++) {
            int len = bb.getInt(i);
            if (len < 1 || len > 40 || i + 4 + len + 8 > b.length) continue;
            String s;
            try {
                s = dec.decode(ByteBuffer.wrap(b, i + 4, len)).toString();
            } catch (Exception e) {
                continue;
            }
            if (!s.matches("[\\p{Ll}'<>#!_\\[\\]-]+")) continue;
            long key = bb.getLong(i + 4 + len);
            if (key < 0 || key > 2_000_000) continue;
            out.putIfAbsent(key, s);
        }
        StringBuilder sb = new StringBuilder();
        for (var e : out.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        Files.writeString(Path.of(a[1]), sb.toString());
        System.out.println("simbolos=" + out.size() + " keymax=" + out.lastKey());
    }
}
