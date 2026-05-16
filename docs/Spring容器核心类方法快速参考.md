# Spring容器核心类方法快速参考

## 核心类层次结构

```
ApplicationContext (接口)
    ↓
AbstractApplicationContext (抽象类)
    ↓
├── AbstractRefreshableApplicationContext (支持多次刷新)
│   ↓
│   ├── AbstractXmlApplicationContext (XML配置)
│   └── AnnotationConfigApplicationContext (注解配置)
│
└── GenericApplicationContext (单次刷新，编程式注册)
```

## 关键类方法索引

### 1. AbstractApplicationContext
**包路径**: `org.springframework.context.support`

| 方法名 | 作用 | 关键步骤 |
|--------|------|----------|
| `refresh()` | 容器刷新核心方法 | 12步完整流程 |
| `prepareRefresh()` | 准备刷新环境 | 设置启动时间、激活标志 |
| `obtainFreshBeanFactory()` | 获取新的Bean工厂 | 委托子类刷新Bean工厂 |
| `prepareBeanFactory()` | 准备Bean工厂 | 设置基础配置 |
| `invokeBeanFactoryPostProcessors()` | 调用Bean工厂后置处理器 | 处理配置类 |
| `finishBeanFactoryInitialization()` | 完成Bean工厂初始化 | 实例化所有单例Bean |

**关键代码**:
```java
public void refresh() throws BeansException, IllegalStateException {
    // 1. 准备刷新容器
    prepareRefresh();
    
    // 2. 获取新的Bean工厂（加载Bean定义）
    ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
    
    // 3-10. 各种准备工作...
    
    // 11. 实例化所有非懒加载单例Bean
    finishBeanFactoryInitialization(beanFactory);
    
    // 12. 完成刷新
    finishRefresh();
}
```

### 2. AbstractRefreshableApplicationContext
**包路径**: `org.springframework.context.support`

| 方法名 | 作用 | 实现细节 |
|--------|------|----------|
| `refreshBeanFactory()` | 刷新Bean工厂 | 创建新工厂，加载Bean定义 |
| `createBeanFactory()` | 创建Bean工厂 | 返回DefaultListableBeanFactory |
| `customizeBeanFactory()` | 自定义Bean工厂 | 设置覆盖、循环引用等策略 |
| `loadBeanDefinitions()` | 加载Bean定义 | **抽象方法，子类实现** |

**关键代码**:
```java
protected final void refreshBeanFactory() throws BeansException {
    if (hasBeanFactory()) {
        destroyBeans();
        closeBeanFactory();
    }
    
    DefaultListableBeanFactory beanFactory = createBeanFactory();
    beanFactory.setSerializationId(getId());
    customizeBeanFactory(beanFactory);
    
    // 【核心】加载Bean定义（子类实现）
    loadBeanDefinitions(beanFactory);
    
    this.beanFactory = beanFactory;
}
```

### 3. ConfigurationClassPostProcessor
**包路径**: `org.springframework.context.annotation`

| 方法名 | 作用 | 处理内容 |
|--------|------|----------|
| `processConfigBeanDefinitions()` | 处理配置类 | 扫描、解析、注册Bean定义 |
| `postProcessBeanFactory()` | 后处理Bean工厂 | 增强配置类 |

**处理流程**:
```java
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    // 1. 扫描候选配置类
    // 2. 排序
    // 3. 创建解析器
    // 4. 解析配置类（@ComponentScan、@Import、@Bean等）
    // 5. 注册Bean定义
    // 6. 循环处理新发现的配置类
}
```

### 4. ConfigurationClassParser
**包路径**: `org.springframework.context.annotation`

| 方法名 | 作用 | 处理的注解 |
|--------|------|------------|
| `parse()` | 解析配置类 | 所有配置相关注解 |
| `processComponentScans()` | 处理组件扫描 | @ComponentScan |
| `processImports()` | 处理导入 | @Import |
| `processImportResource()` | 处理资源导入 | @ImportResource |
| `processBeanMethods()` | 处理Bean方法 | @Bean |

**处理顺序**:
```java
protected void processConfigurationClass(ConfigurationClass configClass) {
    // 1. 处理@ComponentScan
    // 2. 处理@Import
    // 3. 处理@ImportResource
    // 4. 处理@Bean方法
    // 5. 处理接口默认方法
    // 6. 处理父类
}
```

### 5. ClassPathBeanDefinitionScanner
**包路径**: `org.springframework.context.annotation`

| 方法名 | 作用 | 返回结果 |
|--------|------|----------|
| `scan()` | 扫描指定包 | 注册的Bean数量 |
| `findCandidateComponents()` | 查找候选组件 | BeanDefinition集合 |

**扫描过滤器**:
- `includeFilters`: 包含的组件类型
- `excludeFilters`: 排除的组件类型
- 默认包含: `@Component`、`@Service`、`@Repository`、`@Controller`

## 注解处理器链

### ConfigurationClassPostProcessor 处理链
```
refresh()
  ↓
invokeBeanFactoryPostProcessors()
  ↓
ConfigurationClassPostProcessor.processConfigBeanDefinitions()
  ↓
ConfigurationClassParser.parse()
  ↓
├─ @ComponentScan → ClassPathBeanDefinitionScanner
├─ @Import → ConfigurationClassParser.processImports()
├─ @ImportResource → ConfigurationClassBeanDefinitionReader
└─ @Bean → ConfigurationClassBeanDefinitionReader
  ↓
ConfigurationClassBeanDefinitionReader.loadBeanDefinitions()
  ↓
BeanDefinitionRegistry.registerBeanDefinition()
```

## Bean定义生命周期

```
1. 发现阶段
   ├─ 组件扫描 (@ComponentScan)
   ├─ 手动注册 (@Import, @Bean)
   └─ XML解析 (@ImportResource)

2. 解析阶段
   ├─ 读取注解元数据
   ├─ 构建ConfigurationClass
   └─ 处理条件注解 (@Conditional)

3. 注册阶段
   ├─ 创建BeanDefinition
   ├─ 设置属性 (scope, lazy, primary等)
   └─ 注册到BeanDefinitionRegistry

4. 实例化阶段
   ├─ 创建Bean实例
   ├─ 属性注入
   ├─ 初始化回调
   └─ 放入单例缓存
```

## 常见配置类标记

| 注解 | 作用 | 识别条件 |
|------|------|----------|
| @Configuration | 全配置类 | proxyBeanMethods启用时增强 |
| @Component | 轻量配置类 | 不增强，按普通组件处理 |
| @ComponentScan | 组件扫描 | 触发扫描流程 |
| @Import | 导入配置 | 直接或间接导入其他配置类 |
| @ImportResource | 导入XML | 兼容XML配置 |

## 关键接口和抽象类

### BeanDefinitionRegistry
**作用**: Bean定义注册表接口
**方法**: 
- `registerBeanDefinition()` - 注册Bean定义
- `removeBeanDefinition()` - 移除Bean定义
- `getBeanDefinition()` - 获取Bean定义

### BeanFactoryPostProcessor
**作用**: Bean工厂后置处理器接口
**时机**: 在Bean定义加载后、Bean实例化前执行
**实现**: ConfigurationClassPostProcessor

### BeanDefinitionReader
**作用**: Bean定义读取器接口
**实现**: 
- XmlBeanDefinitionReader - XML配置读取
- AnnotatedBeanDefinitionReader - 注解配置读取

## 调试技巧

### 断点位置
1. `AbstractApplicationContext.refresh()` - 容器启动入口
2. `ConfigurationClassPostProcessor.processConfigBeanDefinitions()` - 配置类处理
3. `ConfigurationClassParser.parse()` - 单个配置类解析
4. `ClassPathBeanDefinitionScanner.scan()` - 组件扫描

### 日志配置
```xml
<logger name="org.springframework.context" level="DEBUG"/>
<logger name="org.springframework.beans" level="DEBUG"/>
```

### 常用环境变量
```java
// 开启调试模式
System.setProperty("spring.debug", "true");

// 开启条件评估报告
System.setProperty("debug", "true");
```

## 性能考虑

1. **延迟初始化**: `@Lazy` 注解避免不必要的Bean创建
2. **作用域控制**: 合理使用singleton、prototype等作用域
3. **条件加载**: `@Conditional` 系列注解按需加载Bean
4. **扫描优化**: 精确指定@ComponentScan的包路径，避免过大范围

---

*快速参考文档 - Spring Framework核心容器*  
*版本: 6.x*  
*最后更新: 2025年*
