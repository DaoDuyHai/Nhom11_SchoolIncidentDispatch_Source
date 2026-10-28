package com.incidentreport.benchmark;

import java.util.List;

/**
 * Tiện ích vẽ biểu đồ ASCII và định dạng bảng kết quả chuẩn báo cáo.
 */
public class ChartGenerator {

    /**
     * Vẽ một thanh bar chart ASCII
     */
    public static String renderBar(String label, double value, double maxValue, int maxBarChars, String unit) {
        int labelWidth = 24;
        String formattedLabel = String.format("%-" + labelWidth + "s", label);
        if (formattedLabel.length() > labelWidth) {
            formattedLabel = formattedLabel.substring(0, labelWidth);
        }

        double ratio = (maxValue > 0) ? Math.min(1.0, value / maxValue) : 0.0;
        int barChars = (int) Math.round(ratio * maxBarChars);

        StringBuilder sb = new StringBuilder();
        sb.append(formattedLabel).append(" │");
        for (int i = 0; i < barChars; i++) {
            sb.append("█");
        }
        for (int i = barChars; i < maxBarChars; i++) {
            sb.append("░");
        }
        sb.append(String.format(" %8.2f %s", value, unit));
        return sb.toString();
    }

    /**
     * Xuất bảng định dạng Markdown
     */
    public static String toMarkdownTable(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (String h : headers) {
            sb.append(h).append(" | ");
        }
        sb.append("\n| ");
        for (int i = 0; i < headers.length; i++) {
            sb.append("--- | ");
        }
        sb.append("\n");

        for (String[] row : rows) {
            sb.append("| ");
            for (String cell : row) {
                sb.append(cell).append(" | ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Xuất bảng định dạng ASCII Boxed Table
     */
    public static String toAsciiTable(String title, String[] headers, List<String[]> rows) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < cols; i++) {
                if (i < row.length && row[i] != null) {
                    widths[i] = Math.max(widths[i], row[i].length());
                }
            }
        }
        for (int i = 0; i < cols; i++) {
            widths[i] += 2; // padding
        }

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append("\n📍 ").append(title).append("\n");
        }

        // Top border
        sb.append("+");
        for (int w : widths) {
            sb.append("-".repeat(w)).append("+");
        }
        sb.append("\n|");

        // Header row
        for (int i = 0; i < cols; i++) {
            sb.append(String.format(" %-" + (widths[i] - 1) + "s|", headers[i]));
        }
        sb.append("\n+");

        // Separator
        for (int w : widths) {
            sb.append("=".repeat(w)).append("+");
        }
        sb.append("\n");

        // Data rows
        for (String[] row : rows) {
            sb.append("|");
            for (int i = 0; i < cols; i++) {
                String val = (i < row.length && row[i] != null) ? row[i] : "";
                sb.append(String.format(" %-" + (widths[i] - 1) + "s|", val));
            }
            sb.append("\n");
        }

        // Bottom border
        sb.append("+");
        for (int w : widths) {
            sb.append("-".repeat(w)).append("+");
        }
        sb.append("\n");

        return sb.toString();
    }
}

