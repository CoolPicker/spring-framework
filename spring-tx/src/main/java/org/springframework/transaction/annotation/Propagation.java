/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.transaction.annotation;

import org.springframework.transaction.TransactionDefinition;

/**
 * Enumeration that represents transaction propagation behaviors for use
 * with the {@link Transactional} annotation, corresponding to the
 * {@link TransactionDefinition} interface.
 * 事务传播行为:
 * 	为了解决业务层方法之间互相调用的事务问题
 *
 *
 * @author Colin Sampaleanu
 * @author Juergen Hoeller
 * @since 1.2
 */
public enum Propagation {

	/**
	 * Support a current transaction, create a new one if none exists.
	 * Analogous to EJB transaction attribute of the same name.
	 * <p>This is the default setting of a transaction annotation.
	 * 默认的事务传播行为	一根绳上的蚂蚱 ***
	 * 如果当前存在事务,则加入该事务;如果当前没有事务,则创建一个新的事务.
	 * 	1. 如果外部方法没有开启事务, REQUIRED修饰的内部方法会新开启自己的事务,且开启的事务相互独立,互不干扰
	 * 	2. 如果外部方法开启事务并且被REQUIRED的话,所有REQUIRED修饰的内部方法和外部方法属于同一事务,
	 * 		只要一个方法回滚,整个事务均回滚.
	 */
	REQUIRED(TransactionDefinition.PROPAGATION_REQUIRED),

	/**
	 * Support a current transaction, execute non-transactionally if none exists.
	 * Analogous to EJB transaction attribute of the same name.
	 * <p>Note: For transaction managers with transaction synchronization,
	 * PROPAGATION_SUPPORTS is slightly different from no transaction at all,
	 * as it defines a transaction scope that synchronization will apply for.
	 * As a consequence, the same resources (JDBC Connection, Hibernate Session, etc)
	 * will be shared for the entire specified scope. Note that this depends on
	 * the actual synchronization configuration of the transaction manager.
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setTransactionSynchronization
	 * 如果当前存在事务，则加入该事务；如果当前没有事务，则以非事务的方式继续运行。
	 * 		老大都招安啦, 小弟也不再替天行道 *
	 */
	SUPPORTS(TransactionDefinition.PROPAGATION_SUPPORTS),

	/**
	 * Support a current transaction, throw an exception if none exists.
	 * Analogous to EJB transaction attribute of the same name.
	 * MANDATORY - 强制添加事务 	不给糖就捣蛋 **
	 * 如果当前存在事务,则加入该事务;如果当前没有事务,则抛出异常
	 */
	MANDATORY(TransactionDefinition.PROPAGATION_MANDATORY),

	/**
	 * Create a new transaction, and suspend the current transaction if one exists.
	 * Analogous to the EJB transaction attribute of the same name.
	 * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
	 * on all transaction managers. This in particular applies to
	 * {@link org.springframework.transaction.jta.JtaTransactionManager},
	 * which requires the {@code javax.transaction.TransactionManager} to be
	 * made available to it (which is server-specific in standard Java EE).
	 * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
	 * 创建一个新的事务,如果当前存在事务,则把当前事务挂起. 	走自己的路, 让别人说去吧 **
	 * 即 不管外部方法是否开启事务, REQUIRES_NEW 修饰的内部方法会新开启自己的事务,
	 * 		且开启的事务相互独立,互不干扰
	 */
	REQUIRES_NEW(TransactionDefinition.PROPAGATION_REQUIRES_NEW),

	/**
	 * Execute non-transactionally, suspend the current transaction if one exists.
	 * Analogous to EJB transaction attribute of the same name.
	 * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
	 * on all transaction managers. This in particular applies to
	 * {@link org.springframework.transaction.jta.JtaTransactionManager},
	 * which requires the {@code javax.transaction.TransactionManager} to be
	 * made available to it (which is server-specific in standard Java EE).
	 * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
	 * 以非事务方式运行，如果当前存在事务，则把当前事务挂起。 浪出天际
	 */
	NOT_SUPPORTED(TransactionDefinition.PROPAGATION_NOT_SUPPORTED),

	/**
	 * Execute non-transactionally, throw an exception if a transaction exists.
	 * Analogous to EJB transaction attribute of the same name.
	 * 以非事务方式运行，如果当前存在事务，则抛出异常。	谁干活我干谁
	 */
	NEVER(TransactionDefinition.PROPAGATION_NEVER),

	/**
	 * Execute within a nested transaction if a current transaction exists,
	 * behave like PROPAGATION_REQUIRED else. There is no analogous feature in EJB.
	 * <p>Note: Actual creation of a nested transaction will only work on specific
	 * transaction managers. Out of the box, this only applies to the JDBC
	 * DataSourceTransactionManager when working on a JDBC 3.0 driver.
	 * Some JTA providers might support nested transactions as well.
	 * see org.springframework.jdbc.datasource.DataSourceTransactionManager
	 * 如果当前存在事务,则创建一个事务作为当前事务的嵌套事务来运行;
	 * 如果当前没有事务,则该取值等价于 REQUIRED
	 * 即, 	我凉了 - 别管我 - 你们继续浪  	**
	 * 		1. 在外部方法未开启事务的情况下 NESTED和REQUIRED作用相同, 修饰的内部方法都会新开启自己的事务,
	 * 			且开启的事务相互独立,互不干扰
	 * 		2. 如果外部方法开启事务的话, NESTED修饰的内部方法属于外部事务的子事务,外部主事务回滚的话,子事务也会回滚,
	 * 			而内部子事务可以单独回滚而不影响外部主事务和其它子事务.
	 */
	NESTED(TransactionDefinition.PROPAGATION_NESTED);


	private final int value;


	Propagation(int value) {
		this.value = value;
	}

	public int value() {
		return this.value;
	}

}
