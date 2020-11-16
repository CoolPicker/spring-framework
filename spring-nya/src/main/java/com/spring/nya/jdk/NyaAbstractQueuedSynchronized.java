package com.spring.nya.jdk;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

/**
 * 上锁过程重点:
 * 	锁对象: ReentrantLock的实例对象
 * 	自由状态: 自由状态表示锁对象没有被别的线程持有,计数器为0
 * 	计数器: 在lock对象中有一个字段 state 用来记录上锁次数,
 * 		如果lock对象是自由状态则state为0,如果大于零则表示被线程持有了,
 * 		当然也有重入,那么state则 >1
 * 	waitStatus: 仅仅是一个状态而已;ws是一个过渡状态,在不同方法里面判断ws的状态做不同的处理,
 * 		所以 ws=0 有其存在的必要性
 * 	tail: 队列的队尾
 * 	head: 队列的队首
 * 	ts: 第二个给lock加锁的线程
 * 	tf: 第一个给lock加锁的线程
 * 	tc: 当前给线程加锁的线程
 * 	tl: 最后一个加锁的线程
 * 	tn: 随便某个线程
 * 	Node: 节点,里面封装了线程,所以某种意义上node就等于一个线程
 *
 * 	公平锁和非公平锁:
 * 		公平锁的上锁是必须判断自己是不是需要排队；
 * 		而非公平锁是直接进行CAS修改计数器看能不能加锁成功；如果加锁不成功则乖乖排队(调用acquire)；
 * 		所以不管公平还是不公平；只要进到了AQS队列当中那么他就会排队；一朝排队；永远排队记住这点
 *
 */
public abstract class NyaAbstractQueuedSynchronized extends AbstractOwnableSynchronizer
		implements java.io.Serializable
		 {

	private static final long serialVersionUID = 7373984972572414691L;

	/**
	 * Creates a new {@code AbstractQueuedSynchronizer} instance
	 * with initial synchronization state of zero.
	 */
	protected NyaAbstractQueuedSynchronized() { }

	/**
	 * Wait queue node class.
	 *
	 * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
	 * Hagersten) lock queue. CLH locks are normally used for
	 * spinlocks.  We instead use them for blocking synchronizers, but
	 * use the same basic tactic of holding some of the control
	 * information about a thread in the predecessor of its node.  A
	 * "status" field in each node keeps track of whether a thread
	 * should block.  A node is signalled when its predecessor
	 * releases.  Each node of the queue otherwise serves as a
	 * specific-notification-style monitor holding a single waiting
	 * thread. The status field does NOT control whether threads are
	 * granted locks etc though.  A thread may try to acquire if it is
	 * first in the queue. But being first does not guarantee success;
	 * it only gives the right to contend.  So the currently released
	 * contender thread may need to rewait.
	 *
	 * <p>To enqueue into a CLH lock, you atomically splice it in as new
	 * tail. To dequeue, you just set the head field.
	 * <pre>
	 *      +------+  prev +-----+       +-----+
	 * head |      | <---- |     | <---- |     |  tail
	 *      +------+       +-----+       +-----+
	 * </pre>
	 *
	 * <p>Insertion into a CLH queue requires only a single atomic
	 * operation on "tail", so there is a simple atomic point of
	 * demarcation from unqueued to queued. Similarly, dequeuing
	 * involves only updating the "head". However, it takes a bit
	 * more work for nodes to determine who their successors are,
	 * in part to deal with possible cancellation due to timeouts
	 * and interrupts.
	 *
	 * <p>The "prev" links (not used in original CLH locks), are mainly
	 * needed to handle cancellation. If a node is cancelled, its
	 * successor is (normally) relinked to a non-cancelled
	 * predecessor. For explanation of similar mechanics in the case
	 * of spin locks, see the papers by Scott and Scherer at
	 * http://www.cs.rochester.edu/u/scott/synchronization/
	 *
	 * <p>We also use "next" links to implement blocking mechanics.
	 * The thread id for each node is kept in its own node, so a
	 * predecessor signals the next node to wake up by traversing
	 * next link to determine which thread it is.  Determination of
	 * successor must avoid races with newly queued nodes to set
	 * the "next" fields of their predecessors.  This is solved
	 * when necessary by checking backwards from the atomically
	 * updated "tail" when a node's successor appears to be null.
	 * (Or, said differently, the next-links are an optimization
	 * so that we don't usually need a backward scan.)
	 *
	 * <p>Cancellation introduces some conservatism to the basic
	 * algorithms.  Since we must poll for cancellation of other
	 * nodes, we can miss noticing whether a cancelled node is
	 * ahead or behind us. This is dealt with by always unparking
	 * successors upon cancellation, allowing them to stabilize on
	 * a new predecessor, unless we can identify an uncancelled
	 * predecessor who will carry this responsibility.
	 *
	 * <p>CLH queues need a dummy header node to get started. But
	 * we don't create them on construction, because it would be wasted
	 * effort if there is never contention. Instead, the node
	 * is constructed and head and tail pointers are set upon first
	 * contention.
	 *
	 * <p>Threads waiting on Conditions use the same nodes, but
	 * use an additional link. Conditions only need to link nodes
	 * in simple (non-concurrent) linked queues because they are
	 * only accessed when exclusively held.  Upon await, a node is
	 * inserted into a condition queue.  Upon signal, the node is
	 * transferred to the main queue.  A special value of status
	 * field is used to mark which queue a node is on.
	 *
	 * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
	 * Scherer and Michael Scott, along with members of JSR-166
	 * expert group, for helpful ideas, discussions, and critiques
	 * on the design of this class.
	 */
	static final class Node {
		/** Marker to indicate a node is waiting in shared mode */
		static final Node SHARED = new Node();
		/** Marker to indicate a node is waiting in exclusive mode */
		static final Node EXCLUSIVE = null;

		/** waitStatus value to indicate thread has cancelled */
		static final int CANCELLED =  1;
		/** waitStatus value to indicate successor's thread needs unparking */
		static final int SIGNAL    = -1;
		/** waitStatus value to indicate thread is waiting on condition */
		static final int CONDITION = -2;
		/**
		 * waitStatus value to indicate the next acquireShared should
		 * unconditionally propagate
		 */
		static final int PROPAGATE = -3;

		/**
		 * Status field, taking on only the values:
		 *   SIGNAL:     The successor of this node is (or will soon be)
		 *               blocked (via park), so the current node must
		 *               unpark its successor when it releases or
		 *               cancels. To avoid races, acquire methods must
		 *               first indicate they need a signal,
		 *               then retry the atomic acquire, and then,
		 *               on failure, block.
		 *   CANCELLED:  This node is cancelled due to timeout or interrupt.
		 *               Nodes never leave this state. In particular,
		 *               a thread with cancelled node never again blocks.
		 *   CONDITION:  This node is currently on a condition queue.
		 *               It will not be used as a sync queue node
		 *               until transferred, at which time the status
		 *               will be set to 0. (Use of this value here has
		 *               nothing to do with the other uses of the
		 *               field, but simplifies mechanics.)
		 *   PROPAGATE:  A releaseShared should be propagated to other
		 *               nodes. This is set (for head node only) in
		 *               doReleaseShared to ensure propagation
		 *               continues, even if other operations have
		 *               since intervened.
		 *   0:          None of the above
		 *
		 * The values are arranged numerically to simplify use.
		 * Non-negative values mean that a node doesn't need to
		 * signal. So, most code doesn't need to check for particular
		 * values, just for sign.
		 *
		 * The field is initialized to 0 for normal sync nodes, and
		 * CONDITION for condition nodes.  It is modified using CAS
		 * (or when possible, unconditional volatile writes).
		 */
		volatile int waitStatus;

		/**
		 * Link to predecessor node that current node/thread relies on
		 * for checking waitStatus. Assigned during enqueuing, and nulled
		 * out (for sake of GC) only upon dequeuing.  Also, upon
		 * cancellation of a predecessor, we short-circuit while
		 * finding a non-cancelled one, which will always exist
		 * because the head node is never cancelled: A node becomes
		 * head only as a result of successful acquire. A
		 * cancelled thread never succeeds in acquiring, and a thread only
		 * cancels itself, not any other node.
		 */
		volatile Node prev; // 核心 pre

		/**
		 * Link to the successor node that the current node/thread
		 * unparks upon release. Assigned during enqueuing, adjusted
		 * when bypassing cancelled predecessors, and nulled out (for
		 * sake of GC) when dequeued.  The enq operation does not
		 * assign next field of a predecessor until after attachment,
		 * so seeing a null next field does not necessarily mean that
		 * node is at end of queue. However, if a next field appears
		 * to be null, we can scan prev's from the tail to
		 * double-check.  The next field of cancelled nodes is set to
		 * point to the node itself instead of null, to make life
		 * easier for isOnSyncQueue.
		 */
		volatile Node next; // 核心 next

		/**
		 * The thread that enqueued this node.  Initialized on
		 * construction and nulled out after use.
		 */
		volatile Thread thread; // 核心 thread

		/**
		 * Link to next node waiting on condition, or the special
		 * value SHARED.  Because condition queues are accessed only
		 * when holding in exclusive mode, we just need a simple
		 * linked queue to hold nodes while they are waiting on
		 * conditions. They are then transferred to the queue to
		 * re-acquire. And because conditions can only be exclusive,
		 * we save a field by using special value to indicate shared
		 * mode.
		 */
		Node nextWaiter;

		/**
		 * Returns true if node is waiting in shared mode.
		 */
		final boolean isShared() {
			return nextWaiter == SHARED;
		}

		/**
		 * Returns previous node, or throws NullPointerException if null.
		 * Use when predecessor cannot be null.  The null check could
		 * be elided, but is present to help the VM.
		 *
		 * @return the predecessor of this node
		 */
		final Node predecessor() throws NullPointerException {
			Node p = prev;
			if (p == null)
				throw new NullPointerException();
			else
				return p;
		}

		Node() {    // Used to establish initial head or SHARED marker
		}

		Node(Thread thread, Node mode) {     // Used by addWaiter
			this.nextWaiter = mode;
			this.thread = thread;
		}

		Node(Thread thread, int waitStatus) { // Used by Condition
			this.waitStatus = waitStatus;
			this.thread = thread;
		}
	}

	/**
	 * Head of the wait queue, lazily initialized.  Except for
	 * initialization, it is modified only via method setHead.  Note:
	 * If head exists, its waitStatus is guaranteed not to be
	 * CANCELLED.
	 */
	private transient volatile Node head; // 队首

	/**
	 * Tail of the wait queue, lazily initialized.  Modified only via
	 * method enq to add new wait node.
	 */
	private transient volatile Node tail; // 队尾

	/**
	 * The synchronization state.
	 */
	private volatile int state; // 锁状态 加锁成功则为1,重入+1,解锁则为0

	/**
	 * Returns the current value of synchronization state.
	 * This operation has memory semantics of a {@code volatile} read.
	 * @return current state value
	 */
	protected final int getState() {
		return state;
	}

	/**
	 * Sets the value of synchronization state.
	 * This operation has memory semantics of a {@code volatile} write.
	 * @param newState the new state value
	 */
	protected final void setState(int newState) {
		state = newState;
	}

	/**
	 * Atomically sets synchronization state to the given updated
	 * value if the current state value equals the expected value.
	 * This operation has memory semantics of a {@code volatile} read
	 * and write.
	 *
	 * @param expect the expected value
	 * @param update the new value
	 * @return {@code true} if successful. False return indicates that the actual
	 *         value was not equal to the expected value.
	 */
	protected final boolean compareAndSetState(int expect, int update) {
		// See below for intrinsics setup to support this
		return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
	}

	// Queuing utilities

	/**
	 * The number of nanoseconds for which it is faster to spin
	 * rather than to use timed park. A rough estimate suffices
	 * to improve responsiveness with very short timeouts.
	 */
	static final long spinForTimeoutThreshold = 1000L;

	/**
	 * Inserts node into queue, initializing if necessary. See picture above.
	 * @param node the node to insert
	 * @return node's predecessor
	 */
	private Node enq(final Node node) {
		// 死循环
		for (;;) {
			// 队尾复制给t
			// 因为队列没有初始化,故而第一次循环 t==null
			Node t = tail;
			if (t == null) { // Must initialize
				// new Node就是实例化一个Node对象(nn)
				// 调用无参构造方法实例化出来的Node里面三个属性都为null
				// compareAndSetHead入队操作;把这个nn设置称为队列当中的头部,cas防止多线程/确保原子操作
				// 队列当中只有一个节点,即nn
				if (compareAndSetHead(new Node()))
					// 这个时候AQS队列当中只有一个节点，即头部=nn，
					// 所以为了确保队列的完整，设置头部等于尾部，即nn即是头也是尾
					tail = head;
			} else {
				// 第二次循环,队尾tail == t == nn
				// 首先把nc，当前线程所代表的的node的上一个节点改变为nn，
				// 因为这个时候nc需要入队，入队的时候需要把关系维护好
				// 所谓的维护关系就是形成链表，nc的上一个节点只能为nn
				node.prev = t;
				// 入队操作--把nc设置为队尾,队首是nn
				if (compareAndSetTail(t, node)) {
					// 双向链表,维持关系,nn的下一节点设置为nc
					t.next = node;
					// 返回t,即nn,死循环结束
					// 这个返回就是为了终止循环,返回出去的t,没有意义,
					// enq(node)-主要目的在于队列(双向链表)的初始化
					return t;
				}
			}
		}
	}

	/**
	 * Creates and enqueues node for current thread and given mode.
	 *
	 * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
	 * @return the new node
	 */
	private Node addWaiter(Node mode) {
		// 由于AQS队列当中的元素类型为Node,故而要把当前线程 tc 封装成为一个Node对象(nc)
		Node node = new Node(Thread.currentThread(), mode);
		// tail 为队尾,赋值给 pred
		Node pred = tail;
		// 判断pred是否为空,其实就是判断队尾是否有节点,其实只要队列被初始化了,队尾肯定不为空
		// 假设队列里面只有一个元素,那么队尾和队首都是这个元素
		// 换言之就是判断队列有没有初始化
		// 注: 代码执行到这里有两种情况,1- 队列没有初始化 , 2- 队列已经初始化
		// pred不等于空表示第二种情况,队列被初始化了
		if (pred != null) {
			// 直接把当前线程封装的nc的上一个节点设置成为pred即原来的队尾
			node.prev = pred;
			if (compareAndSetTail(pred, node)) {
				// 继而把pred的下一个节点设置为nc,这个nc自己成为了队尾
				pred.next = node;
				// 返回 nc
				return node;
			}
		}
		// 此时队列并没有初始化,enq主要用于初始化队列
		enq(node);
		return node;
	}

	/**
	 * Sets head of queue to be node, thus dequeuing. Called only by
	 * acquire methods.  Also nulls out unused fields for sake of GC
	 * and to suppress unnecessary signals and traversals.
	 *
	 * @param node the node
	 */
	private void setHead(Node node) {
		head = node;
		node.thread = null;
		node.prev = null;
	}

	/**
	 * Wakes up node's successor, if one exists.
	 *
	 * @param node the node
	 */
	private void unparkSuccessor(Node node) {
		/*
		 * If status is negative (i.e., possibly needing signal) try
		 * to clear in anticipation of signalling.  It is OK if this
		 * fails or if status is changed by waiting thread.
		 */
		int ws = node.waitStatus;
		if (ws < 0)
			compareAndSetWaitStatus(node, ws, 0);

		/*
		 * Thread to unpark is held in successor, which is normally
		 * just the next node.  But if cancelled or apparently null,
		 * traverse backwards from tail to find the actual
		 * non-cancelled successor.
		 */
		Node s = node.next;
		if (s == null || s.waitStatus > 0) {
			s = null;
			for (Node t = tail; t != null && t != node; t = t.prev)
				if (t.waitStatus <= 0)
					s = t;
		}
		if (s != null)
			LockSupport.unpark(s.thread);
	}

	/**
	 * Release action for shared mode -- signals successor and ensures
	 * propagation. (Note: For exclusive mode, release just amounts
	 * to calling unparkSuccessor of head if it needs signal.)
	 */
	private void doReleaseShared() {
		/*
		 * Ensure that a release propagates, even if there are other
		 * in-progress acquires/releases.  This proceeds in the usual
		 * way of trying to unparkSuccessor of head if it needs
		 * signal. But if it does not, status is set to PROPAGATE to
		 * ensure that upon release, propagation continues.
		 * Additionally, we must loop in case a new node is added
		 * while we are doing this. Also, unlike other uses of
		 * unparkSuccessor, we need to know if CAS to reset status
		 * fails, if so rechecking.
		 */
		for (;;) {
			Node h = head;
			if (h != null && h != tail) {
				int ws = h.waitStatus;
				if (ws == Node.SIGNAL) {
					if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
						continue;            // loop to recheck cases
					unparkSuccessor(h);
				}
				else if (ws == 0 &&
						!compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
					continue;                // loop on failed CAS
			}
			if (h == head)                   // loop if head changed
				break;
		}
	}

	/**
	 * Sets head of queue, and checks if successor may be waiting
	 * in shared mode, if so propagating if either propagate > 0 or
	 * PROPAGATE status was set.
	 *
	 * @param node the node
	 * @param propagate the return value from a tryAcquireShared
	 */
	private void setHeadAndPropagate(Node node, int propagate) {
		Node h = head; // Record old head for check below
		setHead(node);
		/*
		 * Try to signal next queued node if:
		 *   Propagation was indicated by caller,
		 *     or was recorded (as h.waitStatus either before
		 *     or after setHead) by a previous operation
		 *     (note: this uses sign-check of waitStatus because
		 *      PROPAGATE status may transition to SIGNAL.)
		 * and
		 *   The next node is waiting in shared mode,
		 *     or we don't know, because it appears null
		 *
		 * The conservatism in both of these checks may cause
		 * unnecessary wake-ups, but only when there are multiple
		 * racing acquires/releases, so most need signals now or soon
		 * anyway.
		 */
		if (propagate > 0 || h == null || h.waitStatus < 0 ||
				(h = head) == null || h.waitStatus < 0) {
			Node s = node.next;
			if (s == null || s.isShared())
				doReleaseShared();
		}
	}

	// Utilities for various versions of acquire

	/**
	 * Cancels an ongoing attempt to acquire.
	 *
	 * @param node the node
	 */
	private void cancelAcquire(Node node) {
		// Ignore if node doesn't exist
		if (node == null)
			return;

		node.thread = null;

		// Skip cancelled predecessors
		Node pred = node.prev;
		while (pred.waitStatus > 0)
			node.prev = pred = pred.prev;

		// predNext is the apparent node to unsplice. CASes below will
		// fail if not, in which case, we lost race vs another cancel
		// or signal, so no further action is necessary.
		Node predNext = pred.next;

		// Can use unconditional write instead of CAS here.
		// After this atomic step, other Nodes can skip past us.
		// Before, we are free of interference from other threads.
		node.waitStatus = Node.CANCELLED;

		// If we are the tail, remove ourselves.
		if (node == tail && compareAndSetTail(node, pred)) {
			compareAndSetNext(pred, predNext, null);
		} else {
			// If successor needs signal, try to set pred's next-link
			// so it will get one. Otherwise wake it up to propagate.
			int ws;
			if (pred != head &&
					((ws = pred.waitStatus) == Node.SIGNAL ||
							(ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
					pred.thread != null) {
				Node next = node.next;
				if (next != null && next.waitStatus <= 0)
					compareAndSetNext(pred, predNext, next);
			} else {
				unparkSuccessor(node);
			}

			node.next = node; // help GC
		}
	}

	/**
	 * Checks and updates status for a node that failed to acquire.
	 * Returns true if thread should block. This is the main signal
	 * control in all acquire loops.  Requires that pred == node.prev.
	 *
	 * @param pred node's predecessor holding status
	 * @param node the node
	 * @return {@code true} if thread should block
	 */
	private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
		int ws = pred.waitStatus;
		if (ws == Node.SIGNAL)
			/*
			 * This node has already set status asking a release
			 * to signal it, so it can safely park.
			 */
			return true;
		if (ws > 0) {
			/*
			 * Predecessor was cancelled. Skip over predecessors and
			 * indicate retry.
			 */
			do {
				node.prev = pred = pred.prev;
			} while (pred.waitStatus > 0);
			pred.next = node;
		} else {
			/*
			 * waitStatus must be 0 or PROPAGATE.  Indicate that we
			 * need a signal, but don't park yet.  Caller will need to
			 * retry to make sure it cannot acquire before parking.
			 */
			compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
		}
		return false;
	}

	/**
	 * Convenience method to interrupt current thread.
	 */
	static void selfInterrupt() {
		Thread.currentThread().interrupt();
	}

	/**
	 * Convenience method to park and then check if interrupted
	 *
	 * @return {@code true} if interrupted
	 */
	private final boolean parkAndCheckInterrupt() {
		LockSupport.park(this);
		return Thread.interrupted();
	}

	/*
	 * Various flavors of acquire, varying in exclusive/shared and
	 * control modes.  Each is mostly the same, but annoyingly
	 * different.  Only a little bit of factoring is possible due to
	 * interactions of exception mechanics (including ensuring that we
	 * cancel if tryAcquire throws exception) and other control, at
	 * least not without hurting performance too much.
	 */

	/**
	 * Acquires in exclusive uninterruptible mode for thread already in
	 * queue. Used by condition wait methods as well as acquire.
	 *
	 * @param node the node
	 * @param arg the acquire argument
	 * @return {@code true} if interrupted while waiting
	 *
	 * 代码能执行到这里说明 tc 需要排队
	 * 需要排队有两种情况,换言之 代码执行到这里有两种情况:
	 * 	1. tf持有了锁,并没有释放,所有tc来加锁的时候需要排队,但这个时候 队列并没有初始化
	 * 	2. tn 持有了锁,那么由于加锁 tn != tf, 所以队列是一定被初始化了的,
	 * 		tc来加锁,那么队列当中有人在排队,故而他要去排队
	 *
	 * 	入参的node 就是当前线程封装的node nc
	 */
	final boolean acquireQueued(final Node node, int arg) {
		// 记住标识很重要
		boolean failed = true;
		try {
			// 同样的一个标识
			boolean interrupted = false;
			// 死循环
			for (;;) {
				// 获取nc的上一节点,有两种情况: 1- 上一节点为头部; 2- 上一节点不为头部
				final Node p = node.predecessor();
				/**
				 * 如果nc的上一个节点为头部，则表示nc为队列当中的第二个元素，为队列当中的第一个排队的Node；
				 * 如果nc为队列当中的第二个元素，第一个排队的则调用tryAcquire去尝试加锁
				 * 只有nc为第二个元素；第一个排队的情况下才会尝试加锁，其他情况直接去park了，
				 * 因为第一个排队的执行到这里的时候需要看看持有有锁的线程有没有释放锁，释放了就轮到我了，就不park了
				 * 有人会疑惑说开始调用tryAcquire加锁失败了（需要排队），这里为什么还要进行tryAcquire不是重复了吗？
				 * 其实不然，因为第一次tryAcquire判断是否需要排队，如果需要排队，那么我就入队；
				 * 当我入队之后我发觉前面那个人就是第一个，持有锁的那个，那么我不死心，再次问问前面那个人搞完没有
				 * 如果他搞完了，我就不park，接着他搞我自己的事；如果他没有搞完，那么我则在队列当中去park，等待别人叫我
				 * 但是如果我去排队，发觉前面那个人在睡觉，前面那个人都在睡觉，那么我也睡觉吧---------------好好理解一下
				 */
				if (p == head && tryAcquire(arg)) {
					//能够执行到这里表示我来加锁的时候，锁被持有了，我去排队，进到队列当中的时候发觉我前面那个人没有park，
					//前面那个人就是当前持有锁的那个人，那么我问问他搞完没有
					//能够进到这个里面就表示前面那个人搞完了；所以这里能执行到的几率比较小；
					// 但是在高并发的世界中这种情况真的需要考虑
					//如果我前面那个人搞完了，我nc得到锁了，那么前面那个人直接出队列，
					// 我自己则是队首；这行代码就是设置自己为队首
					setHead(node);
					p.next = null; // help GC
					// 设置标识---记住记加锁成功的时候为false
					failed = false;
					return interrupted;
				}
				/**
				 * 进到这里分为两种情况
				 * 	1、nc的上一个节点不是头部，说白了，就是我去排队了，但是我上一个人不是队列第一个
				 *  2、第二种情况，我去排队了，发觉上一个节点是第一个，但是他还在搞事没有释放锁
				 *      不管哪种情况这个时候我都需要park，park之前我需要把上一个节点的状态改成park状态
				 *      这里比较难以理解为什么我需要去改变上一个节点的park状态呢？每个node都有一个状态，默认为0，表示无状态
				 *      -1表示在park；当时不能自己把自己改成-1状态？为什么呢？因为你得确定你自己park了才是能改为-1；
				 *      不然你自己改成自己为-1；但是改完之后你没有park那不就骗人？
				 *      所以只能先park；再改状态；但是问题你自己都park了；完全释放CPU资源了，故而没有办法执行任何代码了，
				 *      所以只能别人来改；故而可以看到每次都是自己的后一个节点把自己改成-1状态
				 */
				if (shouldParkAfterFailedAcquire(p, node) &&
						// 改上一个节点的状态成功之后；自己park；
						parkAndCheckInterrupt())
					interrupted = true;
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	/**
	 * Acquires in exclusive interruptible mode.
	 * @param arg the acquire argument
	 */
	private void doAcquireInterruptibly(int arg)
			throws InterruptedException {
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (;;) {
				final Node p = node.predecessor();
				if (p == head && tryAcquire(arg)) {
					setHead(node);
					p.next = null; // help GC
					failed = false;
					return;
				}
				if (shouldParkAfterFailedAcquire(p, node) &&
						parkAndCheckInterrupt())
					throw new InterruptedException();
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	/**
	 * Acquires in exclusive timed mode.
	 *
	 * @param arg the acquire argument
	 * @param nanosTimeout max wait time
	 * @return {@code true} if acquired
	 */
	private boolean doAcquireNanos(int arg, long nanosTimeout)
			throws InterruptedException {
		if (nanosTimeout <= 0L)
			return false;
		final long deadline = System.nanoTime() + nanosTimeout;
		final Node node = addWaiter(Node.EXCLUSIVE);
		boolean failed = true;
		try {
			for (;;) {
				final Node p = node.predecessor();
				if (p == head && tryAcquire(arg)) {
					setHead(node);
					p.next = null; // help GC
					failed = false;
					return true;
				}
				nanosTimeout = deadline - System.nanoTime();
				if (nanosTimeout <= 0L)
					return false;
				if (shouldParkAfterFailedAcquire(p, node) &&
						nanosTimeout > spinForTimeoutThreshold)
					LockSupport.parkNanos(this, nanosTimeout);
				if (Thread.interrupted())
					throw new InterruptedException();
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	/**
	 * Acquires in shared uninterruptible mode.
	 * @param arg the acquire argument
	 */
	private void doAcquireShared(int arg) {
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			boolean interrupted = false;
			for (;;) {
				final Node p = node.predecessor();
				if (p == head) {
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						if (interrupted)
							selfInterrupt();
						failed = false;
						return;
					}
				}
				if (shouldParkAfterFailedAcquire(p, node) &&
						parkAndCheckInterrupt())
					interrupted = true;
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	/**
	 * Acquires in shared interruptible mode.
	 * @param arg the acquire argument
	 */
	private void doAcquireSharedInterruptibly(int arg)
			throws InterruptedException {
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			for (;;) {
				final Node p = node.predecessor();
				if (p == head) {
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						failed = false;
						return;
					}
				}
				if (shouldParkAfterFailedAcquire(p, node) &&
						parkAndCheckInterrupt())
					throw new InterruptedException();
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	/**
	 * Acquires in shared timed mode.
	 *
	 * @param arg the acquire argument
	 * @param nanosTimeout max wait time
	 * @return {@code true} if acquired
	 */
	private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
			throws InterruptedException {
		if (nanosTimeout <= 0L)
			return false;
		final long deadline = System.nanoTime() + nanosTimeout;
		final Node node = addWaiter(Node.SHARED);
		boolean failed = true;
		try {
			for (;;) {
				final Node p = node.predecessor();
				if (p == head) {
					int r = tryAcquireShared(arg);
					if (r >= 0) {
						setHeadAndPropagate(node, r);
						p.next = null; // help GC
						failed = false;
						return true;
					}
				}
				nanosTimeout = deadline - System.nanoTime();
				if (nanosTimeout <= 0L)
					return false;
				if (shouldParkAfterFailedAcquire(p, node) &&
						nanosTimeout > spinForTimeoutThreshold)
					LockSupport.parkNanos(this, nanosTimeout);
				if (Thread.interrupted())
					throw new InterruptedException();
			}
		} finally {
			if (failed)
				cancelAcquire(node);
		}
	}

	// Main exported methods

	/**
	 * Attempts to acquire in exclusive mode. This method should query
	 * if the state of the object permits it to be acquired in the
	 * exclusive mode, and if so to acquire it.
	 *
	 * <p>This method is always invoked by the thread performing
	 * acquire.  If this method reports failure, the acquire method
	 * may queue the thread, if it is not already queued, until it is
	 * signalled by a release from some other thread. This can be used
	 * to implement method {@link Lock#tryLock()}.
	 *
	 * <p>The default
	 * implementation throws {@link UnsupportedOperationException}.
	 *
	 * @param arg the acquire argument. This value is always the one
	 *        passed to an acquire method, or is the value saved on entry
	 *        to a condition wait.  The value is otherwise uninterpreted
	 *        and can represent anything you like.
	 * @return {@code true} if successful. Upon success, this object has
	 *         been acquired.
	 * @throws IllegalMonitorStateException if acquiring would place this
	 *         synchronizer in an illegal state. This exception must be
	 *         thrown in a consistent fashion for synchronization to work
	 *         correctly.
	 * @throws UnsupportedOperationException if exclusive mode is not supported
	 */
	protected boolean tryAcquire(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to set the state to reflect a release in exclusive
	 * mode.
	 *
	 * <p>This method is always invoked by the thread performing release.
	 *
	 * <p>The default implementation throws
	 * {@link UnsupportedOperationException}.
	 *
	 * @param arg the release argument. This value is always the one
	 *        passed to a release method, or the current state value upon
	 *        entry to a condition wait.  The value is otherwise
	 *        uninterpreted and can represent anything you like.
	 * @return {@code true} if this object is now in a fully released
	 *         state, so that any waiting threads may attempt to acquire;
	 *         and {@code false} otherwise.
	 * @throws IllegalMonitorStateException if releasing would place this
	 *         synchronizer in an illegal state. This exception must be
	 *         thrown in a consistent fashion for synchronization to work
	 *         correctly.
	 * @throws UnsupportedOperationException if exclusive mode is not supported
	 */
	protected boolean tryRelease(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to acquire in shared mode. This method should query if
	 * the state of the object permits it to be acquired in the shared
	 * mode, and if so to acquire it.
	 *
	 * <p>This method is always invoked by the thread performing
	 * acquire.  If this method reports failure, the acquire method
	 * may queue the thread, if it is not already queued, until it is
	 * signalled by a release from some other thread.
	 *
	 * <p>The default implementation throws {@link
	 * UnsupportedOperationException}.
	 *
	 * @param arg the acquire argument. This value is always the one
	 *        passed to an acquire method, or is the value saved on entry
	 *        to a condition wait.  The value is otherwise uninterpreted
	 *        and can represent anything you like.
	 * @return a negative value on failure; zero if acquisition in shared
	 *         mode succeeded but no subsequent shared-mode acquire can
	 *         succeed; and a positive value if acquisition in shared
	 *         mode succeeded and subsequent shared-mode acquires might
	 *         also succeed, in which case a subsequent waiting thread
	 *         must check availability. (Support for three different
	 *         return values enables this method to be used in contexts
	 *         where acquires only sometimes act exclusively.)  Upon
	 *         success, this object has been acquired.
	 * @throws IllegalMonitorStateException if acquiring would place this
	 *         synchronizer in an illegal state. This exception must be
	 *         thrown in a consistent fashion for synchronization to work
	 *         correctly.
	 * @throws UnsupportedOperationException if shared mode is not supported
	 */
	protected int tryAcquireShared(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Attempts to set the state to reflect a release in shared mode.
	 *
	 * <p>This method is always invoked by the thread performing release.
	 *
	 * <p>The default implementation throws
	 * {@link UnsupportedOperationException}.
	 *
	 * @param arg the release argument. This value is always the one
	 *        passed to a release method, or the current state value upon
	 *        entry to a condition wait.  The value is otherwise
	 *        uninterpreted and can represent anything you like.
	 * @return {@code true} if this release of shared mode may permit a
	 *         waiting acquire (shared or exclusive) to succeed; and
	 *         {@code false} otherwise
	 * @throws IllegalMonitorStateException if releasing would place this
	 *         synchronizer in an illegal state. This exception must be
	 *         thrown in a consistent fashion for synchronization to work
	 *         correctly.
	 * @throws UnsupportedOperationException if shared mode is not supported
	 */
	protected boolean tryReleaseShared(int arg) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns {@code true} if synchronization is held exclusively with
	 * respect to the current (calling) thread.  This method is invoked
	 * upon each call to a non-waiting {@link ConditionObject} method.
	 * (Waiting methods instead invoke {@link #release}.)
	 *
	 * <p>The default implementation throws {@link
	 * UnsupportedOperationException}. This method is invoked
	 * internally only within {@link ConditionObject} methods, so need
	 * not be defined if conditions are not used.
	 *
	 * @return {@code true} if synchronization is held exclusively;
	 *         {@code false} otherwise
	 * @throws UnsupportedOperationException if conditions are not supported
	 */
	protected boolean isHeldExclusively() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Acquires in exclusive mode, ignoring interrupts.  Implemented
	 * by invoking at least once {@link #tryAcquire},
	 * returning on success.  Otherwise the thread is queued, possibly
	 * repeatedly blocking and unblocking, invoking {@link
	 * #tryAcquire} until success.  This method can be used
	 * to implement method {@link Lock#lock}.
	 *
	 * @param arg the acquire argument.  This value is conveyed to
	 *        {@link #tryAcquire} but is otherwise uninterpreted and
	 *        can represent anything you like.
	 */
	public final void acquire(int arg) {
		// tryAcquire(arg) 尝试加锁,
		// 如果加锁失败则会调用acquireQueued方法加入队列去排队,
		// 如果加锁成功则不会调用
		// 加入队列之后,线程会立马park,等待解锁之后会被unpark,
		// 醒来之后判断自己是否被打断
		// 注意 tryAcquire方法结果做了取反
		if (!tryAcquire(arg) &&
				// addWaiter方法就是让nc入队,并且维持队列的链表关系
				acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
			selfInterrupt();
	}

	/**
	 * Acquires in exclusive mode, aborting if interrupted.
	 * Implemented by first checking interrupt status, then invoking
	 * at least once {@link #tryAcquire}, returning on
	 * success.  Otherwise the thread is queued, possibly repeatedly
	 * blocking and unblocking, invoking {@link #tryAcquire}
	 * until success or the thread is interrupted.  This method can be
	 * used to implement method {@link Lock#lockInterruptibly}.
	 *
	 * @param arg the acquire argument.  This value is conveyed to
	 *        {@link #tryAcquire} but is otherwise uninterpreted and
	 *        can represent anything you like.
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public final void acquireInterruptibly(int arg)
			throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		if (!tryAcquire(arg))
			doAcquireInterruptibly(arg);
	}

	/**
	 * Attempts to acquire in exclusive mode, aborting if interrupted,
	 * and failing if the given timeout elapses.  Implemented by first
	 * checking interrupt status, then invoking at least once {@link
	 * #tryAcquire}, returning on success.  Otherwise, the thread is
	 * queued, possibly repeatedly blocking and unblocking, invoking
	 * {@link #tryAcquire} until success or the thread is interrupted
	 * or the timeout elapses.  This method can be used to implement
	 * method {@link Lock#tryLock(long, TimeUnit)}.
	 *
	 * @param arg the acquire argument.  This value is conveyed to
	 *        {@link #tryAcquire} but is otherwise uninterpreted and
	 *        can represent anything you like.
	 * @param nanosTimeout the maximum number of nanoseconds to wait
	 * @return {@code true} if acquired; {@code false} if timed out
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public final boolean tryAcquireNanos(int arg, long nanosTimeout)
			throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		return tryAcquire(arg) ||
				doAcquireNanos(arg, nanosTimeout);
	}

	/**
	 * Releases in exclusive mode.  Implemented by unblocking one or
	 * more threads if {@link #tryRelease} returns true.
	 * This method can be used to implement method {@link Lock#unlock}.
	 *
	 * @param arg the release argument.  This value is conveyed to
	 *        {@link #tryRelease} but is otherwise uninterpreted and
	 *        can represent anything you like.
	 * @return the value returned from {@link #tryRelease}
	 */
	public final boolean release(int arg) {
		if (tryRelease(arg)) {
			Node h = head;
			if (h != null && h.waitStatus != 0)
				unparkSuccessor(h);
			return true;
		}
		return false;
	}

	/**
	 * Acquires in shared mode, ignoring interrupts.  Implemented by
	 * first invoking at least once {@link #tryAcquireShared},
	 * returning on success.  Otherwise the thread is queued, possibly
	 * repeatedly blocking and unblocking, invoking {@link
	 * #tryAcquireShared} until success.
	 *
	 * @param arg the acquire argument.  This value is conveyed to
	 *        {@link #tryAcquireShared} but is otherwise uninterpreted
	 *        and can represent anything you like.
	 */
	public final void acquireShared(int arg) {
		if (tryAcquireShared(arg) < 0)
			doAcquireShared(arg);
	}

	/**
	 * Acquires in shared mode, aborting if interrupted.  Implemented
	 * by first checking interrupt status, then invoking at least once
	 * {@link #tryAcquireShared}, returning on success.  Otherwise the
	 * thread is queued, possibly repeatedly blocking and unblocking,
	 * invoking {@link #tryAcquireShared} until success or the thread
	 * is interrupted.
	 * @param arg the acquire argument.
	 * This value is conveyed to {@link #tryAcquireShared} but is
	 * otherwise uninterpreted and can represent anything
	 * you like.
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public final void acquireSharedInterruptibly(int arg)
			throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		if (tryAcquireShared(arg) < 0)
			doAcquireSharedInterruptibly(arg);
	}

	/**
	 * Attempts to acquire in shared mode, aborting if interrupted, and
	 * failing if the given timeout elapses.  Implemented by first
	 * checking interrupt status, then invoking at least once {@link
	 * #tryAcquireShared}, returning on success.  Otherwise, the
	 * thread is queued, possibly repeatedly blocking and unblocking,
	 * invoking {@link #tryAcquireShared} until success or the thread
	 * is interrupted or the timeout elapses.
	 *
	 * @param arg the acquire argument.  This value is conveyed to
	 *        {@link #tryAcquireShared} but is otherwise uninterpreted
	 *        and can represent anything you like.
	 * @param nanosTimeout the maximum number of nanoseconds to wait
	 * @return {@code true} if acquired; {@code false} if timed out
	 * @throws InterruptedException if the current thread is interrupted
	 */
	public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
			throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		return tryAcquireShared(arg) >= 0 ||
				doAcquireSharedNanos(arg, nanosTimeout);
	}

	/**
	 * Releases in shared mode.  Implemented by unblocking one or more
	 * threads if {@link #tryReleaseShared} returns true.
	 *
	 * @param arg the release argument.  This value is conveyed to
	 *        {@link #tryReleaseShared} but is otherwise uninterpreted
	 *        and can represent anything you like.
	 * @return the value returned from {@link #tryReleaseShared}
	 */
	public final boolean releaseShared(int arg) {
		if (tryReleaseShared(arg)) {
			doReleaseShared();
			return true;
		}
		return false;
	}

	// Queue inspection methods

	/**
	 * Queries whether any threads are waiting to acquire. Note that
	 * because cancellations due to interrupts and timeouts may occur
	 * at any time, a {@code true} return does not guarantee that any
	 * other thread will ever acquire.
	 *
	 * <p>In this implementation, this operation returns in
	 * constant time.
	 *
	 * @return {@code true} if there may be other threads waiting to acquire
	 */
	public final boolean hasQueuedThreads() {
		return head != tail;
	}

	/**
	 * Queries whether any threads have ever contended to acquire this
	 * synchronizer; that is if an acquire method has ever blocked.
	 *
	 * <p>In this implementation, this operation returns in
	 * constant time.
	 *
	 * @return {@code true} if there has ever been contention
	 */
	public final boolean hasContended() {
		return head != null;
	}

	/**
	 * Returns the first (longest-waiting) thread in the queue, or
	 * {@code null} if no threads are currently queued.
	 *
	 * <p>In this implementation, this operation normally returns in
	 * constant time, but may iterate upon contention if other threads are
	 * concurrently modifying the queue.
	 *
	 * @return the first (longest-waiting) thread in the queue, or
	 *         {@code null} if no threads are currently queued
	 */
	public final Thread getFirstQueuedThread() {
		// handle only fast path, else relay
		return (head == tail) ? null : fullGetFirstQueuedThread();
	}

	/**
	 * Version of getFirstQueuedThread called when fastpath fails
	 */
	private Thread fullGetFirstQueuedThread() {
		/*
		 * The first node is normally head.next. Try to get its
		 * thread field, ensuring consistent reads: If thread
		 * field is nulled out or s.prev is no longer head, then
		 * some other thread(s) concurrently performed setHead in
		 * between some of our reads. We try this twice before
		 * resorting to traversal.
		 */
		Node h, s;
		Thread st;
		if (((h = head) != null && (s = h.next) != null &&
				s.prev == head && (st = s.thread) != null) ||
				((h = head) != null && (s = h.next) != null &&
						s.prev == head && (st = s.thread) != null))
			return st;

		/*
		 * Head's next field might not have been set yet, or may have
		 * been unset after setHead. So we must check to see if tail
		 * is actually first node. If not, we continue on, safely
		 * traversing from tail back to head to find first,
		 * guaranteeing termination.
		 */

		Node t = tail;
		Thread firstThread = null;
		while (t != null && t != head) {
			Thread tt = t.thread;
			if (tt != null)
				firstThread = tt;
			t = t.prev;
		}
		return firstThread;
	}

	/**
	 * Returns true if the given thread is currently queued.
	 *
	 * <p>This implementation traverses the queue to determine
	 * presence of the given thread.
	 *
	 * @param thread the thread
	 * @return {@code true} if the given thread is on the queue
	 * @throws NullPointerException if the thread is null
	 */
	public final boolean isQueued(Thread thread) {
		if (thread == null)
			throw new NullPointerException();
		for (Node p = tail; p != null; p = p.prev)
			if (p.thread == thread)
				return true;
		return false;
	}

	/**
	 * Returns {@code true} if the apparent first queued thread, if one
	 * exists, is waiting in exclusive mode.  If this method returns
	 * {@code true}, and the current thread is attempting to acquire in
	 * shared mode (that is, this method is invoked from {@link
	 * #tryAcquireShared}) then it is guaranteed that the current thread
	 * is not the first queued thread.  Used only as a heuristic in
	 * ReentrantReadWriteLock.
	 */
	final boolean apparentlyFirstQueuedIsExclusive() {
		Node h, s;
		return (h = head) != null &&
				(s = h.next)  != null &&
				!s.isShared()         &&
				s.thread != null;
	}

	/**
	 * Queries whether any threads have been waiting to acquire longer
	 * than the current thread.
	 *
	 * <p>An invocation of this method is equivalent to (but may be
	 * more efficient than):
	 *  <pre> {@code
	 * getFirstQueuedThread() != Thread.currentThread() &&
	 * hasQueuedThreads()}</pre>
	 *
	 * <p>Note that because cancellations due to interrupts and
	 * timeouts may occur at any time, a {@code true} return does not
	 * guarantee that some other thread will acquire before the current
	 * thread.  Likewise, it is possible for another thread to win a
	 * race to enqueue after this method has returned {@code false},
	 * due to the queue being empty.
	 *
	 * <p>This method is designed to be used by a fair synchronizer to
	 * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
	 * Such a synchronizer's {@link #tryAcquire} method should return
	 * {@code false}, and its {@link #tryAcquireShared} method should
	 * return a negative value, if this method returns {@code true}
	 * (unless this is a reentrant acquire).  For example, the {@code
	 * tryAcquire} method for a fair, reentrant, exclusive mode
	 * synchronizer might look like this:
	 *
	 *  <pre> {@code
	 * protected boolean tryAcquire(int arg) {
	 *   if (isHeldExclusively()) {
	 *     // A reentrant acquire; increment hold count
	 *     return true;
	 *   } else if (hasQueuedPredecessors()) {
	 *     return false;
	 *   } else {
	 *     // try to acquire normally
	 *   }
	 * }}</pre>
	 *
	 * @return {@code true} if there is a queued thread preceding the
	 *         current thread, and {@code false} if the current thread
	 *         is at the head of the queue or the queue is empty
	 * @since 1.7
	 */
	public final boolean hasQueuedPredecessors() {
		Node t = tail;
		Node h = head;
		Node s;
		/**
		 * 下面提到的所有不需要排队，并不是字面意义，我实在想不出什么词语来描述这个“不需要排队”；不需要排队有两种情况
		 * 一：队列没有初始化，不需要排队，不需要排队，不需要排队；直接去加锁，但是可能会失败；为什么会失败呢？
		 * 假设两个线程同时来lock，都看到队列没有初始化，都认为不需要排队，都去进行CAS修改计数器；有一个必然失败
		 * 比如t1先拿到锁，那么另外一个t2则会CAS失败，这个时候t2就会去初始化队列，并排队
		 *
		 * 二：队列被初始化了，但是tc过来加锁，发觉队列当中第一个排队的就是自己；比如重入；
		 * 那么什么叫做第一个排队的呢？下面解释了，很重要往下看；
		 * 这个时候他也不需要排队，不需要排队，不需要排队；为什么不需要排队？
		 * 因为队列当中第一个排队的线程他会去尝试获取一下锁，因为有可能这个时候持有锁锁的那个线程可能释放了锁；
		 * 如果释放了就直接获取锁执行。但是如果没有释放他就会去排队，
		 * 所以这里的不需要排队，不是真的不需要排队
		 *
		 * h != t 判断首不等于尾这里要分三种情况
		 * 1、队列没有初始化，也就是第一个线程tf来加锁的时候那么这个时候队列没有初始化，
		 * h和t都是null，那么这个时候判断不等于则不成立（false）那么由于是&&运算后面的就不会走了，
		 * 直接返回false表示不需要排队，而前面又是取反（if (!hasQueuedPredecessors()），所以会直接去cas加锁。
		 * ----------第一种情况总结：队列没有初始化没人排队，那么我直接不排队，直接上锁；合情合理、有理有据令人信服；
		 * 好比你去小医院看病,到科室门前,如果没人排队,直接推门进去,如果房间没有病人则加锁成功,开始看病;
		 * 如果已有病人,则退到门外,开始排队
		 *
		 * 2、队列被初始化了，后面会分析队列初始化的流程，如果队列被初始化那么h!=t则成立；（不绝对，还有第3种情况）
		 * h != t 返回true；但是由于是&&运算，故而代码还需要进行后续的判断
		 * （有人可能会疑问，比如队列初始化了；里面只有一个数据，那么头和尾都是同一个怎么会成立呢？
		 * 其实这是第3种情况--队头等于队尾；但是这里先不考虑，我们假设现在队列里面有大于1个数据）
		 * 大于1个数据则成立;继续判断把h.next赋值给s；s有是队头的下一个Node，
		 * 这个时候s则表示他是队列当中参与排队的线程而且是排在最前面的；
		 * 为什么是s最前面不是h嘛？诚然h是队列里面的第一个，但是不是排队的第一个；下文有详细解释
		 * 因为h也就是队头对应的Node对象或者线程他是持有锁的，但是不参与排队；
		 * 这个很好理解，比如你去买车票，你如果是第一个这个时候售票员已经在给你服务了，你不算排队，你后面的才算排队；
		 * 队列里面的h是不参与排队的这点一定要明白；参考下面关于队列初始化的解释；
		 * 因为h要么是虚拟出来的节点，要么是持有锁的节点；什么时候是虚拟的呢？什么时候是持有锁的节点呢？下文分析
		 * 然后判断s是否等于空，其实就是判断队列里面是否只有一个数据；
		 * 假设队列大于1个，那么肯定不成立（s==null---->false），因为大于一个Node的时候h.next肯定不为空；
		 * 由于是||运算如果返回false，还要判断s.thread != Thread.currentThread()；这里又分为两种情况
		 *        2.1 s.thread != Thread.currentThread() 返回true，就是当前线程不等于在排队的第一个线程s；
		 *              那么这个时候整体结果就是h!=t：true; （s==null false || s.thread != Thread.currentThread() true  最后true）
		 *              结果： true && true 方法最终放回true，所以需要去排队
		 *              其实这样符合情理，试想一下买火车票，队列不为空，有人在排队；
		 *              而且第一个排队的人和现在来参与竞争的人不是同一个，那么你就乖乖去排队
		 *        2.2 s.thread != Thread.currentThread() 返回false 表示当前来参与竞争锁的线程和第一个排队的线程是同一个线程
		 *             这个时候整体结果就是h!=t---->true; （s==null false || s.thread != Thread.currentThread() false-----> 最后false）
		 *            结果：true && false 方法最终放回false，所以不需要去排队
		 *            不需要排队则调用 compareAndSetState(0, acquires) 去改变计数器尝试上锁；
		 *            这里又分为两种情况（日了狗了这一行代码；有同学课后反应说子路老师老师老是说这个AQS难，
		 *            你现在仔细看看这一行代码的意义，真的不简单的）
		 *             2.2.1  第一种情况加锁成功？有人会问为什么会成功啊，如这个时候h也就是持有锁的那个线程执行完了
		 *                      释放锁了，那么肯定成功啊；成功则执行 setExclusiveOwnerThread(current); 然后返回true 自己看代码
		 *             2.2.2  第二种情况加锁失败？有人会问为什么会失败啊。假如这个时候h也就是持有锁的那个线程没执行完
		 *                       没释放锁，那么肯定失败啊；失败则直接返回false，不会进else if（else if是相对于 if (c == 0)的）
		 *                      那么如果失败怎么办呢？后面分析；
		 *
		 *----------第二种情况总结，如果队列被初始化了，而且至少有一个人在排队那么自己也去排队；但是有个插曲；
		 * ----------他会去看看那个第一个排队的人是不是自己，如果是自己那么他就去尝试加锁；尝试看看锁有没有释放
		 *----------也合情合理，好比你去买票，如果有人排队，那么你乖乖排队，但是你会去看第一个排队的人是不是你女朋友；
		 *----------如果是你女朋友就相当于是你自己（这里实在想不出现实世界关于重入的例子，只能用男女朋友来替代）；
		 * --------- 你就叫你女朋友看看售票员有没有搞完，有没有轮到你女朋友，因为你女朋友是第一个排队的
		 * 疑问：比如如果在在排队，那么他是park状态，如果是park状态，自己怎么还可能重入啊。
		 * 希望有同学可以想出来为什么和我讨论一下，作为一个菜逼，希望有人教教我
		 *
		 *
		 * 3、队列被初始化了，但是里面只有一个数据；什么情况下才会出现这种情况呢？ts加锁的时候里面就只有一个数据？
		 * 其实不是，因为队列初始化的时候会虚拟一个h作为头结点，tc=ts作为第一个排队的节点；tf为持有锁的节点
		 * 为什么这么做呢？因为AQS认为h永远是不排队的，假设你不虚拟节点出来那么ts就是h，
		 *  而ts其实需要排队的，因为这个时候tf可能没有执行完，还持有着锁，ts得不到锁，故而他需要排队；
		 * 那么为什么要虚拟为什么ts不直接排在tf之后呢，上面已经时说明白了，tf来上锁的时候队列都没有，他不进队列，
		 * 故而ts无法排在tf之后，只能虚拟一个thread=null的节点出来（Node对象当中的thread为null）；
		 * 那么问题来了；究竟什么时候会出现队列当中只有一个数据呢？假设原队列里面有5个人在排队，当前面4个都执行完了
		 * 轮到第五个线程得到锁的时候；他会把自己设置成为头部，而尾部又没有，故而队列当中只有一个h就是第五个
		 * 至于为什么需要把自己设置成头部；其实已经解释了，因为这个时候五个线程已经不排队了，他拿到锁了；
		 * 所以他不参与排队，故而需要设置成为h；即头部；所以这个时间内，队列当中只有一个节点
		 * 关于加锁成功后把自己设置成为头部的源码，后面会解析到；继续第三种情况的代码分析
		 * 记得这个时候队列已经初始化了，但是只有一个数据，并且这个数据所代表的线程是持有锁
		 * h != t false 由于后面是&&运算，故而返回false可以不参与运算，整个方法返回false；不需要排队
		 *
		 *
		 *-------------第三种情况总结：如果队列当中只有一个节点，而这种情况我们分析了，
		 *-------------这个节点就是当前持有锁的那个节点，故而我不需要排队，进行cas；尝试加锁
		 *-------------这是AQS的设计原理，他会判断你入队之前，队列里面有没有人排队；
		 *-------------有没有人排队分两种情况；队列没有初始化，不需要排队
		 *--------------队列初始化了，按时只有一个节点，也是没人排队，自己先也不排队
		 *--------------只要认定自己不需要排队，则先尝试加锁；加锁失败之后再排队；
		 *--------------再一次解释了不需要排队这个词的歧义性
		 *-------------如果加锁失败了，在去park，下文有详细解释这样设计源码和原因
		 *-------------如果持有锁的线程释放了锁，那么我能成功上锁
		 *
		 **/
		return h != t &&
				((s = h.next) == null || s.thread != Thread.currentThread());
	}


	// Instrumentation and monitoring methods

	/**
	 * Returns an estimate of the number of threads waiting to
	 * acquire.  The value is only an estimate because the number of
	 * threads may change dynamically while this method traverses
	 * internal data structures.  This method is designed for use in
	 * monitoring system state, not for synchronization
	 * control.
	 *
	 * @return the estimated number of threads waiting to acquire
	 */
	public final int getQueueLength() {
		int n = 0;
		for (Node p = tail; p != null; p = p.prev) {
			if (p.thread != null)
				++n;
		}
		return n;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire.  Because the actual set of threads may change
	 * dynamically while constructing this result, the returned
	 * collection is only a best-effort estimate.  The elements of the
	 * returned collection are in no particular order.  This method is
	 * designed to facilitate construction of subclasses that provide
	 * more extensive monitoring facilities.
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			Thread t = p.thread;
			if (t != null)
				list.add(t);
		}
		return list;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire in exclusive mode. This has the same properties
	 * as {@link #getQueuedThreads} except that it only returns
	 * those threads waiting due to an exclusive acquire.
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getExclusiveQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			if (!p.isShared()) {
				Thread t = p.thread;
				if (t != null)
					list.add(t);
			}
		}
		return list;
	}

	/**
	 * Returns a collection containing threads that may be waiting to
	 * acquire in shared mode. This has the same properties
	 * as {@link #getQueuedThreads} except that it only returns
	 * those threads waiting due to a shared acquire.
	 *
	 * @return the collection of threads
	 */
	public final Collection<Thread> getSharedQueuedThreads() {
		ArrayList<Thread> list = new ArrayList<Thread>();
		for (Node p = tail; p != null; p = p.prev) {
			if (p.isShared()) {
				Thread t = p.thread;
				if (t != null)
					list.add(t);
			}
		}
		return list;
	}

	/**
	 * Returns a string identifying this synchronizer, as well as its state.
	 * The state, in brackets, includes the String {@code "State ="}
	 * followed by the current value of {@link #getState}, and either
	 * {@code "nonempty"} or {@code "empty"} depending on whether the
	 * queue is empty.
	 *
	 * @return a string identifying this synchronizer, as well as its state
	 */
	public String toString() {
		int s = getState();
		String q  = hasQueuedThreads() ? "non" : "";
		return super.toString() +
				"[State = " + s + ", " + q + "empty queue]";
	}


	// Internal support methods for Conditions

	/**
	 * Returns true if a node, always one that was initially placed on
	 * a condition queue, is now waiting to reacquire on sync queue.
	 * @param node the node
	 * @return true if is reacquiring
	 */
	final boolean isOnSyncQueue(Node node) {
		if (node.waitStatus == Node.CONDITION || node.prev == null)
			return false;
		if (node.next != null) // If has successor, it must be on queue
			return true;
		/*
		 * node.prev can be non-null, but not yet on queue because
		 * the CAS to place it on queue can fail. So we have to
		 * traverse from tail to make sure it actually made it.  It
		 * will always be near the tail in calls to this method, and
		 * unless the CAS failed (which is unlikely), it will be
		 * there, so we hardly ever traverse much.
		 */
		return findNodeFromTail(node);
	}

	/**
	 * Returns true if node is on sync queue by searching backwards from tail.
	 * Called only when needed by isOnSyncQueue.
	 * @return true if present
	 */
	private boolean findNodeFromTail(Node node) {
		Node t = tail;
		for (;;) {
			if (t == node)
				return true;
			if (t == null)
				return false;
			t = t.prev;
		}
	}

	/**
	 * Transfers a node from a condition queue onto sync queue.
	 * Returns true if successful.
	 * @param node the node
	 * @return true if successfully transferred (else the node was
	 * cancelled before signal)
	 */
	final boolean transferForSignal(Node node) {
		/*
		 * If cannot change waitStatus, the node has been cancelled.
		 */
		if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
			return false;

		/*
		 * Splice onto queue and try to set waitStatus of predecessor to
		 * indicate that thread is (probably) waiting. If cancelled or
		 * attempt to set waitStatus fails, wake up to resync (in which
		 * case the waitStatus can be transiently and harmlessly wrong).
		 */
		Node p = enq(node);
		int ws = p.waitStatus;
		if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
			LockSupport.unpark(node.thread);
		return true;
	}

	/**
	 * Transfers node, if necessary, to sync queue after a cancelled wait.
	 * Returns true if thread was cancelled before being signalled.
	 *
	 * @param node the node
	 * @return true if cancelled before the node was signalled
	 */
	final boolean transferAfterCancelledWait(Node node) {
		if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
			enq(node);
			return true;
		}
		/*
		 * If we lost out to a signal(), then we can't proceed
		 * until it finishes its enq().  Cancelling during an
		 * incomplete transfer is both rare and transient, so just
		 * spin.
		 */
		while (!isOnSyncQueue(node))
			Thread.yield();
		return false;
	}

	/**
	 * Invokes release with current state value; returns saved state.
	 * Cancels node and throws exception on failure.
	 * @param node the condition node for this wait
	 * @return previous sync state
	 */
	final int fullyRelease(Node node) {
		boolean failed = true;
		try {
			int savedState = getState();
			if (release(savedState)) {
				failed = false;
				return savedState;
			} else {
				throw new IllegalMonitorStateException();
			}
		} finally {
			if (failed)
				node.waitStatus = Node.CANCELLED;
		}
	}

	// Instrumentation methods for conditions

	/**
	 * Queries whether the given ConditionObject
	 * uses this synchronizer as its lock.
	 *
	 * @param condition the condition
	 * @return {@code true} if owned
	 * @throws NullPointerException if the condition is null
	 */
	public final boolean owns(ConditionObject condition) {
		return condition.isOwnedBy(this);
	}

	/**
	 * Queries whether any threads are waiting on the given condition
	 * associated with this synchronizer. Note that because timeouts
	 * and interrupts may occur at any time, a {@code true} return
	 * does not guarantee that a future {@code signal} will awaken
	 * any threads.  This method is designed primarily for use in
	 * monitoring of the system state.
	 *
	 * @param condition the condition
	 * @return {@code true} if there are any waiting threads
	 * @throws IllegalMonitorStateException if exclusive synchronization
	 *         is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this synchronizer
	 * @throws NullPointerException if the condition is null
	 */
	public final boolean hasWaiters(ConditionObject condition) {
		if (!owns(condition))
			throw new IllegalArgumentException("Not owner");
		return condition.hasWaiters();
	}

	/**
	 * Returns an estimate of the number of threads waiting on the
	 * given condition associated with this synchronizer. Note that
	 * because timeouts and interrupts may occur at any time, the
	 * estimate serves only as an upper bound on the actual number of
	 * waiters.  This method is designed for use in monitoring of the
	 * system state, not for synchronization control.
	 *
	 * @param condition the condition
	 * @return the estimated number of waiting threads
	 * @throws IllegalMonitorStateException if exclusive synchronization
	 *         is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this synchronizer
	 * @throws NullPointerException if the condition is null
	 */
	public final int getWaitQueueLength(ConditionObject condition) {
		if (!owns(condition))
			throw new IllegalArgumentException("Not owner");
		return condition.getWaitQueueLength();
	}

	/**
	 * Returns a collection containing those threads that may be
	 * waiting on the given condition associated with this
	 * synchronizer.  Because the actual set of threads may change
	 * dynamically while constructing this result, the returned
	 * collection is only a best-effort estimate. The elements of the
	 * returned collection are in no particular order.
	 *
	 * @param condition the condition
	 * @return the collection of threads
	 * @throws IllegalMonitorStateException if exclusive synchronization
	 *         is not held
	 * @throws IllegalArgumentException if the given condition is
	 *         not associated with this synchronizer
	 * @throws NullPointerException if the condition is null
	 */
	public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
		if (!owns(condition))
			throw new IllegalArgumentException("Not owner");
		return condition.getWaitingThreads();
	}

	/**
	 * Condition implementation for a {@link
	 * java.util.concurrent.locks.AbstractQueuedSynchronizer} serving as the basis of a {@link
	 * Lock} implementation.
	 *
	 * <p>Method documentation for this class describes mechanics,
	 * not behavioral specifications from the point of view of Lock
	 * and Condition users. Exported versions of this class will in
	 * general need to be accompanied by documentation describing
	 * condition semantics that rely on those of the associated
	 * {@code AbstractQueuedSynchronizer}.
	 *
	 * <p>This class is Serializable, but all fields are transient,
	 * so deserialized conditions have no waiters.
	 */
	public class ConditionObject implements Condition, java.io.Serializable {
		private static final long serialVersionUID = 1173984872572414699L;
		/** First node of condition queue. */
		private transient Node firstWaiter;
		/** Last node of condition queue. */
		private transient Node lastWaiter;

		/**
		 * Creates a new {@code ConditionObject} instance.
		 */
		public ConditionObject() { }

		// Internal methods

		/**
		 * Adds a new waiter to wait queue.
		 * @return its new wait node
		 */
		private Node addConditionWaiter() {
			Node t = lastWaiter;
			// If lastWaiter is cancelled, clean out.
			if (t != null && t.waitStatus != Node.CONDITION) {
				unlinkCancelledWaiters();
				t = lastWaiter;
			}
			Node node = new Node(Thread.currentThread(), Node.CONDITION);
			if (t == null)
				firstWaiter = node;
			else
				t.nextWaiter = node;
			lastWaiter = node;
			return node;
		}

		/**
		 * Removes and transfers nodes until hit non-cancelled one or
		 * null. Split out from signal in part to encourage compilers
		 * to inline the case of no waiters.
		 * @param first (non-null) the first node on condition queue
		 */
		private void doSignal(Node first) {
			do {
				if ( (firstWaiter = first.nextWaiter) == null)
					lastWaiter = null;
				first.nextWaiter = null;
			} while (!transferForSignal(first) &&
					(first = firstWaiter) != null);
		}

		/**
		 * Removes and transfers all nodes.
		 * @param first (non-null) the first node on condition queue
		 */
		private void doSignalAll(Node first) {
			lastWaiter = firstWaiter = null;
			do {
				Node next = first.nextWaiter;
				first.nextWaiter = null;
				transferForSignal(first);
				first = next;
			} while (first != null);
		}

		/**
		 * Unlinks cancelled waiter nodes from condition queue.
		 * Called only while holding lock. This is called when
		 * cancellation occurred during condition wait, and upon
		 * insertion of a new waiter when lastWaiter is seen to have
		 * been cancelled. This method is needed to avoid garbage
		 * retention in the absence of signals. So even though it may
		 * require a full traversal, it comes into play only when
		 * timeouts or cancellations occur in the absence of
		 * signals. It traverses all nodes rather than stopping at a
		 * particular target to unlink all pointers to garbage nodes
		 * without requiring many re-traversals during cancellation
		 * storms.
		 */
		private void unlinkCancelledWaiters() {
			Node t = firstWaiter;
			Node trail = null;
			while (t != null) {
				Node next = t.nextWaiter;
				if (t.waitStatus != Node.CONDITION) {
					t.nextWaiter = null;
					if (trail == null)
						firstWaiter = next;
					else
						trail.nextWaiter = next;
					if (next == null)
						lastWaiter = trail;
				}
				else
					trail = t;
				t = next;
			}
		}

		// public methods

		/**
		 * Moves the longest-waiting thread, if one exists, from the
		 * wait queue for this condition to the wait queue for the
		 * owning lock.
		 *
		 * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
		 *         returns {@code false}
		 */
		public final void signal() {
			if (!isHeldExclusively())
				throw new IllegalMonitorStateException();
			Node first = firstWaiter;
			if (first != null)
				doSignal(first);
		}

		/**
		 * Moves all threads from the wait queue for this condition to
		 * the wait queue for the owning lock.
		 *
		 * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
		 *         returns {@code false}
		 */
		public final void signalAll() {
			if (!isHeldExclusively())
				throw new IllegalMonitorStateException();
			Node first = firstWaiter;
			if (first != null)
				doSignalAll(first);
		}

		/**
		 * Implements uninterruptible condition wait.
		 * <ol>
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 *      throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled.
		 * <li> Reacquire by invoking specialized version of
		 *      {@link #acquire} with saved state as argument.
		 * </ol>
		 */
		public final void awaitUninterruptibly() {
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			boolean interrupted = false;
			while (!isOnSyncQueue(node)) {
				LockSupport.park(this);
				if (Thread.interrupted())
					interrupted = true;
			}
			if (acquireQueued(node, savedState) || interrupted)
				selfInterrupt();
		}

		/*
		 * For interruptible waits, we need to track whether to throw
		 * InterruptedException, if interrupted while blocked on
		 * condition, versus reinterrupt current thread, if
		 * interrupted while blocked waiting to re-acquire.
		 */

		/** Mode meaning to reinterrupt on exit from wait */
		private static final int REINTERRUPT =  1;
		/** Mode meaning to throw InterruptedException on exit from wait */
		private static final int THROW_IE    = -1;

		/**
		 * Checks for interrupt, returning THROW_IE if interrupted
		 * before signalled, REINTERRUPT if after signalled, or
		 * 0 if not interrupted.
		 */
		private int checkInterruptWhileWaiting(Node node) {
			return Thread.interrupted() ?
					(transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
					0;
		}

		/**
		 * Throws InterruptedException, reinterrupts current thread, or
		 * does nothing, depending on mode.
		 */
		private void reportInterruptAfterWait(int interruptMode)
				throws InterruptedException {
			if (interruptMode == THROW_IE)
				throw new InterruptedException();
			else if (interruptMode == REINTERRUPT)
				selfInterrupt();
		}

		/**
		 * Implements interruptible condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 *      throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled or interrupted.
		 * <li> Reacquire by invoking specialized version of
		 *      {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * </ol>
		 */
		public final void await() throws InterruptedException {
			if (Thread.interrupted())
				throw new InterruptedException();
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				LockSupport.park(this);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
					break;
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
				interruptMode = REINTERRUPT;
			if (node.nextWaiter != null) // clean up if cancelled
				unlinkCancelledWaiters();
			if (interruptMode != 0)
				reportInterruptAfterWait(interruptMode);
		}

		/**
		 * Implements timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 *      throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 *      {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * </ol>
		 */
		public final long awaitNanos(long nanosTimeout)
				throws InterruptedException {
			if (Thread.interrupted())
				throw new InterruptedException();
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			final long deadline = System.nanoTime() + nanosTimeout;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					transferAfterCancelledWait(node);
					break;
				}
				if (nanosTimeout >= spinForTimeoutThreshold)
					LockSupport.parkNanos(this, nanosTimeout);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
					break;
				nanosTimeout = deadline - System.nanoTime();
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
				interruptMode = REINTERRUPT;
			if (node.nextWaiter != null)
				unlinkCancelledWaiters();
			if (interruptMode != 0)
				reportInterruptAfterWait(interruptMode);
			return deadline - System.nanoTime();
		}

		/**
		 * Implements absolute timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 *      throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 *      {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * <li> If timed out while blocked in step 4, return false, else true.
		 * </ol>
		 */
		public final boolean awaitUntil(Date deadline)
				throws InterruptedException {
			long abstime = deadline.getTime();
			if (Thread.interrupted())
				throw new InterruptedException();
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			boolean timedout = false;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (System.currentTimeMillis() > abstime) {
					timedout = transferAfterCancelledWait(node);
					break;
				}
				LockSupport.parkUntil(this, abstime);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
					break;
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
				interruptMode = REINTERRUPT;
			if (node.nextWaiter != null)
				unlinkCancelledWaiters();
			if (interruptMode != 0)
				reportInterruptAfterWait(interruptMode);
			return !timedout;
		}

		/**
		 * Implements timed condition wait.
		 * <ol>
		 * <li> If current thread is interrupted, throw InterruptedException.
		 * <li> Save lock state returned by {@link #getState}.
		 * <li> Invoke {@link #release} with saved state as argument,
		 *      throwing IllegalMonitorStateException if it fails.
		 * <li> Block until signalled, interrupted, or timed out.
		 * <li> Reacquire by invoking specialized version of
		 *      {@link #acquire} with saved state as argument.
		 * <li> If interrupted while blocked in step 4, throw InterruptedException.
		 * <li> If timed out while blocked in step 4, return false, else true.
		 * </ol>
		 */
		public final boolean await(long time, TimeUnit unit)
				throws InterruptedException {
			long nanosTimeout = unit.toNanos(time);
			if (Thread.interrupted())
				throw new InterruptedException();
			Node node = addConditionWaiter();
			int savedState = fullyRelease(node);
			final long deadline = System.nanoTime() + nanosTimeout;
			boolean timedout = false;
			int interruptMode = 0;
			while (!isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					timedout = transferAfterCancelledWait(node);
					break;
				}
				if (nanosTimeout >= spinForTimeoutThreshold)
					LockSupport.parkNanos(this, nanosTimeout);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
					break;
				nanosTimeout = deadline - System.nanoTime();
			}
			if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
				interruptMode = REINTERRUPT;
			if (node.nextWaiter != null)
				unlinkCancelledWaiters();
			if (interruptMode != 0)
				reportInterruptAfterWait(interruptMode);
			return !timedout;
		}

		//  support for instrumentation

		/**
		 * Returns true if this condition was created by the given
		 * synchronization object.
		 *
		 * @return {@code true} if owned
		 */
		final boolean isOwnedBy(NyaAbstractQueuedSynchronized sync) {
			return sync == NyaAbstractQueuedSynchronized.this;
		}

		/**
		 * Queries whether any threads are waiting on this condition.
		 * Implements {java.util.concurrent.locks.AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
		 *
		 * @return {@code true} if there are any waiting threads
		 * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
		 *         returns {@code false}
		 */
		protected final boolean hasWaiters() {
			if (!isHeldExclusively())
				throw new IllegalMonitorStateException();
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION)
					return true;
			}
			return false;
		}

		/**
		 * Returns an estimate of the number of threads waiting on
		 * this condition.
		 * Implements {java.util.concurrent.locks.AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
		 *
		 * @return the estimated number of waiting threads
		 * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
		 *         returns {@code false}
		 */
		protected final int getWaitQueueLength() {
			if (!isHeldExclusively())
				throw new IllegalMonitorStateException();
			int n = 0;
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION)
					++n;
			}
			return n;
		}

		/**
		 * Returns a collection containing those threads that may be
		 * waiting on this Condition.
		 * Implements {java.util.concurrent.locks.AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
		 *
		 * @return the collection of threads
		 * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
		 *         returns {@code false}
		 */
		protected final Collection<Thread> getWaitingThreads() {
			if (!isHeldExclusively())
				throw new IllegalMonitorStateException();
			ArrayList<Thread> list = new ArrayList<Thread>();
			for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus == Node.CONDITION) {
					Thread t = w.thread;
					if (t != null)
						list.add(t);
				}
			}
			return list;
		}
	}

	/**
	 * Setup to support compareAndSet. We need to natively implement
	 * this here: For the sake of permitting future enhancements, we
	 * cannot explicitly subclass AtomicInteger, which would be
	 * efficient and useful otherwise. So, as the lesser of evils, we
	 * natively implement using hotspot intrinsics API. And while we
	 * are at it, we do the same for other CASable fields (which could
	 * otherwise be done with atomic field updaters).
	 */
	private static final Unsafe unsafe = Unsafe.getUnsafe();
	private static final long stateOffset;
	private static final long headOffset;
	private static final long tailOffset;
	private static final long waitStatusOffset;
	private static final long nextOffset;

	static {
		try {
			stateOffset = unsafe.objectFieldOffset
					(NyaAbstractQueuedSynchronized.class.getDeclaredField("state"));
			headOffset = unsafe.objectFieldOffset
					(NyaAbstractQueuedSynchronized.class.getDeclaredField("head"));
			tailOffset = unsafe.objectFieldOffset
					(NyaAbstractQueuedSynchronized.class.getDeclaredField("tail"));
			waitStatusOffset = unsafe.objectFieldOffset
					(Node.class.getDeclaredField("waitStatus"));
			nextOffset = unsafe.objectFieldOffset
					(Node.class.getDeclaredField("next"));

		} catch (Exception ex) { throw new Error(ex); }
	}

	/**
	 * CAS head field. Used only by enq.
	 */
	private final boolean compareAndSetHead(Node update) {
		return unsafe.compareAndSwapObject(this, headOffset, null, update);
	}

	/**
	 * CAS tail field. Used only by enq.
	 */
	private final boolean compareAndSetTail(Node expect, Node update) {
		return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
	}

	/**
	 * CAS waitStatus field of a node.
	 */
	private static final boolean compareAndSetWaitStatus(Node node,
														 int expect,
														 int update) {
		return unsafe.compareAndSwapInt(node, waitStatusOffset,
				expect, update);
	}

	/**
	 * CAS next field of a node.
	 */
	private static final boolean compareAndSetNext(Node node,
												   Node expect,
												   Node update) {
		return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
	}
}

