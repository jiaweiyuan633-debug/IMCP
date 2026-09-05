package cn.admin.scaffold.module.monitor.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component("demoTask")
public class DemoTask {

    public void runDemo() {
        log.info("Demo task executed at {}", LocalDateTime.now());
    }
}

