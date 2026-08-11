package com.example.admin.module.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangeRequestParserTest {

    private static final long TOTAL = 1000;

    @Test
    void parsesClosedRange() {
        RangeRequestParser.ByteRange range = RangeRequestParser.parse("bytes=0-499", TOTAL);
        assertThat(range).isNotNull();
        assertThat(range.start()).isEqualTo(0);
        assertThat(range.end()).isEqualTo(499);
        assertThat(range.length()).isEqualTo(500);
    }

    @Test
    void parsesOpenEndedRange() {
        RangeRequestParser.ByteRange range = RangeRequestParser.parse("bytes=500-", TOTAL);
        assertThat(range.start()).isEqualTo(500);
        assertThat(range.end()).isEqualTo(999);
        assertThat(range.length()).isEqualTo(500);
    }

    @Test
    void parsesSuffixRange() {
        RangeRequestParser.ByteRange range = RangeRequestParser.parse("bytes=-100", TOTAL);
        assertThat(range.start()).isEqualTo(900);
        assertThat(range.end()).isEqualTo(999);
        assertThat(range.length()).isEqualTo(100);
    }

    @Test
    void clampsEndBeyondTotal() {
        RangeRequestParser.ByteRange range = RangeRequestParser.parse("bytes=0-9999", TOTAL);
        assertThat(range.end()).isEqualTo(999);
        assertThat(range.length()).isEqualTo(1000);
    }

    @Test
    void returnsNullWithoutRangeHeader() {
        assertThat(RangeRequestParser.parse(null, TOTAL)).isNull();
        assertThat(RangeRequestParser.parse("items=0-1", TOTAL)).isNull();
    }

    @Test
    void returnsNullWhenStartOutOfBounds() {
        assertThat(RangeRequestParser.parse("bytes=1000-", TOTAL)).isNull();
    }

    @Test
    void returnsNullWhenRangeReversed() {
        assertThat(RangeRequestParser.parse("bytes=500-100", TOTAL)).isNull();
    }

    @Test
    void returnsNullForMalformed() {
        assertThat(RangeRequestParser.parse("bytes=-0", TOTAL)).isNull();
        assertThat(RangeRequestParser.parse("bytes=abc", TOTAL)).isNull();
    }

    @Test
    void requestedDetectsBytesPrefix() {
        assertThat(RangeRequestParser.requested("bytes=0-1")).isTrue();
        assertThat(RangeRequestParser.requested("items=0-1")).isFalse();
        assertThat(RangeRequestParser.requested(null)).isFalse();
    }
}
