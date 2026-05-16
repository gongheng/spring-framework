/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction;

import org.jspecify.annotations.Nullable;

/**
 * Interface that defines Spring-compliant transaction properties.
 * Based on the propagation behavior definitions analogous to EJB CMT attributes.
 *
 * <p>Note that isolation level and timeout settings will not get applied unless
 * an actual new transaction gets started. As only {@link #PROPAGATION_REQUIRED},
 * {@link #PROPAGATION_REQUIRES_NEW}, and {@link #PROPAGATION_NESTED} can cause
 * that, it usually doesn't make sense to specify those settings in other cases.
 * Furthermore, be aware that not all transaction managers will support those
 * advanced features and thus might throw corresponding exceptions when given
 * non-default values.
 *
 * <p>The {@linkplain #isReadOnly() read-only flag} applies to any transaction context,
 * whether backed by an actual resource transaction or operating non-transactionally
 * at the resource level. In the latter case, the flag will only apply to managed
 * resources within the application, such as a Hibernate {@code Session}.
 *
 * @author Juergen Hoeller
 * @since 08.05.2003
 * @see PlatformTransactionManager#getTransaction(TransactionDefinition)
 * @see org.springframework.transaction.support.DefaultTransactionDefinition
 * @see org.springframework.transaction.interceptor.TransactionAttribute
 */
public interface TransactionDefinition {

	/**
	 * 【支持当前事务，如果不存在则创建新事务】
	 *
	 * <h3>行为描述：</h3>
	 * <ul>
	 * <li><b>有现有事务</b>：加入现有事务，成为其一部分</li>
	 * <li><b>无现有事务</b>：创建新事务</li>
	 * <li><b>最常用传播行为</b>：这是默认的传播行为</li>
	 * </ul>
	 *
	 * <h3>使用场景：</h3>
	 * <pre>
	 * @Service
	 * public class OrderService {
	 *
	 *     @Transactional(propagation = Propagation.REQUIRED)
	 *     public void createOrder(Order order) {
	 *         // 如果调用方已有事务，加入该事务
	 *         // 否则创建新事务
	 *         orderRepository.save(order);
	 *         inventoryService.updateStock(order);
	 *     }
	 * }
	 * </pre>
	 *
	 * <h3>事务边界：</h3>
	 * <p>这是最常用的传播行为，通常定义了事务同步范围。
	 * 同一事务同步范围内的所有操作要么全部成功，要么全部回滚。
	 */
	int PROPAGATION_REQUIRED = 0;

	/**
	 * 【支持当前事务，如果不存在则以非事务方式执行】
	 *
	 * <h3>行为描述：</h3>
	 * <ul>
	 * <li><b>有现有事务</b>：加入现有事务，成为其一部分</li>
	 * <li><b>无现有事务</b>：以非事务方式执行</li>
	 * <li><b>特殊事务作用域</b>：即使没有实际事务，也会定义一个事务同步范围</li>
	 * </ul>
	 *
	 * <h3>使用场景：</h3>
	 * <pre>
	 * @Service
	 * public class QueryService {
	 *
	 *     @Transactional(propagation = Propagation.SUPPORTS)
	 *     public User findUserById(Long id) {
	 *         // 如果调用方有事务，加入该事务
	 *         // 如果调用方无事务，以非事务方式执行（适用于只读查询）
	 *         return userRepository.findById(id);
	 *     }
	 * }
	 * </pre>
	 *
	 * <h3>注意事项：</h3>
	 * <p><b>注意：</b>对于具有事务同步的事务管理器，{@code PROPAGATION_SUPPORTS}与完全没有事务略有不同，
	 * 因为它定义了一个事务同步可能应用的范围。因此，相同的资源（JDBC {@code Connection}、
	 * Hibernate {@code Session}等）将在整个指定范围内共享。具体行为取决于事务管理器的实际同步配置。
	 *
	 * <p><b>特别小心：</b>在{@code PROPAGATION_SUPPORTS}范围内，不要依赖
	 * {@code PROPAGATION_REQUIRED}或{@code PROPAGATION_REQUIRES_NEW} <i>内部</i>的事务
	 * （这可能导致运行时的同步冲突）。如果这种嵌套不可避免，请确保适当配置事务管理器
	 * （通常切换到"在实际事务上同步"）。
	 *
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setTransactionSynchronization
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
	 */
	int PROPAGATION_SUPPORTS = 1;

	/**
	 * 【支持当前事务，如果不存在则抛出异常】
	 *
	 * <h3>行为描述：</h3>
	 * <ul>
	 * <li><b>有现有事务</b>：加入现有事务，成为其一部分</li>
	 * <li><b>无现有事务</b>：抛出IllegalTransactionStateException异常</li>
	 * <li><b>强制事务环境</b>：确保方法必须在事务中执行</li>
	 * </ul>
	 *
	 * <h3>使用场景：</h3>
	 * <pre>
	 * @Service
	 * public class PaymentService {
	 *
	 *     @Transactional(propagation = Propagation.MANDATORY)
	 *     public void processPayment(Payment payment) {
	 *         // 必须在事务环境中执行，否则抛出异常
	 *         // 用于确保关键操作（如支付）必须在事务中进行
	 *         paymentRepository.process(payment);
	 *     }
	 * }
	 * </pre>
	 *
	 * <h3>注意事项：</h3>
	 * <p>注意：{@code PROPAGATION_MANDATORY}范围内的事务同步总是由周围事务驱动。
	 * 这个传播行为通常用于强制要求事务环境的关键业务操作，如果不在事务中调用会立即失败。
	 */
	int PROPAGATION_MANDATORY = 2;

	/**
	 * 【创建新事务，如果存在当前事务则挂起当前事务】
	 *
	 * <h3>行为描述：</h3>
	 * <ul>
	 * <li><b>有现有事务</b>：挂起当前事务，创建新事务</li>
	 * <li><b>无现有事务</b>：创建新事务</li>
	 * <li><b>独立事务</b>：新事务与挂起的事务完全独立</li>
	 * <li><b>事务隔离</b>：新事务的提交/回滚不影响挂起的事务</li>
	 * </ul>
	 *
	 * <h3>使用场景：</h3>
	 * <pre>
	 * @Service
	 * public class AuditService {
	 *
	 *     @Transactional(propagation = Propagation.REQUIRES_NEW)
	 *     public void logAudit(AuditLog log) {
	 *         // 无论调用方是否有事务，都创建新事务
	 *         // 即使外层事务回滚，审计日志也会被记录
	 *         auditRepository.save(log);
	 *     }
	 * }
	 * </pre>
	 *
	 * <h3>注意事项：</h3>
	 * <p><b>注意：</b>实际的事务挂起并非在所有事务管理器上都能开箱即用。
	 * 这特别适用于{@link org.springframework.transaction.jta.JtaTransactionManager}，
	 * 它需要{@code jakarta.transaction.TransactionManager}可用（这在标准Jakarta EE中是服务器特定的）。
	 *
	 * <p>{@code PROPAGATION_REQUIRES_NEW}范围总是定义自己的事务同步。
	 * 现有的同步将被适当地挂起和恢复。这意味着在新事务中注册的同步回调不会影响挂起的事务。
	 *
	 * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
	 */
	int PROPAGATION_REQUIRES_NEW = 3;

	/**
	 * Do not support a current transaction; rather always execute non-transactionally.
	 * Analogous to the EJB transaction attribute of the same name.
	 * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
	 * on all transaction managers. This in particular applies to
	 * {@link org.springframework.transaction.jta.JtaTransactionManager},
	 * which requires the {@code jakarta.transaction.TransactionManager} to be
	 * made available to it (which is server-specific in standard Jakarta EE).
	 * <p>Note that transaction synchronization is <i>not</i> available within a
	 * {@code PROPAGATION_NOT_SUPPORTED} scope. Existing synchronizations
	 * will be suspended and resumed appropriately.
	 * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
	 */
	int PROPAGATION_NOT_SUPPORTED = 4;

	/**
	 * Do not support a current transaction; throw an exception if a current transaction
	 * exists. Analogous to the EJB transaction attribute of the same name.
	 * <p>Note that transaction synchronization is <i>not</i> available within a
	 * {@code PROPAGATION_NEVER} scope.
	 */
	int PROPAGATION_NEVER = 5;

	/**
	 * 【如果存在当前事务，则在嵌套事务中执行，否则行为类似REQUIRED】
	 *
	 * <h3>行为描述：</h3>
	 * <ul>
	 * <li><b>有现有事务</b>：在嵌套事务中执行（基于Savepoint机制）</li>
	 * <li><b>无现有事务</b>：创建新事务（类似REQUIRED）</li>
	 * <li><b>嵌套提交</b>：嵌套事务的提交依赖于外层事务</li>
	 * <li><b>嵌套回滚</b>：嵌套事务可以独立回滚，不影响外层事务</li>
	 * </ul>
	 *
	 * <h3>使用场景：</h3>
	 * <pre>
	 * @Service
	 * public class OrderService {
	 *
	 *     @Transactional
	 *     public void processOrder(Order order) {
	 *         try {
	 *             // 主业务逻辑
	 *             orderRepository.save(order);
	 *             
	 *             // 子操作（可能失败但不影响主流程）
	 *             notificationService.sendNotification(order);
	 *         } catch (Exception e) {
	 *             // 即使发送通知失败，订单仍然可以正常提交
	 *             log.error("Notification failed but order is saved", e);
	 *         }
	 *     }
	 * }
	 * 
	 * @Service
	 * public class NotificationService {
	 *
	 *     @Transactional(propagation = Propagation.NESTED)
	 *     public void sendNotification(Order order) {
	 *         // 如果发送失败，只回滚这个方法，不影响订单的保存
	 *         notificationRepository.save(new Notification(order));
	 *     }
	 * }
	 * </pre>
	 *
	 * <h3>嵌套事务原理：</h3>
	 * <pre>
	 * 主事务开始
	 *   ↓
	 * 执行业务操作
	 *   ↓
	 * 创建Savepoint（嵌套事务起点）
	 *   ↓
	 * 执行嵌套操作
	 *   ↓
	 * [如果嵌套操作成功] → 释放Savepoint，继续主事务
	 * [如果嵌套操作失败] → 回滚到Savepoint，继续主事务
	 *   ↓
	 * 主事务提交或回滚
	 * </pre>
	 *
	 * <h3>注意事项：</h3>
	 * <p><b>注意：</b>嵌套事务的实际创建只在特定事务管理器上有效。
	 * 开箱即用，这仅适用于在JDBC 3.0+驱动程序上工作的JDBC
	 * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager}。
	 * 某些JTA提供者可能也支持嵌套事务。
	 *
	 * <p>嵌套事务使用数据库的Savepoint机制实现，因此：
	 * <ul>
	 * <li>需要JDBC 3.0+驱动支持</li>
	 * <li>只对DataSourceTransactionManager有效</li>
	 * <li>JTA事务管理器通常不支持</li>
	 * </ul>
	 *
	 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
	 */
	int PROPAGATION_NESTED = 6;


	/**
	 * 【使用底层数据存储的默认隔离级别】
	 *
	 * <h3>说明：</h3>
	 * <p>使用数据库的默认隔离级别，通常是READ_COMMITTED或REPEATABLE_READ，
	 * 具体取决于数据库的类型和配置。
	 *
	 * <h3>其他隔离级别：</h3>
	 * <p>所有其他隔离级别都对应JDBC的隔离级别常量：
	 * <ul>
	 * <li>{@link #ISOLATION_READ_UNCOMMITTED} - 读未提交</li>
	 * <li>{@link #ISOLATION_READ_COMMITTED} - 读已提交</li>
	 * <li>{@link #ISOLATION_REPEATABLE_READ} - 可重复读</li>
	 * <li>{@link #ISOLATION_SERIALIZABLE} - 串行化</li>
	 * </ul>
	 *
	 * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
	 * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
	 * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
	 * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
	 */
	int ISOLATION_DEFAULT = -1;

	/**
	 * Indicates that dirty reads, non-repeatable reads, and phantom reads
	 * can occur.
	 * <p>This level allows a row changed by one transaction to be read by another
	 * transaction before any changes in that row have been committed (a "dirty read").
	 * If any of the changes are rolled back, the second transaction will have
	 * retrieved an invalid row.
	 * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
	 */
	int ISOLATION_READ_UNCOMMITTED = 1;  // same as java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;

	/**
	 * Indicates that dirty reads are prevented; non-repeatable reads and
	 * phantom reads can occur.
	 * <p>This level only prohibits a transaction from reading a row with uncommitted
	 * changes in it.
	 * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
	 */
	int ISOLATION_READ_COMMITTED = 2;  // same as java.sql.Connection.TRANSACTION_READ_COMMITTED;

	/**
	 * Indicates that dirty reads and non-repeatable reads are prevented;
	 * phantom reads can occur.
	 * <p>This level prohibits a transaction from reading a row with uncommitted changes
	 * in it, and it also prohibits the situation where one transaction reads a row,
	 * a second transaction alters the row, and the first transaction re-reads the row,
	 * getting different values the second time (a "non-repeatable read").
	 * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
	 */
	int ISOLATION_REPEATABLE_READ = 4;  // same as java.sql.Connection.TRANSACTION_REPEATABLE_READ;

	/**
	 * Indicates that dirty reads, non-repeatable reads, and phantom reads
	 * are prevented.
	 * <p>This level includes the prohibitions in {@link #ISOLATION_REPEATABLE_READ}
	 * and further prohibits the situation where one transaction reads all rows that
	 * satisfy a {@code WHERE} condition, a second transaction inserts a row
	 * that satisfies that {@code WHERE} condition, and the first transaction
	 * re-reads for the same condition, retrieving the additional "phantom" row
	 * in the second read.
	 * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
	 */
	int ISOLATION_SERIALIZABLE = 8;  // same as java.sql.Connection.TRANSACTION_SERIALIZABLE;


	/**
	 * Use the default timeout of the underlying transaction system,
	 * or none if timeouts are not supported.
	 */
	int TIMEOUT_DEFAULT = -1;


	/**
	 * Return the propagation behavior.
	 * <p>Must return one of the {@code PROPAGATION_XXX} constants
	 * defined on {@link TransactionDefinition this interface}.
	 * <p>The default is {@link #PROPAGATION_REQUIRED}.
	 * @return the propagation behavior
	 * @see #PROPAGATION_REQUIRED
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
	 */
	default int getPropagationBehavior() {
		return PROPAGATION_REQUIRED;
	}

	/**
	 * Return the isolation level.
	 * <p>Must return one of the {@code ISOLATION_XXX} constants defined on
	 * {@link TransactionDefinition this interface}. Those constants are designed
	 * to match the values of the same constants on {@link java.sql.Connection}.
	 * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
	 * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
	 * transactions. Consider switching the "validateExistingTransaction" flag to
	 * "true" on your transaction manager if you'd like isolation level declarations
	 * to get rejected when participating in an existing transaction with a different
	 * isolation level.
	 * <p>The default is {@link #ISOLATION_DEFAULT}. Note that a transaction manager
	 * that does not support custom isolation levels will throw an exception when
	 * given any other level than {@link #ISOLATION_DEFAULT}.
	 * @return the isolation level
	 * @see #ISOLATION_DEFAULT
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setValidateExistingTransaction
	 */
	default int getIsolationLevel() {
		return ISOLATION_DEFAULT;
	}

	/**
	 * Return the transaction timeout.
	 * <p>Must return a number of seconds, or {@link #TIMEOUT_DEFAULT}.
	 * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
	 * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
	 * transactions.
	 * <p>Note that a transaction manager that does not support timeouts will throw
	 * an exception when given any other timeout than {@link #TIMEOUT_DEFAULT}.
	 * <p>The default is {@link #TIMEOUT_DEFAULT}.
	 * @return the transaction timeout
	 */
	default int getTimeout() {
		return TIMEOUT_DEFAULT;
	}

	/**
	 * Return whether to optimize as a read-only transaction.
	 * <p>The read-only flag applies to any transaction context, whether backed
	 * by an actual resource transaction ({@link #PROPAGATION_REQUIRED}/
	 * {@link #PROPAGATION_REQUIRES_NEW}) or operating non-transactionally at
	 * the resource level ({@link #PROPAGATION_SUPPORTS}). In the latter case,
	 * the flag will only apply to managed resources within the application,
	 * such as a Hibernate {@code Session}.
	 * <p>This just serves as a hint for the actual transaction subsystem;
	 * it will <i>not necessarily</i> cause failure of write access attempts.
	 * A transaction manager which cannot interpret the read-only hint will
	 * <i>not</i> throw an exception when asked for a read-only transaction.
	 * @return {@code true} if the transaction is to be optimized as read-only
	 * ({@code false} by default)
	 * @see org.springframework.transaction.support.TransactionSynchronization#beforeCommit(boolean)
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isCurrentTransactionReadOnly()
	 */
	default boolean isReadOnly() {
		return false;
	}

	/**
	 * Return the name of this transaction. Can be {@code null}.
	 * <p>This will be used as the transaction name to be shown in a
	 * transaction monitor, if applicable.
	 * <p>In case of Spring's declarative transactions, the exposed name will be
	 * the {@code fully-qualified class name + "." + method name} (by default).
	 * @return the name of this transaction ({@code null} by default}
	 * @see org.springframework.transaction.interceptor.TransactionAspectSupport
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#getCurrentTransactionName()
	 */
	default @Nullable String getName() {
		return null;
	}


	// Static builder methods

	/**
	 * Return an unmodifiable {@code TransactionDefinition} with defaults.
	 * <p>For customization purposes, use the modifiable
	 * {@link org.springframework.transaction.support.DefaultTransactionDefinition}
	 * instead.
	 * @since 5.2
	 */
	static TransactionDefinition withDefaults() {
		return StaticTransactionDefinition.INSTANCE;
	}

}
