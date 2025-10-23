package tw.fengqing.spring.springbucks.waiter.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 訂單監聽器 - 處理訂單完成消息
 * 使用 Spring Cloud Stream 函數式編程模型
 */
@Component
@Slf4j
public class OrderListener {

    /**
     * 處理訂單完成消息的函數式 Bean
     * 接收訂單 ID，通知客戶
     * @return 訂單完成處理函數
     */
    @Bean
    public Consumer<Long> finishedOrders() {
        return id -> {
            log.info("We've finished an order [{}].", id);
        };
    }
}