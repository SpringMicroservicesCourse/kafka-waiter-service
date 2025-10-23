package tw.fengqing.spring.springbucks.waiter.service;

import tw.fengqing.spring.springbucks.waiter.integration.Barista;
import tw.fengqing.spring.springbucks.waiter.model.Coffee;
import tw.fengqing.spring.springbucks.waiter.model.CoffeeOrder;
import tw.fengqing.spring.springbucks.waiter.model.OrderState;
import tw.fengqing.spring.springbucks.waiter.repository.CoffeeOrderRepository;
import tw.fengqing.spring.springbucks.waiter.support.OrderProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.joda.money.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
@Slf4j
public class CoffeeOrderService implements MeterBinder {
    @Value("${stream.bindings.new-orders-binding}")
    private String newOrdersBindingFromConfig;

    @Autowired
    private CoffeeOrderRepository orderRepository;
    @Autowired
    private OrderProperties orderProperties;
    @Autowired
    private StreamBridge streamBridge;

    private String waiterId = UUID.randomUUID().toString();

    private Counter orderCounter = null;

  public CoffeeOrder get(Long id) {
        // 查詢不存在ID或該筆訂單未完成交易時，會拋出 EntityNotFoundException 異常
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found, ID: " + id));
    }

    public CoffeeOrder createOrder(String customer, Coffee...coffee) {
        CoffeeOrder order = CoffeeOrder.builder()
                .customer(customer)
                .items(new ArrayList<>(Arrays.asList(coffee)))
                .discount(orderProperties.getDiscount())
                .total(calcTotal(coffee))
                .state(OrderState.INIT)
                .waiter(orderProperties.getWaiterPrefix() + waiterId)
                .build();
        CoffeeOrder saved = orderRepository.save(order);
        log.info("New Order: {}", saved);
        orderCounter.increment();
        return saved;
    }

    public boolean updateState(CoffeeOrder order, OrderState state) {
        if (order == null) {
            log.warn("Can not find order.");
            return false;
        }
        if (state.compareTo(order.getState()) <= 0) {
            log.warn("Wrong State order: {}, {}", state, order.getState());
            return false;
        }
        order.setState(state);
        orderRepository.save(order);
        log.info("Updated Order: {}", order);
        if (state == OrderState.PAID) {
            // 有返回值，如果要關注發送結果，則判斷返回值
            // 一般消息體不會這麼簡單
            streamBridge.send(newOrdersBindingFromConfig, order.getId());
            log.info("Sent order {} to barista for processing", order.getId());
        }
        return true;
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        this.orderCounter = meterRegistry.counter("order.count");
    }

    private Money calcTotal(Coffee...coffee) {
        List<Money> items = Stream.of(coffee).map(c -> c.getPrice())
                .collect(Collectors.toList());
        return Money.total(items).multipliedBy(orderProperties.getDiscount())
                .dividedBy(100, RoundingMode.HALF_UP);
    }
}
