package com.celltrace.logger;

import java.util.ArrayList;
import java.util.List;

/** 一次完整采样的结果（服务小区 + 邻区 + 会话统计） */
public class Snapshot {

    public CellRecord primary;
    public List<CellRecord> neighbors = new ArrayList<>();

    public boolean recording;
    public long rows;
    public long startMs;
    public String csvPath;
    public int intervalMs = 1000;
    public String note = "";

    public int peek() {
        if (primary == null || primary.rsrp == CellRecord.INVALID) return CellRecord.INVALID;
        return primary.rsrp;
    }
}
