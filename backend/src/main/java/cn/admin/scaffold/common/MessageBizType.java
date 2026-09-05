package cn.admin.scaffold.common;

/**
 * 消息业务类型常量，对应消息中心的 bizType，供业务跳转与消息生产者统一使用。
 */
public final class MessageBizType {

    private MessageBizType() {
    }

    public static final String WORKFLOW = "workflow";
    public static final String FILE = "file";
    public static final String AI = "ai";
}
