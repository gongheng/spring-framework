# Bean完整生命周期详解

## 🎯 核心概念

Spring Bean的完整生命周期是指：**从Bean实例的创建到销毁的整个过程**。理解Bean的生命周期对于掌握Spring的核心原理至关重要。

**生命周期阶段**：
1. **实例化**（Instantiation）：创建Bean实例
2. **属性赋值**（Population）：注入依赖属性
3. **初始化**（Initialization）：执行初始化逻辑
4. **使用**（Usage）：Bean被应用程序使用
5. **销毁**（Destruction）：执行销毁逻辑

---

## 📊 Bean完整生命周期流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  Spring容器启动                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            1. BeanDefinition加载阶段                         │
│              (refresh() → invokeBeanFactoryPostProcessors()) │
│                                                              │
│ - ConfigurationClassPostProcessor扫描组件                    │
│ - 加载所有BeanDefinition到BeanFactory                       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            2. Bean实例化阶段                                 │
│           (finishBeanFactoryInitialization())                │
│                                                              │
│ getBean(beanName)                                           │
│   ↓                                                          │
│ doGetBean(beanName)                                         │
│   ↓                                                          │
│ createBean(beanName, mbd, args)                             │
│   ↓                                                          │
│ doCreateBean(beanName, mbdToUse, args)                      │
│   ↓                                                          │
│ 【步骤1】createBeanInstance()                                │
│   ├─ 推断构造器                                             │
│   ├─ 反射创建Bean实例                                        │
│   └─ wrapper = new BeanWrapper(instance)                    │
│   ↓                                                          │
│ 【步骤2】applyMergedBeanDefinitionPostProcessors()          │
│   ├─ 合并BeanDefinition                                     │
│   └─ 应用MergedBeanDefinitionPostProcessor                  │
│   ↓                                                          │
│ 【步骤3】addSingletonFactory()                               │
│   └─ 暴露早期引用到三级缓存（解决循环依赖）                    │
│   ↓                                                          │
│ 【步骤4】populateBean() ⭐️ 属性赋值阶段                     │
│   ├─ 获取属性值                                             │
│   ├─ applyPropertyValues()                                  │
│   └─ 依赖注入                                               │
│   ↓                                                          │
│ 【步骤5】initializeBean() ⭐️ 初始化阶段                     │
│   ├─ invokeAwareMethods() 【Aware接口回调】                 │
│   │   ├─ BeanNameAware                                      │
│   │   ├─ BeanFactoryAware                                   │
│   │   └─ ApplicationContextAware                            │
│   │   ↓                                                     │
│   ├─ applyBeanPostProcessorsBeforeInitialization() 【前置处理】
│   │   └─ 执行所有BeanPostProcessor.postProcessBeforeInitialization()
│   │   ↓                                                     │
│   ├─ invokeInitMethods() 【初始化方法回调】                  │
│   │   ├─ 如果实现InitializingBean接口：afterPropertiesSet() │
│   │   └─ 如果有@PostConstruct注解：调用标记方法               │
│   │   └─ 如果有init-method配置：调用自定义初始化方法          │
│   │   ↓                                                     │
│   └─ applyBeanPostProcessorsAfterInitialization() 【后置处理】
│       └─ 执行所有BeanPostProcessor.postProcessAfterInitialization()
│       └─ ⭐️ 如果有AOP代理，在这里创建代理对象                  │
│   ↓                                                          │
│ 【步骤6】registerDisposableBean()                            │
│   └─ 注册Bean的销毁方法                                      │
│   ↓                                                          │
│ ✅ Bean创建完成，存入一级缓存（singletonObjects）            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            3. Bean使用阶段                                   │
│                                                              │
│ - Bean被应用程序使用                                          │
│ - 可以从容器中获取Bean：context.getBean(UserService.class) │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            4. Bean销毁阶段                                   │
│              (容器关闭时)                                     │
│                                                              │
│ ConfigurableApplicationContext.close()                      │
│   ↓                                                          │
│ doClose()                                                   │
│   ↓                                                          │
│ destroyBeans()                                              │
│   ↓                                                          │
│ destroySingleton(beanName)                                  │
│   ↓                                                          │
│ 【步骤1】getBean() 获取Bean实例                               │
│   ↓                                                          │
│ 【步骤2】bean.destroy()                                      │
│   ├─ 如果实现DisposableBean接口：destroy()                  │
│   ├─ 如果有@PreDestroy注解：调用标记方法                     │
│   └─ 如果有destroy-method配置：调用自定义销毁方法             │
│   ↓                                                          │
│ ✅ Bean销毁完成                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 核心源码分析

### 阶段1：Bean实例化

#### 方法1：getBean() - 获取Bean

**位置**：`AbstractBeanFactory.java:256`

```java
public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
    return doGetBean(name, requiredType, null, false);
}
```

#### 方法2：doGetBean() - 获取Bean的核心逻辑

**位置**：`AbstractBeanFactory.java:272`

```java
protected <T> T doGetBean(
    String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly) {

    // 1. 转换Bean名称（处理&前缀、别名等）
    String beanName = transformedBeanName(name);

    // 2. ⭐️ 先从缓存中获取Bean
    Object sharedInstance = getSingleton(beanName);
    if (sharedInstance != null && args == null) {
        // 如果缓存中存在，直接返回
        return (T) getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    // 3. 检查是否正在创建
    if (isPrototypeCurrentlyInCreation(beanName)) {
        throw new BeanCurrentlyInCreationException(beanName);
    }

    // 4. 标记Bean正在创建
    if (!typeCheckOnly) {
        markBeanAsCreated(beanName);
    }

    try {
        // 5. ⭐️ 创建Bean实例
        BeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

        // 6. 处理depends-on依赖
        String[] dependsOn = mbd.getDependsOn();
        if (dependsOn != null) {
            for (String dep : dependsOn) {
                getBean(dep);
            }
        }

        // 7. ⭐️ 创建Bean实例
        if (mbd.isSingleton()) {
            sharedInstance = getSingleton(beanName, () -> {
                try {
                    return createBean(beanName, mbd, args);
                } catch (BeansException ex) {
                    destroySingleton(beanName);
                    throw ex;
                }
            });
        }
    } catch (BeansException ex) {
        cleanupAfterBeanCreationFailure(beanName);
        throw ex;
    }

    return (T) getObjectForBeanInstance(sharedInstance, name, beanName, requiredType);
}
```

#### 方法3：createBean() - 创建Bean实例

**位置**：`AbstractAutowireCapableBeanFactory.java:537`

```java
@Override
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
    throws BeanCreationException {

    RootBeanDefinition mbdToUse = mbd;

    // 1. 确保BeanDefinition是合并后的
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }

    // 2. ⭐️ 准备方法覆盖（lookup-method、replace-method）
    mbdToUse.prepareMethodOverrides();

    try {
        // 3. ⭐️ 给BeanPostProcessor机会返回代理对象（AOP的关键）
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    } catch (Throwable ex) {
        throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, ex);
    }

    try {
        // 4. ⭐️ 真正创建Bean实例
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        return beanInstance;
    } catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
        throw ex;
    } catch (Throwable ex) {
        throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, ex);
    }
}
```

#### 方法4：doCreateBean() - 真正创建Bean实例

**位置**：`AbstractAutowireCapableBeanFactory.java:624`

```java
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
    // 1. ⭐️ 实例化Bean
    BeanWrapper instanceWrapper = null;
    if (instanceWrapper == null) {
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    Object bean = instanceWrapper.getWrappedInstance();
    Class<?> beanType = instanceWrapper.getWrappedClass();

    // 2. ⭐️ 应用MergedBeanDefinitionPostProcessor
    synchronized (mbd.postProcessingLock) {
        if (!mbd.postProcessed) {
            applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            mbd.postProcessed = true;
        }
    }

    // 3. ⭐️ 暴露早期引用到三级缓存（解决循环依赖）
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
        isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // 4. ⭐️ 初始化Bean实例
    Object exposedObject = bean;
    try {
        populateBean(beanName, mbd, instanceWrapper);  // 属性赋值
        exposedObject = initializeBean(beanName, exposedObject, mbd);  // 初始化
    } catch (Throwable ex) {
        // 异常处理
    }

    // 5. ⭐️ 注册销毁方法
    registerDisposableBeanIfNecessary(beanName, bean, mbd);

    return exposedObject;
}
```

### 阶段2：属性赋值

#### 方法5：populateBean() - 属性赋值

**位置**：`AbstractAutowireCapableBeanFactory.java:1426`

```java
protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
    // 1. 获取PropertyValues
    PropertyValues pvs = mbd.getPropertyValues();

    // 2. ⭐️ 给InstantiationAwareBeanPostProcessor机会修改属性
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp =
                    (InstantiationAwareBeanPostProcessor) bp;
                if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                    return;  // 如果返回false，跳过属性赋值
                }
            }
        }
    }

    // 3. ⭐️ 自动装配模式（byName、byType）
    int resolvedAutowireMode = mbd.getResolvedAutowireMode();
    if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
        MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

        // byName自动装配
        if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
            autowireByName(beanName, mbd, bw, newPvs);
        }

        // byType自动装配
        if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
            autowireByType(beanName, mbd, bw, newPvs);
        }

        pvs = newPvs;
    }

    // 4. ⭐️ 应用@Value和@Autowired
    boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
    if (hasInstAwareBpps) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp =
                    (InstantiationAwareBeanPostProcessor) bp;
                // ⭐️ 这里处理@Autowired和@Value注解
                PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
                if (pvsToUse == null) {
                    return;
                }
                pvs = pvsToUse;
            }
        }
    }

    // 5. ⭐️ 应用属性值
    applyPropertyValues(beanName, mbd, bw, pvs);
}
```

### 阶段3：初始化

#### 方法6：initializeBean() - 初始化Bean

**位置**：`AbstractAutowireCapableBeanFactory.java:1797`

```java
protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
    // 1. ⭐️ 执行Aware接口回调
    invokeAwareMethods(beanName, bean);

    // 2. ⭐️ 执行BeanPostProcessor前置处理
    Object wrappedBean = bean;
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }

    // 3. ⭐️ 执行初始化方法
    try {
        invokeInitMethods(beanName, wrappedBean, mbd);
    } catch (Throwable ex) {
        throw new BeanCreationException(
            (mbd != null ? mbd.getResourceDescription() : null), beanName, ex);
    }

    // 4. ⭐️ 执行BeanPostProcessor后置处理
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }

    return wrappedBean;
}
```

#### 方法7：invokeAwareMethods() - Aware接口回调

**位置**：`AbstractAutowireCapableBeanFactory.java:1733`

```java
private void invokeAwareMethods(String beanName, Object bean) {
    if (bean instanceof Aware) {
        // 1. BeanNameAware
        if (bean instanceof BeanNameAware) {
            ((BeanNameAware) bean).setBeanName(beanName);
        }

        // 2. BeanClassLoaderAware
        if (bean instanceof BeanClassLoaderAware) {
            ((BeanClassLoaderAware) bean).setBeanClassLoader(getBeanClassLoader());
        }

        // 3. BeanFactoryAware
        if (bean instanceof BeanFactoryAware) {
            ((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
        }
    }
}
```

#### 方法8：applyBeanPostProcessorsBeforeInitialization() - 前置处理

**位置**：`AbstractAutowireCapableBeanFactory.java:1174`

```java
@Override
public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName) {
    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {
        Object current = processor.postProcessBeforeInitialization(result, beanName);
        if (current == null) {
            return result;
        }
        result = current;
    }
    return result;
}
```

#### 方法9：invokeInitMethods() - 初始化方法

**位置**：`AbstractAutowireCapableBeanFactory.java:1863`

```java
protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
    // 1. ⭐️ 执行InitializingBean接口的afterPropertiesSet()方法
    boolean isInitializingBean = (bean instanceof InitializingBean);
    if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
        ((InitializingBean) bean).afterPropertiesSet();
    }

    // 2. ⭐️ 执行自定义的init-method方法
    if (mbd != null && bean.getClass() != NullBean.class) {
        String initMethodName = mbd.getInitMethodName();
        if (StringUtils.hasText(initMethodName) &&
            !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
            !mbd.isExternallyManagedInitMethod(initMethodName)) {
            // 反射调用自定义初始化方法
            invokeCustomInitMethod(bean, initMethodName);
        }
    }
}
```

#### 方法10：applyBeanPostProcessorsAfterInitialization() - 后置处理

**位置**：`AbstractAutowireCapableBeanFactory.java:1188`

```java
@Override
public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName) {
    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {
        Object current = processor.postProcessAfterInitialization(result, beanName);
        if (current == null) {
            return result;
        }
        result = current;
    }
    return result;
}
```

**关键点**：AOP代理在这里创建！

```java
// AbstractAutoProxyCreator.postProcessAfterInitialization()
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);

        // ⭐️ 如果需要代理，创建代理对象
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
    }
    return bean;
}
```

### 阶段4：销毁

#### 方法11：destroy() - 销毁Bean

**位置**：`DisposableBeanAdapter.java:273`

```java
@Override
public void destroy() {
    // 1. ⭐️ 执行@PreDestroy注解的方法
    if (this.invokeDisposableBean) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking destroy() on bean with name '" + this.beanName + "'");
        }
        try {
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                    ((DisposableBean) this.bean).destroy();
                    return null;
                });
            } else {
                ((DisposableBean) this.bean).destroy();
            }
        } catch (Throwable ex) {
            // 异常处理
        }
    }

    // 2. ⭐️ 执行自定义的destroy-method方法
    if (this.destroyMethod != null) {
        invokeCustomDestroyMethod(this.destroyMethod);
    }
}
```

---

## 🎯 Bean生命周期扩展点

### 扩展点1：BeanFactoryPostProcessor

**作用**：在Bean实例化之前修改BeanDefinition

**调用时机**：容器启动时，在Bean实例化之前

**示例**：
```java
@Component
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 可以在这里修改BeanDefinition
        BeanDefinition bd = beanFactory.getBeanDefinition("userService");
        bd.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
    }
}
```

### 扩展点2：BeanPostProcessor

**作用**：在Bean初始化前后进行自定义处理

**调用时机**：每个Bean初始化前后

**示例**：
```java
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Bean初始化前
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Bean初始化后（可以在这里创建代理对象）
        return bean;
    }
}
```

### 扩展点3：InitializingBean

**作用**：Bean属性设置完成后执行初始化逻辑

**调用时机**：属性赋值完成后

**示例**：
```java
@Component
public class UserService implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        // 初始化逻辑
    }
}
```

### 扩展点4：DisposableBean

**作用**：Bean销毁时执行清理逻辑

**调用时机**：容器关闭时

**示例**：
```java
@Component
public class UserService implements DisposableBean {

    @Override
    public void destroy() {
        // 清理逻辑
    }
}
```

### 扩展点5：Aware接口

**作用**：让Bean感知Spring容器的资源

**类型**：
- `BeanNameAware`：获取Bean名称
- `BeanFactoryAware`：获取BeanFactory
- `ApplicationContextAware`：获取ApplicationContext

**示例**：
```java
@Component
public class UserService implements BeanNameAware, ApplicationContextAware {

    private String beanName;
    private ApplicationContext applicationContext;

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
```

---

## 🎯 完整示例

### 示例：演示Bean生命周期

```java
@Component
public class LifecycleBean implements BeanNameAware, BeanFactoryAware,
        InitializingBean, DisposableBean {

    private String beanName;
    private BeanFactory beanFactory;

    // 1. 构造器
    public LifecycleBean() {
        System.out.println("【1】构造器执行");
    }

    // 2. BeanNameAware
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("【2】BeanNameAware.setBeanName()：" + name);
    }

    // 3. BeanFactoryAware
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        System.out.println("【3】BeanFactoryAware.setBeanFactory()");
    }

    // 4. @PostConstruct
    @PostConstruct
    public void postConstruct() {
        System.out.println("【5】@PostConstruct执行");
    }

    // 5. InitializingBean
    @Override
    public void afterPropertiesSet() {
        System.out.println("【6】InitializingBean.afterPropertiesSet()");
    }

    // 6. init-method
    public void initMethod() {
        System.out.println("【7】init-method执行");
    }

    // 7. @PreDestroy
    @PreDestroy
    public void preDestroy() {
        System.out.println("【9】@PreDestroy执行");
    }

    // 8. DisposableBean
    @Override
    public void destroy() {
        System.out.println("【10】DisposableBean.destroy()");
    }

    // 9. destroy-method
    public void destroyMethod() {
        System.out.println("【11】destroy-method执行");
    }
}

// BeanPostProcessor
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean) {
            System.out.println("【4】BeanPostProcessor.postProcessBeforeInitialization()");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean) {
            System.out.println("【8】BeanPostProcessor.postProcessAfterInitialization()");
        }
        return bean;
    }
}
```

**输出**：
```
【1】构造器执行
【2】BeanNameAware.setBeanName()：lifecycleBean
【3】BeanFactoryAware.setBeanFactory()
【4】BeanPostProcessor.postProcessBeforeInitialization()
【5】@PostConstruct执行
【6】InitializingBean.afterPropertiesSet()
【7】init-method执行
【8】BeanPostProcessor.postProcessAfterInitialization()
【9】@PreDestroy执行
【10】DisposableBean.destroy()
【11】destroy-method执行
```

---

## 🎯 总结

### 核心要点

1. ✅ **Bean生命周期分为5个阶段**：实例化、属性赋值、初始化、使用、销毁
2. ✅ **实例化**：反射创建Bean实例
3. ✅ **属性赋值**：依赖注入（@Autowired、@Value）
4. ✅ **初始化**：
   - Aware接口回调
   - BeanPostProcessor前置处理
   - InitializingBean.afterPropertiesSet()
   - @PostConstruct
   - init-method
   - BeanPostProcessor后置处理（AOP代理在这里创建）
5. ✅ **销毁**：
   - @PreDestroy
   - DisposableBean.destroy()
   - destroy-method

### 扩展点执行顺序

1. BeanFactoryPostProcessor（容器启动时）
2. InstantiationAwareBeanPostProcessor（实例化前后）
3. BeanPostProcessor（初始化前后）
4. InitializingBean（初始化时）
5. DisposableBean（销毁时）

### 最佳实践

- ✅ 使用`@PostConstruct`代替InitializingBean
- ✅ 使用`@PreDestroy`代替DisposableBean
- ✅ 优先使用注解，避免实现接口
- ✅ BeanPostProcessor是扩展Bean逻辑的最佳方式

### 扩展阅读

- [Bean实例化过程深度解析](./02-Bean实例化过程深度解析.md)
- [Bean属性注入详解](./03-Bean属性注入与初始化详解.md)
- [BeanPostProcessor机制详解](./04-BeanPostProcessor机制详解.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐（高级）
