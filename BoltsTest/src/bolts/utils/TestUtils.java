package bolts.utils;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import bolts.Task;

public class TestUtils {

  private TestUtils() {
  }

  public static void waitForTask(Task<?> t) throws InterruptedException {
    t.waitForCompletion();
    if (t.isFaulted()) {
      throw new RuntimeException(t.getError());
    }
  }

  /**
   * A helper method to get an HTML string with pre-populated meta tags.
   * values should contain pairs of "property" and "content" values to inject into
   * the meta tags.
   */
  public static String getHtmlWithMetaTags(String... values) {
    StringBuilder sb = new StringBuilder("<html><head>");
    for (int i = 0; i < values.length; i += 2) {
      sb.append("<meta property=\"");
      sb.append(values[i]);
      sb.append("\"");
      if (i + 1 < values.length && values[i + 1] != null) {
        sb.append(" content=\"");
        sb.append(values[i + 1]);
        sb.append("\"");
      }
      sb.append(">");
    }
    sb.append("</head><body>Hello, world!</body></html>");
    return sb.toString();
  }

  /**
   * Gets a Uri for the specified data by writing it to a temporary file.
   */
  public static Uri getURLForData(Context context, String data) throws IOException {
    File result = File.createTempFile("temp",
        ".html",
        context.getCacheDir());
    PrintWriter writer = new PrintWriter(result);
    writer.write(data);
    writer.close();
    result.deleteOnExit();
    return Uri.parse(result.toURI().toString());
  }

}
