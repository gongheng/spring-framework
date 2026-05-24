# Spring容器启动流程详解

## 🎯 核心概念

Spring容器的启动过程是理解Spring Framework的关键，无论是Spring Boot应用还是传统的Spring应用，最终都会走到`AbstractApplicationContext.refresh()`这个核心方法。

**核心观点**：所有Spring应用（Boot和非Boot）都会执行`AbstractApplicationContext.refresh()`，但细节实现不同。

---

## 📊 ApplicationContext家族与继承体系

### 继承体系概览

```
ApplicationContext (接口)
  │
  ├─ ConfigurableApplicationContext (接口)
  │   │
  │   └─ AbstractApplicationContext (抽象类) ⭐️ 核心抽象类
  │       │
  │       ├─ AbstractRefreshableApplicationContext
  │       │   └─ AbstractXmlApplicationContext
  │       │       └─ ClassPathXmlApplicationContext 【传统XML应用】
  │       │
  │       └─ GenericApplicationContext 【现代应用使用】
  │           ├─ AnnotationConfigApplicationContext 【传统注解应用】
  │           └─ GenericWebApplicationContext
  │               └─ AnnotationConfigServletWebServerApplicationContext 【SpringBoot应用】
```

### 关键区别

| 特性 | AbstractRefreshableApplicationContext | GenericApplicationContext |
|------|--------------------------------------|--------------------------|
| **BeanFactory创建时机** | refresh()时创建 | 构造函数中创建 |
| **refreshBeanFactory()实现** | 创建新BeanFactory并加载Bean定义 | 只设置序列化ID |
| **Bean定义加载时机** | refreshBeanFactory()中加载 | invokeBeanFactoryPostProcessors()中加载 |
| **支持多次refresh()** | ✅ 支持 | ❌ 不支持 |
| **应用场景** | 传统XML应用 | 注解应用、SpringBoot应用 |

---

## 🔄 refresh()方法的12个步骤

### 完整流程图

```
AbstractApplicationContext.refresh()
│
├─ 1. prepareRefresh()
│   ├─ 设置启动时间
│   ├─ 初始化属性源
│   └─ 验证必需属性
│
├─ 2. obtainFreshBeanFactory()⭐️ 关键步骤
│   ├─ 调用refreshBeanFactory() 【子类实现不同】
│   └─ 返回BeanFactory实例
│
├─ 3. prepareBeanFactory(beanFactory)
│   ├─ 设置类加载器
│   ├─ 设置Bean表达式解析器
│   ├─ 添加BeanPostProcessor
│   └─ 注册依赖接口
│
├─ 4. postProcessBeanFactory(beanFactory)
│   └─ 子类可以重写，添加特殊处理
│
├─ 5. invokeBeanFactoryPostProcessors(beanFactory)⭐️ 最关键
│   ├─ 执行BeanFactoryPostProcessor
│   ├─ ConfigurationClassPostProcessor.processConfigBeanDefinitions()
│   ├─ 解析配置类注解
│   ├─ ComponentScan扫描
│   └─ 加载Bean定义
│
├─ 6. registerBeanPostProcessors(beanFactory)
│   └─ 注册BeanPostProcessor
│
├─ 7. initMessageSource()
│   └─ 初始化国际化资源
│
├─ 8. initApplicationEventMulticaster()
│   └─ 初始化事件广播器
│
├─ 9. onRefresh()
│   └─ 子类可以重写，刷新特殊资源
│
├─ 10. registerListeners()
│   └─ 注册事件监听器
│
├─ 11. finishBeanFactoryInitialization(beanFactory)⭐️ 关键步骤
│   ├─ 实例化所有非懒加载的单例Bean
│   ├─ createBeanInstance()
│   ├─ populateBean()
│   └─ initializeBean()
│
└─ 12. finishRefresh()
    ├─ 发布ContextRefreshedEvent
    ├─ 启动生命周期Bean
    └─ 发布其他事件
```

### 详细步骤说明

#### 步骤1：prepareRefresh() - 准备刷新

**位置**：`AbstractApplicationContext.java:590`

**作用**：
- 记录容器启动时间
- 设置容器的active和closed标志
- 初始化属性源（PropertySource）
- 验证必需的属性

**代码示例**：
```java
protected void prepareRefresh() {
    this.startupDate = System.currentTimeMillis();
    this.closed.set(false);
    this.active.set(true);

    initPropertySources(); // 初始化属性源
    getEnvironment().validateRequiredProperties(); // 验证必需属性
}
```

#### 步骤2：obtainFreshBeanFactory() - 获取新的BeanFactory

**位置**：`AbstractApplicationContext.java:593`

**作用**：
- 调用子类的refreshBeanFactory()方法
- 返回新的BeanFactory实例

**关键点**：这是模板方法模式的核心，不同的ApplicationContext实现有不同的refreshBeanFactory()实现

##### 传统XML应用的实现

**位置**：`AbstractRefreshableApplicationContext.java:121`

```java
protected final void refreshBeanFactory() throws BeansException {
    // 销毁旧的BeanFactory
    if (hasBeanFactory()) {
        destroyBeans();
        closeBeanFactory();
    }

    // 创建新的BeanFactory
    DefaultListableBeanFactory beanFactory = createBeanFactory();
    beanFactory.setSerializationId(getId());
    customizeBeanFactory(beanFactory);

    // ⭐️ 从XML文件加载Bean定义
    loadBeanDefinitions(beanFactory);

    this.beanFactory = beanFactory;
}
```

##### 注解应用和Boot应用的实现

**位置**：`GenericApplicationContext.java:296`

```java
@Override
protected final void refreshBeanFactory() throws IllegalStateException {
    // 只设置序列化ID
    if (!this.refreshed.compareAndSet(false, true)) {
        throw new IllegalStateException("只允许刷新一次");
    }
    this.beanFactory.setSerializationId(getId());
    // ⭐️ 不加载Bean定义，Bean定义在invokeBeanFactoryPostProcessors()中加载
}
```

**为什么设计不同？**

- **XML应用**：XML配置是外部资源文件，需要从XML文件读取Bean定义，支持多次刷新重新加载配置
- **注解应用**：注解配置是编程式的，Bean定义通过扫描注解获得，在内存中，不需要每次refresh()都重新扫描

#### 步骤3：prepareBeanFactory() - 准备BeanFactory

**位置**：`AbstractApplicationContext.java:596`

**作用**：
- 设置类加载器
- 设置Bean表达式解析器（SpEL）
- 添加PropertyEditorRegistrar
- 添加BeanPostProcessor（ApplicationContextAwareProcessor）
- 注册依赖接口（BeanFactory、ResourceLoader等）
- 忽略自动装配的接口

**关键代码**：
```java
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 设置类加载器
    beanFactory.setBeanClassLoader(getClassLoader());

    // 设置Bean表达式解析器
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver());

    // 添加PropertyEditorRegistrar
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this));

    // 添加BeanPostProcessor
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

    // 注册依赖接口
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // 忽略自动装配的接口
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
}
```

#### 步骤4：postProcessBeanFactory() - 后处理BeanFactory

**位置**：`AbstractApplicationContext.java:600`

**作用**：
- 子类可以重写此方法
- 在标准初始化之后修改BeanFactory
- 添加特殊的BeanPostProcessor

**典型实现**：
```java
// AbstractRefreshableWebApplicationContext
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext, this.servletConfig));
    beanFactory.ignoreDependencyInterface(ServletContextAware.class);
}
```

#### 步骤5：invokeBeanFactoryPostProcessors() - 调用BeanFactoryPostProcessor

**位置**：`AbstractApplicationContext.java:604`

**作用**：
- 执行所有注册的BeanFactoryPostProcessor
- **ConfigurationClassPostProcessor是最重要的一个**
- 扫描组件、加载Bean定义

**核心调用链**：
```
invokeBeanFactoryPostProcessors(beanFactory)
  ↓
PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors()
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions()
  ↓
ConfigurationClassParser.parse()
  ↓
ComponentScanAnnotationParser.parse()
  ↓
ClassPathBeanDefinitionScanner.doScan()
  ↓
scanCandidateComponents()
  ↓
扫描.class文件，创建BeanDefinition
  ↓
注册到BeanFactory
```

**详细说明**：参见 [ConfigurationClassPostProcessor深度解析](../03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md)

#### 步骤6：registerBeanPostProcessors() - 注册BeanPostProcessor

**位置**：`AbstractApplicationContext.java:606`

**作用**：
- 实例化并注册所有BeanPostProcessor
- 按优先级排序
- 区分内部BeanPostProcessor和普通BeanPostProcessor

**关键点**：
- BeanPostProcessor在Bean创建时会被调用
- 必须在Bean实例化之前注册完成

#### 步骤7：initMessageSource() - 初始化消息源

**位置**：`AbstractApplicationContext.java:616`

**作用**：
- 初始化国际化资源
- 支持多语言

#### 步骤8：initApplicationEventMulticaster() - 初始化事件广播器

**位置**：`AbstractApplicationContext.java:619`

**作用**：
- 初始化事件广播器
- 用于发布和监听应用事件

#### 步骤9：onRefresh() - 刷新特殊资源

**位置**：`AbstractApplicationContext.java:622`

**作用**：
- 子类可以重写
- 刷新特殊的资源

**典型实现**：
```java
// ServletWebServerApplicationContext
protected void onRefresh() {
    super.onRefresh();
    createWebServer(); // 创建Web服务器
}
```

#### 步骤10：registerListeners() - 注册监听器

**位置**：`AbstractApplicationContext.java:624`

**作用**：
- 注册所有事件监听器
- 发布早期应用事件

#### 步骤11：finishBeanFactoryInitialization() - 完成BeanFactory初始化

**位置**：`AbstractApplicationContext.java:626`

**作用**：
- 实例化所有非懒加载的单例Bean
- 完成BeanFactory的初始化

**核心调用链**：
```
finishBeanFactoryInitialization(beanFactory)
  ↓
preInstantiateSingletons()
  ↓
getBean(beanName)
  ↓
createBean(beanName, mbd, args)
  ↓
doCreateBean(beanName, mbdToUse, args)
  ↓
createBeanInstance() 【创建Bean实例】
  ↓
populateBean() 【填充属性】
  ↓
initializeBean() 【初始化Bean】
```

**详细说明**：参见 [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)

#### 步骤12：finishRefresh() - 完成刷新

**位置**：`AbstractApplicationContext.java:628`

**作用**：
- 发布ContextRefreshedEvent事件
- 启动生命周期Bean
- 发布其他事件

---

## 🎯 三种Spring应用的启动流程对比

### 应用类型1：传统Spring XML应用

```java
// 用户代码
public static void main(String[] args) {
    ApplicationContext ctx = new ClassPathXmlApplicationContext("beans.xml");
}
```

**refresh()流程**：
```
AbstractApplicationContext.refresh()
  ↓
obtainFreshBeanFactory()
  ↓
AbstractRefreshableApplicationContext.refreshBeanFactory()
  ├─ 销毁旧的BeanFactory（如果存在）
  ├─ 创建新的BeanFactory：createBeanFactory()
  ├─ 设置序列化ID
  ├─ 自定义Bean工厂配置
  └─ ⭐️ loadBeanDefinitions(beanFactory) 【从XML加载Bean定义】
     ↓
     XmlBeanDefinitionReader.loadBeanDefinitions("beans.xml")
     ↓
     解析XML文件
     ↓
     为每个<bean>标签创建BeanDefinition
     ↓
     注册到BeanFactory
  ↓
invokeBeanFactoryPostProcessors(beanFactory)
  └─ BeanFactory已经包含所有Bean定义（从XML加载的）
     └─ 可能有一些后处理，但不会扫描新的Bean定义
  ↓
finishBeanFactoryInitialization(beanFactory)
  ↓
实例化所有非懒加载的单例Bean
```

**关键点**：
- ✅ Bean定义在refreshBeanFactory()中从XML加载
- ✅ invokeBeanFactoryPostProcessors()主要用于后处理，不扫描新Bean

### 应用类型2：传统Spring注解应用

```java
// 用户代码
public static void main(String[] args) {
    ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);
}
```

**refresh()流程**：
```
AnnotationConfigApplicationContext构造函数
  ↓
创建DefaultListableBeanFactory 【BeanFactory在这里创建好了】
  ↓
AnnotatedBeanDefinitionReader.register(AppConfig.class) 【注册主配置类】
  ↓
registerBean()
  ↓
为AppConfig创建BeanDefinition
  ↓
注册到BeanFactory
  ↓
refresh()
  ↓
AbstractApplicationContext.refresh()
  ↓
obtainFreshBeanFactory()
  ↓
GenericApplicationContext.refreshBeanFactory()
  ├─ 只设置序列化ID：beanFactory.setSerializationId(getId())
  ├─ 不重新创建BeanFactory
  └─ ⭐️ 不加载Bean定义！
  ↓
invokeBeanFactoryPostProcessors(beanFactory) 【第604行 - ⭐️ Bean定义加载的入口】
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions()
  ├─ 从已有的Bean定义中找到配置类（AppConfig）
  ├─ 解析配置类的注解（@ComponentScan、@Import、@Bean）
  ├─ ⭐️ 执行ComponentScan扫描
  │    ├─ ComponentScanAnnotationParser.parse()
  │    ├─ ClassPathBeanDefinitionScanner.doScan()
  │    ├─ scanCandidateComponents()
  │    └─ 扫描.class文件，创建BeanDefinition
  ├─ 处理@Import注解
  ├─ 处理@Bean方法
  └─ 注册所有Bean定义到BeanFactory
  ↓
finishBeanFactoryInitialization(beanFactory)
  ↓
实例化所有非懒加载的单例Bean
```

**关键点**：
- ✅ Bean定义在invokeBeanFactoryPostProcessors()中扫描加载
- ✅ refreshBeanFactory()不加载Bean定义，只设置序列化ID

### 应用类型3：SpringBoot应用

```java
// 用户代码
@SpringBootApplication
public class TodoApplication {
    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
```

**启动流程**：
```
SpringApplication.run(TodoApplication.class, args) 【SpringApplication:304】
  ↓
createApplicationContext()
  ↓
AnnotationConfigServletWebServerApplicationContext创建
  ↓
GenericApplicationContext构造函数
  ↓
创建DefaultListableBeanFactory 【BeanFactory在这里创建好了】
  ↓
prepareContext()
  ↓
BeanDefinitionLoader.load(TodoApplication.class) 【注册主配置类】
  ├─ AnnotatedBeanDefinitionReader.register(TodoApplication.class)
  ├─ 为TodoApplication创建BeanDefinition
  └─ 注册到BeanFactory
  ↓
refreshContext(context)
  ↓
AbstractApplicationContext.refresh()
  ↓
obtainFreshBeanFactory()
  ↓
GenericApplicationContext.refreshBeanFactory()
  ├─ 只设置序列化ID：beanFactory.setSerializationId(getId())
  ├─ 不重新创建BeanFactory
  └─ ⭐️ 不加载Bean定义！
  ↓
invokeBeanFactoryPostProcessors(beanFactory) 【第604行 - ⭐️ Bean定义加载的入口】
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions()
  ├─ 从已有的Bean定义中找到配置类（TodoApplication）
  ├─ 解析TodoApplication的@SpringBootApplication注解
  ├─ ⭐️ 处理@ComponentScan注解（自动扫描）
  │    ├─ 解析basePackages（默认扫描com.example.todo包）
  │    ├─ ComponentScanAnnotationParser.parse()
  │    ├─ ClassPathBeanDefinitionScanner.doScan()
  │    ├─ scanCandidateComponents()
  │    ├─ 扫描.class文件：classpath*:com/example/todo/**/*.class
  │    ├─ 使用ASM读取类元数据
  │    ├─ 检查注解：@Component、@Service、@Repository、@Controller
  │    ├─ 创建BeanDefinition
  │    └─ 注册到BeanFactory
  ├─ 处理@Import注解（导入自动配置类）
  ├─ 处理@Bean方法
  └─ 注册所有Bean定义到BeanFactory
  ↓
finishBeanFactoryInitialization(beanFactory)
  ↓
实例化所有非懒加载的单例Bean
  ↓
finishRefresh()
  ↓
启动Web服务器（内嵌Tomcat）
  ↓
✅ 容器启动完成！
```

**关键点**：
- ✅ Bean定义在invokeBeanFactoryPostProcessors()中扫描加载
- ✅ refreshBeanFactory()不加载Bean定义，只设置序列化ID
- ✅ 自动扫描主配置类所在包及其子包

---

## 🔑 关键知识点

### 1️⃣ 模板方法模式

`AbstractApplicationContext.refresh()`使用了**模板方法模式**：

- **模板方法**：refresh() 定义了容器刷新的标准流程
- **原语操作**：obtainFreshBeanFactory()、onRefresh()等 由子类实现
- **钩子方法**：postProcessBeanFactory() 子类可以重写

### 2️⃣ Bean定义加载的两个时机

| 应用类型 | Bean定义加载时机 | 加载方式 |
|---------|-----------------|---------|
| 传统Spring XML | refreshBeanFactory() | 从XML文件加载 |
| 传统Spring注解 | invokeBeanFactoryPostProcessors() | ConfigurationClassPostProcessor扫描 |
| SpringBoot | invokeBeanFactoryPostProcessors() | ConfigurationClassPostProcessor自动扫描 |

### 3️⃣ Bean创建的时机

- **步骤5**：invokeBeanFactoryPostProcessors() 只扫描和注册Bean定义，不创建Bean实例
- **步骤11**：finishBeanFactoryInitialization() 才真正创建Bean实例

### 4️⃣ 为什么GenericApplicationContext不支持多次refresh()

```java
// GenericApplicationContext.refreshBeanFactory()
@Override
protected final void refreshBeanFactory() throws IllegalStateException {
    if (!this.refreshed.compareAndSet(false, true)) {
        throw new IllegalStateException("GenericApplicationContext does not support multiple refresh attempts");
    }
    this.beanFactory.setSerializationId(getId());
}
```

**原因**：
- BeanFactory在构造函数中创建
- 注解配置是编程式的，不需要重新扫描
- 避免重复扫描导致的性能问题

---

## 💡 调试技巧

### 关键断点位置

1. **refresh()入口**：
   ```java
   AbstractApplicationContext.java:582
   refresh()
   ```

2. **obtainFreshBeanFactory()**：
   ```java
   AbstractApplicationContext.java:593
   obtainFreshBeanFactory()
   ```

3. **invokeBeanFactoryPostProcessors()**：
   ```java
   AbstractApplicationContext.java:604
   invokeBeanFactoryPostProcessors(beanFactory)
   ```

4. **ConfigurationClassPostProcessor**：
   ```java
   ConfigurationClassPostProcessor.java:387
   processConfigBeanDefinitions(registry)
   ```

5. **ComponentScan扫描**：
   ```java
   ClassPathScanningCandidateComponentProvider.java:446
   scanCandidateComponents(basePackage)
   ```

### 观察重点

- **BeanFactory的Bean定义数量**：`beanFactory.getBeanDefinitionCount()`
- **Bean定义名称**：`beanFactory.getBeanDefinitionNames()`
- **当前步骤**：观察refresh()执行到哪一步
- **Bean实例数量**：`beanFactory.getSingletonCount()`

---

## 🎯 总结

### 核心要点

1. ✅ **所有Spring应用都走AbstractApplicationContext.refresh()**
2. ✅ **都执行12个标准步骤**
3. ✅ **Bean定义加载时机不同**：
   - XML应用：refreshBeanFactory()
   - 注解应用和Boot应用：invokeBeanFactoryPostProcessors()
4. ✅ **Bean实例化时机相同**：finishBeanFactoryInitialization()

### 设计模式

- **模板方法模式**：refresh()定义标准流程
- **策略模式**：不同的ApplicationContext有不同的refreshBeanFactory()实现
- **工厂模式**：BeanFactory负责创建Bean
- **观察者模式**：事件发布和监听

### 扩展阅读

- [ConfigurationClassPostProcessor深度解析](../03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md)
- [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)
- [refresh()源码深度分析](../08-源码分析/01-refresh方法源码深度分析.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐（进阶级）
