package cn.admin.scaffold.common.outbox;

/**
 * 发件箱事件处理器：按 {@link #topic()} 路由 {@link OutboxDispatcher} 投递的负载。
 *
 * <p>实现类应对单条 payload 具备幂等性（同一负载重复投递不产生副作用），
 * 因为重试（spring-retry）与失败回投（指数退避）都可能重复调用。
 * 返回 true 表示投递成功（进入终态）；抛异常或返回 false 表示失败（Dispatcher 记一次重试）。
 */
public interface OutboxHandler {

    /** 事件主题，与 {@link OutboxPublisher#publish} 传入的 topic 一一对应。 */
    String topic();

    /** 投递一条负载。返回 true=成功；抛异常/false=失败待重试。 */
    boolean handle(String payload);
}
