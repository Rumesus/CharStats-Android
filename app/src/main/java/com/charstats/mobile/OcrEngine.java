package com.charstats.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.*;

public class OcrEngine {
    private final Context context;

    public OcrEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public void ensureData() throws IOException {
        File base = new File(context.getFilesDir(), "tesseract");
        File tess = new File(base, "tessdata");
        if (!tess.exists() && !tess.mkdirs()) throw new IOException("Не вдалося створити tessdata");
        for (String lang : new String[]{"ukr","rus","eng","osd"}) {
            File out = new File(tess, lang + ".traineddata");
            if (out.exists() && out.length() > 1000) continue;
            try (InputStream in = context.getAssets().open("tessdata/" + lang + ".traineddata");
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        }
    }

    public String recognize(Bitmap source, String languages) throws IOException {
        ensureData();
        String best = "";
        int bestScore = -1;
        for (int angle : new int[]{0,90,270,180}) {
            Bitmap b = rotate(source, angle);
            TessBaseAPI api = new TessBaseAPI();
            try {
                boolean ok = api.init(new File(context.getFilesDir(), "tesseract").getAbsolutePath(), languages);
                if (!ok) continue;
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                api.setImage(b);
                String txt = api.getUTF8Text();
                int score = score(txt);
                if (score > bestScore) {
                    bestScore = score;
                    best = txt == null ? "" : txt;
                }
            } finally {
                api.recycle();
                if (b != source) b.recycle();
            }
        }
        return best;
    }

    private Bitmap rotate(Bitmap src, int angle) {
        if (angle == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(angle);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private int score(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i=0;i<s.length();i++) if (Character.isLetterOrDigit(s.charAt(i))) n++;
        return n;
    }
}
