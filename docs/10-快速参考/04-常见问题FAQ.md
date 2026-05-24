# Spring常见问题FAQ

## 🎯 容器启动相关问题

### Q1: Spring容器启动流程是什么？

**A**: Spring容器启动的核心流程是`AbstractApplicationContext.refresh()`，包含12个步骤：

1. prepareRefresh() - 准备刷新
2. obtainFreshBeanFactory() - 获取BeanFactory
3. prepareBeanFactory() - 准备BeanFactory
4. postProcessBeanFactory() - 后处理BeanFactory
5. invokeBeanFactoryPostProcessors() - **Bean定义加载**
6. registerBeanPostProcessors() - 注册BeanPostProcessor
7. initMessageSource() - 初始化消息源
8. initApplicationEventMulticaster() - 初始化事件广播器
9. onRefresh() - 刷新特殊资源
10. registerListeners() - 注册监听器
11. finishBeanFactoryInitialization() - **Bean实例化**
12. finishRefresh() - 完成刷新

**详细说明**：参见 [Spring容器启动流程详解](../01-核心容器/01-Spring容器启动流程详解.md)

---

### Q2: Bean定义什么时候加载？

**A**: 取决于应用类型：

| 应用类型 | Bean定义加载时机 |
|---------|-----------------|
| 传统Spring XML | refreshBeanFactory()中从XML加载 |
| 传统Spring注解 | invokeBeanFactoryPostProcessors()中扫描加载 |
| SpringBoot | invokeBeanFactoryPostProcessors()中自动扫描加载 |

**核心类**：`ConfigurationClassPostProcessor`

**详细说明**：参见 [ConfigurationClassPostProcessor深度解析](../03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md)

---

### Q3: Spring如何解决循环依赖？

**A**: Spring使用**三级缓存**解决单例Bean的循环依赖：

1. **一级缓存**（singletonObjects）：完整的Bean实例
2. **二级缓存**（earlySingletonObjects）：早期暴露的Bean
3. **三级缓存**（singletonFactories）：Bean工厂

**支持的循环依赖**：
- ✅ 单例Bean的setter注入和字段注入
- ❌ 构造器注入
- ❌ 原型Bean（Prototype）

**详细说明**：参见 [循环依赖解决机制详解](../04-依赖注入/05-循环依赖解决机制详解.md)

---

## 🎯 Bean创建相关问题

### Q4: Bean的完整生命周期是什么？

**A**: Bean生命周期分为5个阶段：

1. **实例化**：反射创建Bean实例
2. **属性赋值**：依赖注入（@Autowired、@Value）
3. **初始化**：
   - Aware接口回调
   - BeanPostProcessor前置处理
   - InitializingBean.afterPropertiesSet()
   - @PostConstruct
   - init-method
   - BeanPostProcessor后置处理（AOP代理在这里创建）
4. **使用**：Bean被应用程序使用
5. **销毁**：
   - @PreDestroy
   - DisposableBean.destroy()
   - destroy-method

**详细说明**：参见 [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)

---

### Q5: @Autowired注解的原理是什么？

**A**: @Autowired基于**BeanPostProcessor**实现：

1. 容器启动时注册`AutowiredAnnotationBeanPostProcessor`
2. 在Bean属性赋值阶段（populateBean）
3. `AutowiredAnnotationBeanPostProcessor`处理@Autowired注解
4. 从BeanFactory中查找依赖的Bean
5. 通过反射注入依赖

**支持三种注入方式**：
- 字段注入：`@Autowired private UserService userService;`
- Setter注入：`@Autowired public void setUserService(UserService userService) {}`
- 构造器注入：`@Autowired public UserController(UserService userService) {}`

**详细说明**：参见 [依赖注入实现原理](../04-依赖注入/01-依赖注入实现原理.md)

---

### Q6: @PostConstruct和@PreDestroy注解的作用是什么？

**A**:

- **@PostConstruct**：在Bean初始化时执行（在属性赋值完成后）
- **@PreDestroy**：在Bean销毁时执行（在容器关闭时）

**执行顺序**：
1. 构造器
2. 依赖注入
3. @PostConstruct
4. Bean使用
5. @PreDestroy
6. Bean销毁

**注意**：需要JSR-250支持（spring-context依赖已包含）

---

## 🎯 AOP相关问题

### Q7: Spring AOP的实现原理是什么？

**A**: Spring AOP基于**动态代理**实现：

**JDK动态代理**：
- 适用场景：目标对象实现了接口
- 实现原理：使用`java.lang.reflect.Proxy`创建代理对象
- 限制：只能代理接口方法

**CGLIB代理**：
- 适用场景：目标对象没有实现接口
- 实现原理：使用字节码生成库（如CGLIB）创建子类代理
- 限制：无法代理final类和final方法

**详细说明**：参见 [AOP核心概念与术语详解](../06-AOP原理/01-AOP核心概念与术语详解.md)

---

### Q8: @Transactional注解为什么失效？

**A**: 常见失效场景：

1. **同类方法调用**：AOP代理不生效
   ```java
   @Transactional
   public void methodA() {
       methodB();  // 失效
   }
   ```

2. **方法不是public**：Spring AOP只支持public方法

3. **方法是final**：CGLIB无法代理final方法

4. **异常被捕获**：异常不传播到事务拦截器
   ```java
   @Transactional
   public void method() {
       try {
           // ...
       } catch (Exception e) {
           // 失效：异常被捕获
       }
   }
   ```

5. **使用了错误的事务管理器**

**解决方案**：
- 通过注入self调用：`self.methodB()`
- 不捕获异常，让异常传播
- 使用@Transactional时明确指定rollbackFor

**详细说明**：参见 [@Transactional注解原理深度解析](../05-事务管理/02-Transactional注解原理深度解析.md)

---

### Q9: @Transactional注解的默认回滚规则是什么？

**A**: 默认回滚规则：

- ✅ **回滚**：RuntimeException和Error
- ❌ **不回滚**：检查型异常（Checked Exception）

**自定义回滚规则**：
```java
@Transactional(rollbackFor = Exception.class)  // 所有异常都回滚
@Transactional(noRollbackFor = BusinessException.class)  // 指定异常不回滚
```

---

## 🎯 事务管理相关问题

### Q10: Spring事务传播机制有哪些？

**A**: 7种事务传播级别：

| 传播级别 | 说明 | 使用场景 |
|---------|------|---------|
| REQUIRED（默认） | 如果当前存在事务，加入事务；否则创建新事务 | 大多数场景 |
| REQUIRES_NEW | 总是创建新事务，挂起当前事务 | 独立事务场景 |
| NESTED | 如果当前存在事务，创建嵌套事务；否则创建新事务 | 部分回滚场景 |
| SUPPORTS | 如果当前存在事务，加入事务；否则非事务执行 | 查询场景 |
| NOT_SUPPORTED | 总是非事务执行，挂起当前事务 | 不需要事务的场景 |
| NEVER | 总是非事务执行，如果存在事务抛出异常 | 强制非事务场景 |
| MANDATORY | 必须在事务中执行，否则抛出异常 | 强制事务场景 |

**详细说明**：参见 [事务传播机制深度解析](../05-事务管理/03-事务传播机制深度解析.md)

---

### Q11: @Transactional和@TransactionalEventListener的区别是什么？

**A**:

- **@Transactional**：声明式事务管理，确保方法在事务中执行
- **@TransactionalEventListener**：事务事件监听器，在事务提交后触发事件

**使用场景**：
- 使用@Transactional保证数据一致性
- 使用@TransactionalEventListener在事务提交后发送通知

---

## 🎯 设计模式相关问题

### Q12: Spring中使用了哪些设计模式？

**A**: Spring中使用的核心设计模式：

| 设计模式 | 应用场景 | 示例 |
|---------|---------|------|
| **工厂模式** | BeanFactory创建Bean | `getBean()` |
| **单例模式** | 默认Bean作用域 | `@Scope("singleton")` |
| **代理模式** | AOP实现 | JDK动态代理、CGLIB代理 |
| **模板方法模式** | 容器刷新流程 | `AbstractApplicationContext.refresh()` |
| **观察者模式** | 事件监听 | `ApplicationEvent` |
| **策略模式** | 不同事务管理器 | `PlatformTransactionManager` |
| **适配器模式** | 不同HandlerAdapter | `RequestMappingHandlerAdapter` |
| **装饰器模式** | BeanWrapper包装 | `BeanWrapper` |

**详细说明**：参见 [Spring中设计模式使用分析](../08-源码分析/05-Spring中设计模式使用分析.md)

---

### Q13: Spring中的单例Bean是线程安全的吗？

**A**: Spring单例Bean本身**不是线程安全的**：

- Spring容器只保证单例Bean的唯一性
- 线程安全需要开发者自己保证

**解决方案**：

1. **使用局部变量**：避免实例变量
2. **使用ThreadLocal**：线程局部变量
3. **使用同步锁**：synchronized
4. **使用并发集合**：ConcurrentHashMap
5. **使用不可变对象**：final

---

## 🎯 性能优化相关问题

### Q14: 如何优化Spring应用性能？

**A**: 性能优化建议：

1. **延迟初始化**：
   ```java
   @Lazy
   @Component
   public class ExpensiveService {}
   ```

2. **使用原型作用域**：
   ```java
   @Scope("prototype")
   @Component
   public class PrototypeService {}
   ```

3. **避免循环依赖**：重构设计

4. **合理使用AOP**：避免过宽的切入点

5. **使用@Autowired(required = false)**：避免强制依赖

6. **使用构造器注入**：避免字段注入

7. **合理配置事务**：
   - 查询操作使用只读事务
   - 设置合理的超时时间
   - 事务粒度要小

---

### Q15: Spring应用启动慢怎么办？

**A**: 排查和优化：

1. **启用启动分析**：
   ```java
   @SpringBootApplication
   public class Application {
       public static void main(String[] args) {
           new SpringApplicationBuilder(Application.class)
               .logStartupInfo(false)
               .run(args);
       }
   }
   ```

2. **延迟初始化**：
   ```properties
   spring.main.lazy-initialization=true
   ```

3. **排除不需要的自动配置**：
   ```java
   @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
   ```

4. **减少Bean扫描范围**：
   ```java
   @SpringBootApplication(scanBasePackages = "com.example.core")
   ```

5. **使用SpringBoot DevTools**：
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-devtools</artifactId>
   </dependency>
   ```

---

## 🎯 其他常见问题

### Q16: ApplicationContext和BeanFactory的区别是什么？

**A**:

| 特性 | BeanFactory | ApplicationContext |
|------|-------------|---------------------|
| 实例化Bean | 延迟实例化 | 启动时实例化 |
| 功能 | 基础功能 | 增强功能（事件、国际化等） |
| 性能 | 启动快，首次获取慢 | 启动慢，首次获取快 |
| 使用场景 | 资源受限环境 | 生产环境 |

**推荐**：生产环境使用ApplicationContext

---

### Q17: @Component、@Service、@Repository、@Controller的区别是什么？

**A**:

| 注解 | 说明 | 使用场景 |
|------|------|---------|
| @Component | 通用组件注解 | 任何组件 |
| @Service | 服务层注解 | 业务逻辑层 |
| @Repository | 持久层注解 | 数据访问层（自动转换异常） |
| @Controller | 控制层注解 | Web层（MVC） |

**本质**：@Service、@Repository、@Controller都是@Component的元注解

---

### Q18: @Value和@Autowired的区别是什么？

**A**:

| 特性 | @Value | @Autowired |
|------|--------|-----------|
| 作用 | 注入配置值 | 注入Bean |
| 来源 | 配置文件、环境变量 | Spring容器 |
| 类型 | 简单类型、字符串 | 任意Bean |
| 可选性 | 可以设置默认值 | 可以设置required=false |

**示例**：
```java
@Value("${app.name}")
private String appName;

@Autowired
private UserService userService;
```

---

### Q19: Spring Boot自动配置原理是什么？

**A**: Spring Boot自动配置基于**@EnableAutoConfiguration**：

1. **启动时加载**：`@SpringBootApplication`包含`@EnableAutoConfiguration`
2. **读取配置**：`META-INF/spring.factories`（2.x）或`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x）
3. **条件装配**：根据`@Conditional`注解决定是否生效
4. **注册Bean**：符合条件的自动配置类注册Bean

**详细说明**：参见 [SpringBoot自动配置原理详解](../07-高级特性/02-SpringBoot自动配置原理详解.md)

---

### Q20: 如何实现自定义注解？

**A**: 示例：

```java
// 1. 定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogExecutionTime {
    String value() default "";
}

// 2. 定义切面
@Aspect
@Component
public class LogExecutionTimeAspect {

    @Around("@annotation(logExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, LogExecutionTime logExecutionTime) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();
        System.out.println("执行时间：" + (end - start) + "ms");
        return result;
    }
}

// 3. 使用注解
@Service
public class UserService {

    @LogExecutionTime
    public void method() {
        // ...
    }
}
```

---

## 🔗 扩展阅读

- [Spring容器启动流程详解](../01-核心容器/01-Spring容器启动流程详解.md)
- [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)
- [循环依赖解决机制详解](../04-依赖注入/05-循环依赖解决机制详解.md)
- [AOP核心概念与术语详解](../06-AOP原理/01-AOP核心概念与术语详解.md)
- [@Transactional注解原理深度解析](../05-事务管理/02-Transactional注解原理深度解析.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐（入门级）
