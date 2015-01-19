package bolts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Used to control the cancellation of asynchronous operations or tasks.
 * <p/>
 * Create an instance of {@code CancellationToken.Source} and pass the token returned from
 * {@code #getToken()} to the asynchronous operation(s). Call
 * {@code CancellationToken.Source#cancel()} to cancel the operations.
 * <p/>
 * A {@code CancellationToken} can only be cancelled once - it should not be passed to future operations
 * once cancelled.
 */
public class CancellationToken {

  private boolean cancellationRequested;
  private Map<Object, Listener> listenersMap = new HashMap<>();

  private CancellationToken() {
  }

  /**
   * @return {@code true} if the cancellation was requested from the source, {@code false} otherwise.
   */
  public boolean isCancellationRequested() {
    synchronized (this) {
      return cancellationRequested;
    }
  }

  /**
   * Throws an exception if the user has a requested this token be cancelled. May be used to stop
   * execution of a thread or runnable.
   *
   * @throws CancelledException
   */
  public void throwIfCancellationRequested() throws CancelledException {
    synchronized (this) {
      if (cancellationRequested) {
        throw new CancelledException(this);
      }
    }
  }

  /**
   * Register a listener to be notified when this token is cancelled.
   * If another listener was previously registered with the specified {@code id} then this listener replaces that one.
   *
   * @param id
   *          the listener ID that can be used to unregister the listener later.
   * @param listener
   *          the listener to be notified.
   */
  public void register(ListenerId id, Listener listener) {
    synchronized (this) {
      listenersMap.put(id, listener);
    }
  }

  /**
   * Unregister a previously a registered {@code Listener}. This may be useful if the task completes successfully or is
   * cancelled in another manner in order to reclaim memory.
   * Has no effect if the listener was previously unregistered or never registered.
   *
   * @param id the listener ID that was previously used when registering the listener.
   */
  public void unregister(ListenerId id) {
    synchronized (this) {
      listenersMap.remove(id);
    }
  }

  private boolean tryCancel() {
    synchronized (this) {
      if (cancellationRequested) {
        return false;
      }

      cancellationRequested = true;
    }
    notifyListeners();
    return true;
  }

  private void notifyListeners() {
    // Defensive copy in case the map is modified by a listener.
    Collection<Listener> listeners;
    synchronized (this) {
      listeners = new ArrayList<>(listenersMap.values());
    }
    for (Listener listener : listeners) {
      listener.onCancellationRequested(this);
    }
  }

  /**
   * Listener to be notified when a token is cancelled.
   */
  public interface Listener {
    /**
     * The user has requested cancellation of the operation by cancelling the token.
     *
     * @param token the token that was cancelled.
     */
    void onCancellationRequested(CancellationToken token);
  }

  /**
   * Used to identify {@code Listener}s registered with a token.
   */
  // Necessary because generally unregister() proceeds register() in user code.
  public static final class ListenerId {
    /**
     * Create a new {@code ListenerId}.
     */
    public ListenerId() {
    }
    // Guarantees hashCode implementation.
  }

  /**
   * The source of a token is used to create and control {@code CancellationToken}s. To create a
   * {@code CancellationToken} first create a {@code CancellationToken.Source} then call
   * {@code CancellationToken.Source#getToken()} to retrieve the token for the source.
   */
  public static class Source {

    private final CancellationToken token;

    /**
     * Create a new {@code CancellationToken.Source}.
     */
    public Source() {
      token = new CancellationToken();
    }

    /**
     * @return the token that can be passed to asynchronous method to control cancellation.
     */
    public CancellationToken getToken() {
      return token;
    }

    /**
     * Cancels the token if it has not already been cancelled.
     */
    public void cancel() {
      token.tryCancel();
    }
  }
}
