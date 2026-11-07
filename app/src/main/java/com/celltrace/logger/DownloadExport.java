package com.celltrace.logger;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 把记录文件复制到手机的「下载/CellTraceLogger」目录，插数据线/用文件管理器就能直接取。
 * Android 10+ 走 MediaStore（无需存储权限）；9 及以下写公共目录（需要 WRITE_EXTERNAL_STORAGE）。
 */
public final class DownloadExport {

    private static final String FOLDER = "CellTraceLogger";

    private DownloadExport() {
    }

    /** @return 给用户看的结果文案 */
    public static String copy(Context c, File src) {
        if (src == null || !src.isFile()) return "文件不存在";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String r = copyViaMediaStore(c, src);
            File meta = metaOf(src);
            if (meta.isFile()) copyViaMediaStore(c, meta);
            return r;
        }

        if (c.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                != PackageManager.PERMISSION_GRANTED) {
            return "需要存储权限才能写入「下载」目录，请在系统设置里授予后重试";
        }
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) return "无法创建目录 " + dir.getAbsolutePath();
            File out = new File(dir, src.getName());
            streamCopy(src, out);
            File meta = metaOf(src);
            if (meta.isFile()) streamCopy(meta, new File(dir, meta.getName()));
            MediaScannerConnection.scanFile(c,
                    new String[]{out.getAbsolutePath()}, new String[]{"text/csv"}, null);
            return "已导出到 " + out.getAbsolutePath();
        } catch (Throwable t) {
            return "导出失败：" + t.getMessage();
        }
    }

    private static String copyViaMediaStore(Context c, File src) {
        try {
            String mime = src.getName().endsWith(".json") ? "application/json" : "text/csv";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, src.getName());
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            v.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return "导出失败：无法创建文件";

            OutputStream os = c.getContentResolver().openOutputStream(uri);
            if (os == null) return "导出失败：无法写入";
            InputStream is = new FileInputStream(src);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            is.close();
            os.flush();
            os.close();

            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            c.getContentResolver().update(uri, v, null, null);
            return "已导出到 下载/" + FOLDER + "/" + src.getName();
        } catch (Throwable t) {
            return "导出失败：" + t.getMessage();
        }
    }

    private static void streamCopy(File in, File out) throws Exception {
        InputStream is = new FileInputStream(in);
        OutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        is.close();
        os.flush();
        os.close();
    }

    /** trace_xxx.csv → trace_xxx.meta.json */
    private static File metaOf(File csv) {
        String n = csv.getName();
        if (n.endsWith(".csv")) n = n.substring(0, n.length() - 4);
        return new File(csv.getParentFile(), n + ".meta.json");
    }
}
