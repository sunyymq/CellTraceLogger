# CellTraceLogger

蜂窝信号路测记录 App（Android 7.0+，零第三方依赖，纯 Framework API）。

## 功能

- 实时显示服务小区：制式（NR / LTE / WCDMA / GSM）、运营商、PCI、TAC/LAC、CID/NCI、ARFCN、频段
- 信号指标：RSRP / RSRQ / SINR / RSSI + 5 格信号条 + RSRP 实时趋势曲线
- 邻区列表（最多 12 条，含制式 / 频段 / PCI / RSRP）
- 定位信息：经纬度、速度、海拔、定位精度、参与定位卫星数（GNSS）
- 前台服务持续采样，息屏后台记录，通知栏显示实时状态
- 一键导出 CSV（分享到微信 / 邮箱 / 网盘），UTF-8 BOM，Excel / WPS 直接打开不乱码

## CSV 字段

```
epoch_ms, time, registered, slot, rat, carrier, mcc, mnc, tac, cid, pci, arfcn, band,
rsrp_dbm, rsrq_db, sinr_db, rssi_dbm, level, lat, lon, alt_m, speed_mps, acc_m, sats,
neighbor_count, neighbors
```

`neighbors` 形如 `LTE/123/-95;NR/456/-102`。

记录文件位置：`Android/data/com.celltrace.logger/files/traces/trace_YYYYMMDD_HHMMSS.csv`

## 构建

```bash
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或推到 GitHub 后由 Actions 自动构建：`.github/workflows/build.yml`（Gradle 8.7 + JDK 17 + AGP 8.5.2），
产物同时上传 artifact 并附到 Release。

## 说明

- 「频段」在系统未直接下发时按 EARFCN / NR-ARFCN 区间估算（3GPP 36.101 / 38.101），边界可能有个位数误差；API 30+ 优先取系统 `getBands()`。
- Android 要求定位权限才能读取小区信息，首次启动必须授予「位置信息」权限。
- 部分机型/网络不下发邻区列表，此时邻区为空属正常现象。
- APK 为 debug 签名，适合自用侧载；如需上架请自行生成正式签名。
