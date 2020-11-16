package com.spring.nya.jdk;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 加锁过程总结:
 * 	如果是第一个线程tf,那么和队列无关,线程直接持有锁;
 * 	并且也不会初始化队列,如果接下来的线程都是交替执行,那么永远和AQS队列无关,都是直接线程持有锁;
 * 	如果发生了竞争,比如 tf持有锁的过程中, t2来lock,那么这个时候就会初始化AQS;
 * 	初始化AQS的时候会在队列的头部虚拟一个Thread为null的Node,因为队列当中的head永远是持有锁的那个node;
 * 	现在第一次的时候持有锁的是tf而tf不在队列当中所以虚拟了一个node节点;
 * 	队列当中的除了head之外的所有的node都在park,当tf释放锁之后unpark某个node之后,node被唤醒;
 * 	如果node是 t2 ,那么这个时候会首先把t2变成head,在setHead方法里面会把t2设置为head,
 * 	并且把node的thread设置为null,为什么设置为null?
 * 	因为现在 t2 已经拿到锁了,node就不要排队了,那么node对thread的引用就没有意义了.
 * 	所以队列的head里面的thread永远为null
 **/
public class NyaReentrantLock implements Lock, java.io.Serializable {
	private static final long serialVersionUID = 7373984872572414699L;
	/** Synchronizer providing all implementation mechanics */
	private final Sync sync;

	/**
	 * Base of synchronization control for this lock. Subclassed
	 * into fair and nonfair versions below. Uses AQS state to
	 * represent the number of holds on the lock.
	 */
	abstract static class Sync extends NyaAbstractQueuedSynchronized {
		private static final long serialVersionUID = -5179523762034025860L;

		/**
		 * Performs {@link Lock#lock}. The main reason for subclassing
		 * is to allow fast path for nonfair version.
		 */
		abstract void lock();

		/**
		 * Performs non-fair tryLock.  tryAcquire is implemented in
		 * subclasses, but both need nonfair try for trylock method.
		 */
		final boolean nonfairTryAcquire(int acquires) {
			final Thread current = Thread.currentThread();
			int c = getState();
			if (c == 0) {
				if (compareAndSetState(0, acquires)) {
					setExclusiveOwnerThread(current);
					return true;
				}
			}
			else if (current == getExclusiveOwnerThread()) {
				int nextc = c + acquires;
				if (nextc < 0) // overflow
					throw new Error("Maximum lock count exceeded");
				setState(nextc);
				return true;
			}
			return false;
		}

		protected final boolean tryRelease(int releases) {
			int c = getState() - releases;
			if (Thread.currentThread() != getExclusiveOwnerThread())
				throw new IllegalMonitorStateException();
			boolean free = false;
			if (c == 0) {
				free = true;
				setExclusiveOwnerThread(null);
			}
			setState(c);
			return free;
		}

		protected final boolean isHeldExclusively() {
			// While we must in general read state before owner,
			// we don't need to do so to check if current thread is owner
			return getExclusiveOwnerThread() == Thread.currentThread();
		}

		final ConditionObject newCondition() {
			return new ConditionObject();
		}

		// Methods relayed from outer class

		final Thread getOwner() {
			return getState() == 0 ? null : getExclusiveOwnerThread();
		}

		final int getHoldCount() {
			return isHeldExclusively() ? getState() : 0;
		}

		final boolean isLocked() {
			return getState() != 0;
		}

		/**
		 * Reconstitutes the instance from a stream (that is, deserializes it).
		 */
		private void readObject(java.io.ObjectInputStream s)
				throws java.io.IOException, ClassNotFoundException {
			s.defaultReadObject();
			setState(0); // reset to unlocked state
		}
	}

	/**
	 * 不公平锁
	 * Sync object for non-fair locks
	 */
	static final class NonfairSync extends Sync {
		private static final long serialVersionUID = 7316153563782823691L;

		/**
		 * Performs lock.  Try immediate barge, backing up to normal
		 * acquire on failure.
		 */
		final void lock() {
			if (compareAndSetState(0, 1))
				setExclusiveOwnerThread(Thread.currentThread());
			else
				acquire(1);
		}

		protected final boolean tryAcquire(int acquires) {
			return nonfairTryAcquire(acquires);
		}
	}

	/**
	 * 公平锁
	 * Sync object for fair locks
	 */
	static final class FairSync extends Sync {
		private static final long serialVersionUID = -3000897897090466540L;

		final void lock() {
			acquire(1);
		}

		/**
		 * Fair version of tryAcquire.  Don't grant access unless
		 * recursive call or no waiters or is first.
		 */
		protected final boolean tryAcquire(int acquires) {
			// 获取当前线程
			final Thread current = Thread.currentThread();
			// 获取lock对象的上锁状态,如果锁是自由状态则 =0,如果被上锁则 为1,大于1表示重入
			int c = getState();
			if (c == 0) { // 没人占用锁 ---> 我要去上锁 --- 锁是自由状态
				// hasQueuedPredecessors,判断自己是否需要排队
				// 如果不需要排队则进行cas尝试加锁,如果加锁成功则当前线程设置为拥有锁的线程
				// 继而返回true
				if (!hasQueuedPredecessors() &&
						compareAndSetState(0, acquires)) {
					// 设置当前线程为拥有锁的线程,方法后面判断是不是重入(只需把这个线程拿出来判断是否当前线程即可判断重入)
					setExclusiveOwnerThread(current);
					return true;
				}
			}
			// 如果 c != 0,而且当前线程不等于拥有锁的线程则不会进else if,直接返回false,加锁失败
			// 如果 c != 0,而且当前线程等于拥有锁的线程则表示这是一次重入,那么直接把状态+1表示重入次数+1
			// 这里也从侧面表明ReentrantLock是可以重入的,因为如果是重入也返回true,也能lock成功
			else if (current == getExclusiveOwnerThread()) {
				int nextc = c + acquires;
				if (nextc < 0)
					throw new Error("Maximum lock count exceeded");
				setState(nextc);
				return true;
			}
			return false;
		}
	}

	/**
	 * Creates an instance of {@code NyaReentrantLock}.
	 * This is equivalent to using {@code NyaReentrantLock(false)}.
	 */
	public NyaReentrantLock() {
		sync = new NonfairSync();
	}

	/**
	 * Creates an instance of {@code NyaReentrantLock} with the
	 * given fairness policy.
	 *
	 * @param fair {@code true} if this lock should use a fair ordering policy
	 */
	public NyaReentrantLock(boolean fair) {
		sync = fair ? new FairSync() : new NonfairSync();
	}

	/**
	 * Acquires the lock.
	 *
	 * <p>Acquires the lock if it is not held by another thread and returns
	 * immediately, setting the lock hold count to one.
	 *
	 * <p>If the current thread already holds the lock then the hold
	 * count is incremented by one and the method returns immediately.
	 *
	 * <p>If the lock is held by another thread then the
	 * current thread becomes disabled for thread scheduling
	 * purposes and lies dormant until the lock has been acquired,
	 * at which time the lock hold count is set to one.
	 */
	public void lock() {
		sync.lock();
	}

	/**
	 * Acquires the lock unless the current thread is
	 * {@linkplain Thread#interrupt interrupted}.
	 *
	 * <p>Acquires the lock if it is not held by another thread and returns
	 * immediately, setting the lock hold count to one.
	 *
	 * <p>If the current thread already holds this lock then the hold count
	 * is incremented by one and the method returns immediately.
	 *
	 * <p>If the lock is held by another thread then the
	 * current thread becomes disabled for thread scheduling
	 * purposes and lies dormant until one of two things happens:
	 *
	 * <ul>
	 *
	 * <li>The lock is acquired by the current thread; or
	 *
	 * <li>Some other thread {@linkplain Thread#interrupt interrupts} the
	 * current thread.
	 *
	 * </ul>
	 *
	 * <p>If the lock is acquired by the current thread then the lock hold
	 * count is set to one.
	 *
	 * <p>If the current thread:
	 *
	 * <ul>
	 *
	 * <li>has its interrupted status set on entry to this method; or
	 *
	 * <li>is {@linkplain Thread#interrupt interrupted} while acquiring
	 * the lock,
	 *
	 * </ul>
	 *
	 * then {@link InterruptedException} is thrown and the current thread's
	 * interrupted status is cleared.
	 *
	 * <p>In this implementation, as this method is an explicit
	 * interruption point, preference is given to responding to the
	 * interrupt over normal or reentrant acquisition of the lock.
	 *
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public void lockInterruptibly() throws InterruptedException {
		sync.acquireInterruptibly(1);
	}

	/**
	 * Acquires the lock only if it is not held by another thread at the time
	 * of invocation.
	 *
	 * <p>Acquires the lock if it is not held by another thread and
	 * returns immediately with the value {@code true}, setting the
	 * lock hold count to one. Even when this lock has been set to use a
	 * fair ordering policy, a call to {@code tryLock()} <em>will</em>
	 * immediately acquire the lock if it is available, whether or not
	 * other threads are currently waiting for the lock.
	 * This &quot;barging&quot; behavior can be useful in certain
	 * circumstances, even though it breaks fairness. If you want to honor
	 * the fairness setting for this lock, then use
	 * {@link #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
	 * which is almost equivalent (it also detects interruption).
	 *
	 * <p>If the current thread already holds this lock then the hold
	 * count is incremented by one and the method returns {@code true}.
	 *
	 * <p>If the lock is held by another thread then this method will return
	 * immediately with the value {@code false}.
	 *
	 * @return {@code true} if the lock was free and was acquired by the
	 *         current thread, or the lock was already held by the current
	 *         thread; and {@code false} otherwise
	 */
	public boolean tryLock() {
		return sync.nonfairTryAcquire(1);
	}

	/**
	 * Acquires the lock if it is not held by another thread within the given
	 * waiting time and the current thread has not been
	 * {@linkplain Thread#interrupt interrupted}.
	 *
	 * <p>Acquires the lock if it is not held by another thread and returns
	 * immediately with the value {@code true}, setting the lock hold count
	 * to one. If this lock has been set to use a fair ordering policy then
	 * an available lock <em>will not</em> be acquired if any other threads
	 * are waiting for the lock. This is in contrast to the {@link #tryLock()}
	 * method. If you want a timed {@code tryLock} that does permit barging on
	 * a fair lock then combine the timed and un-timed forms together:
	 *
	 *  <pre> {@code
	 * if (lock.tryLock() ||
	 *     lock.tryLock(timeout, unit)) {
	 *   ...
	 * }}</pre>
	 *
	 * <p>If the current thread
	 * already holds this lock then the hold count is incremented by one and
	 * the method returns {@code true}.
	 *
	 * <p>If the lock is held by another thread then the
	 * current thread becomes disabled for thread scheduling
	 * purposes and lies dormant until one of three things happens:
	 *
	 * <ul>
	 *
	 * <li>The lock is acquired by the current thread; or
	 *
	 * <li>Some other thread {@linkplain Thread#interrupt interrupts}
	 * the current thread; or
	 *
	 * <li>The specified waiting time elapses
	 *
	 * </ul>
	 *
	 * <p>If the lock is acquired then the value {@code true} is returned and
	 * the lock hold count is set to one.
	 *
	 * <p>If the current thread:
	 *
	 * <ul>
	 *
	 * <li>has its interrupted status set on entry to this method; or
	 *
	 * <li>is {@linkplain Thread#interrupt interrupted} while
	 * acquiring the lock,
	 *
	 * </ul>
	 * then {@link InterruptedException} is thrown and the current thread's
	 * interrupted status is cleared.
	 *
	 * <p>If the specified waiting time elapses then the value {@code false}
	 * is returned.  If the time is less than or equal to zero, the method
	 * will not wait at all.
	 *
	 * <p>In this implementation, as this method is an explicit
	 * interruption point, preference is given to responding to the
	 * interrupt over normal or reentrant acquisition of the lock, and
	 * over reporting the elapse of the waiting time.
	 *
	 * @param timeout the time to wait for the lock
	 * @param unit the time unit of the timeout argument
	 * @return {@code true} if the lock was free and was acquired by the
	 *         current thread, or the lock was already held by the current
	 *         thread; and {@code false} if the waiting time elapsed before
	 *         the lock could be acquired
	 * @throws InterruptedException if the current thread is interrupted
	 * @throws NullPointerException if the time unit is null
	 */
	public boolean tryLock(long timeout, TimeUnit unit)
			throws InterruptedException {
		return sync.tryAcquireNanos(1, unit.toNanos(timeout));
	}

	/**
	 * Attempts to release this lock.
	 *
	 * <p>If the current thread is the holder of this lock then the hold
	 * count is decremented.  If the hold count is now zero then the lock
	 * is released.  If the current thread is not the holder of this
	 * lock then {@link IllegalMonitorStateException} is thrown.
	 *
	 * @throws IllegalMonitorStateException if the current thread does not
	 *         hold this lock
	 */
	public void unlock() {
		sync.release(1);
	}

	/**
	 * Returns a {@link Condition} instance for use with this
	 * {@link Lock} instance.
	 *
	 * <p>The returned {@link Condition} instance supports the same
	 * usages as do the {@link Object} monitor methods ({@link
	 * Object#wait() wait}, {@link Object#notify notify}, and {@link
	 * Object#notifyAll notifyAll}) when used with the built-in
	 * monitor lock.
	 *
	 * <ul>
	 *
	 * <li>If this lock is not held when any of the {@link Condition}
	 * {@linkplain Condition#await() waiting} or {@linkplain
	 * Condition#signal signalling} methods are called, then an {@link
	 * IllegalMonitorStateException} is thrown.
	 *
	 * <li>When the condition {@linkplain Condition#await() waiting}
	 * methods are called the lock is released and, before they
	 * return, the lock is reacquired and the lock hold count restored
	 * to what it was when the method was called.
	 *
	 * <li>If a thread is {@linkplain Thread#interrupt interrupted}
	 * while waiting then the wait will terminate, an {@link
	 * InterruptedException} will be thrown, and the thread's
	 * interrupted status will be cleared.
	 *
	 * <li> Waiting threads are signalled in FIFO order.
	 *
	 * <li>The ordering of lock reacquisition for threads returning
	 * from waiting methods is the same as for threads initially
	 * acquiring the lock, which is in the default case not specified,
	 * but for <em>fair</em> locks favors those threads that have been
	 * waiting the longest.
	 *
	 * </ul>
	 *
	 * @return the Condition object
	 */
	public Condition newCondition() {
		return sync.newCondition();
	}

	/**
	 * Queries the number of holds on this lock by the current thread.
	 *
	 * <p>A thread has a hold on a lock for each lock action that is not
	 * matched by an unlock action.
	 *
	 * <p>The hold count information is typically only used for testing and
	 * debugging purposes. For example, if a certain section of code should
	 * not be entered with the lock already held then we can assert that
	 * fact:
	 *
	 *  <pre> {@code
	 * class X {
	 *   NyaReentrantLock lock = new NyaReentrantLock();
	 *   // ...
	 *   public void m() {
	 *     assert lock.getHoldCount() == 0;
	 *     lock.lock();
	 *     try {
	 *       // ... method body
	 *     } finally {
	 *       lock.unlock();
	 *     }
	 *   }
	 * }}</pre>
	 *
	 * @return the number of holds on this lock by the current thread,
	 *         or zero if this lock is not held by the current thread
	 */
	public int getHoldCount() {
		return sync.getHoldCount();
	}

	/**
	 * Queries if this lock is held by the current thread.
	 *
	 * <p>Analogous to the {@link Thread#holdsLock(Object)} method for
	 * built-in monitor locks, this method is typically used for
	 * debugging and testing. For example, a method that should only be
	 * called while a lock is held can assert that this is the case:
	 *
	 *  <pre> {@code
	 * class X {
	 *   NyaReentrantLock lock = new NyaReentrantLock();
	 *   // ...
	 *
	 *   public void m() {
	 *       assert lock.isHeldByCurrentThread();
	 *       // ... method body
	 *   }
	 * }}</pre>
	 *
	 * <p>It can also be used to ensure that a reentrant lock is used
	 * in a non-reentrant manner, for example:
	 *
	 *  <pre> {@code
	 * class X {
	 *   NyaReentrantLock lock = new NyaReentrantLock();
	 *   // ...
	 *
	 *   public void m() {
	 *       assert !lock.isHeldByCurrentThread();
	 *       lock.lock();
	 *       try {
	 *           // ... method body
	 *       } finally {
	 *           lock.unlock();
	 *       }
	 *   }
	 * }}</pre>
	 *
	 * @return {@code true} if current thread holds this lock and
	 *         {@code false} otherwise
	 */
	public boolean isHeldByCurrentThread() {
		return sync.isHeldExclusively();
	}

	/**
	 * Queries if this lock is held by any thread. This method is
	 * designed for use in monitoring of the system state,
	 * not for synchronization control.
	 *
	 * @return {@code true} if any thread holds this lock and
	 *         {@code false} otherwise
	 */
	public boolean isLocked() {
		return sync.isLocked();
	}

	/**
	 * Returns {@code true} if this lock has fairness set true.
	 *
	 * @return {@code true} if this lock has fairness set true
	 */
	public final boolean isFair() {
		return sync instanceof FairSync;
	}

	/**
	 * Returns the thread that currently owns this lock, or
	 * {@code null} if not owned. When this method is called by a
	 * thread that is not the owner, the return value reflects a
	 * best-effort approximation of current lock status. For example,
	 * the owner may be momentarily {@code null} even if there are
	 * threads trying to acquire the lock but have not yet done so.
	 * This method is designed to facilitate construction of
	 * subclasses that provide more extensive lock monitoring
	 * facilities.
	 *
	 * @return the owner, or {@code null} if not owned
	 */
	protected Thread getOwner() {
		return sync.getOwner();
	}

	/**
	 * Queries whether any threads are waiting to acquire this lock. Note that
	 * because cancellations may occur at any time, a {@code true}
	 * return does not guarantee that any other thread will ever
	 * acquire this lock.  This method is designed primarily for use in
	 * monitoring of the system state.
	 *
	 * @return {@code true} if there may be other threads waiting to
	 *         acquire the lock
	 */
	public final boolean hasQueuedThreads() {
		return sync.hasQueuedThreads();
	}

	/**
	 * Queries whether the given thread is waiting to acquire this
	 * lock. Note that because cancellations may occur at any time, a
	 * {@code true} return does not guarantee that this thread
	 * will ever acquire this lock.  This method is designed primarily for use
	 * in monitoring of the system state.
	 *
	 * @param thread the thread
	 * @return {@code true} if the given thread is queued waiting for this lock
	 * @throws NullPointerException if the thread is null
	 */
	public final boolean hasQueuedThread(Thread thread) {
		return sync.isQueued(thread);
	}

	/**
	 * Returns an estimate of the number of threads waiting to
	 * acquire this lock.  The value is only an estimate because the number of
	 * threads may change dynamically while this method traverses
	 * internal data structures.  This method is designed for use in
	 * monitoring of the system state, not for synchronization
	 * control.
	 *
	 * @return the estimated number of threads waiting for this lock
	 */
	public final int getQueueLength() {
		return sync.getQueueLength();
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire this lock.  Because the actual set of threads may change
	 * dynamically while constructing this result, the returned
	 * collection is only a best-effort estimate.  The elements of the
	 * returned collection are in no particular order.  This method is
	 * designed to facilitate construction of subclasses that provide
	 * more extensive monitoring facilities.
	 *
	 * @return the collection of threads
	 */
	protected Collection<Thread> getQueuedThreads() {
		return sync.getQueuedThreads();
	}

	/**
	 * Queries whether any threads are waiting on the given condition
	 * associated with this lock. Note that because timeouts and
	 * interrupts may occur at any time, a {@code true} return does
	 * not guarantee that a future {@code signal} will awaken any
	 * threads.  This method is designed primarily for use in
	 * monitoring of the system state.
	 *
	 * @param condition the condition
	 * @return {@code true} if there are any waiting threads
	 * @throws IllegalMonitorStateException if this lock is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this lock
	 * @throws NullPointerException if the condition is null
	 */
	public boolean hasWaiters(Condition condition) {
		if (condition == null)
			throw new NullPointerException();
		if (!(condition instanceof NyaAbstractQueuedSynchronized.ConditionObject))
			throw new IllegalArgumentException("not owner");
		return sync.hasWaiters((NyaAbstractQueuedSynchronized.ConditionObject)condition);
	}

	/**
	 * Returns an estimate of the number of threads waiting on the
	 * given condition associated with this lock. Note that because
	 * timeouts and interrupts may occur at any time, the estimate
	 * serves only as an upper bound on the actual number of waiters.
	 * This method is designed for use in monitoring of the system
	 * state, not for synchronization control.
	 *
	 * @param condition the condition
	 * @return the estimated number of waiting threads
	 * @throws IllegalMonitorStateException if this lock is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this lock
	 * @throws NullPointerException if the condition is null
	 */
	public int getWaitQueueLength(Condition condition) {
		if (condition == null)
			throw new NullPointerException();
		if (!(condition instanceof NyaAbstractQueuedSynchronized.ConditionObject))
			throw new IllegalArgumentException("not owner");
		return sync.getWaitQueueLength((NyaAbstractQueuedSynchronized.ConditionObject)condition);
	}

	/**
	 * Returns a collection containing those threads that may be
	 * waiting on the given condition associated with this lock.
	 * Because the actual set of threads may change dynamically while
	 * constructing this result, the returned collection is only a
	 * best-effort estimate. The elements of the returned collection
	 * are in no particular order.  This method is designed to
	 * facilitate construction of subclasses that provide more
	 * extensive condition monitoring facilities.
	 *
	 * @param condition the condition
	 * @return the collection of threads
	 * @throws IllegalMonitorStateException if this lock is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this lock
	 * @throws NullPointerException if the condition is null
	 */
	protected Collection<Thread> getWaitingThreads(Condition condition) {
		if (condition == null)
			throw new NullPointerException();
		if (!(condition instanceof NyaAbstractQueuedSynchronized.ConditionObject))
			throw new IllegalArgumentException("not owner");
		return sync.getWaitingThreads((NyaAbstractQueuedSynchronized.ConditionObject)condition);
	}

	/**
	 * Returns a string identifying this lock, as well as its lock state.
	 * The state, in brackets, includes either the String {@code "Unlocked"}
	 * or the String {@code "Locked by"} followed by the
	 * {@linkplain Thread#getName name} of the owning thread.
	 *
	 * @return a string identifying this lock, as well as its lock state
	 */
	public String toString() {
		Thread o = sync.getOwner();
		return super.toString() + ((o == null) ?
				"[Unlocked]" :
				"[Locked by thread " + o.getName() + "]");
	}
}
