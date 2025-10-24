# kafka-waiter-service

> SpringBucks waiter service with Spring Cloud Stream Kafka integration and Resilience4j rate limiting

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A comprehensive microservice demonstrating Spring Cloud Stream with Apache Kafka for event-driven architecture, featuring coffee order management, rate limiting, and service discovery.

## Features

- **Event-Driven Architecture** with Spring Cloud Stream
- **Apache Kafka Integration** for message brokering
- **Functional Programming Model** (Consumer/Supplier pattern)
- **Rate Limiting** with Resilience4j
- **Service Discovery** with Consul
- **REST API** for coffee and order management
- **JPA Persistence** with MariaDB
- **Money Handling** with Joda Money
- **Health Monitoring** with Spring Boot Actuator
- **Containerized Deployment** with Docker Compose

## Tech Stack

- **Spring Boot** 3.4.5
- **Spring Cloud Stream** 2024.0.2
- **Apache Kafka** (KRaft mode, no ZooKeeper)
- **Spring Data JPA** for persistence
- **Resilience4j** for rate limiting
- **Consul** for service discovery
- **MariaDB** database
- **Joda Money** 2.0.2
- **Lombok** for boilerplate reduction
- **Maven** 3.8+

## Getting Started

### Prerequisites

- **JDK 21** or higher
- **Maven 3.8+** (or use included Maven Wrapper)
- **Docker & Docker Compose** for infrastructure
- **MariaDB** (or use Docker container)

### Installation & Run

```bash
# Clone the repository
git clone https://github.com/SpringMicroservicesCourse/spring-cloud-stream-kafka
cd kafka-waiter-service

# Start infrastructure (Kafka, MariaDB, Consul)
docker-compose up -d
docker run -d --name mariadb \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=springbucks \
  -e MYSQL_USER=springbucks \
  -e MYSQL_PASSWORD=springbucks \
  -p 3306:3306 mariadb:latest
docker run -d --name=consul -p 8500:8500 -p 8600:8600/udp \
  consul:1.4.5 agent -server -ui -bootstrap-expect=1 -client=0.0.0.0

# Run the application
./mvnw spring-boot:run
```

### Alternative: Run as JAR

```bash
# Build
./mvnw clean package

# Run
java -jar target/kafka-waiter-service-0.0.1-SNAPSHOT.jar
```

## Configuration

### Application Properties

```properties
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:mariadb://localhost:3306/springbucks
spring.datasource.username=springbucks
spring.datasource.password=springbucks

# Kafka Binder Configuration
spring.cloud.stream.kafka.binder.brokers=localhost
spring.cloud.stream.kafka.binder.defaultBrokerPort=9092

# Functional Programming Model
spring.cloud.function.definition=finishedOrders

# Input Binding (Receive finished order notifications)
spring.cloud.stream.bindings.finishedOrders-in-0.destination=finishedOrders
spring.cloud.stream.bindings.finishedOrders-in-0.group=waiter-service

# Output Binding (Send new orders to barista)
spring.cloud.stream.bindings.newOrders-out-0.destination=newOrders

# Rate Limiter Configuration
resilience4j.ratelimiter.instances.order.limit-for-period=3
resilience4j.ratelimiter.instances.order.limit-refresh-period=30s
resilience4j.ratelimiter.instances.order.timeout-duration=1s
```

### Configuration Highlights

| Property | Value | Description |
|----------|-------|-------------|
| `spring.cloud.stream.kafka.binder.brokers` | localhost | Kafka broker address |
| `spring.cloud.function.definition` | finishedOrders | Function beans to bind |
| `limit-for-period` | 3 | Max 3 requests per period |
| `limit-refresh-period` | 30s | Refresh period duration |

### Consul Discovery

Access Consul UI at: `http://localhost:8500`

- Service Name: `waiter-service`
- Port: 8080
- Health Check: `/actuator/health`

## API Endpoints

### Coffee Management

| Method | Path | Description | Example |
|--------|------|-------------|---------|
| GET | `/coffee/` | Get all coffee menu | `curl http://localhost:8080/coffee/` |
| GET | `/coffee/{id}` | Get coffee by ID | `curl http://localhost:8080/coffee/1` |
| GET | `/coffee/?name={name}` | Get coffee by name | `curl http://localhost:8080/coffee/?name=latte` |
| POST | `/coffee/` | Add new coffee | See below |

**Add Coffee Example:**
```bash
curl -X POST http://localhost:8080/coffee/ \
  -H "Content-Type: application/json" \
  -d '{"name":"americano","price":120.00}'
```

### Order Management

| Method | Path | Description | Example |
|--------|------|-------------|---------|
| GET | `/order/{id}` | Get order by ID | `curl http://localhost:8080/order/1` |
| POST | `/order/` | Create new order | See below |
| PUT | `/order/{id}` | Update order state | See below |

**Create Order Example:**
```bash
curl -X POST http://localhost:8080/order/ \
  -H "Content-Type: application/json" \
  -d '{"customer":"Ray Chu","items":["latte","espresso"]}'
```

**Update Order State Example:**
```bash
curl -X PUT http://localhost:8080/order/1 \
  -H "Content-Type: application/json" \
  -d '{"state":"PAID"}'
```

## Key Components

### 1. Order Event Listener

**File:** `integration/OrderListener.java`

```java
@Component
@Slf4j
public class OrderListener {
    
    /**
     * Functional bean to process finished order messages
     * Receives order ID and logs completion
     */
    @Bean
    public Consumer<Long> finishedOrders() {
        return id -> {
            log.info("We've finished an order [{}].", id);
        };
    }
}
```

**Key Features:**
- Uses Spring Cloud Stream functional programming model
- Automatically binds to Kafka topic via configuration
- Stateless message consumption

### 2. Order Service with StreamBridge

**File:** `service/CoffeeOrderService.java`

```java
@Service
@Transactional
public class CoffeeOrderService {
    @Value("${stream.bindings.new-orders-binding}")
    private String newOrdersBindingFromConfig;
    
    @Autowired
    private StreamBridge streamBridge;
    
    public boolean updateState(CoffeeOrder order, OrderState state) {
        // ... state validation ...
        order.setState(state);
        orderRepository.save(order);
        
        if (state == OrderState.PAID) {
            // Dynamic message sending with StreamBridge
            streamBridge.send(newOrdersBindingFromConfig, order.getId());
            log.info("Sent order {} to barista for processing", order.getId());
        }
        return true;
    }
}
```

**Why StreamBridge?**
- ✅ **Dynamic Sending**: No need to pre-define output channels
- ✅ **Type-Safe**: Compile-time checking for binding names
- ✅ **Flexible**: Supports multiple output destinations

### 3. Rate Limiting with Resilience4j

**Annotation-Based (Controller):**
```java
@RestController
@RequestMapping("/order")
public class CoffeeOrderController {
    
    @PostMapping("/")
    @RateLimiter(name = "order")  // ← Declarative rate limiting
    public CoffeeOrder create(@RequestBody NewOrderRequest newOrder) {
        // ... order creation logic ...
    }
}
```

**Programmatic (Service):**
```java
@GetMapping("/{id}")
public CoffeeOrder getOrder(@PathVariable("id") Long id) {
    CoffeeOrder order = null;
    try {
        order = rateLimiter.executeSupplier(() -> orderService.get(id));
    } catch(RequestNotPermitted e) {
        log.warn("Request Not Permitted! {}", e.getMessage());
    }
    return order;
}
```

## Docker Infrastructure

### Kafka Container (KRaft Mode)

```yaml
services:
  kafka-spring-course:
    image: confluentinc/cp-kafka:latest
    container_name: kafka-spring-course
    hostname: kafka  # ⚠️ Required for internal communication
    ports:
      - 9092:9092
    environment:
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:29093'
      # ... see docker-compose.yml for full configuration
```

**Why hostname is critical:**
- Kafka uses `kafka:29092` for internal broker communication
- Without `hostname: kafka`, container cannot resolve the address
- Results in `UnresolvedAddressException` on startup

### Verify Kafka Topics

```bash
# Enter Kafka container
docker exec -it kafka-spring-course /bin/bash

# List all topics
kafka-topics --bootstrap-server localhost:9092 --list

# Expected output:
newOrders          # Waiter → Barista
finishedOrders     # Barista → Waiter
```

## Monitoring & Observability

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "binders": {
      "status": "UP",
      "details": {
        "kafka": {
          "status": "UP"
        }
      }
    }
  }
}
```

### Rate Limiter Metrics

```bash
# Check available permissions
curl http://localhost:8080/actuator/metrics/resilience4j.ratelimiter.available.permissions

# Check waiting threads
curl http://localhost:8080/actuator/metrics/resilience4j.ratelimiter.waiting_threads
```

### Stream Bindings

```bash
curl http://localhost:8080/actuator/bindings
```

**Expected Response:**
```json
{
  "finishedOrders-in-0": {
    "group": "waiter-service",
    "bindingName": "finishedOrders-in-0"
  },
  "newOrders-out-0": {
    "bindingName": "newOrders-out-0"
  }
}
```

## Order State Flow

```
INIT → PAID → BREWING → BREWED → TAKEN
  ↓      ↓                          ↓
Created  Sent to Kafka    Customer picks up
         (newOrders)
```

## Best Practices Demonstrated

1. **Event-Driven Architecture**: Loose coupling between services via Kafka
2. **Functional Programming**: Clean, testable message handlers
3. **Rate Limiting**: Protect endpoints from overload
4. **Service Discovery**: Dynamic service location via Consul
5. **Money Precision**: Use Joda Money for financial calculations
6. **Transaction Management**: Ensure data consistency with `@Transactional`
7. **Health Monitoring**: Comprehensive actuator endpoints

## Development vs Production

### Development (Current Configuration)

```properties
# Show SQL for debugging
spring.jpa.properties.hibernate.show_sql=true
spring.jpa.properties.hibernate.format_sql=true

# Expose all actuator endpoints
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
```

### Production (Recommended)

```properties
# Hide SQL statements
spring.jpa.properties.hibernate.show_sql=false

# Limit actuator exposure
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=when-authorized

# Kafka producer reliability
spring.cloud.stream.kafka.binder.producer-properties.acks=all
spring.cloud.stream.kafka.binder.producer-properties.compression.type=snappy
```

## Testing

```bash
# Run unit tests
./mvnw test

# Integration test
./mvnw verify
```

## Troubleshooting

### Kafka Connection Issues

**Symptom:** `UnresolvedAddressException`
**Solution:** Ensure `hostname: kafka` is set in `docker-compose.yml`

### Rate Limiter Not Working

**Check:**
1. ✅ Resilience4j dependency is included
2. ✅ Configuration uses correct property names (2.x syntax)
3. ✅ `@EnableAspectJAutoProxy` is present (for annotation-based)

### Database Connection Failed

**Check:**
1. ✅ MariaDB container is running: `docker ps | grep mariadb`
2. ✅ Database credentials match configuration
3. ✅ Port 3306 is not in use by other services

## Extended Practice

**Suggested Enhancements:**

1. Add dead letter queue (DLQ) for failed messages
2. Implement order cancellation workflow
3. Add Prometheus metrics for monitoring
4. Create admin dashboard with WebSocket updates
5. Implement multi-instance deployment
6. Add integration tests with Testcontainers
7. Implement SAGA pattern for distributed transactions

## References

- [Spring Cloud Stream Documentation](https://spring.io/projects/spring-cloud-stream)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Spring Data JPA Documentation](https://docs.spring.io/spring-data/jpa/)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## About Us

我們主要專注在敏捷專案管理、物聯網（IoT）應用開發和領域驅動設計（DDD）。喜歡把先進技術和實務經驗結合，打造好用又靈活的軟體解決方案。近來也積極結合 AI 技術，推動自動化工作流，讓開發與運維更有效率、更智慧。持續學習與分享，希望能一起推動軟體開發的創新和進步。

## Contact

**風清雲談** - 專注於敏捷專案管理、物聯網（IoT）應用開發和領域驅動設計（DDD）。

- 🌐 官方網站：[風清雲談部落格](https://blog.fengqing.tw/)
- 📘 Facebook：[風清雲談粉絲頁](https://www.facebook.com/profile.php?id=61576838896062)
- 💼 LinkedIn：[Chu Kuo-Lung](https://www.linkedin.com/in/chu-kuo-lung)
- 📺 YouTube：[雲談風清頻道](https://www.youtube.com/channel/UCXDqLTdCMiCJ1j8xGRfwEig)
- 📧 Email：[fengqing.tw@gmail.com](mailto:fengqing.tw@gmail.com)

---

**⭐ 如果這個專案對您有幫助，歡迎給個 Star！**
