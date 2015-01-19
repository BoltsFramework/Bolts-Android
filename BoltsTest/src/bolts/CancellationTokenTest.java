package bolts;

import android.test.InstrumentationTestCase;

public class CancellationTokenTest extends InstrumentationTestCase {
  public void testTokenIsCancelled() {
    CancellationToken.Source tokenSource = new CancellationToken.Source();
    CancellationToken token = tokenSource.getToken();

    tokenSource.cancel();

    assertTrue(token.isCancellationRequested());
  }

  public void testTokenThrowsWhenCancelled() {
    CancellationToken.Source tokenSource = new CancellationToken.Source();
    CancellationToken token = tokenSource.getToken();

    tokenSource.cancel();

    try {
      token.throwIfCancellationRequested();
      fail(CancelledException.class.getSimpleName() + " should be thrown");
    } catch (CancelledException e) {
      assertEquals(token, e.getToken());
    }
  }

  public void testTokenNotifiesRegisteredListenerWhenCancelled() {
    final Capture<CancellationToken> tokenCapture = new Capture<>();
    CancellationToken.Source tokenSource = new CancellationToken.Source();
    CancellationToken token = tokenSource.getToken();
    CancellationToken.Listener listener = new CancellationToken.Listener() {
      @Override
      public void onCancellationRequested(CancellationToken token) {
        tokenCapture.set(token);
      }
    };
    token.register(new CancellationToken.ListenerId(), listener);

    tokenSource.cancel();

    assertEquals(token, tokenCapture.get());
  }

  public void testTokenDoesNotNotifyUnregisteredListenerWhenCancelled() {
    final Capture<CancellationToken> tokenCapture = new Capture<>();
    CancellationToken.Source tokenSource = new CancellationToken.Source();
    CancellationToken token = tokenSource.getToken();
    CancellationToken.Listener listener = new CancellationToken.Listener() {
      @Override
      public void onCancellationRequested(CancellationToken token) {
        tokenCapture.set(token);
      }
    };
    CancellationToken.ListenerId listenerId = new CancellationToken.ListenerId();
    token.register(listenerId, listener);
    token.unregister(listenerId);

    tokenSource.cancel();

    assertEquals(null, tokenCapture.get());
  }
}
