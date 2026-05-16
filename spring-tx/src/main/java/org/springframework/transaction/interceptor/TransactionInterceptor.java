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

package org.springframework.transaction.interceptor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionManager;

/**
 * 【Spring声明式事务的核心拦截器】AOP Alliance MethodInterceptor实现
 *
 * <h3>核心作用：</h3>
 * <ul>
 * <li><b>方法拦截</b>：拦截带@Transactional注解的方法</li>
 * <li><b>事务管理</b>：根据事务属性管理事务的生命周期</li>
 * <li><b>异常处理</b>：根据异常类型决定提交或回滚</li>
 * <li><b>事务同步</b>：管理事务同步的注册和调用</li>
 * </ul>
 *
 * <h3>工作原理：</h3>
 * <pre>
 * 1. 拦截方法调用
 * 2. 获取事务属性（@Transactional注解配置）
 * 3. 确定事务管理器
 * 4. 根据传播行为创建或加入事务
 * 5. 执行目标方法
 * 6. 根据执行结果提交或回滚事务
 * 7. 清理事务资源
 * </pre>
 *
 * <h3>继承关系：</h3>
 * <ul>
 * <li><b>继承TransactionAspectSupport</b>：包含事务管理的核心逻辑</li>
 * <li><b>实现MethodInterceptor</b>：AOP拦截器接口</li>
 * <li><b>实现ApplicationEventPublisherAware</b>：发布事务相关事件</li>
 * </ul>
 *
 * <h3>线程安全性：</h3>
 * <p>TransactionInterceptors是线程安全的，可以在多线程环境中使用。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @see TransactionProxyFactoryBean
 * @see org.springframework.aop.framework.ProxyFactoryBean
 * @see org.springframework.aop.framework.ProxyFactory
 */
@SuppressWarnings("serial")
public class TransactionInterceptor extends TransactionAspectSupport
		implements MethodInterceptor, ApplicationEventPublisherAware, Serializable {

	private @Nullable ApplicationEventPublisher applicationEventPublisher;


	/**
	 * Create a new TransactionInterceptor.
	 * <p>Transaction manager and transaction attributes still need to be set.
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 * @see #setTransactionAttributeSource(TransactionAttributeSource)
	 */
	public TransactionInterceptor() {
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param tas the attribute source to be used to find transaction attributes
	 * @since 5.2.5
	 * @see #setTransactionManager
	 * @see #setTransactionAttributeSource
	 */
	public TransactionInterceptor(TransactionManager ptm, TransactionAttributeSource tas) {
		setTransactionManager(ptm);
		setTransactionAttributeSource(tas);
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param tas the attribute source to be used to find transaction attributes
	 * @see #setTransactionManager
	 * @see #setTransactionAttributeSource
	 * @deprecated in favor of
	 * {@link #TransactionInterceptor(TransactionManager, TransactionAttributeSource)}
	 */
	@Deprecated(since = "5.2.5")
	public TransactionInterceptor(PlatformTransactionManager ptm, TransactionAttributeSource tas) {
		setTransactionManager(ptm);
		setTransactionAttributeSource(tas);
	}

	/**
	 * Create a new TransactionInterceptor.
	 * @param ptm the default transaction manager to perform the actual transaction management
	 * @param attributes the transaction attributes in properties format
	 * @see #setTransactionManager
	 * @see #setTransactionAttributes(java.util.Properties)
	 * @deprecated in favor of {@link #setTransactionAttributes(Properties)}
	 */
	@Deprecated(since = "5.2.5")
	public TransactionInterceptor(PlatformTransactionManager ptm, Properties attributes) {
		setTransactionManager(ptm);
		setTransactionAttributes(attributes);
	}


	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * 【事务拦截的核心方法】拦截方法调用并管理事务
	 *
	 * <h3>执行流程：</h3>
	 * <pre>
	 * 1. 确定目标类（可能为null）
	 * 2. 调用invokeWithinTransaction方法管理事务
	 *    ├─ 获取事务属性
	 *    ├─ 根据传播行为创建/加入事务
	 *    ├─ 执行目标方法
	 *    └─ 根据执行结果提交或回滚事务
	 * 3. 发布事务事件（如果配置了事件发布器）
	 * </pre>
	 *
	 * <h3>事务处理逻辑：</h3>
	 * <ul>
	 * <li><b>事务属性获取</b>：从@Transaction注解或配置中获取</li>
	 * <li><b>传播行为处理</b>：根据传播行为决定如何处理事务</li>
	 * <li><b>异常回滚规则</b>：根据异常类型和回滚规则决定是否回滚</li>
	 * <li><b>事件发布</b>：发布事务相关事件供监听器处理</li>
	 * </ul>
	 *
	 * @param invocation 方法调用对象，包含目标方法、参数等信息
	 * @return 方法执行的结果
	 * @throws Throwable 方法执行过程中抛出的异常
	 */
	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		// 【第一步：确定目标类】
		// Work out the target class: may be {@code null}.
		// The TransactionAttributeSource should be passed the target class
		// as well as the method, which may be from an interface.
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// 【第二步：调用事务管理核心逻辑】
		// Adapt to TransactionAspectSupport's invokeWithinTransaction...
		return invokeWithinTransaction(invocation.getMethod(), targetClass, new InvocationCallback() {
			/**
			 * 【执行目标方法】在事务上下文中执行实际的方法调用
			 */
			@Override
			public @Nullable Object proceedWithInvocation() throws Throwable {
				return invocation.proceed();
			}

			/**
			 * 【回滚事件处理】当事务回滚时调用
			 */
			@Override
			public void onRollback(Throwable failure, TransactionExecution execution) {
				// 创建方法回滚事件
				MethodRollbackEvent event = new MethodRollbackEvent(invocation, failure, execution);
				logger.trace(event, failure);

				// 发布回滚事件给监听器
				if (applicationEventPublisher != null) {
					try {
						applicationEventPublisher.publishEvent(event);
					}
					catch (Throwable ex) {
						if (logger.isWarnEnabled()) {
							logger.warn("Failed to publish " + event, ex);
						}
					}
				}
			}
		});
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void writeObject(ObjectOutputStream oos) throws IOException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		oos.defaultWriteObject();

		// Deserialize superclass fields.
		oos.writeObject(getTransactionManagerBeanName());
		oos.writeObject(getTransactionManager());
		oos.writeObject(getTransactionAttributeSource());
		oos.writeObject(getBeanFactory());
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization, although this class itself doesn't carry state anyway...
		ois.defaultReadObject();

		// Serialize all relevant superclass fields.
		// Superclass can't implement Serializable because it also serves as base class
		// for AspectJ aspects (which are not allowed to implement Serializable)!
		setTransactionManagerBeanName((String) ois.readObject());
		setTransactionManager((PlatformTransactionManager) ois.readObject());
		setTransactionAttributeSource((TransactionAttributeSource) ois.readObject());
		setBeanFactory((BeanFactory) ois.readObject());
	}

}
