package com.charstats.mobile;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

public class FileProcessor {
    private final Context context;
    private final OcrEngine ocr;

    public FileProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.ocr = new OcrEngine(context);
        PDFBoxResourceLoader.init(context);
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl");
    }

    public String read(Uri uri, String name, String langs, boolean ocrAllPdf) throws Exception {
        String ext = extension(name);
        switch (ext) {
            case "txt": return readTxt(uri);
            case "doc": return readDoc(uri);      // автоматична конвертація DOC -> текст
            case "docx": return readDocx(uri);
            case "xls":
            case "xlsx": return readExcel(uri);
            case "pdf": return readPdf(uri, langs, ocrAllPdf);
            case "jpg": case "jpeg": case "png": case "bmp":
            case "webp": case "tif": case "tiff": return readImage(uri, langs);
            default: throw new IllegalArgumentException("Формат не підтримується: " + ext);
        }
    }

    private String readTxt(Uri uri) throws IOException {
        byte[] data = bytes(uri);
        String s = new String(data, StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') >= 0) {
            try { s = new String(data, Charset.forName("windows-1251")); } catch(Exception ignored){}
        }
        return s;
    }

    private String readDoc(Uri uri) throws Exception {
        try (InputStream in = open(uri); HWPFDocument doc = new HWPFDocument(in);
             WordExtractor ex = new WordExtractor(doc)) {
            return ex.getText();
        }
    }

    private String readDocx(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = open(uri); XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            for (XWPFTable t : doc.getTables())
                for (XWPFTableRow r : t.getRows())
                    for (XWPFTableCell c : r.getTableCells())
                        sb.append(c.getText()).append('\t');
        }
        return sb.toString();
    }

    private String readExcel(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        DataFormatter fmt = new DataFormatter();
        try (InputStream in = open(uri); Workbook wb = WorkbookFactory.create(in)) {
            for (Sheet sh : wb) {
                for (Row row : sh) {
                    for (Cell cell : row) sb.append(fmt.formatCellValue(cell)).append('\t');
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String readPdf(Uri uri, String langs, boolean ocrAll) throws Exception {
        byte[] data = bytes(uri);
        try (PDDocument doc = PDDocument.load(data)) {
            String text = new PDFTextStripper().getText(doc);
            boolean sparse = countLetters(text) < Math.max(25, doc.getNumberOfPages() * 8);
            if (!ocrAll && !sparse) return text;
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<doc.getNumberOfPages();i++) {
                Bitmap b = renderer.renderImageWithDPI(i, 220);
                try { sb.append(ocr.recognize(b, langs)).append('\n'); }
                finally { b.recycle(); }
            }
            return sb.toString();
        }
    }

    private String readImage(Uri uri, String langs) throws Exception {
        Bitmap b;
        try (InputStream in = open(uri)) { b = BitmapFactory.decodeStream(in); }
        if (b == null) return "";
        try {
            Bitmap oriented = orient(uri, b);
            try { return ocr.recognize(oriented, langs); }
            finally { if (oriented != b) oriented.recycle(); }
        } finally { b.recycle(); }
    }

    public Bitmap previewBitmap(Uri uri, String name) throws Exception {
        String ext = extension(name);
        if (ext.equals("pdf")) {
            byte[] data = bytes(uri);
            try (PDDocument doc = PDDocument.load(data)) {
                if (doc.getNumberOfPages() == 0) return null;
                return new PDFRenderer(doc).renderImage(0, 1.4f);
            }
        }
        if (Arrays.asList("jpg","jpeg","png","bmp","webp","tif","tiff").contains(ext)) {
            Bitmap b;
            try (InputStream in = open(uri)) { b = BitmapFactory.decodeStream(in); }
            if (b == null) return null;
            Bitmap o = orient(uri, b);
            if (o != b) b.recycle();
            return o;
        }
        return null;
    }

    private Bitmap orient(Uri uri, Bitmap b) {
        try (InputStream in = open(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int angle = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 :
                    o == ExifInterface.ORIENTATION_ROTATE_180 ? 180 :
                    o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (angle == 0) return b;
            Matrix m = new Matrix(); m.postRotate(angle);
            return Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
        } catch(Exception e) { return b; }
    }

    private InputStream open(Uri uri) throws FileNotFoundException {
        return context.getContentResolver().openInputStream(uri);
    }

    private byte[] bytes(Uri uri) throws IOException {
        try (InputStream in = open(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf,0,n);
            return out.toByteArray();
        }
    }

    private String extension(String name) {
        int p = name.lastIndexOf('.');
        return p < 0 ? "" : name.substring(p+1).toLowerCase(Locale.ROOT);
    }

    private int countLetters(String s) {
        int n=0; if (s == null) return 0;
        for (int i=0;i<s.length();i++) if (Character.isLetterOrDigit(s.charAt(i))) n++;
        return n;
    }
}
