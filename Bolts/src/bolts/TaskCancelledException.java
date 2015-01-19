package bolts;

public class TaskCancelledException extends RuntimeException {

  private CancellationToken token;

  public TaskCancelledException(CancellationToken token) {
    this.token = token;
  }

  public CancellationToken getToken() {
    return token;
  }
}
