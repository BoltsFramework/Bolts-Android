package bolts;

/**
 * Represents an exception used to communicate task cancellation.
 */
public class TaskCanceledException extends RuntimeException {
  private final Task<?> canceledTask;

  /**
   * Constructs a new {@code TaskCanceledException} with the current stack trace, and with a
   * reference to the task that is the cause of this exception.
   *
   * @param canceledTask
   *            The task that was canceled.
   */
  TaskCanceledException(Task<?> canceledTask) {
    this.canceledTask = canceledTask;
  }

  /**
   * @return The task associated with this exception.
   */
  public Task<?> getTask() {
    return canceledTask;
  }
}
