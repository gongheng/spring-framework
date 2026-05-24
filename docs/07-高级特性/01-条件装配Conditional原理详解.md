# @Conditional条件装配原理详解

## 🎯 核心作用

`@Conditional`注解是Spring的条件装配机制，它允许**根据特定条件决定是否注册Bean**。这是Spring Boot自动配置的核心基础。

**核心作用**：
- 实现Bean的条件化注册
- 支持多个条件的组合（AND、OR）
- 是Spring Boot自动配置的基础

---

## 📍 @Conditional注解定义

### 注解源码

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {
    /**
     * 条件类数组，所有条件都必须满足才能注册Bean
     */
    Class<? extends Condition>[] value();
}
```

### Condition接口

```java
@FunctionalInterface
public interface Condition {
    /**
     * 判断条件是否匹配
     * 
     * @param context 条件上下文
     * @param metadata 元数据注解信息
     * @return true表示条件匹配，false表示条件不匹配
     */
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

---

## 🔄 条件装配工作原理

### 完整流程图

```
Spring容器启动
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions()
  ↓
ConfigurationClassParser.parse()
  ↓
doProcessConfigurationClass()
  ↓
【处理@Bean方法】
  ↓
retrieveBeanMethodMetadata()
  ↓
为每个@Bean方法创建BeanDefinition
  ↓
【关键步骤】检查@Conditional注解 ⭐️
  ↓
shouldSkip(beanDefinition.getMetadata())
  ↓
ConditionEvaluator.shouldSkip()
  ↓
遍历所有@Conditional注解的条件类
  ↓
for (Class<? extends Condition> conditionClass : conditions) {
    Condition condition = conditionClass.newInstance();
    if (!condition.matches(context, metadata)) {
        return true;  // 条件不匹配，跳过这个Bean
    }
}
  ↓
如果所有条件都满足：
  注册Bean到容器
如果任意条件不满足：
  跳过这个Bean，不注册
```

---

## 🔑 核心源码分析

### 方法1：shouldSkip() - 判断是否跳过

**位置**：`ConditionEvaluator.java:47`

```java
public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
    return shouldSkip(metadata, null);
}

public boolean shouldSkip(AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
    // 1. 如果元数据为空或不是注解类型，不跳过
    if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
        return false;
    }

    // 2. 如果没有指定阶段，自动检测
    if (phase == null) {
        if (metadata instanceof AnnotationMetadata &&
                ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
            return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
        }
        return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
    }

    // 3. 获取所有@Conditional注解的条件类
    List<Condition> conditions = new ArrayList<>();
    for (String[] conditionClasses : getConditionClasses(metadata)) {
        for (String conditionClass : conditionClasses) {
            Condition condition = getCondition(conditionClass);
            conditions.add(condition);
        }
    }

    // 4. 对条件进行排序
    AnnotationAwareOrderComparator.sort(conditions);

    // 5. ⭐️ 遍历所有条件，判断是否匹配
    for (Condition condition : conditions) {
        ConfigurationPhase requiredPhase = null;
        if (condition instanceof ConfigurationCondition) {
            requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
        }
        if ((requiredPhase == null || requiredPhase == phase) &&
            !condition.matches(this.context, metadata)) {
            return true;  // 条件不匹配，跳过
        }
    }

    return false;  // 所有条件都满足
}
```

### 方法2：condition.matches() - 条件匹配判断

**位置**：自定义Condition实现类

```java
public class MyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 1. 获取Bean定义注册表
        BeanDefinitionRegistry registry = context.getRegistry();

        // 2. 获取环境信息
        Environment environment = context.getEnvironment();

        // 3. 获取资源加载器
        ResourceLoader resourceLoader = context.getResourceLoader();

        // 4. 获取类加载器
        ClassLoader classLoader = context.getClassLoader();

        // 5. ⭐️ 自定义条件判断逻辑
        boolean hasBean = registry.containsBeanDefinition("dataSource");
        boolean isProd = environment.acceptsProfiles("prod");

        return hasBean && isProd;
    }
}
```

---

## 🎯 常见条件注解

### 1️⃣ @ConditionalOnClass - 类路径存在指定类

```java
@ConditionalOnClass(name = "com.example.MyClass")
// 或者
@ConditionalOnClass(DataSource.class)
```

**Condition实现**：
```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {
    Class<?>[] value() default {};

    String[] name() default {};
}
```

**使用场景**：
```java
@Configuration
@ConditionalOnClass(DataSource.class)  // 类路径上存在DataSource类时才生效
public class DataSourceAutoConfiguration {
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

---

### 2️⃣ @ConditionalOnMissingClass - 类路径不存在指定类

```java
@ConditionalOnMissingClass("com.example.MyClass")
```

**使用场景**：
```java
@Configuration
@ConditionalOnMissingClass("com.example.CustomDataSource")
public class DefaultDataSourceConfiguration {
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

---

### 3️⃣ @ConditionalOnBean - 容器中存在指定Bean

```java
@ConditionalOnBean(DataSource.class)
// 或者
@ConditionalOnBean(name = "dataSource")
```

**Condition实现**：
```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnBeanCondition.class)
public @interface ConditionalOnBean {
    Class<?>[] value() default {};

    String[] name() default {};
}
```

**使用场景**：
```java
@Configuration
@ConditionalOnBean(DataSource.class)  // 容器中存在DataSource Bean时才生效
public class JdbcTemplateConfiguration {
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

---

### 4️⃣ @ConditionalOnMissingBean - 容器中不存在指定Bean

```java
@ConditionalOnMissingBean(DataSource.class)
// 或者
@ConditionalOnMissingBean(name = "dataSource")
```

**使用场景**：
```java
@Configuration
@ConditionalOnMissingBean(DataSource.class)  // 容器中不存在DataSource Bean时才生效
public class DefaultDataSourceConfiguration {
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

---

### 5️⃣ @ConditionalOnProperty - 配置属性匹配

```java
@ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
```

**Condition实现**：
```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnPropertyCondition.class)
public @interface ConditionalOnProperty {
    String name();  // 配置属性名

    String havingValue() default "";  // 期望的值

    boolean matchIfMissing() default true;  // 如果配置不存在，是否匹配
}
```

**使用场景**：
```java
@Configuration
@ConditionalOnProperty(
    name = "spring.datasource.url",
    matchIfMissing = false  // 如果配置不存在，不生效
)
public class DataSourceConfiguration {
    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

---

### 6️⃣ @ConditionalOnResource - 资源存在

```java
@ConditionalOnResource(resources = "classpath:META-INF/config.xml")
```

**使用场景**：
```java
@Configuration
@ConditionalOnResource(resources = "classpath:META-INF/persistence.xml")
public class JpaConfiguration {
    @Bean
    public EntityManagerFactory entityManagerFactory() {
        return new EntityManagerFactory();
    }
}
```

---

### 7️⃣ @ConditionalOnExpression - SpEL表达式

```java
@ConditionalOnExpression("'${environment}' == 'production' and '${feature.enabled}' == 'true'")
```

**使用场景**：
```java
@Configuration
@ConditionalOnExpression("${cache.enabled:false} and '${cache.type}' == 'redis'")
public class RedisCacheConfiguration {
    @Bean
    public CacheManager cacheManager() {
        return new RedisCacheManager();
    }
}
```

---

### 8️⃣ @ConditionalOnJava - Java版本匹配

```java
@ConditionalOnJava(JavaVersion.EIGHT)
```

**使用场景**：
```java
@Configuration
@ConditionalOnJava(JavaVersion.EIGHT)  // Java 8及以上版本
public class Java8Configuration {
    @Bean
    public DateTimeFormatter dateTimeFormatter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    }
}
```

---

### 9️⃣ @ConditionalOnWebApplication - Web应用

```java
@ConditionalOnWebApplication
```

**使用场景**：
```java
@Configuration
@ConditionalOnWebApplication  // 仅在Web应用中生效
public class WebConfiguration {
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
```

---

### 🔟 @ConditionalOnNotWebApplication - 非Web应用

```java
@ConditionalOnNotWebApplication
```

**使用场景**：
```java
@Configuration
@ConditionalOnNotWebApplication  // 仅在非Web应用中生效
public class StandaloneConfiguration {
    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            System.out.println("Standalone application started");
        };
    }
}
```

---

## 🎯 自定义条件注解

### 步骤1：实现Condition接口

```java
public class DatabaseTypeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 1. 获取环境信息
        Environment environment = context.getEnvironment();

        // 2. 读取配置属性
        String databaseType = environment.getProperty("app.database.type");

        // 3. 获取注解元数据
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
            ConditionalOnDatabaseType.class.getName());

        // 4. 获取期望的数据库类型
        String expectedType = (String) attributes.get("type");

        // 5. 判断是否匹配
        return expectedType.equals(databaseType);
    }
}
```

### 步骤2：定义自定义注解

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(DatabaseTypeCondition.class)
public @interface ConditionalOnDatabaseType {
    String type();
}
```

### 步骤3：使用自定义注解

```java
@Configuration
public class DataSourceConfiguration {

    @Bean
    @ConditionalOnDatabaseType(type = "mysql")
    public DataSource mysqlDataSource() {
        return new MysqlDataSource();
    }

    @Bean
    @ConditionalOnDatabaseType(type = "postgresql")
    public DataSource postgresqlDataSource() {
        return new PostgresqlDataSource();
    }
}
```

---

## 🎯 条件组合

### AND组合（所有条件都满足）

```java
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(name = "spring.datasource.url")
@ConditionalOnMissingBean(DataSource.class)
public class DataSourceAutoConfiguration {
    // 所有条件都满足时才生效
}
```

### OR组合（任意条件满足）

```java
public class OrCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 获取所有条件类
        List<Class<? extends Condition>> conditionClasses = getConditionClasses(metadata);

        // 任意一个条件满足即可
        for (Class<? extends Condition> conditionClass : conditionClasses) {
            Condition condition = BeanUtils.instantiateClass(conditionClass);
            if (condition.matches(context, metadata)) {
                return true;
            }
        }

        return false;
    }
}

// 使用
@Configuration
@Conditional(OrCondition.class)
public class OrConditionConfiguration {
    // 任意一个条件满足时生效
}
```

---

## 🔑 ConditionContext详解

### ConditionContext接口

```java
public interface ConditionContext {
    /**
     * 获取Bean定义注册表
     */
    BeanDefinitionRegistry getRegistry();

    /**
     * 获取环境信息
     */
    Environment getEnvironment();

    /**
     * 获取资源加载器
     */
    ResourceLoader getResourceLoader();

    /**
     * 获取类加载器
     */
    ClassLoader getClassLoader();
}
```

### 使用示例

```java
public class ComplexCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 1. 检查Bean定义
        BeanDefinitionRegistry registry = context.getRegistry();
        boolean hasDataSource = registry.containsBeanDefinition("dataSource");

        // 2. 检查环境属性
        Environment environment = context.getEnvironment();
        boolean isProd = environment.acceptsProfiles("prod");
        String dbType = environment.getProperty("app.database.type");

        // 3. 检查资源
        ResourceLoader resourceLoader = context.getResourceLoader();
        boolean hasConfigFile = resourceLoader.getResource("classpath:config.xml").exists();

        // 4. 检查类
        ClassLoader classLoader = context.getClassLoader();
        boolean hasJdbcDriver = isClassAvailable("com.mysql.cj.jdbc.Driver", classLoader);

        // 5. 组合条件
        return hasDataSource && isProd && "mysql".equals(dbType) && hasConfigFile && hasJdbcDriver;
    }

    private boolean isClassAvailable(String className, ClassLoader classLoader) {
        try {
            classLoader.loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

---

## 🎯 Spring Boot自动配置中的应用

### 自动配置示例

```java
@Configuration
@ConditionalOnClass(DataSource.class)  // 类路径上存在DataSource
@ConditionalOnProperty(name = "spring.datasource.url")  // 配置了数据源URL
@ConditionalOnMissingBean(DataSource.class)  // 容器中不存在DataSource
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return createDataSource(properties);
    }
}
```

### 自动配置原理

```
Spring Boot启动
  ↓
读取META-INF/spring.factories（2.x）或
读取META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports（3.x）
  ↓
加载所有自动配置类
  ↓
每个自动配置类上都有@Conditional注解
  ↓
根据条件判断是否生效
  ↓
例如：
  - DataSourceAutoConfiguration
    - @ConditionalOnClass(DataSource.class)  ✅ 存在
    - @ConditionalOnProperty(name = "spring.datasource.url")  ❌ 不存在
    - 结论：不生效，不会创建DataSource Bean
```

---

## 🎯 实战示例

### 示例1：根据环境选择数据源

```java
@Configuration
public class DataSourceConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.database.type", havingValue = "mysql")
    @ConditionalOnClass(com.mysql.cj.jdbc.Driver.class)
    public DataSource mysqlDataSource() {
        return new MysqlDataSource();
    }

    @Bean
    @ConditionalOnProperty(name = "app.database.type", havingValue = "postgresql")
    @ConditionalOnClass(org.postgresql.Driver.class)
    public DataSource postgresqlDataSource() {
        return new PostgresqlDataSource();
    }
}
```

### 示例2：功能开关

```java
@Configuration
@ConditionalOnProperty(name = "feature.cache.enabled", havingValue = "true", matchIfMissing = false)
@EnableCaching
public class CacheConfiguration {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
```

### 示例3：自定义条件

```java
// 自定义条件
public class SystemPropertyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String propertyName = (String) metadata.getAnnotationAttributes(
            ConditionalOnSystemProperty.class.getName()).get("value");
        String propertyValue = System.getProperty(propertyName);
        return "true".equals(propertyValue);
    }
}

// 自定义注解
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(SystemPropertyCondition.class)
public @interface ConditionalOnSystemProperty {
    String value();
}

// 使用
@Configuration
@ConditionalOnSystemProperty("app.feature.enabled")
public class FeatureConfiguration {
    // 只有系统属性app.feature.enabled=true时才生效
}
```

---

## 🔍 调试技巧

### 启用条件评估报告

```properties
# application.properties
debug=true
```

**输出**：
```
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------
   DataSourceAutoConfiguration matched
      - @ConditionalOnClass found required class 'javax.sql.DataSource' (OnClassCondition)

Negative matches:
-----------------
   RedisAutoConfiguration did not match
      - @ConditionalOnClass did not find required class 'org.springframework.data.redis.connection.RedisConnectionFactory' (OnClassCondition)
```

### 编程式查看条件

```java
@Component
public class ConditionDebugger implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ConfigurableListableBeanFactory beanFactory = event.getApplicationContext().getBeanFactory();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            System.out.println("Bean: " + beanName);
            System.out.println("  - Scope: " + beanDefinition.getScope());
            System.out.println("  - Resource: " + beanDefinition.getResourceDescription());
        }
    }
}
```

---

## 🎯 总结

### 核心要点

1. ✅ **@Conditional是条件装配的核心**：根据条件决定是否注册Bean
2. ✅ **Condition接口**：实现matches()方法自定义条件
3. ✅ **常见条件注解**：
   - @ConditionalOnClass
   - @ConditionalOnMissingClass
   - @ConditionalOnBean
   - @ConditionalOnMissingBean
   - @ConditionalOnProperty
   - @ConditionalOnResource
   - @ConditionalOnExpression
   - @ConditionalOnJava
4. ✅ **Spring Boot自动配置的基础**：大量使用@Conditional注解
5. ✅ **支持条件组合**：AND、OR等逻辑

### 最佳实践

- ✅ 优先使用Spring Boot提供的条件注解
- ✅ 自定义条件时要注意性能
- ✅ 使用debug=true查看条件评估报告
- ✅ 条件要尽量简单明确

### 扩展阅读

- [SpringBoot自动配置原理详解](./02-SpringBoot自动配置原理详解.md)
- [Environment环境抽象详解](./03-Environment环境抽象详解.md)
- [@Transactional注解原理深度解析](../05-事务管理/02-Transactional注解原理深度解析.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐（高级）
