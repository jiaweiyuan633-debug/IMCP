package cn.admin.scaffold.config;

import org.dromara.warm.plugin.modes.sb.config.WarmFlowProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * warm-flow 兼容配置（Spring Boot 3.5.12 起需要）。
 *
 * <p>背景：warm-flow 的 {@code SpringUtil} 以「static + 首次赋值后不再更新」持有
 * {@code ApplicationContext}（其 {@code setApplicationContext} 有 {@code if (applicationContext == null)}
 * 守卫），而 {@code BeanConfig.initFlow()} 通过该静态上下文反查 {@code WarmFlowProperties}。多上下文
 * 同 JVM 时（本工程 9 个 *IT 在 Boot 3.5 测试装配下各自独立 Spring 上下文，不再像 3.4.x 那样共享同一
 * 上下文），后创建上下文的 initFlow 会解析到首个已加载上下文——那里既缓存了运行时类型恰为
 * {@code WarmFlowProperties} 的 initFlow 单例，又有 {@code @EnableConfigurationProperties} 注册的
 * {@code warm-flow-org...WarmFlowProperties} 定义，两候选同时命中导致 {@code getBean(WarmFlowProperties)}
 * 抛 {@code NoUniqueBeanDefinitionException}，ApplicationContext 加载失败。
 *
 * <p>修复：声明一个 {@code @Primary WarmFlowProperties}——{@code getBean(Class)} 按 {@code @Primary}
 * 消歧（跨上下文同 JVM 亦然），任意上下文数量均可加载；该实例经 ConfigurationProperties 绑定机制
 * 落入同一 {@code warm-flow.*} 前缀，与 warm-flow 自注册的属性 bean 语义一致。
 */
@Configuration
public class WarmFlowConfig {

    @Bean
    @Primary
    public WarmFlowProperties warmFlowProperties() {
        return new WarmFlowProperties();
    }
}
