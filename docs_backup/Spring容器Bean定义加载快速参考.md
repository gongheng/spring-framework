# Spring容器Bean定义加载快速参考

## 🎯 核心问题：Bean定义什么时候加载的？

**答案**：在`invokeBeanFactoryPostProcessors()`步骤中加载（第604行）

## 📍 关键位置

```java
// AbstractApplicationContext.java 第604行
invokeBeanFactoryPostProcessors(beanFactory);
```

## 🔄 完整调用链路

```
SpringApplication.run() 【应用启动】
  ↓
refresh() 【Spring容器刷新：第582行】
  ↓
obtainFreshBeanFactory() 【第593行】
  ↓
GenericApplicationContext.refreshBeanFactory() 【第296行】
  - 只设置序列化ID，不加载Bean定义！
  ↓
invokeBeanFactoryPostProcessors(beanFactory) 【⭐️ 第604行 - Bean定义加载的真正入口】
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions() 【第389行】
  ↓
ConfigurationClassParser.parse() 【第168行】
  ↓
doProcessConfigurationClass() 【第303行】
  ↓
ComponentScanAnnotationParser.parse() 【第68行】
  ↓
ClassPathBeanDefinitionScanner.doScan() 【第275行】
  ↓
scanCandidateComponents(basePackage) 【🎯 第446行 - 扫描.class文件】
  ↓
读取.class文件 → 检查注解 → 创建BeanDefinition
  ↓
registerBeanDefinition() 【注册到BeanFactory】
```

## 📊 非Boot vs SpringBoot对比

| 对比维度 | 传统Spring（非Boot） | SpringBoot |
|---------|-------------------|-----------|
| **应用入口** | 手动创建ApplicationContext | `SpringApplication.run()` |
| **ApplicationContext** | `ClassPathXmlApplicationContext`<br>`AnnotationConfigApplicationContext` | `AnnotationConfigServletWebServerApplicationContext` |
| **BeanFactory创建** | XML应用：refresh()时创建<br>注解应用：构造函数中创建 | 构造函数中创建 |
| **Bean定义加载** | XML：refreshBeanFactory()中加载<br>注解：invokeBeanFactoryPostProcessors() | invokeBeanFactoryPostProcessors() |
| **ComponentScan** | 需手动配置`@ComponentScan` | `@SpringBootApplication`包含`@ComponentScan` |
| **包扫描范围** | 需手动指定basePackages | 默认扫描主配置类所在包及其子包 |

## 🔑 关键源码位置

| 功能 | 类名 | 方法 | 行号 |
|------|------|------|------|
| **SpringBoot启动** | SpringApplication | run() | 304 |
| **创建ApplicationContext** | SpringApplication | createApplicationContext() | 579 |
| **Spring容器刷新** | AbstractApplicationContext | refresh() | 582 |
| **⭐️ Bean定义加载入口** | AbstractApplicationContext | invokeBeanFactoryPostProcessors() | 604 |
| **处理配置类** | ConfigurationClassPostProcessor | processConfigBeanDefinitions() | 389 |
| **解析配置类** | ConfigurationClassParser | doProcessConfigurationClass() | 303 |
| **🎯 扫描.class文件** | ClassPathScanningCandidateComponentProvider | scanCandidateComponents() | 446 |

## 💡 核心概念

### Bean定义加载 ≠ Bean实例化

- **Bean定义加载**：扫描classpath，读取注解，创建BeanDefinition
  - 时机：`invokeBeanFactoryPostProcessors()` (第604行)
  - 结果：BeanFactory包含所有Bean定义

- **Bean实例化**：根据BeanDefinition创建Bean实例
  - 时机：`finishBeanFactoryInitialization()` (第622行)
  - 结果：所有Bean都创建好了

### 三个关键阶段

```
1. BeanFactory创建
   GenericApplicationContext构造函数 → 创建DefaultListableBeanFactory

2. Bean定义加载 ⭐️
   invokeBeanFactoryPostProcessors() → ConfigurationClassPostProcessor
   → 扫描@ComponentScan → 创建BeanDefinition → 注册到BeanFactory

3. Bean实例化
   finishBeanFactoryInitialization() → 创建Bean实例
```

## 🚀 SpringBoot应用的Bean定义加载流程

```java
// 1. 用户启动应用
SpringApplication.run(TodoApplication.class, args);

// 2. 创建ApplicationContext（内部自动）
AnnotationConfigServletWebServerApplicationContext创建
  ↓
GenericApplicationContext构造函数创建BeanFactory

// 3. 刷新容器（内部自动）
context.refresh()
  ↓
invokeBeanFactoryPostProcessors(beanFactory) 【⭐️ 关键步骤】
  ↓
ConfigurationClassPostProcessor处理@SpringBootApplication
  ↓
解析@ComponentScan注解
  ↓
扫描com.example.todo包及其子包
  ↓
找到所有@Component、@Service、@Repository、@Controller类
  ↓
为每个类创建BeanDefinition
  ↓
注册到BeanFactory
  ↓
✅ BeanFactory现在包含所有Bean定义！
```

## 📝 快速调试指南

### 设置断点位置

1. **入口断点**：`AbstractApplicationContext.invokeBeanFactoryPostProcessors()` (第604行)
2. **处理断点**：`ConfigurationClassPostProcessor.processConfigBeanDefinitions()` (第389行)
3. **扫描断点**：`ClassPathScanningCandidateComponentProvider.scanCandidateComponents()` (第446行)

### 观察重点

- **BeanFactory中的Bean定义数量**：在关键步骤查看`beanFactory.getBeanDefinitionCount()`
- **扫描的包路径**：查看`packageSearchPattern`变量
- **找到的组件类**：查看`candidates`集合

---

**详细文档**：`/docs/Spring容器启动与BeanFactory加载完整流程.md`
**文档创建时间**：2026-05-18
