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

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.util.ClassUtils;

/**
 * Spring AOP代理工厂的默认实现，负责创建JDK动态代理或CGLIB代理
 *
 * 【代理类型选择的决策核心】
 * 这个类是Spring AOP中决定使用哪种代理技术的关键，它会根据配置信息
 * 和目标类的情况来决定使用JDK动态代理还是CGLIB代理。
 *
 * <h3>使用CGLIB代理的条件（满足任一即使用CGLIB）：</h3>
 * <ul>
 * <li>{@code optimize}标志被设置为true（强制使用CGLIB以进行优化）</li>
 * <li>{@code proxyTargetClass}标志被设置为true（强制代理目标类）</li>
 * <li>没有指定代理接口（只能使用CGLIB代理类）</li>
 * </ul>
 *
 * <h3>代理类型选择逻辑：</h3>
 * <pre>
 * 1. 检查是否满足CGLIB代理条件
 *    ├─ 满足条件 → 检查目标类类型
 *    │   ├─ 目标类是接口 → 使用JDK动态代理
 *   │   ├─ 目标类已经是代理类 → 使用JDK动态代理
 *   │   ├─ 目标类是Lambda类 → 使用JDK动态代理
 *   │   └─ 其他情况 → 使用CGLIB代理
 *    └─ 不满足条件 → 使用JDK动态代理
 * </pre>
 *
 * <h3>使用建议：</h3>
 * <ul>
 * <li>默认情况下：优先使用JDK动态代理（基于接口）</li>
 * <li>需要代理类时：设置proxyTargetClass=true强制使用CGLIB</li>
 * <li>性能优化时：设置optimize=true使用CGLIB的优化特性</li>
 * </ul>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 */
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	/**
	 * Singleton instance of this class.
	 * @since 6.0.10
	 */
	public static final DefaultAopProxyFactory INSTANCE = new DefaultAopProxyFactory();

	private static final long serialVersionUID = 7930414337282325166L;


	/**
	 * 【AOP代理创建的核心方法】根据配置创建相应的AOP代理对象
	 *
	 * 这个方法是Spring AOP代理创建的决策中心，根据配置和目标类的情况
	 * 决定使用JDK动态代理还是CGLIB代理。
	 *
	 * <h3>代理类型决策流程：</h3>
	 * <pre>
	 * 第一步：检查是否应该使用CGLIB代理
	 * ├─ optimize=true（优化标志）
	 * ├─ proxyTargetClass=true（强制代理类）
	 * └─ 没有指定代理接口
	 *
	 * 第二步：如果需要CGLIB代理，进一步检查目标类类型
	 * ├─ 目标类为null且没有接口 → 抛出异常
	 * ├─ 目标类是接口 → 使用JDK动态代理
	 * ├─ 目标类已经是JDK代理 → 使用JDK动态代理
	 * ├─ 目标类是Lambda类 → 使用JDK动态代理
	 * └─ 其他情况 → 使用CGLIB代理
	 *
	 * 第三步：否则使用JDK动态代理
	 * </pre>
	 *
	 * <h3>JDK动态代理 vs CGLIB代理：</h3>
	 * <ul>
	 * <li><b>JDK动态代理</b>：基于接口，使用Java反射机制</li>
	 * <li><b>CGLIB代理</b>：基于继承，使用字节码生成技术</li>
	 * </ul>
	 *
	 * @param config AOP配置对象，包含代理创建所需的所有信息
	 * @return 相应的AOP代理对象（JdkDynamicAopProxy或ObjenesisCglibAopProxy）
	 * @throws AopConfigException 如果无法确定目标类且没有指定接口
	 */
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		// 【第一步：判断是否应该使用CGLIB代理】
		// 满足以下任一条件则考虑使用CGLIB：
		// 1. optimize=true：优化标志，通常用于CGLIB的优化
		// 2. proxyTargetClass=true：强制代理目标类而不是接口
		// 3. 没有用户指定的代理接口：只能代理类，必须使用CGLIB
		if (config.isOptimize() || config.isProxyTargetClass() || !config.hasUserSuppliedInterfaces()) {
			Class<?> targetClass = config.getTargetClass();

			// 【异常情况：既没有目标类，又没有接口】
			// 这种情况无法创建代理，必须抛出异常
			if (targetClass == null && config.getProxiedInterfaces().length == 0) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}

			// 【第二步：特殊情况下仍使用JDK动态代理】
			// 即使满足CGLIB条件，以下情况仍使用JDK动态代理：
			// 1. targetClass == null：没有目标类信息，只能基于接口代理
			// 2. 目标类是接口：接口只能用JDK动态代理
			// 3. 目标类已经是代理类：避免重复代理
			// 4. 目标类是Lambda类：Lambda类需要特殊处理
			if (targetClass == null || targetClass.isInterface() ||
					Proxy.isProxyClass(targetClass) || ClassUtils.isLambdaClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}

			// 【正常情况：使用CGLIB代理】
			// 使用ObjenesisCglibAopProxy而不是CglibAopProxy
			// Objenesis是一个对象实例化工具，可以绕过构造器创建对象
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			// 【第三步：默认使用JDK动态代理】
			// 有明确的接口定义，优先使用JDK动态代理
			return new JdkDynamicAopProxy(config);
		}
	}

}
