package com.charstats.mobile;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ReportWriter {

    public static File writeExcel(Context ctx, List<ResultItem> items) throws Exception {
        File out = new File(ctx.getCacheDir(), "статистика_символів.xlsx");
        List<ResultItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator
                .comparing((ResultItem r) -> group(r.path), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> order(r.path), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Статистика");
            String[] hdr = {"Файл","З пробілами","Без пробілів","Символи + проб. + 15%","Сторінки","Вартість"};
            Row h = sh.createRow(0);
            for (int i=0;i<hdr.length;i++) h.createCell(i).setCellValue(hdr[i]);

            int row = 1;
            String currentGroup = null, currentOrder = null;
            double tw=0, two=0, t15=0, tp=0, tc=0;
            for (ResultItem r : sorted) {
                String g = group(r.path), o = order(r.path);
                if (!Objects.equals(g,currentGroup)) {
                    currentGroup=g; currentOrder=null;
                    Row gr = sh.createRow(row++);
                    gr.createCell(0).setCellValue("РОБОТА: " + g);
                }
                if (!o.isEmpty() && !Objects.equals(o,currentOrder)) {
                    currentOrder=o;
                    Row or = sh.createRow(row++);
                    or.createCell(0).setCellValue("ЗАМОВЛЕННЯ: " + o);
                }
                Row rr = sh.createRow(row++);
                rr.createCell(0).setCellValue(r.path);
                if (r.error != null) {
                    rr.createCell(1).setCellValue("помилка");
                    rr.createCell(2).setCellValue(r.error);
                    continue;
                }
                rr.createCell(1).setCellValue(r.withSpaces);
                rr.createCell(2).setCellValue(r.withoutSpaces);
                rr.createCell(3).setCellValue(r.plus15);
                rr.createCell(4).setCellValue(r.pages);
                rr.createCell(5).setCellValue(r.cost);
                for (int c=1;c<=5;c++) rr.getCell(c).setCellStyle(number2(wb));
                tw += r.withSpaces; two += r.withoutSpaces; t15 += r.plus15; tp += r.pages; tc += r.cost;
            }
            Row total=sh.createRow(row);
            total.createCell(0).setCellValue("РАЗОМ");
            total.createCell(1).setCellValue(tw);
            total.createCell(2).setCellValue(two);
            total.createCell(3).setCellValue(t15);
            total.createCell(4).setCellValue(tp);
            total.createCell(5).setCellValue(tc);
            for (int c=1;c<=5;c++) total.getCell(c).setCellStyle(number2(wb));

            try (OutputStream os = new FileOutputStream(out)) { wb.write(os); }
        }
        return out;
    }

    private static CellStyle number2(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        return s;
    }

    public static File writeClientPdf(Context ctx, List<ResultItem> items) throws Exception {
        File out = new File(ctx.getCacheDir(), "розрахунок_для_клієнта.pdf");
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        int pageNo=1, y=45;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(842,595,pageNo).create());
        Canvas c = page.getCanvas();

        p.setTextSize(18); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("Розрахунок для клієнта", 35, y, p);
        y+=35;
        p.setTextSize(11); p.setTypeface(Typeface.DEFAULT);
        c.drawText("Файл",35,y,p); c.drawText("Кількість символів",470,y,p);
        c.drawText("Сторінки",630,y,p); c.drawText("Вартість",725,y,p);
        y+=20;

        double ts=0,tp=0,tc=0;
        for (ResultItem r : items) {
            if (r.error != null) continue;
            if (y > 545) {
                pdf.finishPage(page);
                page = pdf.startPage(new PdfDocument.PageInfo.Builder(842,595,++pageNo).create());
                c = page.getCanvas(); y=40;
            }
            String n = r.path.length() > 68 ? "…" + r.path.substring(r.path.length()-67) : r.path;
            c.drawText(n,35,y,p);
            c.drawText(String.format("%.2f",r.plus15),470,y,p);
            c.drawText(String.format("%.2f",r.pages),630,y,p);
            c.drawText(String.format("%.2f",r.cost),725,y,p);
            y+=18; ts+=r.plus15; tp+=r.pages; tc+=r.cost;
        }
        y+=8; p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("Разом",35,y,p); c.drawText(String.format("%.2f",ts),470,y,p);
        c.drawText(String.format("%.2f",tp),630,y,p); c.drawText(String.format("%.2f",tc),725,y,p);
        pdf.finishPage(page);
        try (OutputStream os = new FileOutputStream(out)) { pdf.writeTo(os); }
        pdf.close();
        return out;
    }

    private static String[] parts(String p) { return p.replace('/','\\').split("\\\\"); }
    private static String group(String p) {
        String[] a=parts(p); return a.length>1?a[0]:"Окремі файли";
    }
    private static String order(String p) {
        String[] a=parts(p); if(a.length<=2)return "";
        return String.join("\\", Arrays.copyOfRange(a,1,a.length-1));
    }
}
