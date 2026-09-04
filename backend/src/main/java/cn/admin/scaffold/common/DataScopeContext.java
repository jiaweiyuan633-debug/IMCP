package cn.admin.scaffold.common;

import java.util.List;
import java.util.Set;

public final class DataScopeContext {

    private static final ThreadLocal<Filter> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(Filter filter) {
        HOLDER.set(filter);
    }

    public static Filter get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record Filter(
            List<Long> userIds,
            List<String> usernames,
            Set<String> tables,
            boolean empty) {

        public boolean active() {
            return tables != null && !tables.isEmpty();
        }
    }
}
