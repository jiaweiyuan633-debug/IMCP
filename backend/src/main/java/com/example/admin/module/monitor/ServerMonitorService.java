package com.example.admin.module.monitor;

import com.example.admin.module.monitor.vo.ServerMonitorVo;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServerMonitorService {

    private static final long CACHE_MS = 5000L;

    private volatile ServerMonitorVo cached;
    private volatile long cachedAt;

    public synchronized ServerMonitorVo get() {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < CACHE_MS) {
            return cached;
        }
        cached = collect();
        cachedAt = now;
        return cached;
    }

    private ServerMonitorVo collect() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();

        long memTotal = osBean.getTotalMemorySize();
        long memUsed = memTotal - osBean.getFreeMemorySize();
        long jvmMax = heap.getMax();
        long jvmUsed = heap.getUsed();

        String hostName = "unknown";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            // keep unknown
        }

        double cpu = osBean.getCpuLoad();
        if (cpu < 0) {
            double load = Math.max(osBean.getSystemLoadAverage(), 0);
            cpu = load / Math.max(osBean.getAvailableProcessors(), 1);
        }
        double cpuPercent = Math.round(Math.min(Math.max(cpu, 0), 1) * 10000.0) / 100.0;

        return ServerMonitorVo.builder()
                .osName(osBean.getName())
                .osArch(osBean.getArch())
                .hostName(hostName)
                .cpuCores(osBean.getAvailableProcessors())
                .cpuLoad(cpuPercent)
                .memTotal(memTotal)
                .memUsed(memUsed)
                .memUsagePercent(percent(memUsed, memTotal))
                .jvmMax(jvmMax)
                .jvmUsed(jvmUsed)
                .jvmUsagePercent(percent(jvmUsed, jvmMax))
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .disks(diskInfos())
                .build();
    }

    private List<ServerMonitorVo.DiskInfo> diskInfos() {
        List<ServerMonitorVo.DiskInfo> disks = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return disks;
        }
        for (File root : roots) {
            long total = root.getTotalSpace();
            long free = root.getUsableSpace();
            long used = total - free;
            disks.add(ServerMonitorVo.DiskInfo.builder()
                    .name(root.getPath())
                    .total(total)
                    .used(used)
                    .usagePercent(percent(used, total))
                    .build());
        }
        return disks;
    }

    private double percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(used * 10000.0 / total) / 100.0;
    }
}

