package com.celltrace.logger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 只读文件分享用的最小 ContentProvider。
 * URI 形如 content://com.celltrace.logger.files/f?p=<绝对路径>
 */
public class ExportProvider extends ContentProvider {

    public static final String AUTHORITY = "com.celltrace.logger.files";

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/f?p=" + Uri.encode(f.getAbsolutePath()));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String p = uri.getQueryParameter("p");
        if (p == null) throw new FileNotFoundException("missing path");
        File f = new File(p);
        Context c = getContext();
        String root = c == null ? null : c.getExternalFilesDir(null).getAbsolutePath();
        if (root == null || !f.getAbsolutePath().startsWith(root)) {
            throw new FileNotFoundException("outside app dir");
        }
        if (!f.isFile()) throw new FileNotFoundException(p);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "text/csv";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
