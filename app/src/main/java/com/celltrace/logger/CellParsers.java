package com.celltrace.logger;

import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;

/**
 * 2G/3G/4G 的 CellInfo 解析。5G NR 的解析放在 {@link CellParsersNr}，
 * 避免 API 29 才存在的类在低版本设备上被类校验器提前解析。
 */
public final class CellParsers {

    private CellParsers() {
    }

    public static CellRecord parse(CellInfo info) {
        try {
            if (info instanceof CellInfoLte) return lte((CellInfoLte) info);
            if (info instanceof CellInfoWcdma) return wcdma((CellInfoWcdma) info);
            if (info instanceof CellInfoGsm) return gsm((CellInfoGsm) info);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static CellRecord lte(CellInfoLte ci) {
        CellRecord r = new CellRecord();
        r.rat = "LTE";
        r.registered = ci.isRegistered();
        CellIdentityLte id = ci.getCellIdentity();
        CellSignalStrengthLte ss = ci.getCellSignalStrength();
        if (id != null) {
            r.area = id.getTac();
            r.cellId = id.getCi();
            r.pci = id.getPci();
            r.arfcn = id.getEarfcn();
            r.band = BandTable.lte(id.getEarfcn());
        }
        if (ss != null) {
            r.rsrp = ss.getRsrp();
            r.rsrq = ss.getRsrq();
            r.rssi = ss.getRssi();
            r.level = ss.getLevel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                r.sinr = ss.getRssnr();
            }
        }
        return r;
    }

    private static CellRecord wcdma(CellInfoWcdma ci) {
        CellRecord r = new CellRecord();
        r.rat = "WCDMA";
        r.registered = ci.isRegistered();
        CellIdentityWcdma id = ci.getCellIdentity();
        CellSignalStrengthWcdma ss = ci.getCellSignalStrength();
        if (id != null) {
            r.area = id.getLac();
            r.cellId = id.getCid();
            r.pci = id.getPsc();
            r.arfcn = id.getUarfcn();
        }
        if (ss != null) {
            r.rsrp = ss.getDbm();
            r.level = ss.getLevel();
        }
        return r;
    }

    private static CellRecord gsm(CellInfoGsm ci) {
        CellRecord r = new CellRecord();
        r.rat = "GSM";
        r.registered = ci.isRegistered();
        CellIdentityGsm id = ci.getCellIdentity();
        CellSignalStrengthGsm ss = ci.getCellSignalStrength();
        if (id != null) {
            r.area = id.getLac();
            r.cellId = id.getCid();
            r.arfcn = id.getArfcn();
            r.pci = id.getBsic();
        }
        if (ss != null) {
            r.rsrp = ss.getDbm();
            r.level = ss.getLevel();
        }
        return r;
    }
}
