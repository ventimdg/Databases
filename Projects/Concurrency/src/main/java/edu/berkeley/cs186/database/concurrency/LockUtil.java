package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock
 * acquisition for the user (you, in the last task of Part 2). Generally
 * speaking, you should use LockUtil for lock acquisition instead of calling
 * LockContext methods directly.
 */
public class LockUtil {
    /**
     * Ensure that the current transaction can perform actions requiring
     * `requestType` on `lockContext`.
     *
     * `requestType` is guaranteed to be one of: S, X, NL.
     *
     * This method should promote/escalate/acquire as needed, but should only
     * grant the least permissive set of locks needed. We recommend that you
     * think about what to do in each of the following cases:
     * - The current lock type can effectively substitute the requested type
     * - The current lock type is IX and the requested lock is S
     * - The current lock type is an intent lock
     * - None of the above: In this case, consider what values the explicit
     *   lock type can be, and think about how ancestor looks will need to be
     *   acquired or changed.
     *
     * You may find it useful to create a helper method that ensures you have
     * the appropriate locks on all ancestors.
     */
    public static void ensureSufficientLockHeld(LockContext lockContext, LockType requestType) {
        // requestType must be S, X, or NL
        assert (requestType == LockType.S || requestType == LockType.X || requestType == LockType.NL);

        // Do nothing if the transaction or lockContext is null
        TransactionContext transaction = TransactionContext.getTransaction();
        if (transaction == null || lockContext == null) return;

        // You may find these variables useful
        LockContext parentContext = lockContext.parentContext();
        LockType effectiveLockType = lockContext.getEffectiveLockType(transaction);
        LockType explicitLockType = lockContext.getExplicitLockType(transaction);

        // TODO(proj4_part2): implement
        if (LockType.substitutable(explicitLockType, requestType) || LockType.substitutable(effectiveLockType, requestType)){
            return;
        }
        if (requestType == LockType.NL){
            return;
        } else if (requestType == LockType.S){
            if (explicitLockType == LockType.IX){
                List<Pair<LockContext, LockType>> ancestors = ancestorLocks(parentContext, transaction);
                for (Pair<LockContext, LockType> pair : ancestors){
                    LockType currType = pair.getSecond();
                    if (currType == LockType.SIX){
                        return;
                    }
                }
                lockContext.promote(transaction, LockType.SIX);
            }else if (explicitLockType == LockType.SIX){
                return;
            }else if (explicitLockType == LockType.IS){
                lockContext.escalate(transaction);
            } else if (explicitLockType == LockType.X){
                return;
            } else if (explicitLockType == LockType.NL){
                List<Pair<LockContext, LockType>> ancestors = ancestorLocks(parentContext, transaction);
                for (Pair<LockContext, LockType> pair : ancestors){
                    LockType currType = pair.getSecond();
                    LockContext currContext = pair.getFirst();
                    if (currType == LockType.X || currType == LockType.SIX || currType == LockType.S){
                        return;
                    } else if (currType == LockType.IX || currType == LockType.IS){
                        continue;
                    } else {
                        currContext.acquire(transaction, LockType.IS);
                    }
                }
                lockContext.acquire(transaction, LockType.S);
            }
        } else {
            if (explicitLockType == LockType.IX || explicitLockType == LockType.SIX){
                lockContext.escalate(transaction);
            }else if (explicitLockType == LockType.IS || explicitLockType == LockType.S){
                if (explicitLockType == LockType.IS){
                    lockContext.escalate(transaction);
                }
                List<Pair<LockContext, LockType>> ancestors = ancestorLocks(parentContext, transaction);
                for (Pair<LockContext, LockType> pair : ancestors){
                    LockType currType = pair.getSecond();
                    LockContext currContext = pair.getFirst();
                    if (currType == LockType.IX || currType == LockType.SIX){
                        continue;
                    } else if (currType == LockType.X){
                        currContext.escalate(transaction);
                        return;
                    } else if (currType == LockType.S){
                        currContext.promote(transaction, LockType.SIX);
                    }else {
                        currContext.promote(transaction, LockType.IX);
                    }
                }
                if (lockContext.getExplicitLockType(transaction) != LockType.X){
                    lockContext.promote(transaction, LockType.X);
                }
            } else if (explicitLockType == LockType.NL){
                List<Pair<LockContext, LockType>> ancestors = ancestorLocks(parentContext, transaction);
                for (Pair<LockContext, LockType> pair : ancestors){
                    LockType currType = pair.getSecond();
                    LockContext currContext = pair.getFirst();
                    if (currType == LockType.X){
                        return;
                    } else if (currType == LockType.IX || currType == LockType.SIX){
                        continue;
                    }else if (currType == LockType.IS){
                        currContext.promote(transaction, LockType.IX);
                    }else if (currType == LockType.S){
                        currContext.promote(transaction, LockType.SIX);
                    } else {
                        currContext.acquire(transaction, LockType.IX);
                    }
                }
                lockContext.acquire(transaction, LockType.X);
            }
        }
    }

    // TODO(proj4_part2) add any helper methods you want
    public static List<Pair<LockContext, LockType>> ancestorLocks(LockContext context, TransactionContext transaction){
        List<Pair<LockContext, LockType>> answer = new ArrayList<>();
        while(context != null){
            answer.add(0, new Pair<LockContext, LockType>(context, context.getExplicitLockType(transaction)));
            context = context.parentContext();
        }
        return answer;
    }
}
