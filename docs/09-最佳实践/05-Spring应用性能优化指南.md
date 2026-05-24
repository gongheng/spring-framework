# Spring应用性能优化指南

## 🎯 核心原则

Spring应用性能优化应该从**启动性能**和**运行性能**两个维度进行优化。

---

## 📋 目录

1. [启动性能优化](#1-启动性能优化)
2. [运行性能优化](#2-运行性能优化)
3. [内存优化](#3-内存优化)
4. [数据库性能优化](#4-数据库性能优化)
5. [监控和诊断](#5-监控和诊断)

---

## 1️⃣ 启动性能优化

### ✅ 优化1：延迟初始化

```java
// ❌ 不推荐：所有Bean都在启动时加载
@Component
public class ExpensiveService {
    public ExpensiveService() {
        // 耗时操作
        Thread.sleep(1000);
    }
}

// ✅ 推荐：使用@Lazy延迟初始化
@Component
@Lazy
public class ExpensiveService {
    public ExpensiveService() {
        // 第一次使用时才初始化
    }
}
```

**全局配置**：
```properties
# application.properties
spring.main.lazy-initialization=true
```

**效果**：
- 启动速度提升30%-50%
- 内存占用减少
- 首次访问时会有轻微延迟

---

### ✅ 优化2：排除不需要的自动配置

```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,  // 不需要数据库
    RedisAutoConfiguration.class,       // 不需要Redis
    MongoAutoConfiguration.class        // 不需要MongoDB
})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**效果**：
- 减少不必要的Bean初始化
- 启动速度提升10%-20%

---

### ✅ 优化3：减少Bean扫描范围

```java
// ❌ 不推荐：扫描整个项目
@SpringBootApplication(scanBasePackages = "com.example")

// ✅ 推荐：只扫描核心包
@SpringBootApplication(scanBasePackages = {
    "com.example.core",
    "com.example.service"
})
```

**效果**：
- 减少不必要的类扫描
- 启动速度提升5%-10%

---

### ✅ 优化4：使用SpringBoot DevTools（仅开发环境）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

**效果**：
- 支持热重载
- 加快开发迭代速度
- 自动重启（比手动重启快）

---

## 2️⃣ 运行性能优化

### ✅ 优化1：合理使用缓存

```java
@Service
public class UserService {

    @Cacheable("users")
    public User findById(Long id) {
        // ✅ 推荐：使用缓存减少数据库查询
        return userRepository.findById(id);
    }

    @CacheEvict("users")
    public void updateUser(User user) {
        // ✅ 推荐：更新时清除缓存
        userRepository.save(user);
    }
}
```

**配置缓存**：
```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("users"),
            new ConcurrentMapCache("products")
        ));
        return cacheManager;
    }
}
```

**效果**：
- 减少数据库查询
- 响应速度提升10-100倍

---

### ✅ 优化2：使用异步处理

```java
@Service
public class EmailService {

    @Async
    public void sendEmail(String to, String subject, String content) {
        // ✅ 推荐：异步发送邮件
        mailSender.send(to, subject, content);
    }
}
```

**启用异步**：
```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

**效果**：
- 提高响应速度
- 提升系统吞吐量

---

### ✅ 优化3：使用连接池

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        dataSource.setUsername("root");
        dataSource.setPassword("password");

        // ✅ 推荐：配置连接池参数
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);

        return dataSource;
    }
}
```

**效果**：
- 减少连接创建开销
- 提高数据库访问性能

---

### ✅ 优化4：使用批量操作

```java
@Repository
public class UserRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // ❌ 不推荐：逐条插入
    public void batchInsert(List<User> users) {
        for (User user : users) {
            entityManager.persist(user);
        }
    }

    // ✅ 推荐：批量插入
    @Transactional
    public void batchInsert(List<User> users) {
        for (int i = 0; i < users.size(); i++) {
            entityManager.persist(users.get(i));

            // 每50条flush一次
            if (i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
}
```

**效果**：
- 批量操作性能提升10-100倍
- 减少内存占用

---

## 3️⃣ 内存优化

### ✅ 优化1：避免内存泄漏

```java
// ❌ 不推荐：静态Map导致内存泄漏
@Component
public class CacheService {
    private static final Map<String, Object> CACHE = new HashMap<>();  // ❌ 永不回收

    public void put(String key, Object value) {
        CACHE.put(key, value);
    }
}

// ✅ 推荐：使用Caffeine缓存
@Component
public class CacheService {
    private final Cache<String, Object> cache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    public void put(String key, Object value) {
        cache.put(key, value);
    }
}
```

---

### ✅ 优化2：合理设置Bean作用域

```java
// ❌ 不推荐：所有Bean都是singleton
@Component
public class RequestData {
    // 如果包含大量数据，会导致内存泄漏
    private List<Data> data = new ArrayList<>();
}

// ✅ 推荐：使用request作用域
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestData {
    private List<Data> data = new ArrayList<>();
}
```

---

### ✅ 优化3：使用弱引用

```java
@Component
public class ReferenceCache {

    private final Map<String, WeakReference<Object>> cache = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        cache.put(key, new WeakReference<>(value));
    }

    public Object get(String key) {
        WeakReference<Object> reference = cache.get(key);
        return reference != null ? reference.get() : null;
    }
}
```

---

## 4️⃣ 数据库性能优化

### ✅ 优化1：使用只读事务

```java
@Service
public class UserService {

    // ✅ 推荐：查询操作使用只读事务
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
```

**效果**：
- 数据库不进行加锁
- 提高查询性能

---

### ✅ 优化2：合理设置事务隔离级别

```java
@Service
public class OrderService {

    // ✅ 推荐：根据业务选择合适的隔离级别
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void createOrder(Order order) {
        // 大多数场景使用READ_COMMITTED即可
    }
}
```

**隔离级别选择**：
- `DEFAULT`：使用数据库默认级别
- `READ_UNCOMMITTED`：性能最好，但可能脏读
- `READ_COMMITTED`：推荐，性能和并发性平衡
- `REPEATABLE_READ`：避免不可重复读
- `SERIALIZABLE`：最高隔离级别，性能最差

---

### ✅ 优化3：避免N+1查询问题

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)  // ✅ 推荐：延迟加载
    private List<Order> orders;
}

// ❌ 不推荐：会产生N+1查询
List<User> users = userRepository.findAll();
for (User user : users) {
    user.getOrders().size();  // 每次都查询数据库
}

// ✅ 推荐：使用JOIN FETCH
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders")
List<User> findAllWithOrders();
```

---

### ✅ 优化4：使用索引

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email")
})
public class User {
    @Id
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;
}
```

---

## 5️⃣ 监控和诊断

### ✅ 使用Spring Boot Actuator

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**配置端点**：
```properties
# application.properties
management.endpoints.web.exposure.include=health,metrics,info
management.endpoint.health.show-details=always
```

**访问端点**：
- `/actuator/health` - 健康检查
- `/actuator/metrics` - 性能指标
- `/actuator/info` - 应用信息

---

### ✅ 使用性能分析工具

**JVM参数**：
```bash
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log -jar app.jar
```

**Java Mission Tool（JMC）**：
```bash
jmc
```

**VisualVM**：
```bash
jvisualvm
```

---

### ✅ 使用APM工具

**Spring Boot + Micrometer + Prometheus**：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**配置**：
```properties
# application.properties
management.endpoints.web.exposure.include=prometheus
management.metrics.export.prometheus.enabled=true
```

---

## 🎯 性能优化清单

### 启动性能优化
- ✅ 使用@Lazy延迟初始化
- ✅ 排除不需要的自动配置
- ✅ 减少Bean扫描范围
- ✅ 使用SpringBoot DevTools（开发环境）

### 运行性能优化
- ✅ 使用缓存（@Cacheable）
- ✅ 使用异步处理（@Async）
- ✅ 使用连接池（HikariCP）
- ✅ 使用批量操作

### 内存优化
- ✅ 避免内存泄漏
- ✅ 合理设置Bean作用域
- ✅ 使用弱引用

### 数据库性能优化
- ✅ 使用只读事务
- ✅ 合理设置事务隔离级别
- ✅ 避免N+1查询问题
- ✅ 使用索引

### 监控和诊断
- ✅ 使用Spring Boot Actuator
- ✅ 使用性能分析工具
- ✅ 使用APM工具

---

## 🔗 扩展阅读

- [Bean设计最佳实践](./01-Bean设计最佳实践.md)
- [依赖注入最佳实践](./02-依赖注入最佳实践.md)
- [事务管理最佳实践](./03-事务管理最佳实践.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐（高级）
