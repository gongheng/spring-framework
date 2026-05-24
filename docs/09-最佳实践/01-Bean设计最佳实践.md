# Spring Bean设计最佳实践

## 🎯 核心原则

设计Spring Bean时应该遵循**SOLID原则**和**Spring最佳实践**，确保代码的可维护性、可测试性和可扩展性。

---

## 📋 目录

1. [Bean作用域选择](#1-bean作用域选择)
2. [依赖注入方式选择](#2-依赖注入方式选择)
3. [Bean命名规范](#3-bean命名规范)
4. [Bean初始化和销毁](#4-bean初始化和销毁)
5. [Bean设计原则](#5-bean设计原则)
6. [常见反模式](#6-常见反模式)

---

## 1️⃣ Bean作用域选择

### ✅ 推荐：使用默认的singleton作用域

```java
@Component
public class UserService {
    // ✅ 推荐：默认singleton作用域
}
```

**原因**：
- 线程安全由Spring管理
- 节省内存
- 性能更好

### ⚠️ 谨慎使用：prototype作用域

```java
@Component
@Scope("prototype")
public class PrototypeService {
    // ⚠️ 谨慎使用：每次请求都创建新实例
}
```

**使用场景**：
- 有状态的对象
- 需要每次都是新实例的场景
- 不适合共享的对象

**注意事项**：
- prototype Bean的销毁方法不会被调用
- 需要手动管理资源清理

### ✅ Web应用：使用request/session作用域

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedBean {
    // ✅ 推荐：请求作用域
}

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionScopedBean {
    // ✅ 推荐：会话作用域
}
```

**注意**：必须设置`proxyMode = ScopedProxyMode.TARGET_CLASS`，否则无法注入到singleton Bean中。

---

## 2️⃣ 依赖注入方式选择

### ✅ 推荐：构造器注入

```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;

    // ✅ 推荐：构造器注入
    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
}
```

**优点**：
- 保证依赖不可变（final）
- 保证依赖不为空（避免NPE）
- 易于测试（可以直接new对象）
- 清晰地表达依赖关系

### ⚠️ 不推荐：字段注入

```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;  // ⚠️ 不推荐：字段注入

    @Autowired
    private EmailService emailService;     // ⚠️ 不推荐：字段注入
}
```

**缺点**：
- 无法设置为final
- 无法保证依赖不为空
- 难以测试（必须使用Spring容器）
- 隐藏依赖关系

### ⚠️ 谨慎使用：Setter注入

```java
@Service
public class UserService {
    private UserRepository userRepository;

    // ⚠️ 谨慎使用：Setter注入
    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
```

**使用场景**：
- 可选依赖
- 需要动态更换依赖的场景

---

## 3️⃣ Bean命名规范

### ✅ 推荐：使用类名首字母小写

```java
@Component
public class UserService {
    // Bean名称：userService ✅
}

@Repository
public class UserRepositoryImpl implements UserRepository {
    // Bean名称：userRepositoryImpl ✅
}

@Controller
public class UserController {
    // Bean名称：userController ✅
}
```

### ✅ 推荐：自定义Bean名称

```java
@Component("userService")
public class UserServiceImpl implements UserService {
    // Bean名称：userService ✅
}

@Repository("userDao")
public class UserRepositoryImpl implements UserRepository {
    // Bean名称：userDao ✅
}
```

### ⚠️ 不推荐：使用特殊字符

```java
@Component("user-service")
public class UserService {
    // Bean名称：user-service ⚠️ 不推荐：包含特殊字符
}
```

---

## 4️⃣ Bean初始化和销毁

### ✅ 推荐：使用@PostConstruct和@PreDestroy

```java
@Component
public class DatabaseService {

    @PostConstruct
    public void init() {
        // ✅ 推荐：初始化逻辑
        System.out.println("DatabaseService初始化");
    }

    @PreDestroy
    public void destroy() {
        // ✅ 推荐：销毁逻辑
        System.out.println("DatabaseService销毁");
    }
}
```

**优点**：
- 标准注解（JSR-250）
- 与Spring解耦
- 方法名可以自定义

### ⚠️ 不推荐：实现InitializingBean和DisposableBean

```java
@Component
public class DatabaseService implements InitializingBean, DisposableBean {

    @Override
    public void afterPropertiesSet() {
        // ⚠️ 不推荐：与Spring耦合
    }

    @Override
    public void destroy() {
        // ⚠️ 不推荐：与Spring耦合
    }
}
```

**缺点**：
- 与Spring耦合
- 代码侵入性强

### ⚠️ 不推荐：使用init-method和destroy-method

```java
@Component
public class DatabaseService {
    public void init() {
        // ⚠️ 不推荐：XML配置风格
    }

    public void destroy() {
        // ⚠️ 不推荐：XML配置风格
    }
}

// 配置类
@Configuration
public class AppConfig {
    @Bean(initMethod = "init", destroyMethod = "destroy")
    public DatabaseService databaseService() {
        return new DatabaseService();
    }
}
```

**缺点**：
- XML配置风格
- 不够灵活

---

## 5️⃣ Bean设计原则

### ✅ 原则1：单一职责原则（SRP）

```java
// ❌ 不推荐：一个Bean承担多个职责
@Component
public class UserService {
    public void createUser(User user) { }

    public void sendEmail(String to, String subject, String content) { }

    public void logAction(String action) { }
}

// ✅ 推荐：拆分为多个Bean
@Component
public class UserService {
    private final EmailService emailService;
    private final LogService logService;

    public void createUser(User user) {
        emailService.sendEmail(user.getEmail(), "欢迎", "欢迎注册");
        logService.logAction("用户注册：" + user.getUsername());
    }
}

@Component
public class EmailService {
    public void sendEmail(String to, String subject, String content) { }
}

@Component
public class LogService {
    public void logAction(String action) { }
}
```

### ✅ 原则2：依赖倒置原则（DIP）

```java
// ❌ 不推荐：依赖具体实现
@Component
public class UserService {
    private final UserRepositoryImpl userRepository;  // 依赖具体类

    public UserService(UserRepositoryImpl userRepository) {
        this.userRepository = userRepository;
    }
}

// ✅ 推荐：依赖抽象
@Component
public class UserService {
    private final UserRepository userRepository;  // 依赖接口

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

public interface UserRepository {
    User findById(Long id);
    void save(User user);
}
```

### ✅ 原则3：接口隔离原则（ISP）

```java
// ❌ 不推荐：臃肿的接口
public interface UserService {
    void createUser(User user);
    void updateUser(User user);
    void deleteUser(Long id);
    User findById(Long id);
    List<User> findAll();
    void exportToExcel(List<User> users);
    void importFromExcel(File file);
    void sendEmail(User user, String subject, String content);
}

// ✅ 推荐：拆分为多个接口
public interface UserService {
    void createUser(User user);
    void updateUser(User user);
    void deleteUser(Long id);
    User findById(Long id);
    List<User> findAll();
}

public interface UserExportService {
    void exportToExcel(List<User> users);
    void importFromExcel(File file);
}

public interface UserNotificationService {
    void sendEmail(User user, String subject, String content);
}
```

### ✅ 原则4：开闭原则（OCP）

```java
// ❌ 不推荐：修改现有代码
@Component
public class PaymentService {
    public void pay(String type, BigDecimal amount) {
        if ("ALIPAY".equals(type)) {
            // 支付宝支付逻辑
        } else if ("WECHAT".equals(type)) {
            // 微信支付逻辑
        } else if ("BANK".equals(type)) {
            // 银行卡支付逻辑
        }
        // 每次新增支付方式都需要修改代码
    }
}

// ✅ 推荐：扩展而不修改
public interface PaymentStrategy {
    void pay(BigDecimal amount);
}

@Component
public class AlipayStrategy implements PaymentStrategy {
    @Override
    public void pay(BigDecimal amount) {
        // 支付宝支付逻辑
    }
}

@Component
public class WechatStrategy implements PaymentStrategy {
    @Override
    public void pay(BigDecimal amount) {
        // 微信支付逻辑
    }
}

@Component
public class PaymentService {
    private final Map<String, PaymentStrategy> strategyMap;

    public PaymentService(List<PaymentStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(
                strategy -> strategy.getClass().getSimpleName(),
                Function.identity()
            ));
    }

    public void pay(String type, BigDecimal amount) {
        PaymentStrategy strategy = strategyMap.get(type + "Strategy");
        if (strategy != null) {
            strategy.pay(amount);
        }
    }
}
```

---

## 6️⃣ 常见反模式

### ❌ 反模式1：循环依赖

```java
@Component
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
}

@Component
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
}
```

**解决方案**：
- 使用`@Lazy`注解
- 重构设计，提取公共接口

### ❌ 反模式2：过度使用@Autowired

```java
@Component
public class UserService {
    @Autowired  // ❌ 不推荐：可以省略
    private UserRepository userRepository;

    @Autowired  // ❌ 不推荐：构造器注入不需要@Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
```

**最佳实践**：
- 构造器注入可以省略`@Autowired`（Spring 4.3+）
- 字段注入必须使用`@Autowired`

### ❌ 反模式3：在Bean中调用Bean

```java
@Component
public class ServiceA {
    @Autowired
    private ServiceB serviceB;

    public void methodA() {
        serviceB.methodB();  // ❌ 不推荐：同类方法调用会绕过AOP
    }
}
```

**解决方案**：
- 通过注入self调用
- 提取公共接口

### ❌ 反模式4：在Bean中使用静态方法

```java
@Component
public class UserService {
    private static UserRepository userRepository;  // ❌ 不推荐：静态字段

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        UserService.userRepository = userRepository;
    }

    public static User findById(Long id) {  // ❌ 不推荐：静态方法
        return userRepository.findById(id);
    }
}
```

**问题**：
- 无法进行单元测试
- 无法进行AOP代理
- 无法使用多实例

### ❌ 反模式5：Bean中包含业务逻辑外的代码

```java
@Component
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public void createUser(User user) {
        userRepository.save(user);

        // ❌ 不推荐：包含日志逻辑
        System.out.println("用户创建成功：" + user.getUsername());

        // ❌ 不推荐：包含事务逻辑
        TransactionManager txManager = ...;
        txManager.commit(...);
    }
}
```

**最佳实践**：
- 使用AOP处理横切关注点
- 使用@Transactional管理事务

---

## 🎯 总结

### 最佳实践清单

#### Bean作用域
- ✅ 默认使用singleton作用域
- ✅ 有状态对象使用prototype作用域
- ✅ Web应用使用request/session作用域

#### 依赖注入
- ✅ 优先使用构造器注入
- ✅ 可选依赖使用Setter注入
- ⚠️ 避免使用字段注入

#### 命名规范
- ✅ 使用类名首字母小写
- ✅ 自定义Bean名称要清晰
- ⚠️ 避免使用特殊字符

#### 初始化和销毁
- ✅ 使用@PostConstruct和@PreDestroy
- ⚠️ 避免实现InitializingBean和DisposableBean
- ⚠️ 避免使用init-method和destroy-method

#### 设计原则
- ✅ 遵循单一职责原则（SRP）
- ✅ 遵循依赖倒置原则（DIP）
- ✅ 遵循接口隔离原则（ISP）
- ✅ 遵循开闭原则（OCP）

#### 避免反模式
- ❌ 避免循环依赖
- ❌ 避免过度使用@Autowired
- ❌ 避免同类方法调用
- ❌ 避免使用静态方法
- ❌ 避免包含横切关注点

---

## 🔗 扩展阅读

- [依赖注入实现原理](../04-依赖注入/01-依赖注入实现原理.md)
- [循环依赖解决机制详解](../04-依赖注入/05-循环依赖解决机制详解.md)
- [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐（进阶级）
