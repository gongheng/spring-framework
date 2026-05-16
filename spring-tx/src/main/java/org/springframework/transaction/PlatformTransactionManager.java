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
 * 【Spring事务管理的核心接口】命令式事务基础设施的中心接口
 *
 * <h3>接口作用：</h3>
 * <ul>
 * <li><b>事务管理统一抽象</b>：定义了事务管理的基本操作</li>
 * <li><b>多实现支持</b>：支持JDBC、JPA、JTA等多种事务实现</li>
 * <li><b>声明式和编程式支持</b>：既支持声明式事务也支持编程式事务</li>
 * </ul>
 *
 * <h3>核心方法：</h3>
 * <pre>
 * 1. getTransaction() - 获取或创建事务
 * 2. commit() - 提交事务
 * 3. rollback() - 回滚事务
 * </pre>
 *
 * <h3>使用方式：</h3>
 * <ul>
 * <li><b>直接使用</b>：应用程序可以直接使用此接口（编程式事务）</li>
 * <li><b>TransactionTemplate</b>：推荐使用事务模板进行编程式事务管理</li>
 * <li><b>声明式事务</b>：通过AOP实现声明式事务管理（推荐）</li>
 * </ul>
 *
 * <h3>实现建议：</h3>
 * <p>对于实现者，建议继承 {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}
 * 抽象类，它预定义了传播行为并处理事务同步。子类只需实现底层事务的特定状态的模板方法：
 * <ul>
 * <li>begin - 开始事务</li>
 * <li>suspend - 挂起事务</li>
 * <li>resume - 恢复事务</li>
 * <li>commit - 提交事务</li>
 * <li>rollback - 回滚事务</li>
 * </ul>
 *
 * <h3>典型实现：</h3>
 * <ul>
 * <li><b>DataSourceTransactionManager</b>: 单JDBC数据源的事务管理器</li>
 * <li><b>JpaTransactionManager</b>: JPA规范的事务管理器</li>
 * <li><b>JtaTransactionManager</b>: 分布式事务管理器</li>
 * </ul>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16.05.2003
 * @see org.springframework.transaction.support.TransactionTemplate
 * @see org.springframework.transaction.interceptor.TransactionInterceptor
 * @see org.springframework.transaction.ReactiveTransactionManager
 * @see ConfigurableTransactionManager
 */
public interface PlatformTransactionManager extends TransactionManager {

	/**
	 * 【获取或创建事务】根据指定的传播行为获取当前活动事务或创建新事务
	 *
	 * <h3>方法执行逻辑：</h3>
	 * <pre>
	 * 1. 检查当前是否已存在事务
	 * 2. 根据传播行为决定处理方式：
	 *    - REQUIRED: 如果存在事务则加入，否则创建新事务
	 *    - REQUIRES_NEW: 总是创建新事务，挂起当前事务
	 *    - NESTED: 嵌套事务
	 *    - 等等...
	 * 3. 应用事务配置（隔离级别、超时、只读等）
	 * 4. 返回事务状态对象
	 * </pre>
	 *
	 * <h3>事务属性应用：</h3>
	 * <ul>
	 * <li><b>隔离级别</b>: 只对新事务应用，参与现有事务时忽略</li>
	 * <li><b>超时时间</b>: 只对新事务应用，参与现有事务时忽略</li>
	 * <li><b>只读标志</b>: 优化提示，不支持时忽略</li>
	 * </ul>
	 *
	 * <h3>异常情况：</h3>
	 * <ul>
	 * <li><b>不支持的事务属性</b>: 抛出异常</li>
	 * <li><b>传播行为冲突</b>: 抛出IllegalTransactionStateException</li>
	 * <li><b>系统错误</b>: 抛出TransactionException</li>
	 * </ul>
	 *
	 * @param definition TransactionDefinition实例（可为null表示默认值），
	 *                   描述传播行为、隔离级别、超时等属性
	 * @return 表示新事务或当前事务的状态对象
	 * @throws TransactionException 查找、创建或系统错误时抛出
	 * @throws IllegalTransactionStateException 当事务定义无法执行时抛出
	 *         （例如当前活动事务与指定的传播行为冲突）
	 * @see TransactionDefinition#getPropagationBehavior
	 * @see TransactionDefinition#getIsolationLevel
	 * @see TransactionDefinition#getTimeout
	 * @see TransactionDefinition#isReadOnly
	 */
	TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException;

	/**
	 * 【提交事务】根据事务状态提交给定的事务
	 *
	 * <h3>提交逻辑：</h3>
	 * <pre>
	 * 1. 检查事务状态
	 * 2. 如果事务被标记为rollback-only，执行回滚
	 * 3. 如果是新事务，执行真正的提交
	 * 4. 如果是参与事务，省略提交以正确参与外围事务
	 * 5. 如果有挂起的事务，恢复外围事务
	 * 6. 清理事务资源
	 * </pre>
	 *
	 * <h3>重要特性：</h3>
	 * <ul>
	 * <li><b>rollback-only处理</b>：如果事务被标记为仅回滚，执行回滚而不是提交</li>
	 * <li><b>事务嵌套</b>：正确处理嵌套事务的提交逻辑</li>
	 * <li><b>资源清理</b>：无论成功或失败都清理资源</li>
	 * <li><b>异常传播</b>：可能抛出数据访问异常</li>
	 * </ul>
	 *
	 * <h3>异常类型：</h3>
	 * <ul>
	 * <li><b>UnexpectedRollbackException</b>: 意外的回滚</li>
	 * <li><b>HeuristicCompletionException</b>: 启发式完成异常</li>
	 * <li><b>TransactionSystemException</b>: 系统错误</li>
	 * <li><b>IllegalTransactionStateException</b>: 事务已完成</li>
	 * </ul>
	 *
	 * <h3>注意事项：</h3>
	 * <p>当commit调用完成时，无论正常还是抛出异常，事务必须完全完成和清理。
	 * 在这种情况下不应该期望回滚调用。
	 *
	 * @param status 由getTransaction方法返回的事务状态对象
	 * @throws UnexpectedRollbackException 当事务协调器发起意外回滚时
	 * @throws HeuristicCompletionException 当事务协调器做出启发式决策导致事务失败时
	 * @throws TransactionSystemException 提交或系统错误时（通常由基础资源失败引起）
	 * @throws IllegalTransactionStateException 当给定事务已完成（已提交或已回滚）时
	 * @see TransactionStatus#setRollbackOnly
	 */
	void commit(TransactionStatus status) throws TransactionException;

	/**
	 * 【回滚事务】回滚给定的事务
	 *
	 * <h3>回滚逻辑：</h3>
	 * <pre>
	 * 1. 检查事务状态
	 * 2. 如果是新事务，执行真正的回滚
	 * 3. 如果是参与事务，标记为rollback-only
	 * 4. 如果有挂起的事务，恢复外围事务
	 * 5. 清理事务资源
	 * </pre>
	 *
	 * <h3>重要特性：</h3>
	 * <ul>
	 * <li><b>嵌套处理</b>：正确处理嵌套事务的回滚逻辑</li>
	 * <li><b>rollback-only</b>：对于参与事务，设置rollback-only标志</li>
	 * <li><b>资源清理</b>：确保事务资源被正确清理</li>
	 * <li><b>异常传播</b>：可能抛出数据访问异常</li>
	 * </ul>
	 *
	 * <h3>重要警告：</h3>
	 * <p><b>不要在commit抛出异常后调用rollback</b>。当commit返回时，事务已经完成和清理，
	 * 即使在commit异常的情况下。因此，commit失败后的rollback调用将导致IllegalTransactionStateException。
	 *
	 * @param status 由getTransaction方法返回的事务状态对象
	 * @throws TransactionSystemException 回滚或系统错误时（通常由基础资源失败引起）
	 * @throws IllegalTransactionStateException 当给定事务已完成（已提交或已回滚）时
	 */
	void rollback(TransactionStatus status) throws TransactionException;

}
