# Spring循环依赖解决机制深度解析

## 目录
1. [循环依赖概述](#1-循环依赖概述)
2. [Spring的三级缓存机制](#2-spring的三级缓存机制)
3. [循环依赖解决流程](#3-循环依赖解决流程)
4. [关键源码分析](#4-关键源码分析)
5. [AOP代理与循环依赖](#5-aop代理与循环依赖)
6. [无法解决的循环依赖](#6-无法解决的循环依赖)
7. [最佳实践](#7-最佳实践)

---

## 1. 循环依赖概述

### 1.1 什么是循环依赖

循环依赖是指两个或多个Bean之间相互依赖，形成环形依赖关系。例如：
- Bean A依赖Bean B
- Bean B依赖Bean A
- 形成A→B→A的循环依赖

### 1.2 循环依赖的类型

Spring能够解决的循环依赖主要分为两种：

#### 1.2.1 Setter注入循环依赖（可解决）
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

#### 1.2.2 构造器注入循环依赖（无法解决）
```java
@Component
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@Component
public class ServiceB {
    private final ServiceA serviceA;
    
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

---

## 2. Spring的三级缓存机制

### 2.1 三级缓存概述

Spring使用三级缓存机制来解决单例Bean的循环依赖问题，这三级缓存分别是：

| 缓存级别 | 缓存名称 | 变量名 | 存储内容 | 作用 |
|---------|---------|-------|---------|------|
| 一级缓存 | 完整单例对象缓存 | singletonObjects | 完全初始化完成的Bean实例 | 存储完整的单例Bean，供其他Bean引用 |
| 二级缓存 | 早期单例对象缓存 | earlySingletonObjects | 提前暴露的Bean引用（可能是代理对象） | 存储半成品的Bean，用于解决循环依赖 |
| 三级缓存 | 单例工厂缓存 | singletonFactories | ObjectFactory工厂对象 | 存储能够创建Bean或代理对象的工厂 |

### 2.2 缓存源码分析

#### 2.2.1 三级缓存定义
```java
// DefaultSingletonBeanRegistry.java

/** 【一级缓存】完整的单例Bean对象缓存 */
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

/** 【三级缓存】Bean工厂对象缓存 */
private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(16);

/** 【二级缓存】提前暴露的Bean对象缓存 */
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

/** 【循环依赖检测集合】正在创建中的Bean名称集合 */
private final Set<String> singletonsCurrentlyInCreation = ConcurrentHashMap.newKeySet(16);
```

#### 2.2.2 三级缓存的作用时机

1. **一级缓存（singletonObjects）**
   - Bean完全初始化完成后放入
   - `addSingleton()`方法执行
   - 其他Bean从这里获取完整的Bean

2. **二级缓存（earlySingletonObjects）**
   - 在循环依赖时临时存储早期Bean引用
   - 从三级缓存升级而来
   - 避免重复执行三级缓存的工厂逻辑

3. **三级缓存（singletonFactories）**
   - 在Bean实例化后立即放入
   - `addSingletonFactory()`方法执行
   - 存储的是ObjectFactory而不是Bean本身

---

## 3. 循环依赖解决流程

### 3.1 完整解决流程图

```
创建Bean A
├─ 1. 实例化A（调用构造器）
├─ 2. 暴露早期引用（将A的工厂放入三级缓存）
├─ 3. 属性注入
│   └─ 发现需要Bean B
│       └─ 创建Bean B
│           ├─ 1. 实例化B
│           ├─ 2. 暴露早期引用（将B的工厂放入三级缓存）
│           ├─ 3. 属性注入
│           │   └─ 发现需要Bean A
│           │       └─ 从三级缓存获取A的工厂
│           │           └─ 调用getEarlyBeanReference()
│           │               └─ 将A从三级缓存移到二级缓存
│           │       └─ B获得A的早期引用
│           ├─ 4. 初始化B
│           └─ 5. B完成，放入一级缓存
├─ 4. 继续A的属性注入（获得B的完整引用）
├─ 5. 初始化A
└─ 6. A完成，放入一级缓存
```

### 3.2 详细步骤说明

#### 步骤1：实例化Bean A
```java
// AbstractAutowireCapableBeanFactory.doCreateBean()
// 1. 创建Bean实例
Object bean = instanceWrapper.getWrappedInstance();
```

#### 步骤2：暴露早期引用
```java
// AbstractAutowireCapableBeanFactory.doCreateBean()
// 2. 判断是否需要提前暴露
boolean earlySingletonExposure = (mbd.isSingleton() && 
                                  this.allowCircularReferences &&
                                  isSingletonCurrentlyInCreation(beanName));

if (earlySingletonExposure) {
    // 3. 将ObjectFactory添加到三级缓存
    addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
}
```

#### 步骤3：属性注入A
```java
// AbstractAutowireCapableBeanFactory.populateBean()
// 4. 开始属性注入，发现需要Bean B
populateBean(beanName, mbd, instanceWrapper);
```

#### 步骤4：创建Bean B
```java
// DefaultSingletonBeanRegistry.getSingleton()
// 5. 获取Bean B，触发B的创建流程
Object singletonObject = this.singletonObjects.get(beanName);
if (singletonObject == null) {
    singletonObject = singletonFactory.getObject();
}
```

#### 步骤5：B需要A的引用
```java
// DefaultSingletonBeanRegistry.getSingleton(beanName, true)
// 6. B在属性注入时需要A，从缓存获取A
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 从一级缓存获取（没有）
    Object singletonObject = this.singletonObjects.get(beanName);
    
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        // 从二级缓存获取（没有）
        singletonObject = this.earlySingletonObjects.get(beanName);
        
        if (singletonObject == null && allowEarlyReference) {
            // 从三级缓存获取工厂
            ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
            if (singletonFactory != null) {
                // 调用工厂获取早期引用
                singletonObject = singletonFactory.getObject();
                // 从三级缓存移到二级缓存
                this.earlySingletonObjects.put(beanName, singletonObject);
                this.singletonFactories.remove(beanName);
            }
        }
    }
    return singletonObject;
}
```

#### 步骤6：A完成初始化
```java
// AbstractAutowireCapableBeanFactory.initializeBean()
// 7. A继续完成初始化
exposedObject = initializeBean(beanName, exposedObject, mbd);

// 8. 将完全初始化的A放入一级缓存
addSingleton(beanName, exposedObject);
```

---

## 4. 关键源码分析

### 4.1 核心方法：getEarlyBeanReference()

```java
/**
 * 【获取早期Bean引用的核心方法】获取Bean的早期引用，用于解决循环依赖
 * 
 * 这个方法是Spring解决循环依赖的关键之一，它的作用是：
 * 1. 在Bean实例化后、属性注入前被调用
 * 2. 给SmartInstantiationAwareBeanPostProcessor机会提前创建代理对象
 * 3. 确保在循环依赖中，其他Bean引用的是正确的代理对象
 */
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    // 检查是否需要处理早期引用
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        // 遍历所有智能后置处理器
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

### 4.2 循环依赖检测：beforeSingletonCreation()

```java
/**
 * 【Bean创建前的回调】在Bean创建前进行循环依赖检测
 * 
 * 循环依赖检测机制：
 * 1. 将Bean名称添加到singletonsCurrentlyInCreation集合中
 * 2. 如果添加失败（返回false），说明该Bean已经在创建中，检测到循环依赖
 * 3. 抛出BeanCurrentlyInCreationException异常
 */
protected void beforeSingletonCreation(String beanName) {
    if (!this.inCreationCheckExclusions.contains(beanName) && 
        !this.singletonsCurrentlyInCreation.add(beanName)) {
        throw new BeanCurrentlyInCreationException(beanName);
    }
}
```

### 4.3 三级缓存获取：getSingleton()

```java
/**
 * 【三级缓存获取Bean的核心方法】从缓存中获取单例Bean
 * 
 * 这个方法实现了Spring的三级缓存机制，是解决循环依赖的核心：
 * 1. 首先从一级缓存获取完整的Bean
 * 2. 如果一级缓存没有且Bean正在创建中，从二级缓存获取半成品Bean
 * 3. 如果二级缓存也没有且允许早期引用，从三级缓存获取Bean工厂并创建Bean
 */
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 第一步：检查一级缓存
    Object singletonObject = this.singletonObjects.get(beanName);
    
    // 第二步：检查二级缓存
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        singletonObject = this.earlySingletonObjects.get(beanName);
        
        // 第三步：检查三级缓存
        if (singletonObject == null && allowEarlyReference) {
            if (!this.singletonLock.tryLock()) {
                return null;
            }
            try {
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            singletonObject = singletonFactory.getObject();
                            // 从三级缓存升级到二级缓存
                            if (this.singletonFactories.remove(beanName) != null) {
                                this.earlySingletonObjects.put(beanName, singletonObject);
                            }
                        }
                    }
                }
            } finally {
                this.singletonLock.unlock();
            }
        }
    }
    return singletonObject;
}
```

---

## 5. AOP代理与循环依赖

### 5.1 为什么需要三级缓存

Spring使用三级缓存的设计是为了在循环依赖场景中正确处理AOP代理。虽然从技术角度上看，对于不需要代理的Bean，二级缓存就足够了，但Spring采用了统一的三级缓存机制来处理所有情况。

#### 5.1.1 问题的本质

在循环依赖场景中，如果Bean需要被AOP代理：
- 其他Bean引用的应该是代理对象而不是原始对象
- 但代理对象通常在初始化阶段的最后才创建
- 这就产生了矛盾：循环依赖需要提前引用，但代理需要延后创建

#### 5.1.2 三级缓存的解决方案

```java
// 在三级缓存中存储的是ObjectFactory，而不是Bean本身
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));

// ObjectFactory可以延迟决定返回什么对象
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            // 只有当Bean需要AOP代理时，这里才会提前创建代理对象
            // 如果Bean不需要代理，则返回原始对象
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

### 5.2 AbstractAutoProxyCreator的作用

```java
// AbstractAutoProxyCreator是AOP代理的核心处理器
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
        implements SmartInstantiationAwareBeanPostProcessor {

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) {
        Object proxy = getEarlyBeanReferenceCache().get(beanName);
        if (proxy == null) {
            // 提前创建代理对象
            proxy = wrapIfNecessary(bean, beanName, null);
            getEarlyBeanReferenceCache().put(beanName, proxy);
        }
        return proxy;
    }
}
```

### 5.3 AOP代理与循环依赖的完整流程

```
1. 创建Bean A（原始对象）
2. 将A的ObjectFactory放入三级缓存
3. A开始属性注入，需要B
4. 创建Bean B（原始对象）
5. 将B的ObjectFactory放入三级缓存
6. B开始属性注入，需要A
7. 从三级缓存获取A的工厂
8. 调用getEarlyBeanReference()，发现A需要AOP代理
9. 提前创建A的代理对象，放入二级缓存
10. B获得A的代理对象引用
11. B完成初始化（可能也会被代理）
12. A继续属性注入，获得B的引用
13. A完成初始化
14. 检查：A最终的对象与早期引用是否一致
```

---

## 6. 无法解决的循环依赖

### 6.1 构造器循环依赖

构造器循环依赖无法通过三级缓存解决，因为：

1. **实例化阶段就发生依赖**
   - 构造器注入发生在实例化阶段
   - 此时还没有机会将Bean工厂放入三级缓存

2. **Spring的处理方式**
   - 直接抛出BeanCurrentlyInCreationException异常
   - 提示用户存在构造器循环依赖

#### 6.1.1 错误示例
```java
@Component
public class ServiceA {
    private final ServiceB serviceB;
    
    @Autowired
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@Component
public class ServiceB {
    private final ServiceA serviceA;
    
    @Autowired
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

#### 6.1.2 异常信息
```
org.springframework.beans.factory.BeanCurrentlyInCreationException: 
Error creating bean with name 'serviceA': 
Requested bean is currently in creation: Is there an unresolvable circular reference?
```

### 6.2 原型作用域的循环依赖

Spring不解决原型作用域的循环依赖，因为：

1. **原型Bean不缓存**
   - 每次获取都创建新实例
   - 无法使用缓存机制

2. **容器不管理完整生命周期**
   - 原型Bean的销毁由客户端负责
   - 容器不进行循环依赖检测

### 6.3 解决方案

#### 6.3.1 使用@Lazy注解

@Lazy注解可以解决构造器循环依赖问题。它的工作原理是：

- **创建代理对象**：Spring为延迟的依赖创建一个代理对象
- **延迟解析**：真正的Bean实例在第一次使用时才创建
- **打破循环**：由于注入的是代理对象，不需要立即创建目标Bean，从而打破循环依赖

```java
@Component
public class ServiceA {
    private final ServiceB serviceB;
    
    @Autowired
    public ServiceA(@Lazy ServiceB serviceB) {
        // 这里注入的是ServiceB的代理对象，而不是真实的ServiceB实例
        this.serviceB = serviceB;  // Spring会创建一个代理对象
    }
}

@Component
public class ServiceB {
    private final ServiceA serviceA;
    
    @Autowired
    public ServiceB(ServiceA serviceA) {
        // ServiceA此时已经完成实例化，可以正常注入
        this.serviceA = serviceA;
    }
}
```

**注意**：@Lazy注解虽然可以解决构造器循环依赖，但会增加代码的复杂性和理解的难度，应该谨慎使用。

@Component
public class ServiceB {
    private final ServiceA serviceA;
    
    @Autowired
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

#### 6.3.2 使用Setter注解
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

#### 6.3.3 重新设计架构
```java
// 引入第三个Bean来打破循环依赖
@Component
public class ServiceA {
    @Autowired
    private ServiceMediator mediator;
}

@Component
public class ServiceB {
    @Autowired
    private ServiceMediator mediator;
}

@Component
public class ServiceMediator {
    @Autowired
    private ServiceA serviceA;
    
    @Autowired
    private ServiceB serviceB;
}
```

---

## 7. 最佳实践

### 7.1 避免循环依赖的设计原则

1. **优先使用Setter注入**
   - Setter注入支持循环依赖
   - 构造器注入不支持循环依赖

2. **使用@Lazy延迟加载**
   - 打破构造器循环依赖
   - 延迟代理对象的创建

3. **重新设计类结构**
   - 引入中间层打破循环
   - 使用事件机制解耦
   - 考虑使用设计模式

### 7.2 循环依赖的检测方法

#### 7.2.1 启用循环依赖检测
```java
@Configuration
public class AppConfig {
    
    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return beanFactory -> {
            if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
                clbf.setAllowCircularReferences(false); // 禁用循环依赖
            }
        };
    }
}
```

#### 7.2.2 使用依赖分析工具
```java
@Component
public class DependencyAnalyzer implements BeanFactoryPostProcessor {
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            String[] dependencies = beanFactory.getDependenciesForBean(beanName);
            // 分析依赖关系，检测潜在的循环依赖
        }
    }
}
```

### 7.3 调试循环依赖的技巧

#### 7.3.1 启用调试日志
```properties
# application.properties
logging.level.org.springframework.beans.factory=DEBUG
logging.level.org.springframework.beans.factory.support=TRACE
```

#### 7.3.2 使用断点调试
在以下位置设置断点：
- `DefaultSingletonBeanRegistry.beforeSingletonCreation()`
- `DefaultSingletonBeanRegistry.getSingleton()`
- `AbstractAutowireCapableBeanFactory.getEarlyBeanReference()`

#### 7.3.3 分析Bean创建日志
```
Creating shared instance of singleton bean 'serviceA'
Eagerly caching bean 'serviceA' to allow for resolving potential circular references
Creating shared instance of singleton bean 'serviceB'
Eagerly caching bean 'serviceB' to allow for resolving potential circular references
```

### 7.4 性能考虑

1. **三级缓存的影响**
   - 三级缓存会稍微增加内存开销
   - 但对于大多数应用来说影响可忽略

2. **代理对象的开销**
   - AOP代理会带来性能开销
   - 考虑使用编译时织入代替运行时织入

3. **循环依赖的解决成本**
   - 每次解决循环依赖都需要额外的查找操作
   - 复杂的循环依赖网络可能影响性能

---

## 总结

Spring的三级缓存机制是一个精妙的设计，它能够：

1. **解决单例Bean的循环依赖问题**
2. **支持AOP代理的提前创建**
3. **保持Bean的生命周期完整性**

理解这个机制对于：
- 排查循环依赖问题
- 设计合理的Bean依赖关系
- 优化Spring应用性能

都具有重要意义。

**关键要点：**
- 三级缓存是Spring解决循环依赖的核心机制
- 只有单例Bean的Setter注入循环依赖可以被解决
- AOP代理是三级缓存存在的重要原因
- 构造器循环依赖无法通过三级缓存解决
- 使用@Lazy可以解决构造器循环依赖问题