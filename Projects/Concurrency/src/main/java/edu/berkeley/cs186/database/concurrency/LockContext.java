package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockContext wraps around LockManager to provide the hierarchical structure
 * of multigranularity locking. Calls to acquire/release/etc. locks should
 * be mostly done through a LockContext, which provides access to locking
 * methods at a certain point in the hierarchy (database, table X, etc.)
 */
public class LockContext {
    // You should not remove any of these fields. You may add additional
    // fields/methods as you see fit.

    // The underlying lock manager.
    protected final LockManager lockman;

    // The parent LockContext object, or null if this LockContext is at the top of the hierarchy.
    protected final LockContext parent;

    // The name of the resource this LockContext represents.
    protected ResourceName name;

    // Whether this LockContext is readonly. If a LockContext is readonly, acquire/release/promote/escalate should
    // throw an UnsupportedOperationException.
    protected boolean readonly;

    // A mapping between transaction numbers, and the number of locks on children of this LockContext
    // that the transaction holds.
    protected final Map<Long, Integer> numChildLocks;

    // You should not modify or use this directly.
    protected final Map<String, LockContext> children;

    // Whether or not any new child LockContexts should be marked readonly.
    protected boolean childLocksDisabled;

    public LockContext(LockManager lockman, LockContext parent, String name) {
        this(lockman, parent, name, false);
    }

    protected LockContext(LockManager lockman, LockContext parent, String name,
                          boolean readonly) {
        this.lockman = lockman;
        this.parent = parent;
        if (parent == null) {
            this.name = new ResourceName(name);
        } else {
            this.name = new ResourceName(parent.getResourceName(), name);
        }
        this.readonly = readonly;
        this.numChildLocks = new ConcurrentHashMap<>();
        this.children = new ConcurrentHashMap<>();
        this.childLocksDisabled = readonly;
    }

    /**
     * Gets a lock context corresponding to `name` from a lock manager.
     */
    public static LockContext fromResourceName(LockManager lockman, ResourceName name) {
        Iterator<String> names = name.getNames().iterator();
        LockContext ctx;
        String n1 = names.next();
        ctx = lockman.context(n1);
        while (names.hasNext()) {
            String n = names.next();
            ctx = ctx.childContext(n);
        }
        return ctx;
    }

    /**
     * Get the name of the resource that this lock context pertains to.
     */
    public ResourceName getResourceName() {
        return name;
    }

    /**
     * Acquire a `lockType` lock, for transaction `transaction`.
     *
     * Note: you must make any necessary updates to numChildLocks, or else calls
     * to LockContext#getNumChildren will not work properly.
     *
     * @throws InvalidLockException if the request is invalid
     * @throws DuplicateLockRequestException if a lock is already held by the
     * transaction.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void acquire(TransactionContext transaction, LockType lockType)
            throws InvalidLockException, DuplicateLockRequestException {
        // TODO(proj4_part2): implement
        if (readonly) {
            throw new UnsupportedOperationException("Context is read only");
        }
        if (this.parent == null) {
            lockman.acquire(transaction, name, lockType);
        } else {
            boolean found = false;
            LockContext parent = this.parent;
            List<Lock> allLocks = parent.lockman.getLocks(parent.getResourceName());
            for (Lock lck : allLocks){
                if (!LockType.canBeParentLock(lck.lockType, lockType)
                        && lck.transactionNum == transaction.getTransNum()){
                    throw new InvalidLockException("Lock not compatible with parent");
                }
                if (lck.transactionNum == transaction.getTransNum()){
                    found = true;
                    break;
                }
            }
            if (!found){
                throw new InvalidLockException("No Parent Lock found");
            }
            lockman.acquire(transaction, name, lockType);
            parent.numChildLocks.put(transaction.getTransNum(), parent.getNumChildren(transaction) + 1);
        }

    }

    /**
     * Release `transaction`'s lock on `name`.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#getNumChildren will not work properly.
     *
     * @throws NoLockHeldException if no lock on `name` is held by `transaction`
     * @throws InvalidLockException if the lock cannot be released because
     * doing so would violate multigranularity locking constraints
     * @throws UnsupportedOperationException if context is readonly
     */
    public void release(TransactionContext transaction)
            throws NoLockHeldException, InvalidLockException {
        // TODO(proj4_part2): implement
        if (readonly) {
            throw new UnsupportedOperationException("unsupported operation bro");
        }
        if (this.getNumChildren(transaction) == 0 && this.parent == null){
            this.lockman.release(transaction, this.getResourceName());
        }
        else if (this.getNumChildren(transaction) == 0 && this.parent != null){
            this.lockman.release(transaction, this.getResourceName());
            this.parent.numChildLocks.put(transaction.getTransNum(), this.parent.getNumChildren(transaction) - 1);
        }
        else if (this.getNumChildren(transaction) != 0){
            throw new InvalidLockException("cant release, child has locks");
        }

    }

    /**
     * Promote `transaction`'s lock to `newLockType`. For promotion to SIX from
     * IS/IX, all S and IS locks on descendants must be simultaneously
     * released. The helper function sisDescendants may be helpful here.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or else
     * calls to LockContext#getNumChildren will not work properly.
     *
     * @throws DuplicateLockRequestException if `transaction` already has a
     * `newLockType` lock
     * @throws NoLockHeldException if `transaction` has no lock
     * @throws InvalidLockException if the requested lock type is not a
     * promotion or promoting would cause the lock manager to enter an invalid
     * state (e.g. IS(parent), X(child)). A promotion from lock type A to lock
     * type B is valid if B is substitutable for A and B is not equal to A, or
     * if B is SIX and A is IS/IX/S, and invalid otherwise. hasSIXAncestor may
     * be helpful here.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void promote(TransactionContext transaction, LockType newLockType)
            throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        // TODO(proj4_part2): implement
        if (readonly) {
            throw new UnsupportedOperationException("Read Only");
        }
        LockType curr = lockman.getLockType(transaction, name);
        if (curr == LockType.NL){
            throw new NoLockHeldException("No lock held");
        }
        if (curr == newLockType) {
            throw new DuplicateLockRequestException("Already have lock trying to promote to");
        }
        if (newLockType == LockType.SIX) {
            if (curr != LockType.IS && curr != LockType.IX && curr != LockType.S){
                throw new InvalidLockException("Six can only be promoted from IS , IX, or S");
            }
        } else {
            if (!LockType.substitutable(newLockType, curr)){
                throw new InvalidLockException("Locks Not substitutable, promotion not valid");
            }
        }
        if (parent == null){
            if (newLockType == LockType.SIX) {
                List<ResourceName> six = sisDescendants(transaction);
                if (six.size() > 0){
                    six.add(name);
                    lockman.acquireAndRelease(transaction, name, newLockType, six);
                    for (ResourceName b : six){
                        LockContext a = fromResourceName(lockman,b);
                        if (a.parent != null && b != name) {
                            a.parent.numChildLocks.put(transaction.getTransNum(), a.parent.getNumChildren(transaction) - 1);
                        }
                    }
                } else {
                    ArrayList<ResourceName> temp = new ArrayList<>();
                    temp.add(name);
                    lockman.acquireAndRelease(transaction, name, newLockType, temp);
                }
            } else {
                lockman.promote(transaction, name, newLockType);
            }
        } else {
            LockType partype = lockman.getLockType(transaction, parent.getResourceName());
            if (!LockType.canBeParentLock(partype, newLockType)){
                throw new InvalidLockException("Parent Lock cannot be parent of new lock");
            }
            if (newLockType == LockType.SIX){
                if (hasSIXAncestor(transaction)){
                    throw new InvalidLockException("Already Has six ancestor");
                } else {
                    List<ResourceName> six = sisDescendants(transaction);
                    if (six.size() > 0){
                        six.add(name);
                        lockman.acquireAndRelease(transaction, name, newLockType, six);
                        for (ResourceName b : six){
                            LockContext a = fromResourceName(lockman,b);
                            if (a.parent != null && b != name) {
                                a.parent.numChildLocks.put(transaction.getTransNum(), a.parent.getNumChildren(transaction) - 1);
                            }
                        }
                    } else {
                        ArrayList<ResourceName> temp = new ArrayList<>();
                        temp.add(name);
                        lockman.acquireAndRelease(transaction, name, newLockType, temp);
                    }
                }
            } else {
                lockman.promote(transaction, name, newLockType);
            }
        }
    }

    /**
     * Escalate `transaction`'s lock from descendants of this context to this
     * level, using either an S or X lock. There should be no descendant locks
     * after this call, and every operation valid on descendants of this context
     * before this call must still be valid. You should only make *one* mutating
     * call to the lock manager, and should only request information about
     * TRANSACTION from the lock manager.
     *
     * For example, if a transaction has the following locks:
     *
     *                    IX(database)
     *                    /         \
     *               IX(table1)    S(table2)
     *                /      \
     *    S(table1 page3)  X(table1 page5)
     *
     * then after table1Context.escalate(transaction) is called, we should have:
     *
     *                    IX(database)
     *                    /         \
     *               X(table1)     S(table2)
     *
     * You should not make any mutating calls if the locks held by the
     * transaction do not change (such as when you call escalate multiple times
     * in a row).
     *
     * Note: you *must* make any necessary updates to numChildLocks of all
     * relevant contexts, or else calls to LockContext#getNumChildren will not
     * work properly.
     *
     * @throws NoLockHeldException if `transaction` has no lock at this level
     * @throws UnsupportedOperationException if context is readonly
     */
    public void escalate(TransactionContext transaction) throws NoLockHeldException {
        // TODO(proj4_part2): implement
        if (readonly) {
            throw new UnsupportedOperationException("unsupported exception");
        }
        List<Lock> xactLocks = lockman.getLocks(transaction);

        if (xactLocks == null || xactLocks.size() == 0){
            throw new NoLockHeldException("no locks held exception");
        }
        if (lockman.getLockType(transaction, name) == LockType.NL){
            throw new NoLockHeldException("no locks held exception");
        }

        else{
            List<ResourceName> shared = new ArrayList<>();
            List<ResourceName> exclusive = new ArrayList<>();
            boolean no_change = false;
            for (Lock lck : xactLocks){
                if (lck.name.isDescendantOf(name)){
                    if (lck.lockType == LockType.S || lck.lockType == LockType.IS){
                        shared.add(lck.name);
                    } else if (lck.lockType == LockType.NL){
                        continue;
                    } else {
                        exclusive.add(lck.name);
                    }
                } else if (lck.name == name && (lck.lockType == LockType.S || lck.lockType == LockType.X)){
                    no_change = true;
                    break;
                } else if (lck.name == name && lck.lockType == LockType.IS){
                    shared.add(lck.name);
                } else if (lck.name == name && lck.lockType == LockType.NL){
                    continue;
                } else if (lck.name == name){
                    exclusive.add(lck.name);
                }
            }

            if (exclusive.size() > 0 && !no_change){
                shared.addAll(exclusive);
                for (ResourceName b : shared){
                    LockContext a = fromResourceName(lockman,b);
                    if (a.parent != null && b != name) {
                        a.parent.numChildLocks.put(transaction.getTransNum(), a.parent.getNumChildren(transaction) - 1);
                    }
                }
                this.lockman.acquireAndRelease(transaction, this.getResourceName(), LockType.X, shared);
            }
            else {
                if (!no_change) {
                    for (ResourceName b : shared) {
                        LockContext a = fromResourceName(lockman, b);
                        if (a.parent != null && b != name) {
                            a.parent.numChildLocks.put(transaction.getTransNum(), a.parent.getNumChildren(transaction) - 1);
                        }
                    }
                    this.lockman.acquireAndRelease(transaction, this.getResourceName(), LockType.S, shared);
                }
            }
        }
    }

    /**
     * Get the type of lock that `transaction` holds at this level, or NL if no
     * lock is held at this level.
     */
    public LockType getExplicitLockType(TransactionContext transaction) {
        if (transaction == null){
            return LockType.NL;
        } else {
          return lockman.getLockType(transaction, name);
        }
    }

    /**
     * Gets the type of lock that the transaction has at this level, either
     * implicitly (e.g. explicit S lock at higher level implies S lock at this
     * level) or explicitly. Returns NL if there is no explicit nor implicit
     * lock.
     */
    public LockType getEffectiveLockType(TransactionContext transaction) {
        if (transaction == null){
            return LockType.NL;
        } else {
            List<LockType> lockTypes = new ArrayList<>();
            lockTypes.add(lockman.getLockType(transaction, name));
            LockContext par = parent;
            while (par != null){
                lockTypes.add(lockman.getLockType(transaction, par.getResourceName()));
                par = par.parent;
            }
            if (lockTypes.contains(LockType.X)){
                return LockType.X;
            }
            if (lockTypes.contains(LockType.SIX) && getExplicitLockType(transaction) == LockType.IX){
                return LockType.SIX;
            }
            if (lockTypes.contains(LockType.SIX) && getExplicitLockType(transaction) == LockType.NL){
                return LockType.S;
            }
            if (lockTypes.contains(LockType.SIX) && getExplicitLockType(transaction) == LockType.SIX){
                return LockType.SIX;
            }
            if (lockTypes.contains(LockType.S)){
                return LockType.S;
            }
        }
        return lockman.getLockType(transaction, name);
    }

    /**
     * Helper method to see if the transaction holds a SIX lock at an ancestor
     * of this context
     * @param transaction the transaction
     * @return true if holds a SIX at an ancestor, false if not
     */
    private boolean hasSIXAncestor(TransactionContext transaction) {
        // TODO(proj4_part2): implement
        LockContext par = parent;
        while (par != null){
            LockType type = lockman.getLockType(transaction, par.getResourceName());
            if (type == LockType.SIX){
                return true;
            }
            par = par.parent;
        }
        return false;
    }

    /**
     * Helper method to get a list of resourceNames of all locks that are S or
     * IS and are descendants of current context for the given transaction.
     * @param transaction the given transaction
     * @return a list of ResourceNames of descendants which the transaction
     * holds an S or IS lock.
     */
    private List<ResourceName> sisDescendants(TransactionContext transaction) {
        // TODO(proj4_part2): implement
        List<ResourceName> answer = new ArrayList<>();
        List<Lock> xactLocks = lockman.getLocks(transaction);
        for (Lock lck : xactLocks){
            if (lck.name.isDescendantOf(name)){
                if (lck.lockType == LockType.S || lck.lockType == LockType.IS){
                    answer.add(lck.name);
                }
            }
        }
        return answer;
    }

    /**
     * Disables locking descendants. This causes all new child contexts of this
     * context to be readonly. This is used for indices and temporary tables
     * (where we disallow finer-grain locks), the former due to complexity
     * locking B+ trees, and the latter due to the fact that temporary tables
     * are only accessible to one transaction, so finer-grain locks make no
     * sense.
     */
    public void disableChildLocks() {
        this.childLocksDisabled = true;
    }

    /**
     * Gets the parent context.
     */
    public LockContext parentContext() {
        return parent;
    }

    /**
     * Gets the context for the child with name `name` and readable name
     * `readable`
     */
    public synchronized LockContext childContext(String name) {
        LockContext temp = new LockContext(lockman, this, name,
                this.childLocksDisabled || this.readonly);
        LockContext child = this.children.putIfAbsent(name, temp);
        if (child == null) child = temp;
        return child;
    }

    /**
     * Gets the context for the child with name `name`.
     */
    public synchronized LockContext childContext(long name) {
        return childContext(Long.toString(name));
    }

    /**
     * Gets the number of locks held on children a single transaction.
     */
    public int getNumChildren(TransactionContext transaction) {
        return numChildLocks.getOrDefault(transaction.getTransNum(), 0);
    }

    @Override
    public String toString() {
        return "LockContext(" + name.toString() + ")";
    }
}

