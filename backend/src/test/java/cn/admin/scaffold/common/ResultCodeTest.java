package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultCodeTest {

    @Test
    void errorCodesAreUnique() {
        long unique = Arrays.stream(ResultCode.values())
                .map(ResultCode::getCode)
                .distinct()
                .count();
        assertEquals(ResultCode.values().length, unique);
    }
}
