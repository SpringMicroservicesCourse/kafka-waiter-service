package tw.fengqing.spring.springbucks.waiter.integration;

/**
 * Spring Cloud Stream 函數式編程模型配置
 * 在 Spring Cloud Stream 4.x 中，不再需要 @Input 和 @Output 註解
 * 改為使用函數式編程模型，通過 application.properties 配置綁定
 */
public interface Barista {
    String NEW_ORDERS = "newOrders";
    String FINISHED_ORDERS = "finishedOrders";
}
