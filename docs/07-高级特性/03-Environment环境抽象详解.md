# Environment环境抽象详解

## 🎯 核心作用

`Environment`是Spring的环境抽象接口，它**统一了不同环境下的配置属性访问方式**，支持 profiles（环境配置）和 properties（配置属性）。

**核心作用**：
- 统一访问配置属性（application.properties、application.yml、环境变量等）
- 支持 profiles（dev、test、prod等环境）
- 类型安全的属性绑定（@ConfigurationProperties）
- 属性占位符解析（${...}）

---

## 📍 Environment接口定义

### 接口源码

```java
public interface Environment extends PropertyResolver {

    /**
     * 返回此环境中激活的一组profiles
     */
    String[] getActiveProfiles();

    /**
     * 返回此环境中默认激活的一组profiles
     */
    String[] getDefaultProfiles();

    /**
     * 是否接受给定的profiles
     */
    boolean acceptsProfiles(String... profiles);

    // 继承自PropertyResolver接口的方法
    boolean containsProperty(String key);
    String getProperty(String key);
    String getProperty(String key, String defaultValue);
    <T> T getProperty(String key, Class<T> targetType);
    <T> T getProperty(String key, Class<T> targetType, T defaultValue);
    String getRequiredProperty(String key) throws IllegalStateException;
    <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException;
    String resolvePlaceholders(String text);
    String resolveRequiredPlaceholders(String text) throws IllegalArgumentException;
}
```

---

## 🔄 Environment工作原理

### 配置属性加载优先级

Spring Boot按以下优先级加载配置属性（从高到低）：

```
1. 命令行参数
   --server.port=8081

2. Java系统属性
   System.setProperty("server.port", "8081")

3. 操作系统环境变量
   SERVER_PORT=8081

4. 外部配置文件
   - ./config/application.properties  （最高优先级）
   - ./application.properties
   - classpath:/config/application.properties
   - classpath:/application.properties   （最低优先级）

5. @PropertySource注解
   @PropertySource("classpath:custom.properties")

6. 默认属性
   SpringApplication.setDefaultProperties(...)
```

---

## 🔑 核心源码分析

### 方法1：创建Environment

**位置**：`SpringApplication.prepareEnvironment()`

```java
private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
    ApplicationArguments applicationArguments) {

    // 1. ⭐️ 获取或创建Environment
    ConfigurableEnvironment environment = getOrCreateEnvironment();

    // 2. ⭐️ 配置Environment
    configureEnvironment(environment, applicationArguments.getSourceArgs());

    // 3. ⭐️ 通知监听器（Environment已准备就绪）
    listeners.environmentPrepared(environment);

    // 4. ⭐️ 将Environment绑定到SpringApplication
    return environment;
}
```

---

### 方法2：获取或创建Environment

**位置**：`SpringApplication.getOrCreateEnvironment()`

```java
private ConfigurableEnvironment getOrCreateEnvironment() {
    if (this.environment != null) {
        return this.environment;
    }

    // ⭐️ 根据应用类型创建不同的Environment
    switch (this.webApplicationType) {
        case SERVLET:
            return new StandardServletEnvironment();  // Web应用
        case REACTIVE:
            return new StandardReactiveWebEnvironment();  // 响应式Web应用
        default:
            return new StandardEnvironment();  // 非Web应用
    }
}
```

**Environment继承体系**：
```
Environment (接口)
  │
  └─ ConfigurableEnvironment (接口)
      │
      └─ AbstractEnvironment (抽象类)
          │
          ├─ StandardEnvironment (标准环境)
          │   └─ StandardServletEnvironment (Servlet Web环境)
          │       └─ StandardReactiveWebEnvironment (响应式Web环境)
```

---

### 方法3：配置Environment

**位置**：`SpringApplication.configureEnvironment()`

```java
protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
    // 1. ⭐️ 添加命令行参数PropertySource
    if (this.addCommandLineProperties && args.length > 0) {
        String arguments = new SimpleCommandLinePropertySource(args).getPropertySources();
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
    }

    // 2. ⭐️ 添加profiles
    configureProfiles(environment, args);
}

protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
    // 1. 激活通过spring.profiles.active指定的profiles
    // 2. 添加默认profiles
}
```

---

### 方法4：getProperty() - 获取配置属性

**位置**：`AbstractPropertyResolver.getProperty()`

```java
@Override
public String getProperty(String key) {
    // ⭐️ 从PropertySource中查找属性
    return getProperty(key, String.class);
}

@Override
public <T> T getProperty(String key, Class<T> targetValueType) {
    // ⭐️ 从PropertySource中查找属性，并转换类型
    return getProperty(key, targetValueType, true);
}

@Override
public <T> T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
    if (this.propertySources != null) {
        // ⭐️ 遍历PropertySource（从高到低）
        for (PropertySource<?> propertySource : this.propertySources) {
            Object value = propertySource.getProperty(key);
            if (value != null) {
                // ⭐️ 转换类型
                return convertValueIfNecessary(value, targetValueType);
            }
        }
    }
    return null;
}
```

---

### 方法5：resolvePlaceholders() - 解析占位符

**位置**：`PropertySourcesPropertyResolver.resolvePlaceholders()`

```java
@Override
public String resolvePlaceholders(String text) {
    if (this.propertySources != null && this.text != null) {
        return resolveNestedPlaceholders(text);
    }
    return text;
}

protected String resolveNestedPlaceholders(String text) {
    // ⭐️ 解析${...}占位符
    return this.helper.replacePlaceholders(text, this::getPropertyAsRawString);
}
```

**示例**：
```properties
# application.properties
app.name=MyApp
app.message=${app.name} is running
```

```java
String message = environment.resolvePlaceholders("${app.message}");
// 结果：MyApp is running
```

---

## 🎯 PropertySource详解

### PropertySource继承体系

```
PropertySource (抽象类)
  │
  ├─ CommandLinePropertySource (命令行参数)
  ├─ ServletConfigPropertySource (Servlet配置)
  ├─ ServletContextPropertySource (Servlet上下文)
  ├─ SystemEnvironmentPropertySource (系统环境变量)
  ├─ MapPropertySource (Map属性源)
  │   └─ OriginTrackedMapPropertySource (可追踪来源的Map属性源)
  ├─ PropertiesPropertySource (Properties文件)
  └─ CompositePropertySource (组合属性源)
```

### PropertySource优先级

```java
// StandardEnvironment.prepareCustomServletContext()
protected void prepareCustomServletContext(ServletContext servletContext) {
    // ⭐️ 按优先级添加PropertySource（从高到低）
    MutablePropertySources propertySources = environment.getPropertySources();

    // 1. ServletConfig配置参数（最高优先级）
    propertySources.addLast(new ServletConfigPropertySource("servletConfigInitParams", servletConfig));

    // 2. ServletContext配置参数
    propertySources.addLast(new ServletContextPropertySource("servletContextInitParams", servletContext));

    // 3. JNDI环境变量
    propertySources.addLast(new JndiPropertySource("jndiProperties"));

    // 4. 系统环境变量
    propertySources.addLast(new SystemEnvironmentPropertySource("systemEnvironment"));

    // 5. JVM系统属性
    propertySources.addLast(new MapPropertySource("systemProperties", System.getProperties()));

    // 6. application.properties（默认优先级最低）
    propertySources.addLast(new ResourcePropertySource("applicationConfig: [classpath:/application.properties]"));
}
```

---

## 🎯 Profiles详解

### 什么是Profiles？

Profiles是Spring的条件化配置机制，允许**根据不同环境激活不同的配置**。

### 常见Profiles

- `dev`：开发环境
- `test`：测试环境
- `prod`：生产环境

### 激活Profiles

**方式1：配置文件**
```properties
# application.properties
spring.profiles.active=dev
```

**方式2：命令行参数**
```bash
java -jar app.jar --spring.profiles.active=prod
```

**方式3：环境变量**
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar
```

**方式4：JVM参数**
```bash
java -Dspring.profiles.active=prod -jar app.jar
```

**方式5：编程式**
```java
SpringApplication app = new SpringApplication(MyApplication.class);
app.setAdditionalProfiles("dev");
app.run(args);
```

---

### Profile特定配置文件

Spring Boot支持**Profile特定配置文件**：

```
application.properties              # 通用配置
application-dev.properties         # dev环境配置
application-test.properties        # test环境配置
application-prod.properties        # prod环境配置
```

**加载优先级**：
```
application-prod.properties > application.properties
```

---

### 使用示例

**application.properties**：
```properties
# 通用配置
app.name=MyApp
app.version=1.0.0
```

**application-dev.properties**：
```properties
# 开发环境配置
server.port=8080
spring.datasource.url=jdbc:mysql://localhost:3306/dev_db
logging.level.root=DEBUG
```

**application-prod.properties**：
```properties
# 生产环境配置
server.port=80
spring.datasource.url=jdbc:mysql://prod-server:3306/prod_db
logging.level.root=WARN
```

**使用**：
```java
@RestController
public class InfoController {

    @Value("${server.port}")
    private String serverPort;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @GetMapping("/info")
    public Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("serverPort", serverPort);
        info.put("datasourceUrl", datasourceUrl);
        return info;
    }
}
```

---

## 🎯 @Value注解

### 基本使用

```java
@Component
public class MyComponent {

    // 1. 注入简单值
    @Value("${app.name}")
    private String appName;

    // 2. 注入带默认值的属性
    @Value("${app.timeout:5000}")
    private int timeout;

    // 3. 注入系统属性
    @Value("#{systemProperties['user.home']}")
    private String userHome;

    // 4. 注入表达式结果
    @Value("#{T(Math).random() * 100.0}")
    private double randomValue;

    // 5. 注入其他Bean的属性
    @Value("#{userService.beanName}")
    private String userServiceBeanName;
}
```

---

## 🎯 @ConfigurationProperties注解

### 基本使用

```java
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;
    private String version;
    private int timeout = 5000;
    private List<String> servers = new ArrayList<>();
    private Map<String, String> config = new HashMap<>();

    // getters and setters
}
```

**application.properties**：
```properties
app.name=MyApp
app.version=1.0.0
app.timeout=3000
app.servers[0]=server1.example.com
app.servers[1]=server2.example.com
app.config.key1=value1
app.config.key2=value2
```

### 与@Value的对比

| 特性 | @Value | @ConfigurationProperties |
|------|--------|-------------------------|
| 松散绑定 | ❌ 不支持 | ✅ 支持 |
| 元数据支持 | ❌ 不支持 | ✅ 支持（IDE自动补全） |
| SpEL表达式 | ✅ 支持 | ❌ 不支持 |
| 类型安全 | ❌ 弱 | ✅ 强 |
| 复杂类型 | ❌ 不支持 | ✅ 支持（List、Map等） |
| 校验 | ❌ 不支持 | ✅ 支持（@Validated） |

---

### 松散绑定

```java
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String serverPort;  // server-port、server_port、SERVER_PORT都可以
}
```

**支持的写法**：
```properties
app.server-port=8080
app.server_port=8080
app.SERVER_PORT=8080
```

---

### 校验支持

```java
@Component
@ConfigurationProperties(prefix = "app")
@Validated  // 启用校验
public class AppProperties {

    @NotNull
    private String name;

    @Min(1)
    @Max(100)
    private int timeout;

    @Email
    private String email;

    // getters and setters
}
```

---

## 🎯 @PropertySource注解

### 基本使用

```java
@Configuration
@PropertySource("classpath:custom.properties")
public class MyConfiguration {

    @Value("${custom.property}")
    private String customProperty;
}
```

**custom.properties**：
```properties
custom.property=value
```

### 多个PropertySource

```java
@Configuration
@PropertySources({
    @PropertySource("classpath:config1.properties"),
    @PropertySource("classpath:config2.properties")
})
public class MyConfiguration {
    // ...
}
```

---

## 🔑 Environment使用示例

### 示例1：获取配置属性

```java
@Component
public class EnvironmentExample {

    @Autowired
    private Environment environment;

    public void example() {
        // 1. 获取简单属性
        String appName = environment.getProperty("app.name");

        // 2. 获取带默认值的属性
        int timeout = environment.getProperty("app.timeout", Integer.class, 5000);

        // 3. 检查属性是否存在
        boolean hasProperty = environment.containsProperty("app.name");

        // 4. 获取必需属性（不存在抛出异常）
        String requiredProperty = environment.getRequiredProperty("app.name");

        // 5. 解析占位符
        String message = environment.resolvePlaceholders("${app.name} is running");

        // 6. 获取激活的profiles
        String[] activeProfiles = environment.getActiveProfiles();

        // 7. 检查是否接受某个profile
        boolean isProd = environment.acceptsProfiles("prod");
    }
}
```

---

### 示例2：类型安全的属性绑定

```java
@Component
@ConfigurationProperties(prefix = "datasource")
public class DataSourceProperties {

    private String url;
    private String username;
    private String password;
    private int maximumPoolSize = 10;

    // getters and setters
}
```

**application.properties**：
```properties
datasource.url=jdbc:mysql://localhost:3306/mydb
datasource.username=root
datasource.password=password
datasource.maximum-pool-size=20
```

---

### 示例3：编程式操作Environment

```java
@Component
public class EnvironmentModifier implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // 1. 添加新的PropertySource（最高优先级）
        Map<String, Object> customProperties = new HashMap<>();
        customProperties.put("custom.property", "value");
        environment.getPropertySources().addFirst(
            new MapPropertySource("customProperties", customProperties)
        );

        // 2. 添加新的profile
        environment.addActiveProfile("custom");

        // 3. 遍历所有PropertySource
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            System.out.println("PropertySource: " + propertySource.getName());
        }
    }
}
```

---

## 🔍 调试技巧

### 查看所有配置属性

```java
@RestController
public class EnvironmentController {

    @Autowired
    private Environment environment;

    @GetMapping("/env")
    public Map<String, Object> env() {
        Map<String, Object> result = new HashMap<>();

        // 1. 激活的profiles
        result.put("activeProfiles", environment.getActiveProfiles());

        // 2. 默认profiles
        result.put("defaultProfiles", environment.getDefaultProfiles());

        // 3. 所有系统属性
        Map<String, Object> systemProperties = new HashMap<>();
        System.getProperties().forEach((key, value) ->
            systemProperties.put(key.toString(), value));
        result.put("systemProperties", systemProperties);

        // 4. 所有环境变量
        Map<String, String> envVariables = System.getenv();
        result.put("environmentVariables", envVariables);

        // 5. 所有PropertySource
        List<Map<String, Object>> propertySources = new ArrayList<>();
        for (PropertySource<?> propertySource :
            ((ConfigurableEnvironment) environment).getPropertySources()) {
            Map<String, Object> psInfo = new HashMap<>();
            psInfo.put("name", propertySource.getName());
            psInfo.put("source", propertySource.getSource());
            propertySources.add(psInfo);
        }
        result.put("propertySources", propertySources);

        return result;
    }
}
```

---

## 🎯 总结

### 核心要点

1. ✅ **Environment统一了配置属性访问方式**
2. ✅ **配置属性加载优先级**：命令行参数 > Java系统属性 > 环境变量 > 配置文件
3. ✅ **Profiles支持多环境配置**：dev、test、prod
4. ✅ **@Value用于注入简单属性**
5. ✅ **@ConfigurationProperties用于类型安全的属性绑定**
6. ✅ **支持松散绑定、校验、复杂类型**

### 最佳实践

- ✅ 优先使用`@ConfigurationProperties`
- ✅ 使用`@Value`注入简单值或SpEL表达式
- ✅ 合理使用`profiles`管理多环境配置
- ✅ 使用`application-{profile}.properties`管理特定环境配置
- ✅ 敏感信息使用环境变量

### 扩展阅读

- [条件装配Conditional原理详解](./01-条件装配Conditional原理详解.md)
- [SpringBoot自动配置原理详解](./02-SpringBoot自动配置原理详解.md)
- [Bean设计最佳实践](../09-最佳实践/01-Bean设计最佳实践.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐（高级）
