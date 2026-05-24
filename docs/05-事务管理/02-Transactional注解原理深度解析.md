# @Transactional注解原理深度解析

## 🎯 核心作用

`@Transactional`注解是Spring事务管理的核心注解，它基于**AOP（面向切面编程）**实现，通过在方法执行前后添加事务管理逻辑，确保数据的一致性和完整性。

**核心功能**：
- 自动管理事务的开启、提交、回滚
- 支持多种事务传播机制
- 支持多种事务隔离级别
- 支持声明式事务管理

---

## 📍 注解定义

### @Transactional注解源码

```java
package org.springframework.transaction.annotation;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Transactional {

    // ========== 事务管理器配置 ==========

    /**
     * 指定事务管理器的Bean名称
     * 当容器中有多个事务管理器时使用
     */
    @AliasFor("transactionManager")
    String value() default "";

    /**
     * 指定事务管理器的Bean名称（同value）
     */
    @AliasFor("value")
    String transactionManager() default "";

    // ========== 事务传播机制 ==========

    /**
     * 指定事务传播行为
     * 默认：Propagation.REQUIRED
     */
    Propagation propagation() default Propagation.REQUIRED;

    // ========== 事务隔离级别 ==========

    /**
     * 指定事务隔离级别
     * 默认：Isolation.DEFAULT（使用数据库默认隔离级别）
     */
    Isolation isolation() default Isolation.DEFAULT;

    // ========== 事务超时配置 ==========

    /**
     * 事务超时时间（秒）
     * 默认：-1（使用数据库默认超时时间）
     */
    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;

    // ========== 只读事务配置 ==========

    /**
     * 是否只读事务
     * 默认：false
     * 只读事务用于查询操作，可以提高性能
     */
    boolean readOnly() default false;

    // ========== 回滚配置 ==========

    /**
     * 指定哪些异常触发回滚
     * 默认：RuntimeException和Error
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * 指定哪些异常的子类触发回滚
     */
    Class<? extends Throwable>[] rollbackForClassName() default {};

    /**
     * 指定哪些异常不触发回滚
     * 默认：检查型异常（Checked Exception）不回滚
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * 指定哪些异常的子类不触发回滚
     */
    Class<? extends Throwable>[] noRollbackForClassName() default {};
}
```

---

## 🔄 @Transactional工作原理

### 核心机制：AOP代理

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring容器启动                            │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. 扫描所有带@Transactional注解的方法                          │
│     ↓                                                         │
│  2. 为这些Bean创建代理对象                                     │
│     ↓                                                         │
│  3. 在代理对象中织入事务管理逻辑                               │
│     ↓                                                         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              代理对象（Proxy）                         │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │                                                         │  │
│  │  @Transactional                                        │  │
│  │  public void transferMoney(Long fromId, Long toId,     │  │
│  │          BigDecimal amount) {                          │  │
│  │                                                         │  │
│  │      【1. 开启事务】TransactionManager.begin()          │  │
│  │      ↓                                                  │  │
│  │      try {                                             │  │
│  │          【2. 执行业务逻辑】                            │  │
│  │          target.transferMoney(fromId, toId, amount);   │  │
│  │          ↓                                              │  │
│  │          【3. 提交事务】TransactionManager.commit()     │  │
│  │      } catch (Exception e) {                            │  │
│  │          【4. 回滚事务】TransactionManager.rollback()   │  │
│  │          throw e;                                       │  │
│  │      }                                                  │  │
│  │  }                                                      │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 完整调用链

```
客户端调用：userService.transferMoney(fromId, toId, amount)
  ↓
【1. 进入代理对象】
  ↓
【2. TransactionInterceptor.invoke()】 ⭐️ 事务拦截器
  ↓
【3. 创建事务信息】
  TransactionAspectSupport.createTransactionIfNecessary()
  ↓
  3.1 获取事务属性
      TransactionAttributeSource.getTransactionAttribute()
      ↓
      读取@Transactional注解的属性
      - propagation：传播行为
      - isolation：隔离级别
      - timeout：超时时间
      - readOnly：是否只读
      ↓
  3.2 获取事务管理器
      TransactionManager transactionManager = determineTransactionManager()
      ↓
      根据@Transactional.transactionManager()属性获取事务管理器
      ↓
  3.3 确定事务名称
      String transactionName = determineTransactionName()
      ↓
      默认：类名 + "." + 方法名
      例如：com.example.service.UserService.transferMoney
      ↓
  3.4 创建事务定义
      DefaultTransactionDefinition def = new DefaultTransactionDefinition()
      def.setPropagation(transactionAttribute.getPropagation())
      def.setIsolationLevel(transactionAttribute.getIsolationLevel())
      def.setTimeout(transactionAttribute.getTimeout())
      def.setReadOnly(transactionAttribute.isReadOnly())
      def.setName(transactionName)
      ↓
  3.5 开启事务
      TransactionStatus status = transactionManager.getTransaction(def)
      ↓
      【如果是JDBC事务】
      DataSourceTransactionManager.doBegin()
      ↓
      获取数据库连接：Connection connection = dataSource.getConnection()
      ↓
      设置手动提交：connection.setAutoCommit(false)
      ↓
      设置隔离级别：connection.setTransactionIsolation(isolationLevel)
      ↓
      绑定连接到线程：TransactionSynchronizationManager.bindResource()
      ↓
      【JTA事务】
      JtaTransactionManager.doBegin()
      ↓
      UserTransaction ut = obtainUserTransaction()
      ut.begin()
      ↓
【4. 执行业务逻辑】
  try {
      Object retVal = invocation.proceed()  ← 执行目标方法
      ↓
      【业务逻辑执行】
      userService.transferMoney(fromId, toId, amount)
      ↓
      accountDao.debit(fromId, amount)
      accountDao.credit(toId, amount)
      ↓
  } catch (Throwable ex) {
      ↓
      【5. 异常处理】
      ↓
      判断是否需要回滚：rollbackOn(exception, transactionAttribute)
      ├─ 如果是RuntimeException或Error：回滚 ✅
      ├─ 如果在rollbackFor中：回滚 ✅
      ├─ 如果在noRollbackFor中：不回滚 ❌
      └─ 如果是检查型异常：不回滚 ❌
      ↓
      如果需要回滚：
          transactionManager.rollback(status)
          ↓
          【JDBC事务】
          connection.rollback()
          ↓
          【JTA事务】
          ut.rollback()
      ↓
      throw ex
  }
  ↓
【6. 提交事务】
  ↓
  transactionManager.commit(status)
  ↓
  【JDBC事务】
  connection.commit()
  ↓
  【JTA事务】
  ut.commit()
  ↓
【7. 清理资源】
  ↓
  TransactionSynchronizationManager.unbindResource()
  ↓
  connection.close()

✅ 事务执行完成
```

---

## 🔑 核心源码分析

### 1️⃣ TransactionInterceptor - 事务拦截器

**位置**：`org.springframework.transaction.interceptor.TransactionInterceptor`

```java
public class TransactionInterceptor extends TransactionAspectSupport
        implements MethodInterceptor, Serializable {

    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取目标类
        Class<?> targetClass = (invocation.getThis() != null ?
            AopUtils.getTargetClass(invocation.getThis()) : null);

        // ⭐️ 调用事务处理逻辑
        return invokeWithinTransaction(invocation.getMethod(), targetClass, invocation::proceed);
    }
}
```

### 2️⃣ invokeWithinTransaction() - 在事务中调用

**位置**：`org.springframework.transaction.interceptor.TransactionAspectSupport:279`

```java
@Nullable
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
    final InvocationCallback invocation) throws Throwable {

    // 1. 获取事务属性源
    TransactionAttributeSource tas = getTransactionAttributeSource();

    // 2. ⭐️ 获取事务属性（读取@Transactional注解）
    final TransactionAttribute txAttr = (tas != null ?
        tas.getTransactionAttribute(method, targetClass) : null);

    // 3. ⭐️ 确定事务管理器
    final TransactionManager tm = determineTransactionManager(txAttr);

    // 4. 转换为PlatformTransactionManager
    PlatformTransactionManager ptm = asPlatformTransactionManager(tm);

    // 5. 获取连接点标识符
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

    // 6. ⭐️ 如果是声明式事务（回调型事务）
    if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
        // 6.1 创建事务信息（如果需要）
        TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

        Object retVal;
        try {
            // 6.2 ⭐️ 执行业务逻辑
            retVal = invocation.proceedWithInvocation();
        } catch (Throwable ex) {
            // 6.3 ⭐️ 异常处理：回滚事务
            completeTransactionAfterThrowing(txInfo, ex);
            throw ex;
        } finally {
            // 6.4 清理事务信息
            cleanupTransactionInfo(txInfo);
        }

        // 6.5 ⭐️ 提交事务
        commitTransactionAfterReturning(txInfo);
        return retVal;
    }

    // 7. 编程式事务（略）
    // ...
}
```

### 3️⃣ createTransactionIfNecessary() - 创建事务

**位置**：`org.springframework.transaction.interceptor.TransactionAspectSupport:441`

```java
protected TransactionInfo createTransactionIfNecessary(
    @Nullable PlatformTransactionManager tm,
    @Nullable TransactionAttribute txAttr,
    final String joinpointIdentification) {

    // 1. 如果没有事务属性，创建空事务信息
    if (txAttr == null) {
        return new TransactionInfo(tm, null, joinpointIdentification);
    }

    // 2. ⭐️ 获取事务名称（类名.方法名）
    String transactionName = txAttr.getName();
    if (transactionName == null) {
        transactionName = joinpointIdentification;
    }

    // 3. ⭐️ 开启事务
    TransactionStatus status = null;
    if (txAttr != null) {
        if (tm != null) {
            status = tm.getTransaction(txAttr);
        }
    }

    // 4. 创建事务信息
    return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
}
```

### 4️⃣ getTransaction() - 开启事务

**位置**：`org.springframework.transaction.support.AbstractPlatformTransactionManager:365`

```java
@Override
public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
    throws TransactionException {

    // 1. 获取事务定义
    TransactionDefinition def = (definition != null ? definition :
        new DefaultTransactionDefinition());

    // 2. ⭐️ 获取现有事务（如果存在）
    Object transaction = doGetTransaction();

    // 3. ⭐️ 判断是否已存在事务
    if (isExistingTransaction(transaction)) {
        // 3.1 处理事务传播机制
        return handleExistingTransaction(def, transaction, debugEnabled);
    }

    // 4. 检查超时设置
    if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
        throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
    }

    // 5. ⭐️ 如果当前没有事务，根据传播机制处理
    // 5.1 PROPAGATION_MANDATORY：必须有事务，否则抛出异常
    if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
        throw new IllegalTransactionStateException(
            "No existing transaction found for transaction marked with propagation 'mandatory'");
    }
    // 5.2 PROPAGATION_REQUIRED、PROPAGATION_REQUIRES_NEW、PROPAGATION_NESTED：创建新事务
    else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {

        // 5.2.1 挂起当前事务（如果存在）
        SuspendedResourcesHolder suspendedResources = suspend(null);

        try {
            // 5.2.2 ⭐️ 开启新事务
            doBegin(transaction, def);
            return prepareTransactionStatus(def, transaction, false, debugEnabled, suspendedResources);
        } catch (RuntimeException | Error ex) {
            // 5.2.3 开启失败，恢复挂起的事务
            resume(suspendedResources);
            throw ex;
        }
    }
    // 5.3 PROPAGATION_SUPPORTS、PROPAGATION_NOT_SUPPORTED、PROPAGATION_NEVER：不创建事务
    else {
        // 创建空事务（不开启实际事务）
        return prepareTransactionStatus(def, null, false, debugEnabled, null);
    }
}
```

### 5️⃣ doBegin() - 开启数据库事务

**位置**：`org.springframework.transaction.datasource.DataSourceTransactionManager:249`

```java
@Override
protected void doBegin(Object transaction, TransactionDefinition definition) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

    Connection con = null;

    try {
        // 1. ⭐️ 获取数据库连接
        con = txObject.getConnectionHolder().getConnection();

        // 2. ⭐️ 设置手动提交
        if (con.getAutoCommit()) {
            txObject.setMustRestoreAutoCommit(true);
            con.setAutoCommit(false);
        }

        // 3. ⭐️ 设置事务隔离级别
        if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
            con.setTransactionIsolation(definition.getIsolationLevel());
        }

        // 4. ⭐️ 设置只读事务
        if (definition.isReadOnly()) {
            con.setReadOnly(true);
        }

        // 5. ⭐️ 绑定连接到线程
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.bindResource(
                getDataSource(), txObject.getConnectionHolder());
        }

    } catch (SQLException ex) {
        throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
    }
}
```

### 6️⃣ completeTransactionAfterThrowing() - 异常回滚

**位置**：`org.springframework.transaction.interceptor.TransactionAspectSupport:617`

```java
protected void completeTransactionAfterThrowing(@Nullable TransactionInfo txInfo, Throwable ex) {
    // 1. 判断是否有事务信息
    if (txInfo != null && txInfo.getTransactionStatus() != null) {
        // 2. ⭐️ 判断是否需要回滚
        if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
            try {
                // 2.1 ⭐️ 执行回滚
                txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
            } catch (TransactionSystemException tse) {
                // 回滚异常处理
            } catch (RuntimeException | Error ex2) {
                // 回滚异常处理
            }
        } else {
            // 2.2 如果不需要回滚，尝试提交
            try {
                txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
            } catch (TransactionSystemException tse) {
                // 提交异常处理
            } catch (RuntimeException | Error ex2) {
                // 提交异常处理
            }
        }
    }
}
```

### 7️⃣ rollbackOn() - 判断是否回滚

**位置**：`org.springframework.transaction.interceptor.RuleBasedTransactionAttribute:140`

```java
@Override
public boolean rollbackOn(Throwable ex) {
    // 1. ⭐️ 检查是否在noRollbackFor中
    if (this.noRollbackFor != null) {
        for (Class<?> noRollbackRuleType : this.noRollbackFor) {
            if (noRollbackRuleType.isInstance(ex)) {
                return false;  // 不回滚
            }
        }
    }

    // 2. ⭐️ 检查是否在rollbackFor中
    if (this.rollbackFor != null) {
        for (Class<?> rollbackRuleType : this.rollbackFor) {
            if (rollbackRuleType.isInstance(ex)) {
                return true;  // 回滚
            }
        }
    }

    // 3. ⭐️ 默认规则：RuntimeException或Error回滚
    return (ex instanceof RuntimeException || ex instanceof Error);
}
```

### 8️⃣ commitTransactionAfterReturning() - 提交事务

**位置**：`org.springframework.transaction.interceptor.TransactionAspectSupport:646`

```java
protected void commitTransactionAfterReturning(@Nullable TransactionInfo txInfo) {
    if (txInfo != null && txInfo.getTransactionStatus() != null) {
        // ⭐️ 提交事务
        txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
    }
}
```

### 9️⃣ commit() - 提交数据库事务

**位置**：`org.springframework.transaction.support.AbstractPlatformTransactionManager:723`

```java
@Override
public final void commit(TransactionStatus status) throws TransactionException {
    // 1. 如果已完成，直接返回
    if (status.isCompleted()) {
        throw new IllegalTransactionStateException("Transaction is already completed");
    }

    // 2. ⭐️ 判断是否需要回滚
    DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
    if (defStatus.isLocalRollbackOnly()) {
        // 2.1 标记为只回滚
        processRollback(defStatus, false);
        return;
    }

    // 3. ⭐️ 提交事务
    processCommit(defStatus);
}
```

### 🔟 processCommit() - 处理提交

**位置**：`org.springframework.transaction.support.AbstractPlatformTransactionManager:743`

```java
private void processCommit(DefaultTransactionStatus status) throws TransactionException {
    try {
        // 1. ⭐️ 提交前触发
        triggerBeforeCommit(status);

        // 2. ⭐️ 完成前触发
        triggerBeforeCompletion(status);

        // 3. ⭐️ 提交数据库事务
        doCommit(status);

        // 4. ⭐️ 提交后触发
        triggerAfterCommit(status);

        // 5. ⭐️ 完成后触发
        triggerAfterCompletion(status, TransactionSynch

ronization.STATUS_COMMITTED);

    } finally {
        // 6. 清理资源
        cleanupAfterCompletion(status);
    }
}
```

### 1️⃣1️⃣ doCommit() - 提交数据库事务

**位置**：`org.springframework.transaction.datasource.DataSourceTransactionManager:295`

```java
@Override
protected void doCommit(DefaultTransactionStatus status) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();

    Connection con = txObject.getConnectionHolder().getConnection();

    try {
        // ⭐️ 提交数据库事务
        con.commit();
    } catch (SQLException ex) {
        throw new TransactionSystemException("Could not commit JDBC transaction", ex);
    }
}
```

---

## 🎯 @Transactional使用示例

### 基础使用

```java
@Service
public class UserService {

    @Transactional
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        // 1. 扣款
        accountDao.debit(fromId, amount);

        // 2. 加款
        accountDao.credit(toId, amount);

        // 如果发生异常，事务会自动回滚
    }
}
```

### 指定异常回滚

```java
@Service
public class UserService {

    // 默认：RuntimeException和Error回滚
    @Transactional(rollbackFor = {RuntimeException.class, IOException.class})
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) throws IOException {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

### 指定不回滚的异常

```java
@Service
public class UserService {

    // 即使 BusinessException 是 RuntimeException，也不回滚
    @Transactional(noRollbackFor = BusinessException.class)
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

### 只读事务

```java
@Service
public class UserService {

    // 只读事务用于查询操作，可以提高性能
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userDao.findById(id);
    }
}
```

### 指定事务超时

```java
@Service
public class UserService {

    // 事务超时时间为3秒
    @Transactional(timeout = 3)
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

### 指定事务隔离级别

```java
@Service
public class UserService {

    // 使用READ_COMMITTED隔离级别
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

---

## ⚠️ @Transactional失效场景

### 1️⃣ 同类方法调用

```java
@Service
public class UserService {

    // ✅ 外部调用：有效
    @Transactional
    public void methodA() {
        methodB();  // ❌ 内部调用：失效
    }

    @Transactional
    public void methodB() {
        // 事务失效
    }
}
```

**原因**：Spring AOP基于代理，同类方法调用不会经过代理对象。

**解决方案**：
```java
@Service
public class UserService {

    @Autowired
    private UserService self;  // 注入自己（代理对象）

    @Transactional
    public void methodA() {
        self.methodB();  // ✅ 通过代理对象调用：有效
    }

    @Transactional
    public void methodB() {
        // 事务有效
    }
}
```

### 2️⃣ 方法不是public

```java
@Service
public class UserService {

    // ❌ private方法：事务失效
    @Transactional
    private void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

**原因**：Spring AOP只支持public方法。

### 3️⃣ 方法是final

```java
@Service
public class UserService {

    // ❌ final方法：事务失效
    @Transactional
    public final void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

**原因**：CGLIB无法代理final方法。

### 4️⃣ 方法是static

```java
@Service
public class UserService {

    // ❌ static方法：事务失效
    @Transactional
    public static void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

**原因**：静态方法不属于对象实例。

### 5️⃣ 异常被捕获

```java
@Service
public class UserService {

    @Transactional
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        try {
            accountDao.debit(fromId, amount);
            accountDao.credit(toId, amount);
        } catch (Exception e) {
            // ❌ 异常被捕获，事务不会回滚
            e.printStackTrace();
        }
    }
}
```

**解决方案**：
```java
@Service
public class UserService {

    @Transactional
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
        // ✅ 不捕获异常，让异常传播到事务拦截器
    }
}
```

### 6️⃣ 使用了错误的事务管理器

```java
@Service
public class UserService {

    // ❌ 使用了不存在的事务管理器
    @Transactional("transactionManager2")
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

---

## 🎯 最佳实践

### 1️⃣ 明确指定回滚异常

```java
// ❌ 不推荐：使用默认规则
@Transactional
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // ...
}

// ✅ 推荐：明确指定回滚异常
@Transactional(rollbackFor = Exception.class)
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // ...
}
```

### 2️⃣ 查询操作使用只读事务

```java
// ✅ 推荐：查询操作使用只读事务
@Transactional(readOnly = true)
public User getUserById(Long id) {
    return userDao.findById(id);
}
```

### 3️⃣ 设置合理的事务超时时间

```java
// ✅ 推荐：设置合理的事务超时时间
@Transactional(timeout = 30)
public void batchProcess(List<Data> dataList) {
    // ...
}
```

### 4️⃣ 事务粒度要小

```java
// ❌ 不推荐：事务粒度太大
@Transactional
public void processUser(Long userId) {
    // 查询操作
    User user = userDao.findById(userId);

    // 外部API调用（不应该在事务中）
    ExternalApi.call(user);

    // 更新操作
    userDao.update(user);
}

// ✅ 推荐：事务只包含必要的数据库操作
@Transactional(readOnly = true)
public User getUserById(Long userId) {
    return userDao.findById(userId);
}

public void callExternalApi(User user) {
    ExternalApi.call(user);
}

@Transactional
public void updateUser(User user) {
    userDao.update(user);
}
```

---

## 🔍 调试技巧

### 启用事务日志

```properties
# application.properties
logging.level.org.springframework.transaction=DEBUG
logging.level.org.springframework.jdbc.datasource=DEBUG
```

### 查看事务信息

```java
@Service
public class UserService {

    @Transactional
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        // 查看事务信息
        TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
        System.out.println("事务是否新事务：" + status.isNewTransaction());
        System.out.println("事务是否只读：" + status.isReadOnly());
        System.out.println("事务是否已完成：" + status.isCompleted());

        accountDao.debit(fromId, amount);
        accountDao.credit(toId, amount);
    }
}
```

---

## 🎯 总结

### 核心要点

1. ✅ **@Transactional基于AOP实现**，通过代理对象在方法执行前后添加事务管理逻辑
2. ✅ **事务管理流程**：
   - 开启事务：`connection.setAutoCommit(false)`
   - 执行业务逻辑
   - 提交事务：`connection.commit()`
   - 回滚事务：`connection.rollback()`
3. ✅ **回滚规则**：默认只回滚RuntimeException和Error
4. ✅ **失效场景**：
   - 同类方法调用
   - 方法不是public
   - 方法是final
   - 异常被捕获
   - 使用了错误的事务管理器

### 扩展阅读

- [Spring事务抽象架构详解](./01-Spring事务抽象架构详解.md)
- [事务传播机制深度解析](./03-事务传播机制深度解析.md)
- [事务隔离级别深度解析](./04-事务隔离级别深度解析.md)

---

**文档创建时间**：2026-05-19
**Spring Framework版本**：6.x
**Spring Boot版本**：3.x
**难度评级**：⭐⭐⭐⭐（高级）
