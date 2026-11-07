package com.celltrace.logger;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CSV 落盘。文件放在 App 专属外部目录（Android/data/.../files/traces），
 * 无需存储权限，卸载应用才会被清理，可用「导出」分享出去。
 */
public class CsvStore {

    private static final String HEADER =
            "epoch_ms,time,registered,slot,rat,carrier,mcc,mnc,tac,cid,pci,arfcn,band,"
                    + "rsrp_dbm,rsrq_db,sinr_db,rssi_dbm,level,"
                    + "lat,lon,loc_time_ms,loc_age_s,alt_m,speed_mps,acc_m,sats,"
                    + "neighbor_count,neighbors";

    private File file;
    private BufferedWriter writer;
    private long rows;

    public static File dir(Context c) {
        File d = new File(c.getExternalFilesDir(null), "traces");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    public boolean isOpen() {
        return writer != null;
    }

    public String path() {
        return file == null ? null : file.getAbsolutePath();
    }

    public long rows() {
        return rows;
    }

    /** 新建一个会话文件，写入 BOM + 表头 + 同名 .meta.json，返回是否成功 */
    public boolean open(Context c) {
        return open(c, 1000);
    }

    public boolean open(Context c, int intervalMs) {
        close();
        String name = "trace_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        file = new File(dir(c), name);
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(0xEF);
            fos.write(0xBB);
            fos.write(0xBF); // UTF-8 BOM，保证 Excel / WPS 正确识别中文
            writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"), 8192);
            writer.write(HEADER);
            writer.write("\r\n");
            writer.flush();
            rows = 0;
            writeMeta(c, file, intervalMs);
            return true;
        } catch (IOException e) {
            file = null;
            writer = null;
            return false;
        }
    }

    /** 会话元信息：设备、系统、采样间隔、起始时间。方便日后分析时知道数据是怎么来的 */
    private void writeMeta(Context c, File csvFile, int intervalMs) {
        String base = csvFile.getName();
        if (base.endsWith(".csv")) base = base.substring(0, base.length() - 4);
        File meta = new File(csvFile.getParentFile(), base + ".meta.json");
        String ver = "1.0.0";
        try {
            ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
        }
        String json = "{\n"
                + "  \"app\": \"CellTraceLogger\",\n"
                + "  \"app_version\": \"" + ver + "\",\n"
                + "  \"package\": \"" + c.getPackageName() + "\",\n"
                + "  \"device\": \"" + esc(android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL) + "\",\n"
                + "  \"android\": \"" + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\",\n"
                + "  \"sample_interval_ms\": " + intervalMs + ",\n"
                + "  \"started_at_ms\": " + System.currentTimeMillis() + ",\n"
                + "  \"started_at\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\",\n"
                + "  \"timezone\": \"" + java.util.TimeZone.getDefault().getID() + "\",\n"
                + "  \"csv_columns\": \"" + HEADER + "\",\n"
                + "  \"notes\": \"loc_time_ms/loc_age_s 为坐标自身的采集时刻与数据年龄；坐标为空表示当时无定位\"\n"
                + "}\n";
        try {
            FileOutputStream fos = new FileOutputStream(meta, false);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (IOException ignored) {
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void append(CellRecord r) {
        if (writer == null || r == null) return;
        try {
            writer.write(r.toCsv());
            writer.write("\r\n");
            writer.flush();
            rows++;
        } catch (IOException ignored) {
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            }
        }
        writer = null;
    }

    /** 按时间倒序列出所有会话文件 */
    public static List<File> list(Context c) {
        File[] arr = dir(c).listFiles();
        List<File> out = new ArrayList<>();
        if (arr != null) {
            for (File f : arr) {
                if (f.isFile() && f.getName().endsWith(".csv")) out.add(f);
            }
        }
        out.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        return out;
    }

    public static int clearAll(Context c) {
        List<File> files = list(c);
        int n = 0;
        for (File f : files) {
            if (f.delete()) n++;
        }
        return n;
    }

    public static String describe(List<File> files) {
        if (files.isEmpty()) return "暂无记录文件";
        return files.size() + " 个文件 · " + Arrays.toString(new String[]{files.get(0).getName()});
    }
}
