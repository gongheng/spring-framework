# Spring容器扩展开发实践指南

## 自定义ApplicationContext示例

### 1. 基于注解的自定义容器

```java
package com.example.context;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义注解配置应用上下文
 */
public class CustomAnnotationApplicationContext extends AnnotationConfigApplicationContext {

    public CustomAnnotationApplicationContext(Class<?>... componentClasses) {
        super(componentClasses);
    }

    public CustomAnnotationApplicationContext(String... basePackages) {
        super(basePackages);
    }

    /**
     * 自定义初始化逻辑
     */
    @Override
    public void refresh() throws IllegalStateException {
        System.out.println("=== 开始刷新自定义容器 ===");
        
        // 调用父类标准的刷新流程
        super.refresh();
        
        System.out.println("=== 容器刷新完成 ===");
        System.out.println("注册的Bean数量: " + getBeanDefinitionCount());
    }

    /**
     * 示例：自定义Bean名称生成器
     */
    public static void main(String[] args) {
        // 创建自定义容器
        CustomAnnotationApplicationContext context = 
            new CustomAnnotationApplicationContext("com.example.app");
        
        // 获取所有Bean名称
        System.out.println("容器中的Bean:");
        for (String beanName : context.getBeanDefinitionNames()) {
            System.out.println("- " + beanName);
        }
    }
}
```

### 2. 自定义Bean工厂后置处理器

```java
package com.example.processor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.PriorityOrdered;

/**
 * 自定义Bean工厂后置处理器
 * 用于在Bean定义加载后、Bean实例化前进行自定义处理
 */
public class CustomBeanFactoryPostProcessor implements BeanFactoryPostProcessor, PriorityOrdered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("=== 执行自定义Bean工厂后置处理器 ===");
        
        // 获取所有Bean定义名称
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            
            // 示例：为所有Bean添加自定义属性
            if (beanDefinition.getAttribute("customProcessed") == null) {
                beanDefinition.setAttribute("customProcessed", "true");
                System.out.println("处理Bean定义: " + beanName);
            }
            
            // 示例：修改Bean的作用域
            if (beanName.endsWith("Prototype")) {
                beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
                System.out.println("设置Bean为原型作用域: " + beanName);
            }
        }
    }

    @Override
    public int getOrder() {
        // 设置优先级，数字越小优先级越高
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
```

### 3. 自定义配置类处理器

```java
package com.example.processor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.annotation.ConfigurationClassUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * 扩展的配置类后置处理器
 * 增加自定义的配置类处理逻辑
 */
public class CustomConfigurationClassPostProcessor extends ConfigurationClassPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        System.out.println("=== 开始处理配置类 ===");
        
        // 调用父类标准处理
        super.postProcessBeanDefinitionRegistry(registry);
        
        // 添加自定义处理逻辑
        processCustomConfiguration(registry);
        
        System.out.println("=== 配置类处理完成，总计注册Bean定义: " + registry.getBeanDefinitionCount() + " ===");
    }

    /**
     * 自定义配置类处理
     */
    private void processCustomConfiguration(BeanDefinitionRegistry registry) {
        String[] beanNames = registry.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            
            // 检查是否为配置类
            if (ConfigurationClassUtils.checkConfigurationClassCandidate(
                    beanDefinition, getMetadataReaderFactory())) {
                
                System.out.println("发现配置类: " + beanName);
                
                // 可以在这里添加自定义的配置类处理逻辑
                customizeConfigurationClass(beanName, beanDefinition);
            }
        }
    }

    private void customizeConfigurationClass(String beanName, BeanDefinition beanDefinition) {
        // 示例：为配置类添加自定义属性
        beanDefinition.setAttribute("customConfig", "true");
    }

    private MetadataReaderFactory getMetadataReaderFactory() {
        // 获取MetadataReaderFactory的实现
        // 实际使用时需要从父类获取或注入
        return null;
    }
}
```

### 4. 自定义Bean定义扫描器

```java
package com.example.scanner;

import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * 自定义Bean定义扫描器
 * 支持自定义的扫描过滤器和命名策略
 */
public class CustomClassPathScanner extends ClassPathBeanDefinitionScanner {

    public CustomClassPathScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
        
        // 配置默认的包含过滤器
        registerDefaultFilters();
    }

    @Override
    protected void registerDefaultFilters() {
        System.out.println("=== 配置自定义扫描过滤器 ===");
        
        // 添加对常用注解的支持
        this.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        this.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        this.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        
        // 可以添加自定义的注解过滤器
        // this.addIncludeFilter(new AnnotationTypeFilter(CustomComponent.class));
    }

    /**
     * 扫描并注册Bean定义
     * 
     * @param basePackages 要扫描的基础包
     * @return 注册的Bean数量
     */
    @Override
    public int scan(String... basePackages) {
        System.out.println("=== 开始扫描包: " + String.join(", ", basePackages) + " ===");
        
        int beanCount = super.scan(basePackages);
        
        System.out.println("=== 扫描完成，发现并注册了 " + beanCount + " 个Bean ===");
        
        return beanCount;
    }

    /**
     * 自定义扫描方法
     * 
     * @param basePackages 要扫描的基础包
     * @param useDefaultFilters 是否使用默认过滤器
     * @return 扫描到的Bean定义持有者集合
     */
    public Set<BeanDefinitionHolder> customScan(String[] basePackages, boolean useDefaultFilters) {
        if (!useDefaultFilters) {
            // 清除默认过滤器
            this.resetFilters(false);
        }
        
        return this.doScan(basePackages);
    }

    /**
     * 示例：只扫描特定注解的组件
     */
    public Set<BeanDefinitionHolder> scanOnlyServices(String... basePackages) {
        // 清除所有过滤器
        this.resetFilters(false);
        
        // 只扫描@Service注解
        this.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        
        return this.doScan(basePackages);
    }

    /**
     * 示例：排除特定注解的组件
     */
    public Set<BeanDefinitionHolder> scanExcludeRepositories(String... basePackages) {
        // 配置过滤器
        registerDefaultFilters();
        
        // 排除@Repository注解
        this.addExcludeFilter(new AnnotationTypeFilter(Repository.class));
        
        return this.doScan(basePackages);
    }

    // 静态工厂方法
    public static CustomClassPathScanner createScanner(BeanDefinitionRegistry registry) {
        return new CustomClassPathScanner(registry);
    }
}
```

### 5. 自定义注解和处理器

```java
package com.example.annotation;

import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.*;

/**
 * 自定义模块导入注解
 * 用于自动装配特定模块的所有组件
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ModuleImportSelector.class)
public @interface EnableModule {

    /**
     * 要启用的模块名称
     */
    String[] value() default {};

    /**
     * 是否扫描模块的子包
     */
    boolean scanSubPackages() default true;
}

/**
 * 模块导入选择器
 */
class ModuleImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 获取注解属性
        String[] modules = (String[]) importingClassMetadata
            .getAnnotationAttributes(EnableModule.class.getName())
            .get("value");
        
        System.out.println("=== 导入模块: " + String.join(", ", modules) + " ===");
        
        // 根据模块名称返回对应的配置类
        return getModuleConfigClasses(modules);
    }

    private String[] getModuleConfigClasses(String[] modules) {
        // 这里可以根据模块名称动态返回配置类
        // 实际应用中可以从配置文件或数据库中读取
        
        java.util.List<String> configClasses = new java.util.ArrayList<>();
        
        for (String module : modules) {
            switch (module.toLowerCase()) {
                case "database":
                    configClasses.add("com.example.config.DatabaseConfig");
                    break;
                case "security":
                    configClasses.add("com.example.config.SecurityConfig");
                    break;
                case "cache":
                    configClasses.add("com.example.config.CacheConfig");
                    break;
                default:
                    System.out.println("未知模块: " + module);
            }
        }
        
        return configClasses.toArray(new String[0]);
    }
}
```

### 6. 使用示例

```java
package com.example.app;

import com.example.annotation.EnableModule;
import com.example.context.CustomAnnotationApplicationContext;
import com.example.processor.CustomBeanFactoryPostProcessor;
import com.example.scanner.CustomClassPathScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 主配置类
 */
@Configuration
@EnableModule({"database", "security"})
public class AppConfig {

    @Bean
    public CustomBeanFactoryPostProcessor customBeanFactoryPostProcessor() {
        return new CustomBeanFactoryPostProcessor();
    }

    @Bean
    public CustomClassPathScanner customClassPathScanner() {
        return new CustomClassPathScanner(null);
    }

    public static void main(String[] args) {
        // 使用自定义容器
        CustomAnnotationApplicationContext context = 
            new CustomAnnotationApplicationContext(AppConfig.class);
        
        System.out.println("\n=== 容器初始化完成 ===");
        
        // 获取Bean示例
        if (context.containsBean("customBeanFactoryPostProcessor")) {
            Object processor = context.getBean("customBeanFactoryPostProcessor");
            System.out.println("成功获取自定义处理器: " + processor.getClass().getSimpleName());
        }
        
        context.close();
    }
}
```

## 实用工具方法

### Bean定义检查工具类

```java
package com.example.util;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ConfigurationClassUtils;

/**
 * Bean定义工具类
 */
public class BeanDefinitionUtils {

    /**
     * 打印所有Bean定义信息
     */
    public static void printAllBeanDefinitions(BeanDefinitionRegistry registry) {
        System.out.println("=== 当前注册的Bean定义 ===");
        
        String[] beanNames = registry.getBeanDefinitionNames();
        System.out.println("总数: " + beanNames.length);
        
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            System.out.printf("- %s: %s [scope=%s, lazy=%s]%n",
                beanName,
                beanDefinition.getBeanClassName(),
                beanDefinition.getScope(),
                beanDefinition.isLazyInit());
        }
    }

    /**
     * 检查Bean定义是否为配置类
     */
    public static boolean isConfigurationClass(BeanDefinition beanDefinition) {
        return ConfigurationClassUtils.checkConfigurationClassCandidate(
            beanDefinition, null);
    }

    /**
     * 统计Bean定义数量
     */
    public static BeanDefinitionStats getStatistics(BeanDefinitionRegistry registry) {
        BeanDefinitionStats stats = new BeanDefinitionStats();
        String[] beanNames = registry.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            stats.totalCount++;
            
            if (beanDefinition.isSingleton()) stats.singletonCount++;
            if (beanDefinition.isPrototype()) stats.prototypeCount++;
            if (beanDefinition.isLazyInit()) stats.lazyCount++;
            if (isConfigurationClass(beanDefinition)) stats.configClassCount++;
        }
        
        return stats;
    }

    public static class BeanDefinitionStats {
        public int totalCount = 0;
        public int singletonCount = 0;
        public int prototypeCount = 0;
        public int lazyCount = 0;
        public int configClassCount = 0;

        @Override
        public String toString() {
            return String.format(
                "Bean定义统计: 总数=%d, 单例=%d, 原型=%d, 懒加载=%d, 配置类=%d",
                totalCount, singletonCount, prototypeCount, lazyCount, configClassCount);
        }
    }
}
```

## 调试和监控工具

### 容器启动监听器

```java
package com.example.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * 容器刷新完成监听器
 */
public class ContextRefreshedListener implements 
    ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        AbstractApplicationContext context = 
            (AbstractApplicationContext) event.getApplicationContext();
        
        System.out.println("\n=== 容器刷新完成事件触发 ===");
        System.out.println("Bean定义总数: " + context.getBeanDefinitionCount());
        System.out.println("单例Bean总数: " + context.getDefaultListableBeanFactory()
            .getSingletonCount());
        
        // 打印启动时间
        long startupDate = context.getStartupDate();
        System.out.println("容器启动时间: " + new java.util.Date(startupDate));
    }
}
```

---

*实践指南文档 - Spring容器扩展开发*  
*版本: 1.0*  
*最后更新: 2025年*
