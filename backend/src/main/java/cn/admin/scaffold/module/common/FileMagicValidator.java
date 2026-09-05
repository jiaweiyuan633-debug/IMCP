package cn.admin.scaffold.module.common;

public final class FileMagicValidator {

    private FileMagicValidator() {
    }

    public static boolean isAllowedContent(byte[] head, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "png" -> head.length >= 8
                    && (head[0] & 0xFF) == 0x89
                    && head[1] == 'P'
                    && head[2] == 'N'
                    && head[3] == 'G';
            case "gif" -> head.length >= 4
                    && head[0] == 'G'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == '8';
            case "webp" -> head.length >= 12
                    && head[0] == 'R'
                    && head[1] == 'I'
                    && head[2] == 'F'
                    && head[3] == 'F'
                    && head[8] == 'W'
                    && head[9] == 'E'
                    && head[10] == 'B'
                    && head[11] == 'P';
            case "pdf" -> head.length >= 4
                    && head[0] == '%'
                    && head[1] == 'P'
                    && head[2] == 'D'
                    && head[3] == 'F';
            case "doc", "xls", "ppt" -> head.length >= 8
                    && (head[0] & 0xFF) == 0xD0
                    && (head[1] & 0xFF) == 0xCF
                    && (head[2] & 0xFF) == 0x11
                    && (head[3] & 0xFF) == 0xE0;
            case "docx", "xlsx", "pptx", "zip", "rar", "7z" -> head.length >= 2
                    && head[0] == 'P'
                    && head[1] == 'K';
            default -> true;
        };
    }
}
