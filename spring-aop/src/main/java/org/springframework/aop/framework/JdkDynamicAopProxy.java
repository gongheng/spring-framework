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

package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * 【JDK动态代理的核心实现】基于JDK java.lang.reflect.Proxy的AOP代理实现
 *
 * <h3>JDK动态代理原理：</h3>
 * <ul>
 * <li>基于Java反射机制实现动态代理</li>
 * <li>只能代理接口，不能代理类</li>
 * <li>实现InvocationHandler接口拦截方法调用</li>
 * <li>通过Proxy.newProxyInstance()创建代理实例</li>
 * </ul>
 *
 * <h3>使用限制：</h3>
 * <ul>
 * <li><b>只能代理接口</b>：目标对象必须实现至少一个接口</li>
 * <li><b>不能代理类本身的方法</b>：只能代理接口中声明的方法</li>
 * <li><b>性能开销</b>：每次方法调用都需要反射，有一定性能损耗</li>
 * </ul>
 *
 * <h3>与CGLIB代理的对比：</h3>
 * <ul>
 * <li><b>JDK代理</b>：基于接口，使用反射，JDK内置</li>
 * <li><b>CGLIB代理</b>：基于继承，使用字节码生成，性能更好</li>
 * </ul>
 *
 * <h3>工作流程：</h3>
 * <pre>
 * 1. 实现InvocationHandler接口
 * 2. 在invoke()方法中拦截所有方法调用
 * 3. 获取方法调用链（拦截器链）
 * 4. 按顺序执行拦截器逻辑
 * 5. 最终调用目标对象的方法
 * </pre>
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @author Dave Syer
 * @author Sergey Tsypanov
 * @author Sebastien Deleuze
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

	private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent(
			"kotlinx.coroutines.reactor.MonoKt", JdkDynamicAopProxy.class.getClassLoader());

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/** Config used to configure this proxy. */
	private final AdvisedSupport advised;

	/** Cached in {@link AdvisedSupport#proxyMetadataCache}. */
	private transient ProxiedInterfacesCache cache;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		this.advised = config;

		// Initialize ProxiedInterfacesCache if not cached already
		ProxiedInterfacesCache cache;
		if (config.proxyMetadataCache instanceof ProxiedInterfacesCache proxiedInterfacesCache) {
			cache = proxiedInterfacesCache;
		}
		else {
			cache = new ProxiedInterfacesCache(config);
			config.proxyMetadataCache = cache;
		}
		this.cache = cache;
	}


	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	/**
	 * 【创建JDK动态代理对象】使用JDK的Proxy.newProxyInstance方法创建代理实例
	 *
	 * <h3>代理创建过程：</h3>
	 * <pre>
	 * 1. 确定类加载器（使用determineClassLoader方法）
	 * 2. 获取要代理的接口列表（从缓存的proxiedInterfaces中获取）
	 * 3. 调用Proxy.newProxyInstance创建代理实例
	 * 4. 当前JdkDynamicAopProxy实例作为InvocationHandler
	 * </pre>
	 *
	 * <h3>核心参数说明：</h3>
	 * <ul>
	 * <li><b>classLoader</b>: 类加载器，用于加载代理类</li>
	 * <li><b>proxiedInterfaces</b>: 要代理的接口数组</li>
	 * <li><b>this</b>: InvocationHandler实例，用于处理方法调用</li>
	 * </ul>
	 *
	 * @param classLoader 要使用的类加载器，可能为null
	 * @return 创建的代理对象
	 */
	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		// 【核心代理创建】调用JDK的Proxy.newProxyInstance方法
		// 三个关键参数：
		// 1. 类加载器：用于加载生成的代理类
		// 2. 接口数组：代理对象要实现的接口
		// 3. InvocationHandler：处理代理对象方法调用的处理器（即当前对象）
		return Proxy.newProxyInstance(determineClassLoader(classLoader), this.cache.proxiedInterfaces, this);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
		return Proxy.getProxyClass(determineClassLoader(classLoader), this.cache.proxiedInterfaces);
	}

	/**
	 * Determine whether the JDK bootstrap or platform loader has been suggested ->
	 * use higher-level loader which can see Spring infrastructure classes instead.
	 */
	private ClassLoader determineClassLoader(@Nullable ClassLoader classLoader) {
		if (classLoader == null) {
			// JDK bootstrap loader -> use spring-aop ClassLoader instead.
			return getClass().getClassLoader();
		}
		if (classLoader.getParent() == null) {
			// Potentially the JDK platform loader on JDK 9+
			ClassLoader aopClassLoader = getClass().getClassLoader();
			ClassLoader aopParent = aopClassLoader.getParent();
			while (aopParent != null) {
				if (classLoader == aopParent) {
					// Suggested ClassLoader is ancestor of spring-aop ClassLoader
					// -> use spring-aop ClassLoader itself instead.
					return aopClassLoader;
				}
				aopParent = aopParent.getParent();
			}
		}
		// Regular case: use suggested ClassLoader as-is.
		return classLoader;
	}


	/**
	 * 【JDK动态代理的核心方法】InvocationHandler接口的invoke方法实现
	 *
	 * <h3>方法拦截的核心流程：</h3>
	 * <pre>
	 * 1. 特殊方法处理（equals、hashCode、Advised接口方法）
	 * 2. 暴露代理对象到AopContext（如果配置了exposeProxy）
	 * 3. 获取目标对象
	 * 4. 获取方法调用链（拦截器链）
	 * 5. 执行拦截器链或直接调用目标方法
	 * 6. 处理返回值
	 * 7. 清理资源
	 * </pre>
	 *
	 * <h3>拦截器链执行机制：</h3>
	 * <ul>
	 * <li>如果拦截器链为空：直接反射调用目标方法</li>
	 * <li>如果拦截器链不为空：创建ReflectiveMethodInvocation执行链式调用</li>
	 * </ul>
	 *
	 * <p>Callers will see exactly the exception thrown by the target,
	 * unless a hook method throws an exception.
	 */
	@Override
	public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object oldProxy = null;
		boolean setProxyContext = false;

		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			// 【第一阶段：特殊方法处理】
			// 这些方法不通过拦截器链，而是由代理对象直接处理

			// 处理equals方法：如果目标类没有实现equals方法
			if (!this.cache.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// The target does not implement the equals(Object) method itself.
				return equals(args[0]);
			}
			// 处理hashCode方法：如果目标类没有实现hashCode方法
			else if (!this.cache.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// The target does not implement the hashCode() method itself.
				return hashCode();
			}
			// 处理DecoratingProxy接口方法
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// There is only getDecoratedClass() declared -> dispatch to proxy config.
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}
			// 处理Advised接口方法（代理配置相关的管理方法）
			else if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				// Service invocations on ProxyConfig with the proxy config...
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}

			Object retVal;

			// 【第二阶段：暴露代理对象到AopContext】
			// 如果配置了exposeProxy=true，则将当前代理对象暴露到AopContext中
			// 这样在目标对象中可以通过AopContext.currentProxy()获取当前代理对象
			if (this.advised.isExposeProxy()) {
				// Make invocation available if necessary.
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// 【第三阶段：获取目标对象】
			// 尽可能晚地获取目标对象，以减少对目标对象的占用时间
			// 特别是当目标对象来自对象池时，这点很重要
			// Get as late as possible to minimize the time we "own" the target,
			// in case it comes from a pool.
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			// 【第四阶段：获取拦截器链】
			// 根据方法和目标类获取适用于当前方法的拦截器链
			// 拦截器包括：Advisor、Advice、Interceptor等
			// Get the interception chain for this method.
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// 【第五阶段：执行拦截器链或直接调用目标方法】
			// Check whether we have any advice. If we don't, we can fall back on direct
			// reflective invocation of the target, and avoid creating a MethodInvocation.

			if (chain.isEmpty()) {
				// 【情况1：没有拦截器】直接反射调用目标方法
				// 可以跳过创建MethodInvocation，直接调用目标方法
				// We can skip creating a MethodInvocation: just invoke the target directly
				// Note that the final invoker must be an InvokerInterceptor so we know it does
				// nothing but a reflective operation on the target, and no hot swapping or fancy proxying.
				@Nullable Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			else {
				// 【情况2：有拦截器】创建ReflectiveMethodInvocation执行链式调用
				// We need to create a method invocation...
				MethodInvocation invocation =
						new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
				// 【核心：递归执行拦截器链】
				// proceed()方法会按顺序执行拦截器，最后调用目标方法
				// Proceed to the joinpoint through the interceptor chain.
				retVal = invocation.proceed();
			}

			// Massage return value if necessary.
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// Special case: it returned "this" and the return type of the method
				// is type-compatible. Note that we can't help if the target sets
				// a reference to itself in another returned object.
				retVal = proxy;
			}
			else if (retVal == null && returnType != void.class && returnType.isPrimitive()) {
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}
			if (COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method)) {
				return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()) ?
						CoroutinesUtils.asFlow(retVal) : CoroutinesUtils.awaitSingleOrNull(retVal, args[args.length - 1]);
			}
			return retVal;
		}
		finally {
			if (target != null && !targetSource.isStatic()) {
				// Must have come from TargetSource.
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// Restore old proxy.
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy jdkDynamicAopProxy) {
			otherProxy = jdkDynamicAopProxy;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy jdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = jdkDynamicAopProxy;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.cache = new ProxiedInterfacesCache(this.advised);
	}


	/**
	 * Holder for the complete proxied interfaces and derived metadata,
	 * to be cached in {@link AdvisedSupport#proxyMetadataCache}.
	 * @since 6.1.3
	 */
	private static final class ProxiedInterfacesCache {

		final Class<?>[] proxiedInterfaces;

		final boolean equalsDefined;

		final boolean hashCodeDefined;

		ProxiedInterfacesCache(AdvisedSupport config) {
			this.proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(config, true);

			// Find any {@link #equals} or {@link #hashCode} method that may be defined
			// on the supplied set of interfaces.
			boolean equalsDefined = false;
			boolean hashCodeDefined = false;
			for (Class<?> proxiedInterface : this.proxiedInterfaces) {
				Method[] methods = proxiedInterface.getDeclaredMethods();
				for (Method method : methods) {
					if (AopUtils.isEqualsMethod(method)) {
						equalsDefined = true;
						if (hashCodeDefined) {
							break;
						}
					}
					if (AopUtils.isHashCodeMethod(method)) {
						hashCodeDefined = true;
						if (equalsDefined) {
							break;
						}
					}
				}
				if (equalsDefined && hashCodeDefined) {
					break;
				}
			}
			this.equalsDefined = equalsDefined;
			this.hashCodeDefined = hashCodeDefined;
		}
	}

}
