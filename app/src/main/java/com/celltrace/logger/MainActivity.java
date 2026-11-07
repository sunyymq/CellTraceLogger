package com.celltrace.logger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements AppState.Listener {

    private static final int REQ_PERM = 1001;

    private TextView pillState;
    private TextView txtSub;
    private TextView txtRat;
    private TextView txtCarrier;
    private TextView txtRsrp;
    private TextView vRsrq;
    private TextView vSinr;
    private TextView vRssi;
    private TextView txtSparkRange;
    private TextView vBand;
    private TextView vArfcn;
    private TextView vPci;
    private TextView vTac;
    private TextView vCid;
    private TextView vNb;
    private TextView vLat;
    private TextView vLon;
    private TextView vSpeed;
    private TextView vAlt;
    private TextView vAcc;
    private TextView vSats;
    private TextView vLoctime;
    private TextView vLocage;
    private TextView txtNbTitle;
    private TextView txtNbEmpty;
    private TextView txtRows;
    private TextView txtDuration;
    private TextView btnRecord;
    private TextView btnExport;
    private TextView btnMore;
    private LinearLayout nbList;
    private LevelBarsView bars;
    private SparklineView spark;

    private String nbSignature = "";
    private int intervalMs = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bind();
        wire();
        AppState.addListener(this);
        ensurePermissions();
        startServiceSafely(null);
    }

    private void bind() {
        pillState = findViewById(R.id.pill_state);
        txtSub = findViewById(R.id.txt_sub);
        txtRat = findViewById(R.id.txt_rat);
        txtCarrier = findViewById(R.id.txt_carrier);
        txtRsrp = findViewById(R.id.txt_rsrp);
        vRsrq = findViewById(R.id.v_rsrq);
        vSinr = findViewById(R.id.v_sinr);
        vRssi = findViewById(R.id.v_rssi);
        txtSparkRange = findViewById(R.id.txt_spark_range);
        vBand = findViewById(R.id.v_band);
        vArfcn = findViewById(R.id.v_arfcn);
        vPci = findViewById(R.id.v_pci);
        vTac = findViewById(R.id.v_tac);
        vCid = findViewById(R.id.v_cid);
        vNb = findViewById(R.id.v_nb);
        vLat = findViewById(R.id.v_lat);
        vLon = findViewById(R.id.v_lon);
        vSpeed = findViewById(R.id.v_speed);
        vAlt = findViewById(R.id.v_alt);
        vAcc = findViewById(R.id.v_acc);
        vSats = findViewById(R.id.v_sats);
        vLoctime = findViewById(R.id.v_loctime);
        vLocage = findViewById(R.id.v_locage);
        txtNbTitle = findViewById(R.id.txt_nb_title);
        txtNbEmpty = findViewById(R.id.txt_nb_empty);
        txtRows = findViewById(R.id.txt_rows);
        txtDuration = findViewById(R.id.txt_duration);
        btnRecord = findViewById(R.id.btn_record);
        btnExport = findViewById(R.id.btn_export);
        btnMore = findViewById(R.id.btn_more);
        nbList = findViewById(R.id.nb_list);
        bars = findViewById(R.id.bars);
        spark = findViewById(R.id.spark);
    }

    private void wire() {
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean rec = AppState.recording;
                sendAction(rec ? TraceService.ACTION_STOP_RECORD : TraceService.ACTION_START_RECORD, 0);
                if (!rec) toast("开始记录 · 文件写入 Android/data/" + getPackageName() + "/files/traces");
            }
        });

        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportDialog();
            }
        });

        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore(v);
            }
        });
    }

    // ---------------- 权限 ----------------

    private void ensurePermissions() {
        List<String> need = new ArrayList<>();
        String[] base = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
        };
        for (String p : base) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERM) return;
        boolean ok = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!ok) {
            toast("未授予定位权限：Android 要求定位权限才能读取小区信息");
        } else {
            // 权限到手后让服务按「自动记录」意图开工
            startServiceSafely(null);
        }
    }

    // ---------------- 服务控制 ----------------

    private void sendAction(String action, int ms) {
        Intent i = new Intent(this, TraceService.class).setAction(action);
        if (ms > 0) i.putExtra("ms", ms);
        startServiceSafely(i);
    }

    private void startServiceSafely(Intent i) {
        Intent intent = i != null ? i : new Intent(this, TraceService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable t) {
            toast("服务启动失败：" + t.getClass().getSimpleName());
        }
    }

    // ---------------- 快照刷新 ----------------

    @Override
    public void onSnapshot(Snapshot s) {
        if (isFinishing() || isDestroyed()) return;
        intervalMs = s.intervalMs > 0 ? s.intervalMs : intervalMs;
        CellRecord p = s.primary;

        if (s.recording) {
            pillState.setText("● REC");
            pillState.setTextColor(0xFF0B0F14);
            pillState.setBackgroundResource(R.drawable.bg_pill_rec);
            btnRecord.setText("停止记录");
            btnRecord.setBackgroundResource(R.drawable.btn_primary_stop);
            btnRecord.setTextColor(0xFF0B0F14);
        } else {
            pillState.setText("IDLE");
            pillState.setTextColor(0xFF8B98A5);
            pillState.setBackgroundResource(R.drawable.bg_pill_idle);
            btnRecord.setText("开始记录");
            btnRecord.setBackgroundResource(R.drawable.btn_primary);
            btnRecord.setTextColor(0xFF04121A);
        }

        long dur = s.recording && s.startMs > 0 ? System.currentTimeMillis() - s.startMs : 0;
        txtRows.setText(s.recording ? String.valueOf(s.rows) : "0");
        txtDuration.setText(dur(dur));
        txtSub.setText(String.format(Locale.US, "采样间隔 %d ms · %s",
                intervalMs, s.recording ? "写入中" : "待机"));

        if (p == null) {
            txtRat.setText("--");
            txtCarrier.setText(s.note == null || s.note.isEmpty() ? "无蜂窝数据" : s.note);
            txtRsrp.setText("--");
            bars.setLevel(0);
            vRsrq.setText("--");
            vSinr.setText("--");
            vRssi.setText("--");
            vBand.setText("--");
            vArfcn.setText("--");
            vPci.setText("--");
            vTac.setText("--");
            vCid.setText("--");
            vNb.setText("0");
            vLat.setText("--");
            vLon.setText("--");
            vSpeed.setText("--");
            vAlt.setText("--");
            vAcc.setText("--");
            vSats.setText("--");
            vLoctime.setText("--");
            vLocage.setText("--");
            setNeighbors(new ArrayList<CellRecord>());
            return;
        }

        txtRat.setText(p.rat.isEmpty() ? "--" : p.rat);
        txtRat.setTextColor(colorOfRat(p.rat));
        String carrier = p.carrier.isEmpty() ? "未知运营商" : p.carrier;
        if (!p.mcc.isEmpty()) carrier += "  " + p.mcc + "-" + p.mnc;
        txtCarrier.setText(carrier);

        if (p.rsrp == CellRecord.INVALID) {
            txtRsrp.setText("--");
        } else {
            txtRsrp.setText(String.valueOf(p.rsrp));
            spark.push(p.rsrp);
        }
        txtRsrp.setTextColor(colorOfRsrp(p.rsrp));
        bars.setLevel(p.level);

        vRsrq.setText(p.rsrq == CellRecord.INVALID ? "--" : p.rsrq + " dB");
        vSinr.setText(p.sinr == CellRecord.INVALID ? "--" : p.sinr + " dB");
        vRssi.setText(p.rssi == CellRecord.INVALID ? "--" : p.rssi + " dBm");

        float lo = spark.min();
        float hi = spark.max();
        if (Float.isNaN(lo) || Float.isNaN(hi)) {
            txtSparkRange.setText("等待采样");
        } else {
            txtSparkRange.setText(String.format(Locale.US, "%d ~ %d dBm", (int) lo, (int) hi));
        }

        vBand.setText(p.band == null || p.band.isEmpty() ? "--" : p.band);
        vArfcn.setText(p.arfcnText());
        vPci.setText(p.pciText());
        vTac.setText(p.areaText());
        vCid.setText(p.cellIdText());
        vNb.setText(String.valueOf(p.neighborCount));

        vLat.setText(Double.isNaN(p.lat) ? "--" : String.format(Locale.US, "%.6f", p.lat));
        vLon.setText(Double.isNaN(p.lon) ? "--" : String.format(Locale.US, "%.6f", p.lon));
        vSpeed.setText(Double.isNaN(p.speed) ? "--" : String.format(Locale.US, "%.1f", p.speed * 3.6));
        vAlt.setText(Double.isNaN(p.alt) ? "--" : String.format(Locale.US, "%.0f", p.alt));
        vAcc.setText(Double.isNaN(p.accuracy) ? "--" : String.format(Locale.US, "%.0f", p.accuracy));
        vSats.setText(p.sats < 0 ? "--" : String.valueOf(p.sats));

        if (p.locTs > 0) {
            vLoctime.setText(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(p.locTs)));
        } else {
            vLoctime.setText("--");
        }
        if (p.locAge >= 0) {
            vLocage.setText(String.format(Locale.US, "%.1f", p.locAge));
            vLocage.setTextColor(p.locAge > 5 ? 0xFFFBBF24 : 0xFFE6EDF3);
        } else {
            vLocage.setText("--");
            vLocage.setTextColor(0xFF6B7A8A);
        }

        setNeighbors(s.neighbors);
    }

    private void setNeighbors(List<CellRecord> list) {
        if (list == null) list = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (CellRecord r : list) {
            sig.append(r.rat).append(r.pciText()).append(r.rsrp).append(r.band).append('|');
        }
        txtNbTitle.setText("邻区 · " + list.size());
        if (sig.toString().equals(nbSignature)) return;
        nbSignature = sig.toString();

        nbList.removeAllViews();
        txtNbEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        int max = Math.min(list.size(), 12);
        for (int i = 0; i < max; i++) {
            CellRecord r = list.get(i);
            nbList.addView(buildRow(r));
        }
    }

    private View buildRow(CellRecord r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(0, pad, 0, pad);

        TextView left = new TextView(this);
        String bandTxt = r.band == null || r.band.isEmpty() ? "" : " " + r.band;
        left.setText(r.rat + bandTxt);
        left.setTextSize(12);
        left.setTextColor(colorOfRat(r.rat));
        left.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mid = new TextView(this);
        mid.setText("PCI " + r.pciText());
        mid.setTextSize(12);
        mid.setTextColor(0xFF8B98A5);
        mid.setTypeface(Typeface.MONOSPACE);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        mid.setGravity(Gravity.CENTER);

        TextView right = new TextView(this);
        right.setText(r.rsrp == CellRecord.INVALID ? "--" : r.rsrp + " dBm");
        right.setTextSize(12);
        right.setTextColor(colorOfRsrp(r.rsrp));
        right.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        right.setGravity(Gravity.END);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(left);
        row.addView(mid);
        row.addView(right);
        return row;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private int colorOfRat(String rat) {
        if ("NR".equals(rat)) return 0xFFA78BFA;
        if ("LTE".equals(rat)) return 0xFF22D3EE;
        if ("WCDMA".equals(rat)) return 0xFF38BDF8;
        return 0xFF94A3B8;
    }

    private int colorOfRsrp(int v) {
        if (v == CellRecord.INVALID) return 0xFF6B7A8A;
        if (v >= -85) return 0xFF34D399;
        if (v >= -100) return 0xFF22D3EE;
        if (v >= -110) return 0xFFFBBF24;
        return 0xFFF87171;
    }

    private String dur(long ms) {
        if (ms <= 0) return "00:00:00";
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ---------------- 导出 ----------------

    private void showExportDialog() {
        final List<File> files = CsvStore.list(this);
        if (files.isEmpty()) {
            toast("还没有记录文件，先点「开始记录」");
            return;
        }
        final String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            names[i] = f.getName() + "   " + (f.length() / 1024) + " KB";
        }
        new AlertDialog.Builder(this)
                .setTitle("导出 CSV · 共 " + files.size() + " 个")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        share(files.get(which));
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void exportToDownloads() {
        List<File> files = CsvStore.list(this);
        if (files.isEmpty()) {
            toast("还没有记录文件");
            return;
        }
        String msg = DownloadExport.copy(this, files.get(0));
        toast(msg);
    }

    private boolean bootEnabled() {
        return getSharedPreferences(TraceService.PREFS, MODE_PRIVATE)
                .getBoolean(TraceService.KEY_BOOT, false);
    }

    private void share(File f) {
        Uri uri = ExportProvider.uriFor(f);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, f.getName());
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "导出 " + f.getName()));
        } catch (Throwable t) {
            toast("没有可用的分享应用");
        }
    }

    // ---------------- 更多菜单 ----------------

    private void showMore(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "采样间隔 500 ms");
        menu.getMenu().add(0, 2, 1, "采样间隔 1 秒");
        menu.getMenu().add(0, 3, 2, "采样间隔 2 秒");
        menu.getMenu().add(0, 4, 3, "采样间隔 5 秒");
        menu.getMenu().add(0, 5, 4, "记录文件位置");
        menu.getMenu().add(0, 7, 5, "导出到「下载」文件夹");
        menu.getMenu().add(0, 8, 6, bootEnabled() ? "开机自动记录：开" : "开机自动记录：关");
        menu.getMenu().add(0, 6, 7, "清空全部记录");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        sendAction(TraceService.ACTION_INTERVAL, 500);
                        return true;
                    case 2:
                        sendAction(TraceService.ACTION_INTERVAL, 1000);
                        return true;
                    case 3:
                        sendAction(TraceService.ACTION_INTERVAL, 2000);
                        return true;
                    case 4:
                        sendAction(TraceService.ACTION_INTERVAL, 5000);
                        return true;
                    case 5:
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("记录文件位置")
                                .setMessage(CsvStore.dir(MainActivity.this).getAbsolutePath()
                                        + "\n\n也可通过「导出 CSV」直接分享到微信/邮箱。")
                                .setPositiveButton("知道了", null)
                                .show();
                        return true;
                    case 6:
                        confirmClear();
                        return true;
                    case 7:
                        exportToDownloads();
                        return true;
                    case 8:
                        boolean on = !bootEnabled();
                        getSharedPreferences(TraceService.PREFS, MODE_PRIVATE)
                                .edit().putBoolean(TraceService.KEY_BOOT, on).apply();
                        toast(on ? "已开启：重启手机后自动开始记录" : "已关闭开机自动记录");
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空全部记录？")
                .setMessage("将删除 traces 目录下所有 CSV，不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int n = CsvStore.clearAll(MainActivity.this);
                        nbSignature = "";
                        toast("已删除 " + n + " 个文件");
                    }
                })
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppState.addListener(this);
        Snapshot s = AppState.latest;
        if (s != null) onSnapshot(s);
    }

    @Override
    protected void onDestroy() {
        AppState.removeListener(this);
        super.onDestroy();
    }
}
