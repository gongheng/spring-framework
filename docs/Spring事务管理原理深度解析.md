# Spring数据库事务管理深度解析

## 目录
1. [Spring事务管理概述](#1-spring事务管理概述)
2. [核心接口与类](#2-核心接口与类)
3. [事务传播行为](#3-事务传播行为)
4. [事务隔离级别](#4-事务隔离级别)
5. [事务拦截机制](#5-事务拦截机制)
6. [DataSourceTransactionManager详解](#6-datasourcetransactionmanager详解)
7. [事务同步管理](#7-事务同步管理)
8. [声明式事务使用](#8-声明式事务使用)
9. [编程式事务使用](#9-编程式事务使用)
10. [事务最佳实践](#10-事务最佳实践)

---

## 1. Spring事务管理概述

### 1.1 什么是Spring事务管理

Spring事务管理是Spring框架提供的一套完整的事务管理解决方案，它支持编程式和声明式两种事务管理方式。

#### 核心价值
- **统一的事务管理抽象**：为不同的数据访问技术提供统一的事务管理接口
- **声明式事务**：通过注解或XML配置即可实现事务管理
- **编程式事务**：提供灵活的编程式事务管理API
- **事务传播**：支持多种事务传播行为，处理嵌套调用
- **异常处理**：智能的异常处理和事务回滚机制

### 1.2 Spring事务管理的两种方式

#### 声明式事务（推荐）
```java
@Service
public class UserService {
    
    @Transactional
    public void createUser(User user) {
        // 事务方法
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id);
    }
}
```

#### 编程式事务
```java
@Service
public class UserService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    public void createUser(User user) {
        transactionTemplate.execute(status -> {
            userRepository.save(user);
            return null;
        });
    }
}
```

---

## 2. 核心接口与类

### 2.1 PlatformTransactionManager

#### 接口定义
```java
/**
 * 【事务管理器的核心接口】定义了事务管理的基本操作
 */
public interface PlatformTransactionManager extends TransactionManager {
    
    /**
     * 【获取事务】根据传播行为获取当前事务或创建新事务
     */
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition) 
        throws TransactionException;
    
    /**
     * 【提交事务】提交给定的事务
     */
    void commit(TransactionStatus status) throws TransactionException;
    
    /**
     * 【回滚事务】回滚给定的事务
     */
    void rollback(TransactionStatus status) throws TransactionException;
}
```

#### 核心方法说明

##### getTransaction()
- **作用**：根据事务定义获取或创建事务
- **传播行为处理**：根据传播行为决定是加入现有事务还是创建新事务
- **事务属性应用**：应用隔离级别、超时等属性

##### commit()
- **作用**：提交事务
- **rollback-only检查**：如果事务被标记为仅回滚，执行回滚
- **嵌套事务处理**：正确处理嵌套事务的提交逻辑

##### rollback()
- **作用**：回滚事务
- **参与事务处理**：对于参与事务，设置rollback-only标志
- **资源清理**：确保事务资源被正确清理

### 2.2 TransactionDefinition

#### 事务定义接口
```java
/**
 * 【事务定义接口】定义Spring兼容的事务属性
 */
public interface TransactionDefinition {
    
    // 传播行为常量
    int PROPAGATION_REQUIRED = 0;           // 支持当前事务，如果没有则创建新事务
    int PROPAGATION_SUPPORTS = 1;           // 支持当前事务，如果没有则非事务执行
    int PROPAGATION_MANDATORY = 2;          // 必须在事务中执行
    int PROPAGATION_REQUIRES_NEW = 3;       // 总是创建新事务
    int PROPAGATION_NOT_SUPPORTED = 4;       // 不支持当前事务，总是非事务执行
    int PROPAGATION_NEVER = 5;               // 不支持当前事务，抛出异常
    int PROPAGATION_NESTED = 6;             // 嵌套事务
    
    // 隔离级别常量
    int ISOLATION_DEFAULT = -1;             // 使用默认隔离级别
    int ISOLATION_READ_UNCOMMITTED = 1;     // 读未提交
    int ISOLATION_READ_COMMITTED = 2;       // 读已提交
    int ISOLATION_REPEATABLE_READ = 4;      // 可重复读
    int ISOLATION_SERIALIZABLE = 8;         // 串行化
    
    // 获取传播行为
    int getPropagationBehavior();
    
    // 获取隔离级别
    int getIsolationLevel();
    
    // 获取超时时间（秒）
    int getTimeout();
    
    // 是否只读
    boolean isReadOnly();
    
    // 获取事务名称
    @Nullable String getName();
}
```

### 2.3 TransactionStatus

#### 事务状态接口
```java
/**
 * 【事务状态接口】表示事务的当前状态
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager {
    
    /**
     * 【是否是新事务】检查是否是新创建的事务
     */
    boolean isNewTransaction();
    
    /**
     * 【是否具有保存点】是否支持嵌套事务
     */
    boolean hasSavepoint();
    
    /**
     * 【设置为仅回滚】标记事务只能回滚
     */
    void setRollbackOnly();
    
    /**
     * 【是否仅回滚】检查事务是否被标记为仅回滚
     */
    boolean isRollbackOnly();
    
    /**
     * 【是否已完成】检查事务是否已完成
     */
    boolean isCompleted();
}
```

---

## 3. 事务传播行为

### 3.1 传播行为类型

#### REQUIRED（默认）
```java
@Transactional(propagation = Propagation.REQUIRED)
public void methodA() {
    methodB();  // methodB会加入methodA的事务
}

@Transactional(propagation = Propagation.REQUIRED)
public void methodB() {
    // 业务逻辑
}
```
- **行为**：支持当前事务，如果没有则创建新事务
- **使用场景**：大多数情况下的事务需求

#### REQUIRES_NEW
```java
@Transactional(propagation = Propagation.REQUIRED)
public void methodA() {
    methodB();  // methodB创建新事务，与methodA事务独立
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    // 业务逻辑
}
```
- **行为**：总是创建新事务，挂起当前事务
- **使用场景**：需要独立事务的操作

#### NESTED
```java
@Transactional(propagation = Propagation.REQUIRED)
public void methodA() {
    methodB();  // methodB在methodA事务的嵌套事务中执行
}

@Transactional(propagation = Propagation.NESTED)
public void methodB() {
    // 业务逻辑
}
```
- **行为**：如果当前事务存在，则在嵌套事务中执行
- **使用场景**：需要部分回滚的复杂业务逻辑

#### SUPPORTS
```java
@Transactional(propagation = Propagation.SUPPORTS)
public void methodA() {
    // 如果有事务则加入，否则非事务执行
}
```
- **行为**：支持当前事务，如果没有则非事务执行
- **使用场景**：可选事务的查询操作

#### MANDATORY
```java
@Transactional(propagation = Propagation.MANDATORY)
public void methodA() {
    // 必须在事务中执行，否则抛出异常
}
```
- **行为**：必须在事务中执行，否则抛出异常
- **使用场景**：必须在事务上下文中执行的操作

#### NOT_SUPPORTED
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void methodA() {
    // 总是非事务执行，挂起当前事务
}
```
- **行为**：不支持当前事务，总是非事务执行
- **使用场景**：不需要事务的操作

#### NEVER
```java
@Transactional(propagation = Propagation.NEVER)
public void methodA() {
    // 不支持当前事务，如果存在事务则抛出异常
}
```
- **行为**：不支持当前事务，如果存在事务则抛出异常
- **使用场景**：明确不能在事务中执行的操作

### 3.2 传播行为对比表

| 传播行为 | 外围事务存在 | 外围事务不存在 | 说明 |
|---------|------------|--------------|------|
| REQUIRED | 加入外围事务 | 创建新事务 | 默认传播行为 |
| REQUIRES_NEW | 挂起外围事务，创建新事务 | 创建新事务 | 独立事务 |
| NESTED | 在嵌套事务中执行 | 创建新事务 | 嵌套事务 |
| SUPPORTS | 加入外围事务 | 非事务执行 | 可选事务 |
| MANDATORY | 加入外围事务 | 抛出异常 | 必须在事务中 |
| NOT_SUPPORTED | 挂起外围事务，非事务执行 | 非事务执行 | 不支持事务 |
| NEVER | 抛出异常 | 非事务执行 | 禁止事务 |

---

## 4. 事务隔离级别

### 4.1 隔离级别概述

#### 脏读异常（Dirty Reads）
一个事务读取了另一个事务未提交的数据。

#### 不可重复读（Non-Repeatable Reads）
一个事务内两次读取同一数据，结果不一致（中间有其他事务修改了数据）。

#### 幻读（Phantom Reads）
一个事务内两次查询，结果集不一致（中间有其他事务插入/删除了数据）。

### 4.2 隔离级别详解

#### READ_UNCOMMITTED（读未提交）
```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public void methodA() {
    // 允许脏读、不可重复读、幻读
}
```
- **脏读**：可能发生
- **不可重复读**：可能发生
- **幻读**：可能发生
- **性能**：最好
- **使用场景**：几乎不使用

#### READ_COMMITTED（读已提交）
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void methodA() {
    // 防止脏读，允许不可重复读、幻读
}
```
- **脏读**：防止
- **不可重复读**：可能发生
- **幻读**：可能发生
- **性能**：较好
- **使用场景**：大多数应用的默认选择

#### REPEATABLE_READ（可重复读）
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void methodA() {
    // 防止脏读、不可重复读，允许幻读
}
```
- **脏读**：防止
- **不可重复读**：防止
- **幻读**：可能发生
- **性能**：中等
- **使用场景**：对数据一致性要求较高的场景

#### SERIALIZABLE（串行化）
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void methodA() {
    // 防止脏读、不可重复读、幻读
}
```
- **脏读**：防止
- **不可重复读**：防止
- **幻读**：防止
- **性能**：最差
- **使用场景**：对数据一致性要求极高的场景

### 4.3 隔离级别对比表

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 性能 | 使用频率 |
|---------|------|----------|------|------|---------|
| READ_UNCOMMITTED | 可能 | 可能 | 可能 | 最好 | 极少 |
| READ_COMMITTED | 防止 | 可能 | 可能 | 较好 | 常用 |
| REPEATABLE_READ | 防止 | 防止 | 可能 | 中等 | 较常用 |
| SERIALIZABLE | 防止 | 防止 | 防止 | 最差 | 极少 |

---

## 5. 事务拦截机制

### 5.1 TransactionInterceptor工作原理

#### 核心执行流程
```java
/**
 * 【事务拦截的核心方法】
 */
@Override
public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
    // 1. 确定目标类
    Class<?> targetClass = AopUtils.getTargetClass(invocation.getThis());

    // 2. 调用事务管理核心逻辑
    return invokeWithinTransaction(invocation.getMethod(), targetClass, new InvocationCallback() {
        
        // 3. 执行目标方法
        @Override
        public @Nullable Object proceedWithInvocation() throws Throwable {
            return invocation.proceed();
        }

        // 4. 回滚事件处理
        @Override
        public void onRollback(Throwable failure, TransactionExecution execution) {
            // 发布回滚事件
        }
    });
}
```

### 5.2 事务执行流程图

```
┌─────────────────────────────────────────────────────────────┐
│ Spring事务拦截完整流程                                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ 1. 方法调用拦截                                              │
│    └─ TransactionInterceptor.invoke()                       │
│                                                              │
│ 2. 事务属性获取                                              │
│    ├─ 从@Transaction注解获取事务属性                         │
│    ├─ 确定事务管理器                                         │
│    └─ 获取传播行为、隔离级别等配置                            │
│                                                              │
│ 3. 事务处理                                                  │
│    ├─ 根据传播行为创建/加入事务                              │
│    ├─ 设置隔离级别、只读状态                                 │
│    └─ 绑定资源到当前线程                                     │
│                                                              │
│ 4. 目标方法执行                                              │
│    ├─ 执行业务逻辑                                           │
│    └─ 可能抛出运行时异常                                     │
│                                                              │
│ 5. 事务完成处理                                              │
│    ├─ 无异常：提交事务                                        │
│    ├─ 有异常：根据回滚规则决定提交或回滚                     │
│    └─ 清理资源、触发同步回调                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. DataSourceTransactionManager详解

### 6.1 JDBC事务管理核心实现

#### 类结构
```java
/**
 * 【JDBC数据源事务管理器】单个JDBC DataSource的事务管理实现
 */
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager 
        implements ResourceTransactionManager, InitializingBean {
    
    private DataSource dataSource;
    
    /**
     * 【获取事务对象】开始JDBC事务
     */
    @Override
    protected Object doGetTransaction() {
        // 1. 从DataSource获取Connection
        // 2. 设置事务隔离级别和只读状态
        // 3. 禁用自动提交
        // 4. 绑定Connection到当前线程
        // 5. 返回事务对象
    }

    /**
     * 【开始事务】开始新的JDBC事务
     */
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. 获取数据库连接
        // 2. 设置隔离级别
        // 3. 设置只读状态
        // 4. 禁用自动提交
        // 5. 绑定到线程
    }

    /**
     * 【提交事务】提交JDBC事务
     */
    protected void doCommit(DefaultTransactionStatus status) {
        // 1. 获取事务对应的Connection
        // 2. 调用Connection.commit()
        // 3. 恢复自动提交状态
        // 4. 释放Connection到连接池
    }

    /**
     * 【回滚事务】回滚JDBC事务
     */
    protected void doRollback(DefaultTransactionStatus status) {
        // 1. 获取事务对应的Connection
        // 2. 调用Connection.rollback()
        // 3. 恢复自动提交状态
        // 4. 释放Connection到连接池
    }
}
```

### 6.2 事务生命周期管理

#### 连接管理流程
```
┌─────────────────────────────────────────────────────────────┐
│ JDBC连接事务管理流程                                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ 1. 开始事务前                                                │
│    ├─ 从连接池获取Connection                                │
│    ├─ 设置隔离级别（connection.setTransactionIsolation）    │
│    ├─ 设置只读状态（connection.setReadOnly）                 │
│    ├─ 禁用自动提交（connection.setAutoCommit(false)）         │
│    └─ 绑定到TransactionSynchronizationManager                 │
│                                                              │
│ 2. 事务执行期间                                              │
│    ├─ 应用通过DataSourceUtils获取Connection                  │
│    ├─ 执行SQL操作                                            │
│    └─ Connection保持事务状态                                 │
│                                                              │
│ 3. 提交事务                                                  │
│    ├─ 调用connection.commit()                                 │
│    ├─ 恢复自动提交（connection.setAutoCommit(true)）           │
│    ├─ 触发事务同步回调                                        │
│    └─ 释放Connection到连接池                                  │
│                                                              │
│ 4. 回滚事务                                                  │
│    ├─ 调用connection.rollback()                               │
│    ├─ 恢复自动提交（connection.setAutoCommit(true)）           │
│    ├─ 触发事务同步回调                                        │
│    └─ 释放Connection到连接池                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. 事务同步管理

### 7.1 TransactionSynchronizationManager

#### 核心功能
```java
/**
 * 【事务同步管理器】管理线程级别的资源绑定和事务同步
 */
public abstract class TransactionSynchronizationManager {
    
    // 资源存储：存储事务相关的资源
    private static final ThreadLocal<Map<Object, Object>> resources =
        new NamedThreadLocal<>("Transactional resources");
    
    // 事务同步：存储事务同步回调
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
        new NamedThreadLocal<>("Transaction synchronizations");
    
    // 事务状态：跟踪当前事务的状态
    private static final ThreadLocal<String> currentTransactionName =
        new NamedThreadLocal<>("Current transaction name");
    private static final ThreadLocal<Boolean> currentTransactionReadOnly =
        new NamedThreadLocal<>("Current transaction read-only status");
    private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
        new NamedThreadLocal<>("Current transaction isolation level");
    private static final ThreadLocal<Boolean> actualTransactionActive =
        new NamedThreadLocal<>("Actual transaction active");
}
```

#### 资源绑定机制
```java
/**
 * 【绑定资源到当前线程】
 */
public static void bindResource(Object key, Object value) throws IllegalStateException {
    Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
    Map<Object, Object> map = resources.get();
    if (map == null) {
        map = new HashMap<>();
        resources.set(map);
    }
    Object oldValue = map.put(actualKey, value);
    if (oldValue != null) {
        throw new IllegalStateException("Already value [" + oldValue + "] for key [" +
                actualKey + "] bound to thread");
    }
}

/**
 * 【从当前线程获取资源】
 */
public static Object getResource(Object key) {
    Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
    return doGetResource(actualKey);
}
```

### 7.2 事务同步回调

#### TransactionSynchronization接口
```java
/**
 * 【事务同步接口】定义事务生命周期中的回调点
 */
public interface TransactionSynchronization extends FlushableSynchronization {
    
    /**
     * 【挂起事务】事务被挂起时调用
     */
    void suspend();

    /**
     * 【恢复事务】事务被恢复时调用
     */
    void resume();

    /**
     * 【刷新事务】事务刷新前调用
     */
    @Override
    void flush();

    /**
     * 【提交前】事务提交前调用
     */
    void beforeCommit(boolean readOnly);

    /**
     * 【完成前】事务完成前调用（提交或回滚）
     */
    void beforeCompletion();

    /**
     * 【提交后】事务成功提交后调用
     */
    void afterCommit();

    /**
     * 【完成后】事务完成后调用（无论成功或失败）
     */
    void afterCompletion(int status);
}
```

---

## 8. 声明式事务使用

### 8.1 @Transactional注解

#### 基本使用
```java
@Service
public class UserService {

    @Transactional
    public void createUser(User user) {
        userRepository.save(user);
        emailService.sendWelcomeEmail(user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED,
        timeout = 30,
        readOnly = false,
        rollbackFor = Exception.class,
        noRollbackFor = BusinessException.class
    )
    public void complexOperation(User user) {
        // 复杂的业务逻辑
    }
}
```

### 8.2 类级别的事务配置

```java
@Service
@Transactional(readOnly = true)
public class ProductService {

    public List<Product> getProducts() {
        // 只读查询
    }

    @Transactional(readOnly = false)
    public void saveProduct(Product product) {
        // 写操作，需要事务
    }
}
```

### 8.3 事务回滚规则

#### 默认回滚规则
```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) {
        // 默认情况下，RuntimeException和Error会触发回滚
        // 检查异常不会触发回滚
    }
}
```

#### 自定义回滚规则
```java
@Service
public class OrderService {

    @Transactional(
        rollbackFor = {Exception.class},           // 这些异常会回滚
        noRollbackFor = {BusinessException.class} // 这些异常不会回滚
    )
    public void placeOrder(Order order) {
        // 自定义回滚规则
    }
}
```

### 8.4 事务配置类

```java
@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
```

---

## 9. 编程式事务使用

### 9.1 TransactionTemplate

#### 基本使用
```java
@Service
public class AccountService {

    @Autowired
    private TransactionTemplate transactionTemplate;

    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        transactionTemplate.execute(status -> {
            Account from = accountRepository.findById(fromId);
            Account to = accountRepository.findById(toId);
            
            from.setBalance(from.getBalance().subtract(amount));
            to.setBalance(to.getBalance().add(amount));
            
            accountRepository.save(from);
            accountRepository.save(to);
            
            return null;
        });
    }
}
```

### 9.2 PlatformTransactionManager直接使用

```java
@Service
public class AccountService {

    @Autowired
    private PlatformTransactionManager transactionManager;

    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        TransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(definition);

        try {
            Account from = accountRepository.findById(fromId);
            Account to = accountRepository.findById(toId);
            
            from.setBalance(from.getBalance().subtract(amount));
            to.setBalance(to.getBalance().add(amount));
            
            accountRepository.save(from);
            accountRepository.save(to);
            
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }
}
```

---

## 10. 事务最佳实践

### 10.1 事务边界设计

#### 合理的事务边界
```java
// ✅ 推荐：事务边界清晰
@Service
public class OrderService {

    @Transactional
    public void processOrder(Order order) {
        // 1. 验证订单
        // 2. 创建订单记录
        // 3. 更新库存
        // 4. 发送确认邮件
    }
}

// ❌ 不推荐：事务边界过大
@Service
public class OrderService {

    @Transactional
    public void processOrder(Order order) {
        // 订单处理
        // 库存更新
        // 物流安排
        // 支付处理
        // 用户通知
        // 数据分析
        // 报表生成
    }
}
```

### 10.2 查询优化

#### 只读事务配置
```java
@Service
public class ReportService {

    // 查询方法使用只读事务
    @Transactional(readOnly = true)
    public List<Order> getDailyOrders(LocalDate date) {
        return orderRepository.findByDate(date);
    }

    // 复杂报表查询
    @Transactional(readOnly = true, timeout = 60)
    public Map<String, Object> generateMonthlyReport(int year, int month) {
        Map<String, Object> report = new HashMap<>();
        report.put("orders", orderRepository.findByYearMonth(year, month));
        report.put("revenue", orderRepository.calculateRevenue(year, month));
        return report;
    }
}
```

### 10.3 异常处理

#### 异常回滚策略
```java
@Service
public class PaymentService {

    // 业务异常不回滚
    @Transactional(rollbackFor = {SystemException.class}, 
                   noRollbackFor = {BusinessException.class})
    public void processPayment(Payment payment) {
        try {
            // 支付处理逻辑
        } catch (InsufficientBalanceException e) {
            // 业务异常，不回滚
            throw new BusinessException("余额不足");
        } catch (DataAccessException e) {
            // 系统异常，回滚
            throw new SystemException("支付系统异常", e);
        }
    }
}
```

### 10.4 性能优化

#### 事务超时设置
```java
@Service
public class BatchProcessService {

    // 设置合理的超时时间
    @Transactional(timeout = 30) // 30秒超时
    public void batchProcess(List<Order> orders) {
        for (Order order : orders) {
            processOrder(order);
        }
    }

    // 大批量数据处理
    @Transactional(timeout = 300, propagation = Propagation.REQUIRES_NEW)
    public void largeBatchProcess(List<Order> orders) {
        // 分批处理，每批独立事务
    }
}
```

### 10.5 分布式事务考虑

#### 避免分布式事务
```java
@Service
public class OrderService {

    // ❌ 不推荐：跨数据源事务
    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);      // 主数据库
        inventoryRepository.updateStock();  // 库存数据库 - 分布式事务问题
    }

    // ✅ 推荐：使用消息队列
    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        messageQueue.send(new OrderCreatedEvent(order)); // 异步处理
    }
}
```

### 10.6 事务监控

#### 事务性能监控
```java
@Aspect
@Component
public class TransactionMonitorAspect {

    @Around("@annotation(Transactional)")
    public Object monitorTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();

        try {
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 1000) {
                logger.warn("Transaction method {} took {} ms", methodName, duration);
            }
            
            return result;
        } catch (Throwable e) {
            logger.error("Transaction method {} failed after {} ms", 
                methodName, System.currentTimeMillis() - startTime, e);
            throw e;
        }
    }
}
```

---

## 总结

Spring事务管理是Spring框架的核心功能之一，它提供了完整的事务管理解决方案。

### 核心概念
1. **事务管理器**：PlatformTransactionManager及其实现
2. **事务属性**：传播行为、隔离级别、超时、只读等
3. **事务拦截**：TransactionInterceptor实现声明式事务
4. **资源管理**：TransactionSynchronizationManager管理线程资源
5. **异常处理**：智能的异常处理和回滚机制

### 关键技术点
- **声明式事务**：通过@Transactional注解实现
- **编程式事务**：通过TransactionTemplate实现
- **传播行为**：7种传播行为处理不同场景
- **隔离级别**：4种隔离级别平衡性能和一致性
- **事务同步**：TransactionSynchronization实现资源清理

### 最佳实践
- **合理设计事务边界**：事务方法要简洁高效
- **查询使用只读事务**：提高查询性能
- **避免分布式事务**：使用消息队列等替代方案
- **合理设置超时时间**：防止长事务影响系统性能
- **监控事务执行**：及时发现和处理事务问题

理解Spring事务管理的原理对于开发高质量的企业级应用至关重要，特别是在涉及复杂业务逻辑和高并发场景时。