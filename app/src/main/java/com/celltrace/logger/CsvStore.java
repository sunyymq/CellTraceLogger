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
                    + "rsrp_dbm,rsrq_db,sinr_db,rssi_dbm,level,lat,lon,alt_m,speed_mps,acc_m,sats,"
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

    /** 新建一个会话文件，写入 BOM + 表头，返回是否成功 */
    public boolean open(Context c) {
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
            return true;
        } catch (IOException e) {
            file = null;
            writer = null;
            return false;
        }
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
