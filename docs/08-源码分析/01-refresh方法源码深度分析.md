# refresh方法源码深度分析

## 🎯 核心作用

`AbstractApplicationContext.refresh()`是Spring容器启动的**核心方法**，它定义了Spring容器启动的标准流程，包含12个关键步骤。

**设计模式**：模板方法模式（Template Method Pattern）

---

## 📍 方法签名

```java
// AbstractApplicationContext.java:582
@Override
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // 准备刷新
        prepareRefresh();

        // 告诉子类刷新内部bean工厂
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // 准备bean工厂以便在此上下文中使用
        prepareBeanFactory(beanFactory);

        try {
            // 允许在上下文子类中对bean工厂进行后处理
            postProcessBeanFactory(beanFactory);

            // 调用在上下文中注册为bean的工厂处理器
            invokeBeanFactoryPostProcessors(beanFactory);

            // 注册拦截bean创建的bean处理器
            registerBeanPostProcessors(beanFactory);

            // 初始化此上下文的消息源
            initMessageSource();

            // 初始化此上下文的事件多播器
            initApplicationEventMulticaster();

            // 在特定上下文子类中初始化其他特殊bean
            onRefresh();

            // 检查侦听器bean并注册它们
            registerListeners();

            // 实例化所有剩余的（非懒加载）单例
            finishBeanFactoryInitialization(beanFactory);

            // 完成此上下文的刷新
            finishRefresh();
        } catch (BeansException ex) {
            // 销毁已创建的单例以避免悬空资源
            destroyBeans();
            cancelRefresh(ex);
            throw ex;
        } finally {
            resetCommonCaches();
        }
    }
}
```

---

## 🔑 步骤详解

### 步骤1：prepareRefresh() - 准备刷新

**位置**：`AbstractApplicationContext.java:590`

```java
protected void prepareRefresh() {
    // 1. 设置启动时间
    this.startupDate = System.currentTimeMillis();

    // 2. 设置容器的active和closed标志
    this.closed.set(false);
    this.active.set(true);

    // 3. 初始化属性源
    initPropertySources();

    // 4. 验证必需的属性
    getEnvironment().validateRequiredProperties();

    // 5. 创建早期事件集合（用于在多播器可用之前发布事件）
    if (this.earlyApplicationListeners == null) {
        this.earlyApplicationListeners = new LinkedHashSet<>(
            this.applicationListeners);
    }

    // 6. 更新早期事件集合
    if (this.earlyApplicationEvents == null) {
        this.earlyApplicationEvents = new LinkedHashSet<>();
    }
}
```

**关键点**：
- 记录容器启动时间，用于计算启动耗时
- 设置容器的active标志为true，表示容器正在运行
- 调用`initPropertySources()`初始化属性源（子类可以重写）
- 验证必需的属性（通过`@Required`注解）

---

### 步骤2：obtainFreshBeanFactory() - 获取新的BeanFactory

**位置**：`AbstractApplicationContext.java:593`

```java
protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
    // 1. ⭐️ 刷新BeanFactory（子类实现）
    refreshBeanFactory();

    // 2. ⭐️ 返回BeanFactory
    return this.beanFactory;
}
```

**关键点**：
- 调用子类的`refreshBeanFactory()`方法
- 不同的ApplicationContext有不同的实现：
  - `AbstractRefreshableApplicationContext`：创建新的BeanFactory并加载Bean定义
  - `GenericApplicationContext`：只设置序列化ID，不加载Bean定义

---

### 步骤3：prepareBeanFactory() - 准备BeanFactory

**位置**：`AbstractApplicationContext.java:596`

```java
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 1. 设置类加载器
    beanFactory.setBeanClassLoader(getClassLoader());

    // 2. 设置Bean表达式解析器（SpEL）
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver());

    // 3. 添加属性编辑器注册器
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

    // 4. 添加BeanPostProcessor（ApplicationContextAwareProcessor）
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

    // 5. 忽略自动装配的接口
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSourceAware.class);

    // 6. 注册可解析的依赖
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // 7. 添加ApplicationListenerDetector（检测ApplicationListener）
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector());

    // 8. 添加LoadTimeWeaverAwareProcessor（如果支持LTW）
    if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
    }

    // 9. 注册默认环境Bean
    if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
    }
}
```

**关键点**：
- 设置类加载器（用于加载Bean类）
- 设置SpEL表达式解析器（支持`@Value("#{...}")`）
- 添加`ApplicationContextAwareProcessor`（处理Aware接口回调）
- 忽略自动装配的Aware接口（避免重复注入）
- 注册可解析的依赖（BeanFactory、ResourceLoader等）
- 添加`ApplicationListenerDetector`（检测事件监听器）

---

### 步骤4：postProcessBeanFactory() - 后处理BeanFactory

**位置**：`AbstractApplicationContext.java:600`

```java
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 默认实现为空，子类可以重写
}
```

**典型实现**：
```java
// AbstractRefreshableWebApplicationContext
@Override
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 添加ServletContextAwareProcessor
    beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext, this.servletConfig));

    // 忽略ServletContextAware接口
    beanFactory.ignoreDependencyInterface(ServletContextAware.class);

    // 注册Web特定作用域
    beanFactory.registerScope("request", new RequestScope());
    beanFactory.registerScope("session", new SessionScope());

    // 注册可解析的依赖
    beanFactory.registerResolvableDependency(ServletRequest.class, new RequestObjectFactory());
    beanFactory.registerResolvableDependency(HttpSession.class, new SessionObjectFactory());
}
```

**关键点**：
- 子类可以重写此方法，添加特殊处理
- Web应用会注册request、session作用域
- 添加ServletContext相关的依赖

---

### 步骤5：invokeBeanFactoryPostProcessors() - 调用BeanFactoryPostProcessor

**位置**：`AbstractApplicationContext.java:604`

```java
protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
    // ⭐️ 委托给PostProcessorRegistrationDelegate处理
    PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

    // ⭐️ 检测LoadTimeWeaverAware并加载织入器（如果支持LTW）
    if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }
}
```

**关键点**：
- **这是最关键的一步**：Bean定义加载在这里进行
- `ConfigurationClassPostProcessor`在这里执行
- 扫描组件、加载Bean定义
- 调用所有注册的`BeanFactoryPostProcessor`

**详细说明**：参见 [ConfigurationClassPostProcessor深度解析](../03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md)

---

### 步骤6：registerBeanPostProcessors() - 注册BeanPostProcessor

**位置**：`AbstractApplicationContext.java:606`

```java
protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
    // ⭐️ 委托给PostProcessorRegistrationDelegate处理
    PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
}
```

**关键点**：
- 实例化并注册所有`BeanPostProcessor`
- 按优先级排序：
  1. 实现了`PriorityOrdered`接口的
  2. 实现了`Ordered`接口的
  3. 普通的
- 区分内部BeanPostProcessor和普通BeanPostProcessor
- **必须在Bean实例化之前注册完成**

---

### 步骤7：initMessageSource() - 初始化消息源

**位置**：`AbstractApplicationContext.java:616`

```java
protected void initMessageSource() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();

    // 1. 检查是否已有名为"messageSource"的Bean
    if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
        // 如果有，使用它
        this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
    } else {
        // 2. 如果没有，使用默认的DelegatingMessageSource
        DelegatingMessageSource dms = new DelegatingMessageSource();
        dms.setParentMessageSource(getInternalParentMessageSource());
        this.messageSource = dms;
        beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
    }
}
```

**关键点**：
- 初始化国际化资源
- 支持多语言
- 如果用户定义了名为`messageSource`的Bean，使用用户的
- 否则使用默认的`DelegatingMessageSource`

---

### 步骤8：initApplicationEventMulticaster() - 初始化事件广播器

**位置**：`AbstractApplicationContext.java:619`

```java
protected void initApplicationEventMulticaster() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();

    // 1. 检查是否已有名为"applicationEventMulticaster"的Bean
    if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
        // 如果有，使用它
        this.applicationEventMulticaster =
            beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
    } else {
        // 2. 如果没有，使用默认的SimpleApplicationEventMulticaster
        this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
        beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
    }
}
```

**关键点**：
- 初始化事件广播器
- 用于发布和监听应用事件
- 如果用户定义了名为`applicationEventMulticaster`的Bean，使用用户的
- 否则使用默认的`SimpleApplicationEventMulticaster`

---

### 步骤9：onRefresh() - 刷新特殊资源

**位置**：`AbstractApplicationContext.java:622`

```java
protected void onRefresh() {
    // 默认实现为空，子类可以重写
}
```

**典型实现**：
```java
// ServletWebServerApplicationContext
@Override
protected void onRefresh() {
    super.onRefresh();

    // ⭐️ 创建Web服务器（内嵌Tomcat）
    createWebServer();
}

private void createWebServer() {
    WebServer webServer = this.webServer;
    ServletContext servletContext = getServletContext();

    if (webServer == null && servletContext == null) {
        ServletWebServerFactory factory = getWebServerFactory();
        this.webServer = factory.getWebServer(getSelfInitializer());
    }
}
```

**关键点**：
- 子类可以重写此方法，刷新特殊的资源
- Web应用在这里创建内嵌Web服务器
- 这是refresh()中的一个**钩子方法**

---

### 步骤10：registerListeners() - 注册监听器

**位置**：`AbstractApplicationContext.java:624`

```java
protected void registerListeners() {
    // 1. 注册静态指定的监听器
    for (ApplicationListener<?> listener : getApplicationListeners()) {
        getApplicationEventMulticaster().addApplicationListener(listener);
    }

    // 2. 不要在此处初始化FactoryBeans：我们需要保留所有常规Bean
    //    未初始化，以便让后处理器有机会应用于它们！
    String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);

    for (String listenerBeanName : listenerBeanNames) {
        getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
    }

    // 3. 发布早期应用事件（在多播器可用之前发布的事件）
    Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
    this.earlyApplicationEvents = null;
    if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
        for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
            getApplicationEventMulticaster().multicastEvent(earlyEvent);
        }
    }
}
```

**关键点**：
- 注册所有事件监听器
- 发布早期应用事件（在多播器可用之前收集的事件）

---

### 步骤11：finishBeanFactoryInitialization() - 完成BeanFactory初始化

**位置**：`AbstractApplicationContext.java:626`

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // 1. 初始化ConversionService（如果配置了）
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
        beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
            beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }

    // 2. 注册默认的值解析器（如果没有）
    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }

    // 3. ⭐️ 实例化所有非懒加载的单例Bean
    beanFactory.preInstantiateSingletons();
}
```

**关键点**：
- **这是最关键的一步**：Bean实例化在这里进行
- 调用`DefaultListableBeanFactory.preInstantiateSingletons()`
- 实例化所有非懒加载的单例Bean
- 调用Bean的初始化方法

**详细说明**：参见 [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)

---

### 步骤12：finishRefresh() - 完成刷新

**位置**：`AbstractApplicationContext.java:628`

```java
protected void finishRefresh() {
    // 1. 清除资源缓存
    clearResourceCaches();

    // 2. ⭐️ 初始化生命周期处理器
    initLifecycleProcessor();

    // 3. ⭐️ 将刷新完成事件传播到生命周期处理器
    getLifecycleProcessor().onRefresh();

    // 4. ⭐️ 发布ContextRefreshedEvent事件
    publishEvent(new ContextRefreshedEvent(this));

    // 5. ⭐️ 参与LiveBeansView MBean（如果激活）
    LiveBeansView.registerApplicationContext(this);
}
```

**关键点**：
- 发布`ContextRefreshedEvent`事件
- 启动生命周期Bean
- 容器启动完成

---

## 🔑 关键源码分析

### 调用链分析

```
AbstractApplicationContext.refresh()
  ↓
prepareRefresh()
  ↓
obtainFreshBeanFactory()
  ↓
refreshBeanFactory() 【抽象方法，子类实现】
  ↓
GenericApplicationContext.refreshBeanFactory()
  ├─ 只设置序列化ID
  └─ 不加载Bean定义
  ↓
prepareBeanFactory(beanFactory)
  ├─ 设置类加载器
  ├─ 设置SpEL解析器
  ├─ 添加ApplicationContextAwareProcessor
  └─ 注册可解析的依赖
  ↓
postProcessBeanFactory(beanFactory)
  └─ 子类可以重写
  ↓
invokeBeanFactoryPostProcessors(beanFactory)⭐️ 最关键
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
registerBeanPostProcessors(beanFactory)
  ↓
initMessageSource()
  ↓
initApplicationEventMulticaster()
  ↓
onRefresh()
  └─ 子类可以重写（Web应用创建Web服务器）
  ↓
registerListeners()
  ↓
finishBeanFactoryInitialization(beanFactory)⭐️ 最关键
  ↓
preInstantiateSingletons()
  ↓
getBean(beanName)
  ↓
doGetBean(beanName)
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
  ↓
finishRefresh()
  ↓
publishEvent(new ContextRefreshedEvent(this))
  ↓
✅ 容器启动完成！
```

---

## 🎯 设计模式分析

### 1️⃣ 模板方法模式（Template Method Pattern）

**定义**：在父类中定义算法的骨架，将一些步骤延迟到子类实现。

**应用**：
- **模板方法**：`AbstractApplicationContext.refresh()` 定义了容器刷新的标准流程
- **原语操作**：`obtainFreshBeanFactory()`、`onRefresh()`等 由子类实现
- **钩子方法**：`postProcessBeanFactory()` 子类可以重写

**示例**：
```java
// 抽象类（父类）
public abstract class AbstractApplicationContext {
    // 模板方法：定义算法骨架
    @Override
    public void refresh() throws BeansException, IllegalStateException {
        prepareRefresh();
        obtainFreshBeanFactory();  // 原语操作：子类实现
        prepareBeanFactory(beanFactory);
        postProcessBeanFactory(beanFactory);  // 钩子方法：子类可以重写
        // ...
    }

    // 原语操作：子类必须实现
    protected abstract void refreshBeanFactory() throws BeansException;
}

// 具体类（子类）
public class GenericApplicationContext extends AbstractApplicationContext {
    // 实现原语操作
    @Override
    protected final void refreshBeanFactory() throws IllegalStateException {
        // 只设置序列化ID
        this.beanFactory.setSerializationId(getId());
    }
}
```

### 2️⃣ 策略模式（Strategy Pattern）

**定义**：定义一系列算法，把它们一个个封装起来，并且使它们可相互替换。

**应用**：
- 不同的ApplicationContext有不同的`refreshBeanFactory()`实现
- 不同的ApplicationContext有不同的`onRefresh()`实现

### 3️⃣ 工厂模式（Factory Pattern）

**定义**：定义创建对象的接口，让子类决定实例化哪个类。

**应用**：
- `BeanFactory`负责创建Bean实例
- `obtainFreshBeanFactory()`获取BeanFactory

### 4️⃣ 观察者模式（Observer Pattern）

**定义**：定义对象间的一种一对多的依赖关系，当一个对象的状态发生改变时，所有依赖于它的对象都得到通知并自动更新。

**应用**：
- `initApplicationEventMulticaster()`初始化事件广播器
- `registerListeners()`注册事件监听器
- `publishEvent()`发布事件

---

## 💡 调试技巧

### 关键断点位置

1. **refresh()入口**：
   ```java
   AbstractApplicationContext.java:582
   refresh()
   ```

2. **invokeBeanFactoryPostProcessors()**：
   ```java
   AbstractApplicationContext.java:604
   invokeBeanFactoryPostProcessors(beanFactory)
   ```

3. **ConfigurationClassPostProcessor**：
   ```java
   ConfigurationClassPostProcessor.java:387
   processConfigBeanDefinitions(registry)
   ```

4. **preInstantiateSingletons()**：
   ```java
   DefaultListableBeanFactory.java:878
   preInstantiateSingletons()
   ```

### 观察重点

- **BeanFactory的Bean定义数量**：`beanFactory.getBeanDefinitionCount()`
- **Bean定义名称**：`beanFactory.getBeanDefinitionNames()`
- **当前步骤**：观察refresh()执行到哪一步
- **Bean实例数量**：`beanFactory.getSingletonCount()`

---

## 🎯 总结

### 核心要点

1. ✅ **refresh()是模板方法**：定义了Spring容器启动的标准流程
2. ✅ **12个步骤**：每个步骤都有明确的职责
3. ✅ **两个关键步骤**：
   - 步骤5：`invokeBeanFactoryPostProcessors()` - Bean定义加载
   - 步骤11：`finishBeanFactoryInitialization()` - Bean实例化
4. ✅ **设计模式**：
   - 模板方法模式
   - 策略模式
   - 工厂模式
   - 观察者模式

### 扩展阅读

- [Spring容器启动流程详解](../01-核心容器/01-Spring容器启动流程详解.md)
- [ConfigurationClassPostProcessor深度解析](../03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md)
- [Bean完整生命周期详解](../02-Bean生命周期/01-Bean完整生命周期详解.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐⭐（专家级）
