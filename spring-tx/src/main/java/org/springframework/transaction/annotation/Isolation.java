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
 * Enumeration that represents transaction isolation levels for use
 * with the {@link Transactional} annotation, corresponding to the
 * {@link TransactionDefinition} interface.
 * 事务隔离级别
 *
 * @author Colin Sampaleanu
 * @author Juergen Hoeller
 * @since 1.2
 */
public enum Isolation {

	/**
	 * Use the default isolation level of the underlying datastore.
	 * All other levels correspond to the JDBC isolation levels.
	 * @see java.sql.Connection
	 * 使用 数据库默认的隔离级别，
	 * MySQL 默认采用的 REPEATABLE_READ 隔离级别
	 * 	MySQL InnoDB 在 REPEATABLE_READ (可重读) 事务隔离级别下使用的是 Next-Key Lock锁算法,
	 * 	因此可以避免幻读的产生.所以说 InnoDB存储引擎的默认支持的隔离级别时REPEATABLE_READ 已经可以完全保证事务的隔离性要求,
	 * 	即 达到了SQL标准的 SERIALIZABLE (可串行化)隔离级别.
	 * 	因为隔离级别越低，事务请求的锁越少，所以大部分数据库系统的隔离级别都是 READ-COMMITTED(读取提交内容) ，
	 * 	但是 InnoDB 存储引擎默认使用 REPEATABLE-READ（可重读） 并不会什么任何性能上的损失。
	 * Oracle 默认采用的 READ_COMMITTED 隔离级别.
	 */
	DEFAULT(TransactionDefinition.ISOLATION_DEFAULT),

	/**
	 * A constant indicating that dirty reads, non-repeatable reads and phantom reads
	 * can occur. This level allows a row changed by one transaction to be read by
	 * another transaction before any changes in that row have been committed
	 * (a "dirty read"). If any of the changes are rolled back, the second
	 * transaction will have retrieved an invalid row.
	 * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
	 * 最低的隔离级别, 允许读取尚未提交的数据变更
	 * 可能会导致 脏读/幻读或不可重复读
	 *
	 * 并发场景下的示例 -
	 * 脏读: 读未提交
	 * 不可重复读: 同一事务内, 查询结果不一致
	 * 幻读: 增删场景下,范围查找结果不同
	 */
	READ_UNCOMMITTED(TransactionDefinition.ISOLATION_READ_UNCOMMITTED),

	/**
	 * A constant indicating that dirty reads are prevented; non-repeatable reads
	 * and phantom reads can occur. This level only prohibits a transaction
	 * from reading a row with uncommitted changes in it.
	 * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
	 * 允许读取并发事务已经提交的数据,
	 * 可以阻止脏读, 但是幻读或不可重复读仍有可能发生
	 */
	READ_COMMITTED(TransactionDefinition.ISOLATION_READ_COMMITTED),

	/**
	 * A constant indicating that dirty reads and non-repeatable reads are
	 * prevented; phantom reads can occur. This level prohibits a transaction
	 * from reading a row with uncommitted changes in it, and it also prohibits
	 * the situation where one transaction reads a row, a second transaction
	 * alters the row, and the first transaction rereads the row, getting
	 * different values the second time (a "non-repeatable read").
	 * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
	 * REPEATABLE_READ:
	 * 对同一字段的多次读取结果都是一致的,除非数据是被本身事务所修改,
	 * 可以阻止脏读和不可重复读,但幻读仍有可能发生
	 */
	REPEATABLE_READ(TransactionDefinition.ISOLATION_REPEATABLE_READ),

	/**
	 * A constant indicating that dirty reads, non-repeatable reads and phantom
	 * reads are prevented. This level includes the prohibitions in
	 * {@code ISOLATION_REPEATABLE_READ} and further prohibits the situation
	 * where one transaction reads all rows that satisfy a {@code WHERE}
	 * condition, a second transaction inserts a row that satisfies that
	 * {@code WHERE} condition, and the first transaction rereads for the
	 * same condition, retrieving the additional "phantom" row in the second read.
	 * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
	 * SERIALIZATION:
	 * 最高的隔离级别, 完全服从ACID的隔离级别.所有的事务依次逐个执行, 这样事务之间就完全不可能产生干扰,
	 * 可以防止脏读/不可重复读/幻读
	 */
	SERIALIZABLE(TransactionDefinition.ISOLATION_SERIALIZABLE);


	private final int value;


	Isolation(int value) {
		this.value = value;
	}

	public int value() {
		return this.value;
	}

}
