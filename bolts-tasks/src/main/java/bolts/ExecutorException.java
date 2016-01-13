package bolts;

/**
 * This is a wrapper class for emphasizing that task failed due to bad Executor, rather than the continuation block it self.
 */
public class ExecutorException extends RuntimeException {

    public ExecutorException(Exception e) {
        super("An exception was thrown by a Executor", e);
    }
}
