package com.celltrace.logger;

import android.annotation.TargetApi;
import android.os.Build;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

/** 5G NR 解析（仅 API 29+ 会被加载） */
@TargetApi(Build.VERSION_CODES.Q)
public final class CellParsersNr {

    private CellParsersNr() {
    }

    public static boolean isNr(CellInfo info) {
        return info instanceof CellInfoNr;
    }

    public static CellRecord parse(CellInfo info) {
        if (!(info instanceof CellInfoNr)) return null;
        CellInfoNr ci = (CellInfoNr) info;
        CellRecord r = new CellRecord();
        r.rat = "NR";
        r.registered = ci.isRegistered();
        try {
            CellIdentityNr id = (CellIdentityNr) ci.getCellIdentity();
            if (id != null) {
                r.area = id.getTac();
                r.cellId = id.getNci();
                r.pci = id.getPci();
                r.arfcn = id.getNrarfcn();
                r.band = BandTable.nr(id.getNrarfcn());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    int[] bands = id.getBands();
                    if (bands != null && bands.length > 0 && bands[0] > 0 && bands[0] < 1000) {
                        r.band = "n" + bands[0];
                    }
                }
            }
            CellSignalStrengthNr ss = (CellSignalStrengthNr) ci.getCellSignalStrength();
            if (ss != null) {
                r.rsrp = ss.getSsRsrp();
                r.rsrq = ss.getSsRsrq();
                r.sinr = ss.getSsSinr();
                r.level = ss.getLevel();
            }
        } catch (Throwable ignored) {
        }
        return r;
    }
}
