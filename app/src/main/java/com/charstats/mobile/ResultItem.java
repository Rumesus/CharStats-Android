package com.charstats.mobile;

import android.net.Uri;

public class ResultItem {
    public final Uri uri;
    public final String path;
    public final String name;
    public final long withSpaces;
    public final long withoutSpaces;
    public final double plus15;
    public final double pages;
    public final double cost;
    public final String error;

    public ResultItem(Uri uri, String path, String name, long withSpaces, long withoutSpaces,
                      double plus15, double pages, double cost, String error) {
        this.uri = uri;
        this.path = path;
        this.name = name;
        this.withSpaces = withSpaces;
        this.withoutSpaces = withoutSpaces;
        this.plus15 = plus15;
        this.pages = pages;
        this.cost = cost;
        this.error = error;
    }

    @Override public String toString() {
        if (error != null) return name + "\nПомилка: " + error;
        return name + "\nЗ пробілами: " + withSpaces +
                "   Без пробілів: " + withoutSpaces +
                "   +15%: " + String.format("%.2f", plus15) +
                "   Сторінки: " + String.format("%.2f", pages) +
                "   Вартість: " + String.format("%.2f", cost);
    }
}
