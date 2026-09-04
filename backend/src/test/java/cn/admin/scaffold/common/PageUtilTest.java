package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存分页越界钳制（R4-1.39）：pageNum=0/负数或 pageSize≤0 时旧逻辑 (pageNum-1)*pageSize
 * 为负/越界，subList 抛 IndexOutOfBoundsException 落 500；PageUtil 钳制后归位安全区间。
 */
class PageUtilTest {

    @Test
    void zeroBasedPageClampsToFirstPage() {
        // pageNum=0 旧逻辑 (0-1)*10 为负 → subList 越界 500；钳制后归位首页
        assertThat(PageUtil.fromIndex(0, 10, 25)).isEqualTo(0);
        assertThat(PageUtil.toIndex(0, 10, 25)).isEqualTo(10);
    }

    @Test
    void negativePageNumClampedToFirstPage() {
        assertThat(PageUtil.fromIndex(-3, 10, 25)).isZero();
        assertThat(PageUtil.toIndex(-3, 10, 25)).isEqualTo(10);
    }

    @Test
    void nonPositivePageSizeClampedToOne() {
        assertThat(PageUtil.fromIndex(1, -5, 10)).isZero();
        assertThat(PageUtil.toIndex(1, 0, 10)).isEqualTo(1);
    }

    @Test
    void normalPageSlidesWindowAndClampsTail() {
        assertThat(PageUtil.fromIndex(3, 10, 25)).isEqualTo(20);
        assertThat(PageUtil.toIndex(3, 10, 25)).isEqualTo(25); // 末尾越界收拢到 total
    }

    @Test
    void emptyCollectionStaysAtZero() {
        assertThat(PageUtil.fromIndex(2, 10, 0)).isZero();
        assertThat(PageUtil.toIndex(2, 10, 0)).isZero();
    }
}
