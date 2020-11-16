/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;

/**
 * Interface allowing extension to the Spring AOP framework to allow
 * handling of new Advisors and Advice types.
 *
 * <p>Implementing objects can create AOP Alliance Interceptors from
 * custom advice types, enabling these advice types to be used
 * in the Spring AOP framework, which uses interception under the covers.
 *
 * <p>There is no need for most Spring users to implement this interface;
 * do so only if you need to introduce more Advisor or Advice types to Spring.
 *
 * 适配器模式:
 * 	适配器模式(Adapter Pattern)将一个接口转换成客户希望的另一个接口,
 * 	适配器模式使接口不兼容的那些类可以一起工作, 别名为 包装器(Wrapper)
 *
 * Spring AOP的实现是基于代理模式,
 * 但是 Spring AOP的增强或通知(Advice)使用到了适配器模式,
 * 与之相关的接口是 AdvisorAdapter.
 *
 * Advice 常用的类型有：
 * 	BeforeAdvice 目标方法调用前,前置通知
 *  AfterAdvice 目标方法调用后,后置通知
 *	AfterReturningAdvice 目标方法执行结束后，return之前
 *
 *  每个类型Advice（通知）都有对应的拦截器:
 *  	MethodBeforeAdviceInterceptor
 *  	AfterReturningAdviceAdapter
 *  	AfterReturningAdviceInterceptor
 *  Spring预定义的通知要通过对应的适配器，
 *  适配成 MethodInterceptor接口(方法拦截器)类型的对象
 *  	（如：MethodBeforeAdviceInterceptor 负责适配 MethodBeforeAdvice）
 *
 * @author Rod Johnson
 */
public interface AdvisorAdapter {

	/**
	 * Does this adapter understand this advice object? Is it valid to
	 * invoke the {@code getInterceptors} method with an Advisor that
	 * contains this advice as an argument?
	 * @param advice an Advice such as a BeforeAdvice
	 * @return whether this adapter understands the given advice object
	 * @see #getInterceptor(org.springframework.aop.Advisor)
	 * @see org.springframework.aop.BeforeAdvice
	 */
	boolean supportsAdvice(Advice advice);

	/**
	 * Return an AOP Alliance MethodInterceptor exposing the behavior of
	 * the given advice to an interception-based AOP framework.
	 * <p>Don't worry about any Pointcut contained in the Advisor;
	 * the AOP framework will take care of checking the pointcut.
	 * @param advisor the Advisor. The supportsAdvice() method must have
	 * returned true on this object
	 * @return an AOP Alliance interceptor for this Advisor. There's
	 * no need to cache instances for efficiency, as the AOP framework
	 * caches advice chains.
	 */
	MethodInterceptor getInterceptor(Advisor advisor);

}
