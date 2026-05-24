# Spring Bean完整生命周期深度解析

## 概述

Spring Bean的生命周期是指Bean从创建到销毁的完整过程。理解Bean的生命周期对于掌握Spring框架的工作原理至关重要，也有助于开发者正确地使用Spring的各种特性和接口。

本文档基于Spring Framework 6.x源码，详细分析Bean生命周期的每个阶段，包括核心源码的中文注释和实际的代码示例。

---

## 1. Bean生命周期总览

### 1.1 完整生命周期流程图

```
┌─────────────────────────────────────────────────────────────┐
│ Spring Bean完整生命周期                                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ 【阶段一：Bean定义与注册】                                   │
│  ├─ 扫描配置类（@ComponentScan等）                          │
│  ├─ 解析Bean定义                                           │
│  └─ 注册到BeanDefinitionRegistry                            │
│                                                              │
│ 【阶段二：Bean实例化】                                       │
│  ├─ 实例化前处理（InstantiationAwareBeanPostProcessor）     │
│  ├─ 创建Bean实例（构造函数）                                │
│  └─ 实例化后处理（InstantiationAwareBeanPostProcessor）     │
│                                                              │
│ 【阶段三：属性注入】                                         │
│  ├─ 注入前处理（InstantiationAwareBeanPostProcessor）      │
│  ├─ 设置属性值                                             │
│  ├─ 自动装配（@Autowired、@Resource等）                     │
│  └─ 注入后处理                                             │
│                                                              │
│ 【阶段四：Bean初始化】                                       │
│  ├─ Aware接口回调                                          │
│  ├─ 前置处理器（BeanPostProcessor.beforeInitialization）    │
│  ├─ 初始化方法                                             │
│  │  ├─ @PostConstruct注解方法                              │
│  │  ├─ InitializingBean接口方法                            │
│  │  └─ init-method自定义方法                               │
│  └─ 后置处理器（BeanPostProcessor.afterInitialization）     │
│     └─ AOP代理生成（主要时机）                              │
│                                                              │
│ 【阶段五：Bean使用】                                         │
│  └─ Bean在应用中被使用                                      │
│                                                              │
│ 【阶段六：Bean销毁】                                         │
│  ├─ 容器关闭触发                                           │
│  ├─ 前置销毁处理（@PreDestroy注解）                         │
│  ├─ 销毁方法                                               │
│  │  ├─ DisposableBean接口方法                             │
│  │  └─ destroy-method自定义方法                            │
│  └─ 后置销毁处理                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心方法调用链

```
ApplicationContext.refresh()
  └─ finishBeanFactoryInitialization()
      └─ DefaultListableBeanFactory.preInstantiateSingletons()
          └─ AbstractBeanFactory.getBean()
              └─ AbstractAutowireCapableBeanFactory.createBean()
                  ├─ resolveBeforeInstantiation()        【实例化前处理】
                  └─ doCreateBean()
                      ├─ createBeanInstance()           【创建Bean实例】
                      ├─ applyMergedBeanDefinitionPostProcessors()
                      ├─ addSingletonFactory()            【提前暴露引用】
                      ├─ populateBean()                  【属性注入】
                      │   ├─ postProcessAfterInstantiation()
                      │   ├─ applyPropertyValues()
                      │   └─ autowireBeanProperties()
                      └─ initializeBean()                【Bean初始化】
                          ├─ invokeAwareMethods()
                          ├─ applyBeanPostProcessorsBeforeInitialization()
                          ├─ invokeInitMethods()
                          └─ applyBeanPostProcessorsAfterInitialization()
                              └─ 【AOP代理生成】
```

---

## 2. Bean定义与注册阶段

### 2.1 Bean定义的来源

Spring Bean的定义可以来自多种配置方式：

```java
// 1. 注解方式（最常用）
@Configuration
@ComponentScan("com.example")
public class AppConfig {
    @Bean
    public MyService myService() {
        return new MyService();
    }
}

// 2. XML方式（传统方式）
<beans>
    <bean id="myService" class="com.example.MyService"/>
</beans>

// 3. 编程方式（灵活配置）
GenericApplicationContext context = new GenericApplicationContext();
context.registerBean(MyService.class);
context.refresh();
```

### 2.2 Bean定义的核心信息

每个Bean定义（BeanDefinition）包含以下核心信息：

```java
// BeanDefinition的核心属性
public interface BeanDefinition {
    // Bean的类信息
    Class<?> getBeanClass();
    
    // 作用域：singleton、prototype、request、session等
    String getScope();
    
    // 是否为抽象Bean
    boolean isAbstract();
    
    // 是否为懒加载
    boolean isLazyInit();
    
    // 构造函数参数
    ConstructorArgumentValues getConstructorArgumentValues();
    
    // 属性值
    MutablePropertyValues getPropertyValues();
    
    // 是否为主要候选者（autowiring时）
    boolean isPrimary();
    
    // 依赖的Bean名称
    String[] getDependsOn();
}
```

### 2.3 配置类的处理流程

```java
// ConfigurationClassPostProcessor的处理流程
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    // 1. 扫描所有配置类
    List<BeanDefinitionHolder> configCandidates = scanConfigClasses();
    
    // 2. 解析配置类
    ConfigurationClassParser parser = new ConfigurationClassParser();
    parser.parse(configCandidates);
    
    // 3. 注册发现的Bean定义
    ConfigurationClassBeanDefinitionReader reader = 
        new ConfigurationClassBeanDefinitionReader(registry);
    reader.loadBeanDefinitions(parser.getConfigurationClasses());
}
```

---

## 3. Bean实例化阶段

### 3.1 实例化前的处理

在Bean实例化之前，Spring会调用`InstantiationAwareBeanPostProcessor`的前置处理方法：

```java
// AbstractAutowireCapableBeanFactory.createBean()
protected Object createBean(String beanName, RootBeanDefinition mbd, Object[] args) {
    // 【实例化前处理】
    Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
    if (bean != null) {
        // 如果返回了代理对象，直接跳过后续的实例化过程
        return bean;
    }
    
    // 实际创建Bean实例
    Object beanInstance = doCreateBean(beanName, mbdToUse, args);
    return beanInstance;
}
```

**自定义实例化前处理器**：

```java
@Component
public class MyInstantiationAwareBeanPostProcessor 
    implements InstantiationAwareBeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        // 在Bean实例化前返回代理对象
        if (beanClass == MyService.class) {
            // 返回一个动态代理，跳过正常的实例化过程
            return Proxy.newProxyInstance(
                beanClass.getClassLoader(),
                new Class<?>[] { beanClass },
                new MyInvocationHandler());
        }
        return null; // 返回null表示使用默认实例化逻辑
    }
}
```

### 3.2 Bean实例的创建

Spring使用以下方式创建Bean实例：

```java
// AbstractAutowireCapableBeanFactory.doCreateBean()
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) {
    // 【第一步：创建Bean实例】
    BeanWrapper instanceWrapper = createBeanInstance(beanName, mbd, args);
    Object bean = instanceWrapper.getWrappedInstance();
    
    // 【第二步：应用后置处理器修改Bean定义】
    applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
    
    // 【第三步：提前暴露Bean引用（解决循环依赖）】
    if (mbd.isSingleton() && this.allowCircularReferences) {
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }
    
    // 【第四步：属性注入】
    populateBean(beanName, mbd, instanceWrapper);
    
    // 【第五步：Bean初始化】
    exposedObject = initializeBean(beanName, exposedObject, mbd);
    
    return exposedObject;
}
```

#### 3.2.1 构造函数的选择策略

Spring按照以下优先级选择构造函数：

```java
// 构造函数选择优先级
1. 被@Autowired注解标记的构造函数
2. 有最多参数的构造函数（如果在@Autowired中指定了required=true）
3. 默认构造函数
4. 工厂方法（如果通过工厂方法创建）
```

**示例：指定构造函数**

```java
@Component
public class MyService {
    private final DataSource dataSource;
    private final UserRepository userRepository;
    
    // 【推荐】明确指定构造函数（Spring 4.3+）
    @Autowired  // 可以省略，因为只有一个构造函数时Spring会自动使用
    public MyService(DataSource dataSource, UserRepository userRepository) {
        this.dataSource = dataSource;
        this.userRepository = userRepository;
    }
    
    // 或者使用Java 8+的显式构造函数
    public MyService(DataSource dataSource) {
        this(dataSource, new UserRepository());
    }
}
```

### 3.3 实例化后的处理

```java
// InstantiationAwareBeanPostProcessor的后置处理
protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
    // 【实例化后处理】
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
            // 如果返回false，则跳过后续的属性注入
            if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                return; // 跳过属性注入
            }
        }
    }
    
    // 继续属性注入...
}
```

**自定义实例化后处理器**：

```java
@Component
public class CustomInstantiationProcessor implements InstantiationAwareBeanPostProcessor {
    
    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) {
        // 在Bean实例化后、属性注入前进行自定义处理
        if (bean instanceof MyService) {
            MyService service = (MyService) bean;
            // 进行一些自定义初始化工作
            service.initializeCustomFields();
            
            // 返回false表示跳过Spring的自动属性注入
            // 返回true表示继续正常的属性注入流程
            return true; 
        }
        return true;
    }
}
```

---

## 4. 属性注入阶段

### 4.1 属性注入的核心流程

```java
// AbstractAutowireCapableBeanFactory.populateBean()
protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
    // 【第一步：实例化后处理器回调】
    // 给InstantiationAwareBeanPostProcessor机会在属性设置前修改Bean状态
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
            if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                return; // 跳过属性注入
            }
        }
    }
    
    // 【第二步：设置属性值】
    PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
    
    // 【第三步：后置处理器属性处理】
    // 给后置处理器机会修改属性值
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
            PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
            if (pvsToUse == null) {
                return; // 跳过属性注入
            }
            pvs = pvsToUse;
        }
    }
    
    // 【第四步：应用属性值】
    if (pvs != null) {
        applyPropertyValues(beanName, mbd, bw, pvs);
    }
}
```

### 4.2 自动装配的实现

Spring支持多种自动装配方式：

#### 4.2.1 @Autowired注解装配

```java
@Component
public class OrderService {
    
    @Autowired  // 按类型装配
    private PaymentService paymentService;
    
    @Autowired
    @Qualifier("wechatPaymentService")  // 按名称装配
    private PaymentService wechatPaymentService;
    
    @Autowired
    private List<PaymentService> allPaymentServices;  // 注入所有实现类
}
```

#### 4.2.2 @Resource注解装配

```java
import javax.annotation.Resource;

@Component
public class OrderService {
    
    @Resource  // 按名称装配，等价于@Autowired @Qualifier
    private PaymentService paymentService;
    
    @Resource(name = "wechatPaymentService")
    private PaymentService wechatPaymentService;
}
```

#### 4.2.3 构造函数注入

```java
@Component
public class OrderService {
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    
    // 【推荐方式】构造函数注入
    @Autowired
    public OrderService(PaymentService paymentService, OrderRepository orderRepository) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
    }
}
```

### 4.3 循环依赖的解决

Spring通过提前暴露Bean引用来解决循环依赖：

```java
// AbstractAutowireCapableBeanFactory.doCreateBean()
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, Object[] args) {
    // 创建Bean实例
    Object bean = createBeanInstance(beanName, mbd, args);
    
    // 【解决循环依赖的核心】提前暴露Bean引用
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences);
    if (earlySingletonExposure) {
        // 将Bean的ObjectFactory添加到三级缓存中
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }
    
    // 属性注入时，其他Bean可以通过getBean()获取到这个半成品的Bean
    populateBean(beanName, mbd, instanceWrapper);
    
    return bean;
}
```

**Spring的三级缓存机制**：

```java
// 三级缓存结构
public class DefaultSingletonBeanRegistry {
    
    // 一级缓存：完整的单例Bean
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    
    // 二级缓存：提前暴露的Bean（半成品）
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>();
    
    // 三级缓存：Bean的创建工厂
    private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>();
    
    // 获取Bean的流程
    protected Object getSingleton(String beanName) {
        // 1. 从一级缓存获取完整的Bean
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject != null) {
            return singletonObject;
        }
        
        // 2. 从二级缓存获取提前暴露的Bean
        singletonObject = this.earlySingletonObjects.get(beanName);
        if (singletonObject != null) {
            return singletonObject;
        }
        
        // 3. 从三级缓存获取Bean工厂
        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
        if (singletonFactory != null) {
            singletonObject = singletonFactory.getObject();
            this.earlySingletonObjects.put(beanName, singletonObject);
            this.singletonFactories.remove(beanName);
            return singletonObject;
        }
        
        return null;
    }
}
```

**循环依赖示例**：

```java
@Component
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
    
    public void methodA() {
        serviceB.methodB();
    }
}

@Component
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
    
    public void methodB() {
        serviceA.methodA();
    }
}
```

**Spring解决循环依赖的过程**：

```
1. 创建ServiceA实例（半成品）
2. 将ServiceA的ObjectFactory放入三级缓存
3. 开始注入ServiceA的属性
4. 发现需要ServiceB，开始创建ServiceB
5. 创建ServiceB实例（半成品）
6. 将ServiceB的ObjectFactory放入三级缓存
7. 开始注入ServiceB的属性
8. 发现需要ServiceA，从缓存中获取ServiceA
9. ServiceB获取到ServiceA的引用，完成创建
10. ServiceA获取到ServiceB的引用，完成创建
```

---

## 5. Bean初始化阶段

### 5.1 初始化阶段的核心流程

```java
// AbstractAutowireCapableBeanFactory.initializeBean()
protected Object initializeBean(String beanName, Object bean, RootBeanDefinition mbd) {
    // 【第一步：Aware接口回调】
    invokeAwareMethods(beanName, bean);
    
    Object wrappedBean = bean;
    
    // 【第二步：前置处理器】
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }
    
    try {
        // 【第三步：初始化方法】
        invokeInitMethods(beanName, wrappedBean, mbd);
    }
    catch (Throwable ex) {
        throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
    }
    
    // 【第四步：后置处理器】
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }
    
    return wrappedBean;
}
```

### 5.2 Aware接口的回调顺序

Spring按照严格的顺序调用各种Aware接口：

```java
// AbstractAutowireCapableBeanFactory.invokeAwareMethods()
private void invokeAwareMethods(String beanName, Object bean) {
    if (bean instanceof Aware) {
        // 【第一优先级】BeanNameAware
        if (bean instanceof BeanNameAware) {
            ((BeanNameAware) bean).setBeanName(beanName);
        }
        
        // 【第二优先级】BeanClassLoaderAware
        if (bean instanceof BeanClassLoaderAware) {
            ((BeanClassLoaderAware) bean).setBeanClassLoader(getBeanClassLoader());
        }
        
        // 【第三优先级】BeanFactoryAware
        if (bean instanceof BeanFactoryAware) {
            ((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
        }
    }
}

// ApplicationContextAware在子类中处理
// 优先级：在BeanFactoryAware之后
```

**Aware接口使用示例**：

```java
@Component
public class MyBean implements BeanNameAware, BeanFactoryAware, ApplicationContextAware {
    
    private String beanName;
    private BeanFactory beanFactory;
    private ApplicationContext applicationContext;
    
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("Bean名称: " + name);
    }
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        System.out.println("获取到BeanFactory");
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        System.out.println("获取到ApplicationContext");
    }
    
    @PostConstruct
    public void init() {
        // 在所有Aware接口回调后执行
        System.out.println("所有Aware接口已回调完成");
    }
}
```

### 5.3 BeanPostProcessor的调用时机

```java
// BeanPostProcessor的两个核心方法
public interface BeanPostProcessor {
    
    // 【前置处理器】在初始化方法前调用
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;  // 返回原Bean或包装对象
    }
    
    // 【后置处理器】在初始化方法后调用
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;  // 返回原Bean或包装对象
    }
}
```

**自定义BeanPostProcessor示例**：

```java
@Component
public class CustomBeanPostProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // 在初始化方法前对Bean进行处理
        if (bean instanceof MyService) {
            MyService service = (MyService) bean;
            // 进行一些前置处理
            service.preProcess();
        }
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 在初始化方法后对Bean进行处理
        if (bean instanceof MyService) {
            MyService service = (MyService) service;
            // 进行一些后置处理，如创建代理
            return createProxy(service);
        }
        return bean;
    }
    
    private Object createProxy(MyService service) {
        return Proxy.newProxyInstance(
            service.getClass().getClassLoader(),
            service.getClass().getInterfaces(),
            (proxy, method, args) -> {
                System.out.println("代理方法调用: " + method.getName());
                return method.invoke(service, args);
            });
    }
}
```

### 5.4 初始化方法的调用顺序

Spring按照以下顺序调用初始化方法：

```java
// InitDestroyAnnotationBeanPostProcessor的处理
// 1. @PostConstruct注解的方法（优先级最高）

// InitializingBean接口的处理
// 2. InitializingBean接口的afterPropertiesSet()方法

// 自定义init-method的处理
// 3. XML或@Bean注解中指定的init-method方法
```

**初始化方法示例**：

```java
@Component
public class DatabaseConnection implements InitializingBean {
    
    private Connection connection;
    
    // 【方式1】@PostConstruct注解（推荐）
    @PostConstruct
    public void connect() {
        System.out.println("@PostConstruct: 建立数据库连接");
    }
    
    // 【方式2】InitializingBean接口
    @Override
    public void afterPropertiesSet() {
        System.out.println("InitializingBean: 初始化数据库连接");
    }
    
    // 【方式3】自定义初始化方法
    public void init() {
        System.out.println("init-method: 自定义初始化");
    }
}

// 配置方式
@Bean(initMethod = "init")
public DatabaseConnection databaseConnection() {
    return new DatabaseConnection();
}
```

### 5.5 AOP代理的生成时机

AOP代理主要在`postProcessAfterInitialization`方法中生成：

```java
// AbstractAutoProxyCreator.postProcessAfterInitialization()
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            // 检查是否需要代理
            if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
                return bean;
            }
            
            // 【核心】创建代理对象
            Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors);
            this.proxyTypes.put(cacheKey, proxy.getClass());
            return proxy;
        }
    }
    return bean;
}
```

---

## 6. Bean使用阶段

### 6.1 Bean的获取方式

```java
// 方式1：按名称获取
MyService service = (MyService) context.getBean("myService");

// 方式2：按类型获取
MyService service = context.getBean(MyService.class);

// 方式3：按名称和类型获取
MyService service = context.getBean("myService", MyService.class);

// 方式4：依赖注入（推荐）
@Component
public class OrderController {
    @Autowired
    private MyService myService;  // Spring自动注入
}
```

### 6.2 Bean的作用域

Spring支持多种Bean作用域：

```java
// 1. singleton（默认）- 单例模式，整个容器共享一个实例
@Bean
@Scope("singleton")
public SingletonBean singletonBean() {
    return new SingletonBean();
}

// 2. prototype - 原型模式，每次请求都创建新实例
@Bean
@Scope("prototype")
public PrototypeBean prototypeBean() {
    return new PrototypeBean();
}

// 3. request - Web应用，每个HTTP请求一个实例
@Bean
@Scope(WebApplicationContext.SCOPE_REQUEST)
public RequestBean requestBean() {
    return new RequestBean();
}

// 4. session - Web应用，每个HTTP Session一个实例
@Bean
@Scope(WebApplicationContext.SCOPE_SESSION)
public SessionBean sessionBean() {
    return new SessionBean();
}

// 5. application - Web应用，ServletContext生命周期
@Bean
@Scope(WebApplicationContext.SCOPE_APPLICATION)
public ApplicationBean applicationBean() {
    return new ApplicationBean();
}
```

**不同作用域的Bean生命周期对比**：

| 作用域 | 创建时机 | 销毁时机 | 适用场景 |
|--------|----------|----------|----------|
| singleton | 容器启动时 | 容器关闭时 | 无状态的服务类 |
| prototype | 每次getBean()时 | 由GC回收 | 有状态的对象 |
| request | 每次HTTP请求时 | 请求结束时 | 请求数据封装 |
| session | 每次Session创建时 | Session销毁时 | 用户数据封装 |
| application | Web应用启动时 | Web应用关闭时 | 全局配置 |

---

## 7. Bean销毁阶段

### 7.1 销毁流程的触发时机

Bean销毁在以下情况下触发：

```java
// 1. 容器正常关闭
context.close();  // 或 registerShutdownHook()

// 2. 原型Bean由GC自动回收
// prototype作用域的Bean不由容器管理销毁

// 3. 手动触发销毁
ConfigurableApplicationContext context = ...;
context.getBeanFactory().destroyBean("myBean");
```

### 7.2 销毁方法的调用顺序

```java
// DisposableBeanAdapter.destroy()
public void destroy() {
    // 【第一步】@PreDestroy注解的方法
    for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
        processor.postProcessBeforeDestruction(this.bean, this.beanName);
    }
    
    // 【第二步】DisposableBean接口的destroy()方法
    if (this.invokeDisposableBean) {
        ((DisposableBean) this.bean).destroy();
    }
    
    // 【第三步】destroy-method指定的方法
    if (this.invokeDisposableBean) {
        ((AutoCloseable) this.bean).close();
    }
}
```

**销毁方法示例**：

```java
@Component
public class DatabaseConnection implements DisposableBean {
    
    private Connection connection;
    
    // 【方式1】@PreDestroy注解（推荐）
    @PreDestroy
    public void closeConnection() {
        System.out.println("@PreDestroy: 关闭数据库连接");
    }
    
    // 【方式2】DisposableBean接口
    @Override
    public void destroy() {
        System.out.println("DisposableBean: 释放数据库资源");
    }
    
    // 【方式3】自定义销毁方法
    public void cleanup() {
        System.out.println("destroy-method: 自定义清理");
    }
}

// 配置方式
@Bean(destroyMethod = "cleanup")
public DatabaseConnection databaseConnection() {
    return new DatabaseConnection();
}
```

### 7.3 容器关闭的钩子

```java
// 方式1：编程方式注册关闭钩子
ConfigurableApplicationContext context = ...;
context.registerShutdownHook();  // 注册JVM关闭钩子
// 当JVM关闭时，Spring会自动关闭容器并销毁所有Bean

// 方式2：XML配置方式
<beans default-destroy-method="cleanup">
    <bean id="myBean" class="com.example.MyBean"/>
</beans>

// 方式3：Java配置方式
@Configuration
public class AppConfig {
    @Bean
    public MyBean myBean() {
        return new MyBean();
    }
}
```

---

## 8. 完整生命周期示例

### 8.1 综合示例Bean

```java
/**
 * 完整的Bean生命周期示例
 * 演示Spring Bean生命周期的各个阶段
 */
@Component
@Scope("singleton")
public class LifecycleDemoBean 
    implements BeanNameAware, BeanFactoryAware, InitializingBean, DisposableBean {
    
    private String beanName;
    private BeanFactory beanFactory;
    
    // ==================== 构造函数阶段 ====================
    public LifecycleDemoBean() {
        System.out.println("1. 【构造函数】Bean实例化");
    }
    
    // ==================== Aware接口回调阶段 ====================
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("2. 【Aware接口】BeanNameAware回调，Bean名称: " + name);
    }
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        System.out.println("3. 【Aware接口】BeanFactoryAware回调");
    }
    
    // ==================== 属性注入阶段 ====================
    @Autowired
    public void setDependencyService(DependencyService dependencyService) {
        System.out.println("4. 【属性注入】依赖注入: " + dependencyService.getClass().getSimpleName());
    }
    
    // ==================== BeanPostProcessor阶段 ====================
    
    // 这个方法会在InstantiationAwareBeanPostProcessor中被调用
    public void postProcessAfterInstantiation() {
        System.out.println("5. 【实例化后处理】InstantiationAwareBeanPostProcessor回调");
    }
    
    // ==================== 初始化阶段 ====================
    
    @PostConstruct
    public void postConstruct() {
        System.out.println("6. 【@PostConstruct】注解初始化方法");
    }
    
    @Override
    public void afterPropertiesSet() {
        System.out.println("7. 【InitializingBean】接口初始化方法");
    }
    
    public void customInit() {
        System.out.println("8. 【init-method】自定义初始化方法");
    }
    
    // ==================== 使用阶段 ====================
    public void doBusiness() {
        System.out.println("9. 【使用阶段】Bean执行业务逻辑");
        System.out.println("   当前Bean名称: " + beanName);
        System.out.println("   BeanFactory: " + beanFactory.getClass().getSimpleName());
    }
    
    // ==================== 销毁阶段 ====================
    
    @PreDestroy
    public void preDestroy() {
        System.out.println("10. 【@PreDestroy】注解销毁方法");
    }
    
    @Override
    public void destroy() {
        System.out.println("11. 【DisposableBean】接口销毁方法");
    }
    
    public void customDestroy() {
        System.out.println("12. 【destroy-method】自定义销毁方法");
    }
}
```

### 8.2 配置和测试类

```java
@Configuration
@ComponentScan
public class LifecycleConfig {
    
    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public LifecycleDemoBean lifecycleDemoBean() {
        return new LifecycleDemoBean();
    }
    
    @Bean
    public DependencyService dependencyService() {
        return new DependencyService();
    }
}

// 测试类
public class LifecycleTest {
    public static void main(String[] args) {
        System.out.println("========== Spring容器启动 ==========");
        AnnotationConfigApplicationContext context = 
            new AnnotationConfigApplicationContext(LifecycleConfig.class);
        
        System.out.println("\n========== 使用Bean ==========");
        LifecycleDemoBean bean = context.getBean(LifecycleDemoBean.class);
        bean.doBusiness();
        
        System.out.println("\n========== Spring容器关闭 ==========");
        context.close();  // 触发Bean销毁
    }
}
```

### 8.3 自定义后置处理器示例

```java
/**
 * 自定义Bean后置处理器，监控Bean的生命周期
 */
@Component
public class LifecycleMonitoringBeanPostProcessor 
    implements BeanPostProcessor, InstantiationAwareBeanPostProcessor, DestructionAwareBeanPostProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(LifecycleMonitoringBeanPostProcessor.class);
    
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        logger.info("【实例化前】准备创建Bean: {}, 类型: {}", beanName, beanClass.getSimpleName());
        return null;  // 返回null表示继续正常的实例化流程
    }
    
    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) {
        logger.info("【实例化后】Bean已创建: {}, 类型: {}", beanName, bean.getClass().getSimpleName());
        return true;  // 返回true表示继续属性注入
    }
    
    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        logger.info("【属性注入】注入属性: {}, 属性数量: {}", beanName, 
                  pvs != null ? pvs.getPropertyValues().size() : 0);
        return pvs;
    }
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        logger.info("【初始化前】Bean: {}, 准备执行初始化方法", beanName);
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        logger.info("【初始化后】Bean: {}, 初始化完成，准备使用", beanName);
        
        // 可以为特定Bean创建代理
        if (bean instanceof LifecycleDemoBean) {
            logger.info("【AOP代理】为Bean创建代理: {}", beanName);
            return createProxy(bean);
        }
        return bean;
    }
    
    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) {
        logger.info("【销毁前】Bean: {}, 准备销毁", beanName);
    }
    
    private Object createProxy(Object target) {
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            (proxy, method, args) -> {
                logger.info("【代理调用】方法: {}", method.getName());
                return method.invoke(target, args);
            });
    }
}
```

---

## 9. 高级主题

### 9.1 条件化Bean创建

使用`@Conditional`系列注解实现条件化Bean创建：

```java
@Configuration
public class ConditionalConfig {
    
    @Bean
    @ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
    public FeatureBean featureBean() {
        return new FeatureBean();
    }
    
    @Bean
    @ConditionalOnClass(DataSource.class)
    public DatabaseBean databaseBean() {
        return new DatabaseBean();
    }
    
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public EmbeddedDatabaseBean embeddedDatabaseBean() {
        return new EmbeddedDatabaseBean();
    }
    
    @Bean
    @ConditionalOnExpression("${environment=='production'}")
    public ProductionBean productionBean() {
        return new ProductionBean();
    }
}

// 自定义条件注解
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(DatabaseTypeCondition.class)
public @interface ConditionalOnDatabaseType {
    String value();
}

public class DatabaseTypeCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String databaseType = context.getEnvironment().getProperty("database.type");
        String requiredType = metadata.getAnnotation(ConditionalOnDatabaseType.class).value();
        return requiredType.equals(databaseType);
    }
}
```

### 9.2 Bean的延迟初始化

```java
@Configuration
public class LazyInitConfig {
    
    // 默认情况下，单例Bean在容器启动时就会初始化
    @Bean
    public EagerBean eagerBean() {
        return new EagerBean();  // 容器启动时就创建
    }
    
    // 延迟初始化Bean，只有在第一次使用时才创建
    @Bean
    @Lazy
    public LazyBean lazyBean() {
        return new LazyBean();  // 第一次getBean()时才创建
    }
}

// 全局设置延迟初始化
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(Application.class);
        builder.lazyInitialization(true);  // 全局延迟初始化
        builder.run(args);
    }
}
```

### 9.3 Bean的作用域代理

```java
@Configuration
public class ScopeProxyConfig {
    
    // 为原型作用域的Bean创建代理，解决单例Bean注入原型Bean的问题
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public PrototypeBean prototypeBean() {
        return new PrototypeBean();
    }
}

@Component
public class SingletonService {
    
    @Autowired
    private PrototypeBean prototypeBean;  // 注入的是代理对象
    
    public void doSomething() {
        // 每次调用都会获取新的PrototypeBean实例
        prototypeBean.execute();
    }
}
```

---

## 10. 性能优化建议

### 10.1 合理使用作用域

```java
@Configuration
public class PerformanceConfig {
    
    // 【推荐】无状态的服务类使用单例
    @Bean
    @Scope("singleton")
    public StatelessService statelessService() {
        return new StatelessService();
    }
    
    // 【推荐】有状态的对象使用原型
    @Bean
    @Scope("prototype")
    public StatefulObject statefulObject() {
        return new StatefulObject();
    }
    
    // 【推荐】Web请求相关使用request作用域
    @Bean
    @Scope(WebApplicationContext.SCOPE_REQUEST)
    public RequestData requestData() {
        return new RequestData();
    }
    
    // 【避免】不恰当的单例使用
    // 不要将有状态的对象设为单例，除非确实需要共享状态
}
```

### 10.2 延迟初始化优化

```java
@Configuration
public class LazyInitConfig {
    
    // 【推荐】将非必需的Bean设为延迟初始化
    @Bean
    @Lazy
    public OptionalFeature optionalFeature() {
        return new OptionalFeature();
    }
    
    // 【推荐】使用@Conditional按需创建Bean
    @Bean
    @ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
    public CacheService cacheService() {
        return new CacheService();
    }
}
```

### 10.3 循环依赖避免

```java
// 【推荐】使用@Lazy打破循环依赖
@Service
public class ServiceA {
    
    private final ServiceB serviceB;
    
    @Autowired
    public ServiceA(@Lazy ServiceB serviceB) {  // 使用@Lazy延迟注入
        this.serviceB = serviceB;
    }
}

@Service
public class ServiceB {
    
    private final ServiceA serviceA;
    
    @Autowired
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}

// 【推荐】重构设计避免循环依赖
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@Service
public class ServiceB {
    // 移除对ServiceA的依赖
}
```

---

## 11. 调试与监控

### 11.1 启用调试日志

```xml
<!-- logback.xml -->
<configuration>
    <logger name="org.springframework.beans" level="DEBUG"/>
    <logger name="org.springframework.context" level="DEBUG"/>
    
    <!-- 更详细的Bean创建日志 -->
    <logger name="org.springframework.beans.factory" level="TRACE"/>
</configuration>
```

### 11.2 自定义Bean生命周期监听器

```java
@Component
public class BeanLifecycleListener implements BeanPostProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(BeanLifecycleListener.class);
    
    private final Map<String, Long> creationTimes = new ConcurrentHashMap<>();
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        creationTimes.put(beanName, System.currentTimeMillis());
        logger.info("Bean创建完成: {}, 类型: {}", beanName, bean.getClass().getSimpleName());
        return bean;
    }
    
    @PreDestroy
    public void reportStatistics() {
        logger.info("========== Bean生命周期统计 ==========");
        for (Map.Entry<String, Long> entry : creationTimes.entrySet()) {
            long lifetime = System.currentTimeMillis() - entry.getValue();
            logger.info("Bean: {}, 存活时间: {}ms", entry.getKey(), lifetime);
        }
    }
}
```

### 11.3 监控Bean创建时间

```java
@Component
public class BeanCreationTimeMonitor implements BeanPostProcessor {
    
    private final Map<String, Long> creationTimes = new ConcurrentHashMap<>();
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        creationTimes.put(beanName + "_start", System.currentTimeMillis());
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        long startTime = creationTimes.getOrDefault(beanName + "_start", 0L);
        long creationTime = System.currentTimeMillis() - startTime;
        
        if (creationTime > 100) {  // 超过100ms的Bean创建
            System.out.println("⚠️  慢Bean创建: " + beanName + ", 耗时: " + creationTime + "ms");
        }
        
        return bean;
    }
}
```

---

## 12. 常见问题与解决方案

### 12.1 Bean创建失败

**问题**：
```
BeanCreationException: Error creating bean with name 'myService': 
Instantiation of bean failed: constructor threw exception
```

**解决方案**：

```java
// 1. 检查构造函数是否正确
@Service
public class MyService {
    private final DependencyService dependencyService;
    
    // 【正确】明确的构造函数
    @Autowired
    public MyService(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }
}

// 2. 使用@Autowired(required = false)避免必需依赖
@Service
public class MyService {
    @Autowired(required = false)
    private OptionalDependency optionalDependency;
}

// 3. 使用@Qualifier指定具体的Bean
@Service
public class MyService {
    @Autowired
    @Qualifier("specificImplementation")
    private DependencyService dependencyService;
}
```

### 12.2 属性注入失败

**问题**：
```
BeanCreationException: Error creating bean with name 'myService': 
Could not autowire. No qualifying bean of type 'DataSource' available
```

**解决方案**：

```java
// 1. 提供默认的Bean
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary  // 设置为主要候选者
    public DataSource defaultDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "database.type", havingValue = "mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().url("jdbc:mysql://...").build();
    }
}

// 2. 使用Optional避免必需依赖
@Service
public class MyService {
    @Autowired
    private Optional<DataSource> dataSource;
}

// 3. 使用@Value提供默认值
@Service
public class MyService {
    @Value("${database.url:jdbc:h2:mem:testdb}")
    private String databaseUrl;
}
```

### 12.3 初始化顺序问题

**问题**：
```
BeanCurrentlyInCreationException: Error creating bean with name 'serviceA': 
Requested bean is currently in creation: Is there an unresolvable circular reference?
```

**解决方案**：

```java
// 方案1：使用@Lazy打破循环依赖
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    @Autowired
    public ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

// 方案2：使用@DependsOn控制初始化顺序
@Service
@DependsOn("infrastructureService")
public class BusinessService {
    @Autowired
    private InfrastructureService infrastructureService;
}

// 方案3：重构设计避免循环依赖
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
    
    public void process() {
        // 不再直接依赖ServiceB，而是通过事件或消息通信
        eventPublisher.publish(new ProcessRequestEvent());
    }
}
```

---

## 13. 总结

### 13.1 Bean生命周期关键点

1. **实例化前**：InstantiationAwareBeanPostProcessor有机会返回代理对象
2. **实例化**：选择合适的构造函数创建Bean实例
3. **实例化后**：InstantiationAwareBeanPostProcessor进行后处理
4. **属性注入**：处理@Autowired、@Value等注解的属性
5. **Aware回调**：按顺序调用各种Aware接口
6. **前置处理**：BeanPostProcessor.beforeInitialization
7. **初始化方法**：@PostConstruct → InitializingBean → init-method
8. **后置处理**：BeanPostProcessor.afterInitialization（AOP代理生成）
9. **使用阶段**：Bean在应用中被使用
10. **销毁阶段**：@PreDestroy → DisposableBean → destroy-method

### 13.2 最佳实践建议

1. **使用构造函数注入**：提供明确的依赖关系
2. **合理使用作用域**：根据需要选择合适的作用域
3. **避免循环依赖**：通过重构或使用@Lazy解决
4. **善用后置处理器**：在关键时机进行自定义处理
5. **使用条件化创建**：按需创建Bean，提高启动性能
6. **关注销毁方法**：确保资源正确释放
7. **监控Bean创建**：及时发现性能问题

### 13.3 扩展阅读

- Spring Framework官方文档：https://docs.spring.io/spring-framework/reference/core/beans/
- Spring容器启动流程：`AbstractApplicationContext.refresh()`
- AOP代理原理：`AbstractAutoProxyCreator`
- 依赖注入原理：`AutowiredAnnotationBeanPostProcessor`

---

**文档版本**: 1.0  
**最后更新**: 2025年  
**对应Spring版本**: 6.x  
**作者**: Spring容器深度分析

## 相关源码文件

- `AbstractAutowireCapableBeanFactory.java` - Bean创建核心逻辑
- `DefaultSingletonBeanRegistry.java` - 单例Bean注册表
- `DisposableBeanAdapter.java` - Bean销毁适配器
- `InitDestroyAnnotationBeanPostProcessor.java` - 注解初始化方法处理器
- `AutowiredAnnotationBeanPostProcessor.java` - 自动装配注解处理器