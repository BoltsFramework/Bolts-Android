package bolts;

/**
 * Used to signify that a Task's error went unobserved.
 */
public class UnobservedTaskException extends RuntimeException {
  public UnobservedTaskException(Throwable cause) {
    super(cause);
  }
}
