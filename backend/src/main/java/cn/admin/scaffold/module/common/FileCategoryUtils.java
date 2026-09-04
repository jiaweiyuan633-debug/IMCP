package cn.admin.scaffold.module.common;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileCategoryUtils {

    public static final String IMAGE = "image";
    public static final String PDF = "pdf";
    public static final String OFFICE = "office";
    public static final String ARCHIVE = "archive";
    public static final String TEXT = "text";
    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String OTHER = "other";

    private static final Map<String, String> EXTENSION_CATEGORY = Map.ofEntries(
            Map.entry("jpg", IMAGE), Map.entry("jpeg", IMAGE), Map.entry("png", IMAGE),
            Map.entry("gif", IMAGE), Map.entry("webp", IMAGE), Map.entry("bmp", IMAGE),
            Map.entry("svg", IMAGE), Map.entry("ico", IMAGE),
            Map.entry("pdf", PDF),
            Map.entry("doc", OFFICE), Map.entry("docx", OFFICE), Map.entry("xls", OFFICE),
            Map.entry("xlsx", OFFICE), Map.entry("ppt", OFFICE), Map.entry("pptx", OFFICE),
            Map.entry("csv", OFFICE), Map.entry("ods", OFFICE),
            Map.entry("zip", ARCHIVE), Map.entry("rar", ARCHIVE), Map.entry("7z", ARCHIVE),
            Map.entry("tar", ARCHIVE), Map.entry("gz", ARCHIVE),
            Map.entry("txt", TEXT), Map.entry("md", TEXT), Map.entry("log", TEXT),
            Map.entry("json", TEXT), Map.entry("xml", TEXT), Map.entry("yml", TEXT),
            Map.entry("yaml", TEXT), Map.entry("properties", TEXT),
            Map.entry("mp3", AUDIO), Map.entry("wav", AUDIO), Map.entry("m4a", AUDIO),
            Map.entry("ogg", AUDIO),
            Map.entry("mp4", VIDEO), Map.entry("mkv", VIDEO), Map.entry("avi", VIDEO),
            Map.entry("mov", VIDEO), Map.entry("webm", VIDEO));

    private static final Set<String> KNOWN_CATEGORIES = Set.of(
            IMAGE, PDF, OFFICE, ARCHIVE, TEXT, AUDIO, VIDEO, OTHER);

    private FileCategoryUtils() {
    }

    public static String detect(String extension, String contentType) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (EXTENSION_CATEGORY.containsKey(ext)) {
            return EXTENSION_CATEGORY.get(ext);
        }
        if (StringUtils.hasText(contentType)) {
            String type = contentType.toLowerCase(Locale.ROOT);
            if (type.startsWith("image/")) {
                return IMAGE;
            }
            if (type.startsWith("video/")) {
                return VIDEO;
            }
            if (type.startsWith("audio/")) {
                return AUDIO;
            }
            if (type.equals("application/pdf")) {
                return PDF;
            }
            if (type.contains("officedocument") || type.contains("msword")
                    || type.contains("ms-excel") || type.contains("ms-powerpoint")) {
                return OFFICE;
            }
            if (type.contains("zip") || type.contains("compressed")) {
                return ARCHIVE;
            }
            if (type.startsWith("text/") || type.contains("json") || type.contains("xml")) {
                return TEXT;
            }
        }
        return OTHER;
    }

    public static boolean isKnown(String category) {
        return KNOWN_CATEGORIES.contains(category == null ? null : category.toLowerCase(Locale.ROOT));
    }
}
