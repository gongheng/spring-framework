# Spring Framework 深度学习文档体系

## 📚 文档导航

本文档体系按照从基础到高级、从理论到实践的逻辑组织，帮助你系统性地掌握Spring Framework的核心原理和高级特性。

---

## 🎯 学习路径建议

### 初学者路径
1. 先读 **01-核心容器** → 理解Spring容器的基本工作原理
2. 再读 **02-Bean生命周期** → 理解Bean的创建和销毁过程
3. 然后 **04-依赖注入** → 理解Spring的依赖注入机制
4. 最后 **06-AOP原理** → 理解面向切面编程

### 进阶路径
1. **03-容器扩展** → 学习如何扩展Spring容器
2. **05-事务管理** → 理解Spring的事务抽象机制
3. **07-高级特性** → 学习循环依赖、条件装配等高级主题

### 实战路径
1. **08-源码分析** → 深入理解Spring源码
2. **09-最佳实践** → 学习Spring的最佳实践和性能优化

---

## 📖 文档分类

### 01-核心容器（Core Container）
理解Spring容器的基本工作原理，这是学习Spring的基础

| 文档 | 说明 | 难度 |
|------|------|------|
| [容器启动流程详解](./01-核心容器/01-Spring容器启动流程详解.md) | 详细讲解Spring容器的启动过程，包括refresh()的12个步骤 | ⭐⭐⭐ |
| ApplicationContext家族 | 各种ApplicationContext的实现和使用场景 | ⭐⭐ |
| BeanFactory与ApplicationContext | 两个核心接口的对比和使用场景 | ⭐⭐ |
| BeanDefinition加载机制 | Bean定义如何被发现、解析和注册 | ⭐⭐⭐⭐ |
| GenericApplicationContext原理 | 现代Spring应用使用的ApplicationContext实现 | ⭐⭐⭐ |

### 02-Bean生命周期（Bean Lifecycle）
理解Spring Bean的完整生命周期，这是面试高频考点

| 文档 | 说明 | 难度 |
|------|------|------|
| [Bean完整生命周期](./02-Bean生命周期/01-Bean完整生命周期详解.md) | 从创建到销毁的完整过程，包括所有扩展点 | ⭐⭐⭐⭐ |
| Bean实例化过程 | createBeanInstance()的详细过程 | ⭐⭐⭐⭐ |
| Bean属性注入 | populateBean()和initializeBean()的详细过程 | ⭐⭐⭐⭐ |
| BeanPostProcessor详解 | Bean后置处理器的工作原理和应用场景 | ⭐⭐⭐ |
| Aware接口与回调 | 各种Aware接口的作用和使用场景 | ⭐⭐⭐ |

### 03-容器扩展（Container Extension）
学习如何扩展Spring容器，实现自定义功能

| 文档 | 说明 | 难度 |
|------|------|------|
| BeanFactoryPostProcessor | Bean工厂后置处理器的工作原理 | ⭐⭐⭐⭐ |
| [ConfigurationClassPostProcessor](./03-容器扩展/02-ConfigurationClassPostProcessor深度解析.md) | 配置类后置处理器，注解驱动的核心 | ⭐⭐⭐⭐⭐ |
| 自定义BeanDefinition注册 | 如何编程式注册Bean定义 | ⭐⭐⭐ |
| 自定义属性编辑器 | 如何实现自定义属性类型转换 | ⭐⭐⭐ |
| 自定义Scope | 如何实现自定义的Bean作用域 | ⭐⭐⭐⭐ |

### 04-依赖注入（Dependency Injection）
深入理解Spring的依赖注入机制

| 文档 | 说明 | 难度 |
|------|------|------|
| 依赖注入原理 | Spring如何实现依赖注入 | ⭐⭐⭐⭐ |
| 构造器注入 | 构造器注入的原理和使用场景 | ⭐⭐⭐ |
| Setter注入 | Setter注入的原理和使用场景 | ⭐⭐⭐ |
| 字段注入 | 字段注入（@Autowired）的原理和争议 | ⭐⭐⭐⭐ |
| [循环依赖解决](./04-依赖注入/05-循环依赖解决机制详解.md) | Spring如何解决循环依赖问题 | ⭐⭐⭐⭐⭐ |

### 05-事务管理（Transaction Management）
理解Spring的事务抽象机制

| 文档 | 说明 | 难度 |
|------|------|------|
| 事务抽象架构 | Spring事务管理的架构设计 | ⭐⭐⭐⭐ |
| [@Transactional原理](./05-事务管理/02-Transactional注解原理深度解析.md) | @Transactional注解的工作原理 | ⭐⭐⭐⭐⭐ |
| 事务传播机制 | 7种事务传播级别的详解 | ⭐⭐⭐⭐ |
| 事务隔离级别 | 事务隔离级别的原理和配置 | ⭐⭐⭐⭐ |
| 编程式事务 | 如何使用编程式事务管理 | ⭐⭐⭐ |

### 06-AOP原理（Aspect-Oriented Programming）
理解Spring AOP的实现原理

| 文档 | 说明 | 难度 |
|------|------|------|
| [AOP核心概念](./06-AOP原理/01-AOP核心概念与术语详解.md) | AOP的核心概念和术语 | ⭐⭐⭐ |
| JDK动态代理 | JDK动态代理的实现原理 | ⭐⭐⭐⭐ |
| CGLIB代理 | CGLIB代理的实现原理 | ⭐⭐⭐⭐ |
| @AspectJ注解原理 | @AspectJ注解的工作原理 | ⭐⭐⭐⭐⭐ |
| ProxyFactory详解 | 如何编程式创建代理 | ⭐⭐⭐ |

### 07-高级特性（Advanced Features）
Spring的高级特性

| 文档 | 说明 | 难度 |
|------|------|------|
| [条件装配(@Conditional)](./07-高级特性/01-条件装配Conditional原理详解.md) | @Conditional注解的工作原理 | ⭐⭐⭐⭐ |
| [自动配置原理](./07-高级特性/02-SpringBoot自动配置原理详解.md) | SpringBoot自动配置的实现原理 | ⭐⭐⭐⭐⭐ |
| [环境抽象(Environment)](./07-高级特性/03-Environment环境抽象详解.md) | Spring的环境抽象机制 | ⭐⭐⭐⭐ |
| 资源加载(Resource) | Spring的资源抽象和加载机制 | ⭐⭐⭐ |
| 类型转换(TypeConversion) | Spring的类型转换系统 | ⭐⭐⭐⭐ |

### 08-源码分析（Source Code Analysis）
深入分析Spring源码，理解设计思想

| 文档 | 说明 | 难度 |
|------|------|------|
| [refresh()源码分析](./08-源码分析/01-refresh方法源码深度分析.md) | AbstractApplicationContext.refresh()方法源码分析 | ⭐⭐⭐⭐⭐ |
| Bean创建源码分析 | doCreateBean()方法源码分析 | ⭐⭐⭐⭐⭐ |
| AOP源码分析 | Spring AOP核心源码分析 | ⭐⭐⭐⭐⭐ |
| 事务源码分析 | Spring事务管理核心源码分析 | ⭐⭐⭐⭐⭐ |
| 设计模式分析 | Spring中使用的设计模式分析 | ⭐⭐⭐⭐ |

### 09-最佳实践（Best Practices）
Spring的最佳实践和性能优化

| 文档 | 说明 | 难度 |
|------|------|------|
| [Bean设计最佳实践](./09-最佳实践/01-Bean设计最佳实践.md) | 设计Bean的最佳实践 | ⭐⭐⭐ |
| 依赖注入最佳实践 | 依赖注入的最佳实践 | ⭐⭐⭐ |
| 事务管理最佳实践 | 事务管理的最佳实践 | ⭐⭐⭐⭐ |
| AOP最佳实践 | AOP使用的最佳实践 | ⭐⭐⭐⭐ |
| [性能优化指南](./09-最佳实践/05-Spring应用性能优化指南.md) | Spring应用的性能优化技巧 | ⭐⭐⭐⭐⭐ |

### 10-快速参考（Quick Reference）
快速查阅手册，方便日常开发

| 文档 | 说明 |
|------|------|
| 核心类快速参考 | Spring核心类快速参考 |
| 注解快速参考 | Spring注解快速参考 |
| 配置方式对比 | XML和注解配置方式对比 |
| [常见问题FAQ](./10-快速参考/04-常见问题FAQ.md) | Spring常见问题FAQ |
| 调试技巧 | Spring应用调试技巧和工具 |

---

## 🎯 按主题学习

### 理解Spring容器启动
1. [容器启动流程详解](./01-核心容器/01-Spring容器启动流程详解.md)
2. ApplicationContext家族
3. BeanDefinition加载机制
4. [refresh()源码分析](./08-源码分析/01-refresh方法源码深度分析.md)

### 理解Bean创建和初始化
1. [Bean完整生命周期](./02-Bean生命周期/01-Bean完整生命周期详解.md)
2. Bean实例化过程
3. Bean属性注入
4. 依赖注入原理

### 理解依赖注入和循环依赖
1. 依赖注入原理
2. [循环依赖解决](./04-依赖注入/05-循环依赖解决机制详解.md)
3. Bean创建源码分析

### 理解AOP和事务
1. [AOP核心概念](./06-AOP原理/01-AOP核心概念与术语详解.md)
2. @AspectJ注解原理
3. 事务抽象架构
4. [@Transactional原理](./05-事务管理/02-Transactional注解原理深度解析.md)

### 深入Spring源码
1. [refresh()源码分析](./08-源码分析/01-refresh方法源码深度分析.md)
2. Bean创建源码分析
3. AOP源码分析
4. 设计模式分析

---

## 📝 文档说明

- **难度评级**：
  - ⭐⭐ 入门级，适合Spring初学者
  - ⭐⭐⭐ 进阶级，需要一定的Spring基础
  - ⭐⭐⭐⭐ 高级，需要深入理解Spring原理
  - ⭐⭐⭐⭐⭐ 专家级，深入源码级别

- **文档标识**：
  - 📚 理论知识
  - 💡 实践指南
  - 🔍 源码分析
  - 🎯 最佳实践

---

## 🚀 开始学习

如果你是第一次学习Spring，建议按照**初学者路径**的顺序阅读文档。

如果你已经有Spring基础，可以直接跳转到感兴趣的主题深入学习。

如果你在开发中遇到问题，可以先查看**10-快速参考**部分寻找答案。

---

**文档版本**：v2.0
**最后更新**：2026-05-19
**Spring Framework版本**：7.1.0-SNAPSHOT
**Spring Boot版本**：基于Spring 7.x开发中
