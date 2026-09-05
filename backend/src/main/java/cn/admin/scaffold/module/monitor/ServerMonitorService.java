package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.module.monitor.vo.ServerMonitorVo;
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
        ServerMonitorVo snapshot;
        if (cached != null && now - cachedAt < CACHE_MS) {
            snapshot = cached;
        } else {
            snapshot = collect();
            cached = snapshot;
            cachedAt = now;
        }
        // 返回深拷贝快照：调用方（监控页轮询 / 告警判定）仅应消费不可变观测数据，
        // 不持有 5s 缓存实例的引用，杜绝通过返回值改写共享缓存。
        return defensiveCopy(snapshot);
    }

    private ServerMonitorVo defensiveCopy(ServerMonitorVo source) {
        List<ServerMonitorVo.DiskInfo> disks = null;
        if (source.getDisks() != null) {
            disks = new ArrayList<>(source.getDisks().size());
            for (ServerMonitorVo.DiskInfo disk : source.getDisks()) {
                disks.add(ServerMonitorVo.DiskInfo.builder()
                        .name(disk.getName())
                        .total(disk.getTotal())
                        .used(disk.getUsed())
                        .usagePercent(disk.getUsagePercent())
                        .build());
            }
        }
        return ServerMonitorVo.builder()
                .osName(source.getOsName())
                .osArch(source.getOsArch())
                .hostName(source.getHostName())
                .cpuCores(source.getCpuCores())
                .cpuLoad(source.getCpuLoad())
                .memTotal(source.getMemTotal())
                .memUsed(source.getMemUsed())
                .memUsagePercent(source.getMemUsagePercent())
                .jvmMax(source.getJvmMax())
                .jvmUsed(source.getJvmUsed())
                .jvmUsagePercent(source.getJvmUsagePercent())
                .uptimeSeconds(source.getUptimeSeconds())
                .disks(disks)
                .build();
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

