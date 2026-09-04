package cn.admin.scaffold.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServerMonitorVo {

    private String osName;
    private String osArch;
    private String hostName;
    private int cpuCores;
    private double cpuLoad;
    private long memTotal;
    private long memUsed;
    private double memUsagePercent;
    private long jvmMax;
    private long jvmUsed;
    private double jvmUsagePercent;
    private long uptimeSeconds;
    private List<DiskInfo> disks;

    @Data
    @Builder
    public static class DiskInfo {
        private String name;
        private long total;
        private long used;
        private double usagePercent;
    }
}

