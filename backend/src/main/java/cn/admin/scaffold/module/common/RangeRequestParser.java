package cn.admin.scaffold.module.common;

/**
 * HTTP Range 请求头解析（批次2c）。支持三种形式：
 * bytes=start-end、bytes=start-（至末尾）、bytes=-suffix（末尾 N 字节）。
 * 仅处理单段 Range；多段或多段语法返回 null，由调用方按无效范围处理。
 */
public final class RangeRequestParser {

    private RangeRequestParser() {
    }

    /** 命中的字节区间（闭区间）。 */
    public record ByteRange(long start, long end) {

        public long length() {
            return end - start + 1;
        }
    }

    /** 是否携带 bytes= 前缀的 Range 头（用于区分"无范围"与"范围无效"）。 */
    public static boolean requested(String rangeHeader) {
        return rangeHeader != null && rangeHeader.startsWith("bytes=");
    }

    /**
     * 解析 Range 头。返回 null 表示：未携带 Range 头、语法无效、或 start 越界（此时调用方按 416 处理）。
     */
    public static ByteRange parse(String rangeHeader, long total) {
        if (!requested(rangeHeader) || total <= 0) {
            return null;
        }
        String spec = rangeHeader.substring("bytes=".length()).split(",")[0].trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            if (startPart.isEmpty()) {
                // suffix 形式：bytes=-N 表示最后 N 字节
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                long start = Math.max(0, total - suffix);
                return new ByteRange(start, total - 1);
            }
            long start = Long.parseLong(startPart);
            if (start < 0 || start >= total) {
                return null;
            }
            long end = endPart.isEmpty() ? total - 1 : Long.parseLong(endPart);
            end = Math.min(end, total - 1);
            if (end < start) {
                return null;
            }
            return new ByteRange(start, end);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
