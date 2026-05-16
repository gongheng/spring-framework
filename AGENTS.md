# Spring Framework — Agent 指令

> Spring Framework 7.x（main 分支）。基于 Gradle 的多模块 Java monorepo。

## 快速参考

| 用途 | 命令 |
|---|---|
| 完整构建 + 测试 + 文档 | `./gradlew check antora` |
| 仅构建（跳过测试） | `./gradlew build -x test` |
| 运行全部测试 | `./gradlew test` |
| 单模块测试 | `./gradlew :spring-core:test` |
| 单个测试类 | `./gradlew :spring-core:test --tests "*.ClassUtilsTests"` |
| 代码风格检查 | `./gradlew checkstyleMain checkstyleTest` |
| 架构规则检查 | `./gradlew checkArchitectureMain` |
| RuntimeHints 测试 | `./gradlew runtimeHintsTest` |
| Multi-release 测试 | `./gradlew java21Test` / `java24Test` |
| Javadoc（全模块） | `./gradlew :framework-api:javadoc` |
| KDoc（Kotlin） | `./gradlew :framework-api:dokkaGenerate` |
| 参考文档 | `./gradlew antora` → `framework-docs/build/site/index.html` |

## 环境要求

- **Java 25** toolchain（通过 Gradle toolchain 配置；`.sdkmanrc` 指定 `java=25-librca`）
- **字节码目标版本**: Java 17（主源码使用 `-release 17`）
- **Gradle**: 9.5.0（使用 `./gradlew`）
- **Kotlin**: 2.3.20（在 `gradle.properties` 中管理）
- 没有 Makefile/justfile，所有操作通过 Gradle 完成

## 架构

### 模块分层（自底向上）

```
spring-core                        ← 无内部依赖；shade 了 javapoet, objenesis, cglib, asm
├── spring-beans                   ← core
├── spring-expression              ← core
├── spring-aop                     ← beans, core
├── spring-context                 ← aop, beans, core, expression
│   ├── spring-context-support     ← beans, context, core
│   └── spring-context-indexer     ← 独立模块（编译时索引生成器）
├── spring-tx                      ← beans, core
│   ├── spring-jdbc                ← beans, core, tx
│   ├── spring-orm                 ← beans, core, jdbc, tx
│   ├── spring-r2dbc               ← beans, core, tx + reactor
│   └── spring-jms                 ← beans, core, messaging, tx
├── spring-web                     ← beans, core
│   ├── spring-webmvc              ← aop, beans, context, core, expression, web
│   ├── spring-webflux             ← beans, core, web + reactor
│   └── spring-websocket           ← context, core, web
├── spring-messaging               ← beans, core
├── spring-oxm                     ← beans, core
├── spring-instrument              ← 独立模块
├── spring-aspects                 ← 独立模块（AspectJ）
├── spring-test                    ← core（可选依赖几乎所有其他模块）
└── spring-core-test               ← core（测试基础设施，RuntimeHintsAgent）
```

### 非库模块

| 模块 | 用途 |
|---|---|
| `framework-platform` | 依赖版本 BOM（所有第三方版本集中管理） |
| `framework-bom` | Spring 模块版本 BOM，供消费者使用 |
| `framework-api` | 聚合 Javadoc + KDoc 生成 |
| `framework-docs` | Antora 参考文档源码 |
| `integration-tests` | 跨模块集成测试 |

### 构建约定

- 模块构建文件: `<module-name>.gradle`（如 `spring-core.gradle`）
- 所有 `spring-*` 模块应用 `gradle/spring-module.gradle` → `java-library` + conventions + optional deps + JMH + nullability
- `buildSrc/` 包含 6 个自定义 Gradle 插件（详见 `buildSrc/README.md`）
- `framework-platform` 是第三方依赖版本的唯一事实来源

## 代码规范

### 格式化（不可协商）

- 缩进使用 **Tab**（4 宽度），**禁止空格** — 由 `.editorconfig` 和 checkstyle 强制
- 续行缩进: 8
- 换行符: LF
- 编码: UTF-8
- 分隔符（`,`, `+`, `?`, `:`, `&&`, `||`）必须位于**前一行的末尾**

### 主源码中禁止使用（checkstyle 强制）

- `org.junit.jupiter.api.Assertions` / `Assumptions` — 使用 **AssertJ**
- `org.junit.Assert`（JUnit 4） — 使用 AssertJ
- `org.hamcrest.*` — 使用 AssertJ
- `reactor.core.support.Assert`
- `org.slf4j.LoggerFactory`
- `@Test(expected=...)` — 使用 AssertJ `assertThatException`
- `assertThatExceptionOfType(NullPointerException|IllegalArgumentException|...).class` — 使用 AssertJ 专用方法
- `printStackTrace()`, `System.out.print`, `System.err.print`
- `Integer.TYPE`, `Boolean.TYPE` 等 — 使用 `int.class`, `boolean.class`
- 不带 locale 的 `String.toLowerCase()` / `toUpperCase()` — 使用 `toLowerCase(Locale.ROOT)`

### 空值注解

- 已迁移至 **JSR-305 / JSpecify**: 使用 `org.jspecify.annotations.Nullable` / `@NullMarked`
- 旧的 `org.springframework.lang.Nullable` / `@NonNull` 已**废弃**
- Checkstyle 禁止所有非 JSpecify 的 `NonNull`/`Nullable` 导入
- 在 `package-info.java` 中使用 `@NullMarked` 进行包级别标注

### 测试命名

- 测试类必须以 `**Tests**` 结尾（如 `ClassUtilsTests`） — **不能**以 `Test` 结尾（checkstyle 会拒绝）
- 测试 fixtures 位于 `src/testFixtures/`

### Javadoc

- package-private 及以上可见性必须编写 Javadoc
- `@author` 格式: `First Last`（至少两个词）
- `@since` 使用 `Major.Minor` 格式（如 `@since 5.3`），**禁止** `@since 5.3.0`
- 类型标签顺序: `@author @since @param @see @version @serial @deprecated`
- 方法标签顺序: `@param @return @throws @since @see @deprecated`

### 架构规则（ArchUnit）

- 禁止包循环（例外: `org.springframework.asm`, `cglib`, `javapoet`, `objenesis`）
- Java 类禁止导入 Kotlin 注解（`org.jetbrains.annotations.*`）
- 参见 `buildSrc/.../ArchitectureRules.java`

## 测试

### 技术栈

- **JUnit 5** (Jupiter) + **AssertJ** + **Mockito** + **MockK**（Kotlin）
- ByteBuddy agent 自动配置于测试任务
- 测试重试: CI 环境（`CI=true`）重试 3 次，本地不重试

### 测试模式

```bash
# 运行单个测试方法
./gradlew :spring-beans:test --tests "*.DefaultListableBeanFactoryTests.getContext"

# 运行 RuntimeHints instrumented 测试
./gradlew runtimeHintsTest   # 使用 @EnabledIfRuntimeHintsAgent 注解

# Multi-release JAR 测试（spring-core）
./gradlew java21Test java24Test
```

### 集成测试

- `integration-tests/` 模块：跨模块集成测试，使用多个模块的 test fixtures
- 应用了 `runtimehints-agent` 插件

### 测试 JVM 参数（自动配置）

```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
-Xshare:off
-Djava.awt.headless=true
-Dio.netty.leakDetection.level=paranoid
```

## Commit 与 PR 规范

- PR 仅针对 `main` 分支（backport 视情况而定）
- Commit 主题: **最多 55 个字符**
- Commit 正文: **每行最多 72 个字符**
- **DCO 必需**: 每个 commit 必须包含 `Signed-off-by: Name <email>` 尾部
- 引用 issue: 在 commit 正文或 PR 描述中使用 `Closes gh-NNNNN`
- 提交 PR 前将修正提交 squash 为逻辑提交

## 模块专项说明

### spring-core

- Multi-Release JAR: `src/main/java21/`, `src/main/java24/` 存放版本特定代码
- Shadow 并重打包: `org.springframework.javapoet`, `org.springframework.objenesis`, `org.springframework.cglib`, `org.springframework.asm`

### spring-test

- 唯一支持 JUnit 4（Vintage engine）和 TestNG 的模块（与 Jupiter 并行）

### GraalVM / AOT

- `AotDetector` / `NativeDetector` 用于运行时检测
- `RuntimeHints` API 用于反射/资源提示
- `@EnabledIfRuntimeHintsAgent` 用于测试 native hints

## 关键配置文件

| 文件 | 用途 |
|---|---|
| `build.gradle` | 根构建: 插件、测试依赖、javadoc 链接 |
| `settings.gradle` | 所有包含的模块 |
| `gradle.properties` | 版本号、Kotlin 版本、Gradle 选项 |
| `gradle/spring-module.gradle` | 共享模块配置（应用于所有 `spring-*`） |
| `buildSrc/build.gradle` | 自定义 Gradle 插件 |
| `buildSrc/README.md` | 插件文档 |
| `src/checkstyle/checkstyle.xml` | Checkstyle 规则 |
| `src/checkstyle/checkstyle-suppressions.xml` | 按模块的抑制规则 |
| `framework-platform/framework-platform.gradle` | 所有第三方版本锁定 |
| `.editorconfig` | Tab 缩进、UTF-8、LF |

## 外部参考

- [代码风格 wiki](https://github.com/spring-projects/spring-framework/wiki/Code-Style)
- [IntelliJ IDEA 设置 wiki](https://github.com/spring-projects/spring-framework/wiki/IntelliJ-IDEA-Editor-Settings)
- [从源码构建 wiki](https://github.com/spring-projects/spring-framework/wiki/Build-from-Source)
- [CONTRIBUTING.md](CONTRIBUTING.md)
