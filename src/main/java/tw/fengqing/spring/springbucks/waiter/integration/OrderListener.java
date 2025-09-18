package tw.fengqing.spring.springbucks.waiter.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 訂單監聽器 - 處理完成訂單通知
 * 使用 Spring Cloud Stream 函數式編程模型
 */
@Component
@Slf4j
public class OrderListener {

    /**
     * 處理完成訂單的函數式 Bean
     * 接收完成訂單 ID 並記錄日誌
     * @return 完成訂單處理函數
     */
    @Bean
    public Consumer<Long> finishedOrders() {
        return id -> {
            log.info("We've finished an order [{}].", id);
        };
    }
}