package com.celltrace.logger;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 采集一次蜂窝 + 位置快照。
 * 位置由 Service 通过 GPS/网络定位更新，这里只读取最新值。
 */
public class CellSampler {

    private final Context ctx;
    private final TelephonyManager tm;

    private volatile Location location;
    private volatile int satellites = -1;

    public CellSampler(Context context) {
        this.ctx = context.getApplicationContext();
        this.tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void updateLocation(Location l) {
        if (l != null) location = l;
    }

    public void updateSatellites(int usedInFix) {
        satellites = usedInFix;
    }

    public Snapshot sample() {
        Snapshot s = new Snapshot();
        List<CellRecord> all = new ArrayList<>();

        List<CellInfo> infos = null;
        if (tm != null) {
            try {
                infos = tm.getAllCellInfo();
            } catch (Throwable ignored) {
            }
        }
        if (infos == null) infos = Collections.emptyList();

        for (CellInfo ci : infos) {
            CellRecord r = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && CellParsersNr.isNr(ci)) {
                    r = CellParsersNr.parse(ci);
                } else {
                    r = CellParsers.parse(ci);
                }
            } catch (Throwable ignored) {
            }
            if (r != null) all.add(r);
        }

        // 兜底：部分机型 getAllCellInfo() 返回空，但能拿到 GsmCellLocation
        if (all.isEmpty()) {
            CellRecord r = fromCellLocation();
            if (r != null) all.add(r);
        }

        CellRecord primary = null;
        for (CellRecord r : all) {
            if (!r.registered) continue;
            if (primary == null || CellRecord.rank(r.rat) > CellRecord.rank(primary.rat)) primary = r;
        }
        if (primary == null && !all.isEmpty()) primary = all.get(0);

        if (primary != null) {
            fillOperator(primary);
            Location l = location;
            if (l != null) {
                primary.lat = l.getLatitude();
                primary.lon = l.getLongitude();
                primary.locTs = l.getTime();
                primary.locAge = Math.max(0, (System.currentTimeMillis() - l.getTime()) / 1000.0);
                primary.alt = l.hasAltitude() ? l.getAltitude() : Double.NaN;
                primary.speed = l.hasSpeed() ? l.getSpeed() : Double.NaN;
                primary.accuracy = l.hasAccuracy() ? l.getAccuracy() : Double.NaN;
            }
            primary.sats = satellites;
        }

        List<CellRecord> neighbors = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (CellRecord r : all) {
            if (r == primary || r.registered) continue;
            neighbors.add(r);
            if (sb.length() > 0) sb.append(';');
            sb.append(r.rat).append('/').append(r.pciText()).append('/')
                    .append(r.rsrp == CellRecord.INVALID ? "-" : String.valueOf(r.rsrp));
        }
        if (primary != null) {
            primary.neighborCount = neighbors.size();
            primary.neighbors = sb.toString();
        }

        s.primary = primary;
        s.neighbors = neighbors;
        s.intervalMs = 1000;
        if (primary == null) {
            s.note = "未获取到小区信息：请确认已授予定位权限，并在 设置 → 关于手机 中确认设备支持蜂窝网络。";
        }
        return s;
    }

    private void fillOperator(CellRecord r) {
        if (tm == null) return;
        String name = "";
        String num = "";
        try {
            name = safe(tm.getNetworkOperatorName());
            num = safe(tm.getNetworkOperator());
            if (num.isEmpty()) {
                name = safe(tm.getSimOperatorName());
                num = safe(tm.getSimOperator());
            }
        } catch (Throwable ignored) {
        }
        if (r.carrier.isEmpty()) r.carrier = name;
        if (num.length() >= 4) {
            if (r.mcc.isEmpty()) r.mcc = num.substring(0, 3);
            if (r.mnc.isEmpty()) r.mnc = num.substring(3);
        }
    }

    private CellRecord fromCellLocation() {
        if (tm == null) return null;
        try {
            CellLocation cl = tm.getCellLocation();
            if (cl instanceof GsmCellLocation) {
                GsmCellLocation g = (GsmCellLocation) cl;
                if (g.getCid() < 0) return null;
                CellRecord r = new CellRecord();
                r.rat = "2G/3G";
                r.registered = true;
                r.area = g.getLac();
                r.cellId = g.getCid();
                r.pci = g.getPsc();
                return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
