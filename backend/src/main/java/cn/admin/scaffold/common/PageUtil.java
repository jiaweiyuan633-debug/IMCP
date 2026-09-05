package cn.admin.scaffold.common;

/**
 * 内存分页越界钳制：pageNum/pageSize 直接来自客户端查询参数，pageNum=0/负数时
 * (pageNum-1)*pageSize 为负，subList 负下标抛 IndexOutOfBoundsException 落 500（DoS 面）。
 * 统一钳制 pageNum≥1、pageSize≥1 后计算 from/to，与 MyBatis-Plus {@code PaginationInnerInterceptor
 * .setMaxLimit} 的 DB 分页上界配合，堵住分页参数的越界 500 与全表分页。
 */
public final class PageUtil {

    private PageUtil() {
    }

    /** 内存分页起始下标（含 0）：pageNum=0/负数归位到首页，pageSize≤0 按 1 处理。 */
    public static int fromIndex(long pageNum, long pageSize, int total) {
        long safeSize = Math.max(pageSize, 1);
        long safeFrom = Math.max(pageNum - 1, 0) * safeSize;
        return (int) Math.min(safeFrom, Math.max(total, 0));
    }

    /** 内存分页结束下标（不含）：基于钳制后的 from 前进 safeSize，越界收拢到 total。 */
    public static int toIndex(long pageNum, long pageSize, int total) {
        long safeSize = Math.max(pageSize, 1);
        long safeTo = (long) fromIndex(pageNum, pageSize, total) + safeSize;
        return (int) Math.min(safeTo, Math.max(total, 0));
    }
}
