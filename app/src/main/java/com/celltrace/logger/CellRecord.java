package com.celltrace.logger;

/**
 * 一次采样中的一个小区（含服务小区与邻区）的全部字段。
 * 数值型字段用 {@link #INVALID} 表示"设备没有上报"。
 */
public class CellRecord {

    public static final int INVALID = Integer.MIN_VALUE;

    /** 采样时间（epoch ms） */
    public long ts;

    /** 是否为服务小区（注册小区） */
    public boolean registered;

    /** 制式：NR / LTE / WCDMA / GSM */
    public String rat = "";

    /** SIM 卡槽 */
    public int slot = 1;

    public String carrier = "";
    public String mcc = "";
    public String mnc = "";

    /** TAC（4G/5G）或 LAC（2G/3G） */
    public int area = INVALID;
    public long cellId = -1L;
    public int pci = INVALID;
    public int arfcn = INVALID;
    /** 频段名，如 n78 / B3 */
    public String band = "";

    public int rsrp = INVALID;
    public int rsrq = INVALID;
    public int sinr = INVALID;
    public int rssi = INVALID;
    /** 0..4 信号格 */
    public int level = 0;

    public double lat = Double.NaN;
    public double lon = Double.NaN;
    public double alt = Double.NaN;
    public double speed = Double.NaN;
    public double accuracy = Double.NaN;
    public int sats = -1;

    public int neighborCount = 0;
    /** 邻区摘要：rat/pci/rsrp;rat/pci/rsrp */
    public String neighbors = "";

    /** 制式优先级，用于在多个注册小区里挑主小区 */
    public static int rank(String rat) {
        if (rat == null) return 0;
        if (rat.equals("NR")) return 4;
        if (rat.equals("LTE")) return 3;
        if (rat.equals("WCDMA")) return 2;
        if (rat.equals("GSM")) return 1;
        return 0;
    }

    public String areaText() {
        return area == INVALID || area == Integer.MAX_VALUE ? "--" : String.valueOf(area);
    }

    public String cellIdText() {
        if (cellId < 0) return "--";
        return String.valueOf(cellId);
    }

    public String pciText() {
        return pci == INVALID || pci == Integer.MAX_VALUE ? "--" : String.valueOf(pci);
    }

    public String arfcnText() {
        return arfcn == INVALID || arfcn == Integer.MAX_VALUE ? "--" : String.valueOf(arfcn);
    }

    private static String n(int v) {
        return v == INVALID ? "" : String.valueOf(v);
    }

    private static String d(double v, int digits) {
        if (Double.isNaN(v)) return "";
        return String.format(java.util.Locale.US, "%." + digits + "f", v);
    }

    /** 一行 CSV（不含换行） */
    public String toCsv() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(ts).append(',');
        sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(new java.util.Date(ts))).append(',');
        sb.append(registered ? 1 : 0).append(',');
        sb.append(slot).append(',');
        sb.append(q(rat)).append(',');
        sb.append(q(carrier)).append(',');
        sb.append(q(mcc)).append(',');
        sb.append(q(mnc)).append(',');
        sb.append(areaText()).append(',');
        sb.append(cellIdText()).append(',');
        sb.append(pciText()).append(',');
        sb.append(arfcnText()).append(',');
        sb.append(q(band)).append(',');
        sb.append(n(rsrp)).append(',');
        sb.append(n(rsrq)).append(',');
        sb.append(n(sinr)).append(',');
        sb.append(n(rssi)).append(',');
        sb.append(level).append(',');
        sb.append(d(lat, 7)).append(',');
        sb.append(d(lon, 7)).append(',');
        sb.append(d(alt, 1)).append(',');
        sb.append(d(speed, 2)).append(',');
        sb.append(d(accuracy, 1)).append(',');
        sb.append(sats < 0 ? "" : String.valueOf(sats)).append(',');
        sb.append(neighborCount).append(',');
        sb.append(q(neighbors));
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
