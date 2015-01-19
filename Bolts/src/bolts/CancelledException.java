package bolts;

/**
 * Thrown to indicate an operation was cancelled.
 */
public class CancelledException extends RuntimeException {

  private CancellationToken token;

  /**
   * Create a new {@code CancelledException}.
   *
   * @param token the token that was cancelled resulting in the cancellation of this operation.
   */
  public CancelledException(CancellationToken token) {
    this.token = token;
  }

  /**
   * @return the cancellation token that triggered the exception.
   */
  public CancellationToken getToken() {
    return token;
  }
}
