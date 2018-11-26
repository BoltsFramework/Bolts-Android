Bolts
============
[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]
[![Maven Central][maven-tasks-svg]][maven-tasks-link]
[![Maven Central][maven-applinks-svg]][maven-applinks-link]
[![License][license-svg]][license-link]

Bolts is a collection of low-level libraries designed to make developing mobile
apps easier. Bolts was designed by Parse and Facebook for our own internal use,
and we have decided to open source these libraries to make them available to
others. Using these libraries does not require using any Parse services. Nor
do they require having a Parse or Facebook developer account.

Bolts includes:

* "Tasks", which make organization of complex asynchronous code more manageable. A task is kind of like a JavaScript Promise, but available for iOS and Android.
* An implementation of the [App Links protocol](http://www.applinks.org), helping you link to content in other apps and handle incoming deep-links.

For more information, see the [Bolts Android API Reference](http://boltsframework.github.io/Bolts-Android/).

## Download
Download [the latest JAR][latest] or define in Gradle:

```groovy
dependencies {
  compile 'com.parse.bolts:bolts-tasks:1.4.0'
  compile 'com.parse.bolts:bolts-applinks:1.4.0'
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

# Tasks

To build a truly responsive Android application, you must keep long-running operations off of the UI thread, and be careful to avoid blocking anything the UI thread might be waiting on. This means you will need to execute various operations in the background. To make this easier, we've added a class called `Task`. A `Task` represents an asynchronous operation. Typically, a `Task` is returned from an asynchronous function and gives the ability to continue processing the result of the task. When a `Task` is returned from a function, it's already begun doing its job. A `Task` is not tied to a particular threading model: it represents the work being done, not where it is executing. `Task`s have many advantages over other methods of asynchronous programming, such as callbacks and `AsyncTask`.
* They consume fewer system resources, since they don't occupy a thread while waiting on other `Task`s.
* Performing several `Task`s in a row will not create nested "pyramid" code as you would get when using only callbacks.
* `Task`s are fully composable, allowing you to perform branching, parallelism, and complex error handling, without the spaghetti code of having many named callbacks.
* You can arrange task-based code in the order that it executes, rather than having to split your logic across scattered callback functions.

For the examples in this doc, assume there are async versions of some common Parse methods, called `saveAsync` and `findAsync` which return a `Task`. In a later section, we'll show how to define these functions yourself.

## The `continueWith` Method

Every `Task` has a method named `continueWith` which takes a `Continuation`. A continuation is an interface that you implement which has one method, named `then`. The `then` method is called when the `Task` is complete. You can then inspect the `Task` to check if it was successful and to get its result.

```java
saveAsync(obj).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    if (task.isCancelled()) {
      // the save was cancelled.
    } else if (task.isFaulted()) {
      // the save failed.
      Exception error = task.getError();
    } else {
      // the object was saved successfully.
      ParseObject object = task.getResult();
    }
    return null;
  }
});
```

`Task`s are strongly-typed using Java Generics, so getting the syntax right can be a little tricky at first. Let's look closer at the types involved with an example.

```java
/**
 Gets a String asynchronously.
 */
public Task<String> getStringAsync() {
  // Let's suppose getIntAsync() returns a Task<Integer>.
  return getIntAsync().continueWith(
    // This Continuation is a function which takes an Integer as input,
    // and provides a String as output. It must take an Integer because
    // that's what was returned from the previous Task.
    new Continuation<Integer, String>() {
      // The Task getIntAsync() returned is passed to "then" for convenience.
      public String then(Task<Integer> task) throws Exception {
        Integer number = task.getResult();
        return String.format("%d", Locale.US, number);
      }
    }
  );
}
```

In many cases, you only want to do more work if the previous `Task` was successful, and propagate any errors or cancellations to be dealt with later. To do this, use the `onSuccess` method instead of `continueWith`.

```java
saveAsync(obj).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    // the object was saved successfully.
    return null;
  }
});
```

## Chaining Tasks Together

`Task`s are a little bit magical, in that they let you chain them without nesting. If you use `continueWithTask` instead of `continueWith`, then you can return a new task. The `Task` returned by `continueWithTask` will not be considered complete until the new `Task` returned from within `continueWithTask` is. This lets you perform multiple actions without incurring the pyramid code you would get with callbacks. Likewise, `onSuccessTask` is a version of `onSuccess` that returns a new task. So, use `continueWith`/`onSuccess` to do more synchronous work, or `continueWithTask`/`onSuccessTask` to do more asynchronous work.

```java
final ParseQuery<ParseObject> query = ParseQuery.getQuery("Student");
query.orderByDescending("gpa");
findAsync(query).onSuccessTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(0).put("valedictorian", true);
    return saveAsync(students.get(0));
  }
}).onSuccessTask(new Continuation<ParseObject, Task<List<ParseObject>>>() {
  public Task<List<ParseObject>> then(Task<ParseObject> task) throws Exception {
    ParseObject valedictorian = task.getResult();
    return findAsync(query);
  }
}).onSuccessTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(1).put("salutatorian", true);
    return saveAsync(students.get(1));
  }
}).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    // Everything is done!
    return null;
  }
});
```

## Error Handling

By carefully choosing whether to call `continueWith` or `onSuccess`, you can control how errors are propagated in your application. Using `continueWith` lets you handle errors by transforming them or dealing with them. You can think of failed `Task`s kind of like throwing an exception. In fact, if you throw an exception inside a continuation, the resulting `Task` will be faulted with that exception.

```java
final ParseQuery<ParseObject> query = ParseQuery.getQuery("Student");
query.orderByDescending("gpa");
findAsync(query).onSuccessTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(0).put("valedictorian", true);
    // Force this callback to fail.
    throw new RuntimeException("There was an error.");
  }
}).onSuccessTask(new Continuation<ParseObject, Task<List<ParseObject>>>() {
  public Task<List<ParseObject>> then(Task<ParseObject> task) throws Exception {
    // Now this continuation will be skipped.
    ParseObject valedictorian = task.getResult();
    return findAsync(query);
  }
}).continueWithTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    if (task.isFaulted()) {
      // This error handler WILL be called.
      // The exception will be "There was an error."
      // Let's handle the error by returning a new value.
      // The task will be completed with null as its value.
      return null;
    }

    // This will also be skipped.
    List<ParseObject> students = task.getResult();
    students.get(1).put("salutatorian", true);
    return saveAsync(students.get(1));
  }
}).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    // Everything is done! This gets called.
    // The task's result is null.
    return null;
  }
});
```

It's often convenient to have a long chain of success callbacks with only one error handler at the end.

## Creating Tasks

When you're getting started, you can just use the `Task`s returned from methods like `findAsync` or `saveAsync`. However, for more advanced scenarios, you may want to make your own `Task`s. To do that, you create a `TaskCompletionSource`. This object will let you create a new `Task` and control whether it gets marked as completed or cancelled. After you create a `Task`, you'll need to call `setResult`, `setError`, or `setCancelled` to trigger its continuations.

```java
public Task<String> succeedAsync() {
  TaskCompletionSource<String> successful = new TaskCompletionSource<>();
  successful.setResult("The good result.");
  return successful.getTask();
}

public Task<String> failAsync() {
  TaskCompletionSource<String> failed = new TaskCompletionSource<>();
  failed.setError(new RuntimeException("An error message."));
  return failed.getTask();
}
```

If you know the result of a `Task` at the time it is created, there are some convenience methods you can use.

```java
Task<String> successful = Task.forResult("The good result.");

Task<String> failed = Task.forError(new RuntimeException("An error message."));
```

## Creating Async Methods

With these tools, it's easy to make your own asynchronous functions that return `Task`s. For example, you can define `fetchAsync` easily.

```java
public Task<ParseObject> fetchAsync(ParseObject obj) {
  final TaskCompletionSource<ParseObject> tcs = new TaskCompletionSource<>();
  obj.fetchInBackground(new GetCallback() {
    public void done(ParseObject object, ParseException e) {
     if (e == null) {
       tcs.setResult(object);
     } else {
       tcs.setError(e);
     }
   }
  });
  return tcs.getTask();
}
```

It's similarly easy to create `saveAsync`, `findAsync` or `deleteAsync`. We've also provided some convenience functions to help you create `Task`s from straight blocks of code. `callInBackground` runs a `Task` on our background thread pool, while `call` tries to execute its block immediately.

```java
Task.callInBackground(new Callable<Void>() {
  public Void call() {
    // Do a bunch of stuff.
  }
}).continueWith(...);
```

## Tasks in Series

`Task`s are convenient when you want to do a series of asynchronous operations in a row, each one waiting for the previous to finish. For example, imagine you want to delete all of the comments on your blog.

```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Task<Void>>() {
  public Task<Void> then(Task<List<ParseObject>> results) throws Exception {
    // Create a trivial completed task as a base case.
    Task<Void> task = Task.forResult(null);
    for (final ParseObject result : results) {
      // For each item, extend the task with a function to delete the item.
      task = task.continueWithTask(new Continuation<Void, Task<Void>>() {
        public Task<Void> then(Task<Void> ignored) throws Exception {
          // Return a task that will be marked as completed when the delete is finished.
          return deleteAsync(result);
        }
      });
    }
    return task;
  }
}).continueWith(new Continuation<Void, Void>() {
  public Void then(Task<Void> ignored) throws Exception {
    // Every comment was deleted.
    return null;
  }
});
```

## Tasks in Parallel

You can also perform several `Task`s in parallel, using the `whenAll` method. You can start multiple operations at once and use `Task.whenAll` to create a new `Task` that will be marked as completed when all of its input `Task`s are finished. The new `Task` will be successful only if all of the passed-in `Task`s succeed. Performing operations in parallel will be faster than doing them serially, but may consume more system resources and bandwidth.

```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Task<Void>>() {
  public Task<Void> then(Task<List<ParseObject>> results) throws Exception {
    // Collect one task for each delete into an array.
    ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
    for (ParseObject result : results) {
      // Start this delete immediately and add its task to the list.
      tasks.add(deleteAsync(result));
    }
    // Return a new task that will be marked as completed when all of the deletes are
    // finished.
    return Task.whenAll(tasks);
  }
}).onSuccess(new Continuation<Void, Void>() {
  public Void then(Task<Void> ignored) throws Exception {
    // Every comment was deleted.
    return null;
  }
});
```

## Task Executors

All of the `continueWith` and `onSuccess` methods can take an instance of `java.util.concurrent.Executor` as an optional second argument. This allows you to control how the continuation is executed. `Task.call()` invokes `Callable`s on the current thread and `Task.callInBackground` will use its own thread pool, but you can provide your own executor to schedule work onto a different thread. For example, if you want to do work on a specific thread pool:

```java
static final Executor NETWORK_EXECUTOR = Executors.newCachedThreadPool();
static final Executor DISK_EXECUTOR = Executors.newCachedThreadPool();
```

```java
final Request request = ...
Task.call(new Callable<HttpResponse>() {
  @Override
  public HttpResponse call() throws Exception {
    // Work is specified to be done on NETWORK_EXECUTOR
    return client.execute(request);
  }
}, NETWORK_EXECUTOR).continueWithTask(new Continuation<HttpResponse, Task<byte[]>>() {
  @Override
  public Task<byte[]> then(Task<HttpResponse> task) throws Exception {
    // Since no executor is specified, it's continued on NETWORK_EXECUTOR
    return processResponseAsync(response);
  }
}).continueWithTask(new Continuation<byte[], Task<Void>>() {
  @Override
  public Task<Void> then(Task<byte[]> task) throws Exception {
    // We don't want to clog NETWORK_EXECUTOR with disk I/O, so we specify to use DISK_EXECUTOR
    return writeToDiskAsync(task.getResult());
  }
}, DISK_EXECUTOR);
```

For common cases, such as dispatching on the main thread, we have provided default implementations of Executor. These include `Task.UI_THREAD_EXECUTOR` and `Task.BACKGROUND_EXECUTOR`. For example:

```java
fetchAsync(object).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> object) throws Exception {
    TextView textView = (TextView)findViewById(R.id.name);
    textView.setText(object.get("name"));
    return null;
  }
}, Task.UI_THREAD_EXECUTOR);
```

## Capturing Variables

One difficulty in breaking up code across multiple callbacks is that they have different variable scopes. Java allows functions to "capture" variables from outer scopes, but only if they are marked as `final`, making them immutable. This is inconvenient. That's why we've added another convenience class called `Capture`, which lets you share a mutable variable with your callbacks. Just call `get` and `set` on the variable to change its value.

```java
// Capture a variable to be modified in the Task callbacks.
final Capture<Integer> successfulSaveCount = new Capture<Integer>(0);

saveAsync(obj1).onSuccessTask(new Continuation<ParseObject, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<ParseObject> obj1) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj2);
  }
}).onSuccessTask(new Continuation<ParseObject, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<ParseObject> obj2) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj3);
  }
}).onSuccessTask(new Continuation<ParseObject, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<ParseObject> obj3) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj4);
  }
}).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> obj4) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return null;
  }
}).continueWith(new Continuation<Void, Integer>() {
  public Integer then(Task<Void> ignored) throws Exception {
    // successfulSaveCount now contains the number of saves that succeeded.
    return successfulSaveCount.get();
  }
});
```

## Cancelling Tasks

To cancel a `Task` create a `CancellationTokenSource` and pass the corresponding token to any methods that create a `Task` you want to cancel, then call `cancel()` on the source. This will cancel any ongoing `Task`s that the token was supplied to.


```java
CancellationTokenSource cts = new CancellationTokenSource();

Task<Integer> stringTask = getIntAsync(cts.getToken());

cts.cancel();
```

To cancel an asynchronous call using a token you must first modify the method to accept a `CancellationToken` and use the  `isCancellationRequested()` method to determine when to halt the operation.

```java
/**
 Gets an Integer asynchronously.
 */
public Task<Integer> getIntAsync(final CancellationToken ct) {
  // Create a new Task
  final TaskCompletionSource<Integer> tcs = new TaskCompletionSource<>();

  new Thread() {
    @Override
    public void run() {
      // Check if cancelled at start
      if (ct.isCancellationRequested()) {
        tcs.setCancelled();
        return;
      }

      int result = 0;
      while (result < 100) {
        // Poll isCancellationRequested in a loop
        if (ct.isCancellationRequested()) {
          tcs.setCancelled();
          return;
        }
        result++;
      }
      tcs.setResult(result);
    }
  }.start();

  return tcs.getTask();
}
```

# App Links

[App Links](http://www.applinks.org) provide a cross-platform mechanism that allows a developer to define and publish a deep-linking scheme for their content, allowing other apps to link directly to an experience optimized for the device they are running on. Whether you are building an app that receives incoming links or one that may link out to other apps' content, Bolts provides tools to simplify implementation of the [App Links protocol](http://www.applinks.org/documentation).

## Handling an App Link

The most common case will be making your app receive App Links. In-linking will allow your users to quickly access the richest, most native-feeling presentation of linked content on their devices. Bolts makes it easy to handle an inbound App Link by providing utilities for processing an incoming `Intent`.

For example, you can use the `AppLinks` utility class to parse an incoming `Intent` in your `Activity`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  // An intent filter in your AndroidManifest.xml has probably already filtered by path
  // to some extent.

  // Use the target URL from the App Link to locate content.
  Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(getIntent());
  if (targetUrl != null) {
    // This is activity is started by app link intent.

    // targetUrl is the URL shared externally. In most cases, you embed your content identifier
    // in this data.

    // If you need to access data that you are passing from the meta tag from your website or from opening app
    // you can get them from AppLinkData.
    Bundle applinkData = AppLinks.getAppLinkData(getIntent());
    String id = applinkData.getString("id");

    // You can also get referrer data from AppLinkData
    Bundle referrerAppData = applinkData.getBundle("referer_app_link");

    // Apps can easily check the Extras from the App Link as well.
    Bundle extras = AppLinks.getAppLinkExtras(getIntent());
    String fbAccessToken = extras.getString("fb_access_token");
  } else {
    // Not an applink, your existing code goes here.
  }
}
```

## Navigating to a URL

Following an App Link allows your app to provide the best user experience (as defined by the receiving app) when a user navigates to a link. Bolts makes this process simple, automating the steps required to follow a link:

1. Resolve the App Link by getting the App Link metadata from the HTML at the URL specified
2. Step through App Link targets relevant to the device being used, checking whether the app that can handle the target is present on the device
3. If an app is present, build an `Intent` with the appropriate al_applink_data specified and navigate to that `Intent`
4. Otherwise, open the browser with the original URL specified

In the simplest case, it takes just one line of code to navigate to a URL that may have an App Link:

```java
AppLinkNavigation.navigateInBackground(getContext(), url);
```

### Adding App and Navigation Data

Under most circumstances, the data that will need to be passed along to an app during a navigation will be contained in the URL itself, so that whether or not the app is actually installed on the device, users are taken to the correct content. Occasionally, however, apps will want to pass along data that is relevant for an app-to-app navigation, or will want to augment the App Link protocol with information that might be used by the app to adjust how the app should behave (e.g. showing a link back to the referring app).

If you want to take advantage of these features, you can break apart the navigation process. First, you must have an App Link to which you wish to navigate:

```java
new WebViewAppLinkResolver(getContext()).getAppLinkFromUrlInBackground(url).continueWith(
    new Continuation<AppLink, AppLinkNavigation.NavigationType>() {
      @Override
      public AppLinkNavigation.NavigationType then(Task<AppLink> task) {
        AppLink link = task.getResult();
        return null;
      }
    });
```

Then, you can build an App Link request with any additional data you would like and navigate:

```java
Bundle extras = new Bundle();
extras.putString("fb_access_token", "t0kEn");
Bundle appLinkData = new Bundle();
appLinkData.putString("id", "12345");
AppLinkNavigation navigation = new AppLinkNavigation(link, extras, appLinkData);
return navigation.navigate();
```

### Resolving App Link Metadata

Bolts allows for custom App Link resolution, which may be used as a performance optimization (e.g. caching the metadata) or as a mechanism to allow developers to use a centralized index for obtaining App Link metadata. A custom App Link resolver just needs to be able to take a URL and return an `AppLink` containing the ordered list of `AppLink.Target`s that are applicable for this device. Bolts provides one of these out of the box that performs this resolution on the device using a hidden `WebView`.

You can use any resolver that implements the `AppLinkResolver` interface by using one of the overloads on `AppLinkNavigation`:

```java
AppLinkNavigation.navigateInBackground(url, resolver);
```

Alternatively, a you can swap out the default resolver to be used by the built-in APIs:

```java
AppLinkNavigation.setDefaultResolver(resolver);
AppLinkNavigation.navigateInBackground(url);
```

## Analytics

Bolts introduces Measurement Event. App Links broadcast two Measurement Events to the application, which can be caught and integrated with existing analytics components in your application. ([Android Support Library v4](http://developer.android.com/tools/support-library/index.html) is required in your runtime to enable Analytics.)

*  `al_nav_out` — Raised when your app sends out an App Links URL.
*  `al_nav_in` — Raised when your app opens an incoming App Links URL or intent.

### Listen for App Links Measurement Events

There are other analytics tools that are integrated with Bolts' App Links events, but you can also listen for these events yourself:

```java
LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
manager.registerReceiver(
    new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String eventName = intent.getStringExtra(MeasurementEvent.MEASUREMENT_EVENT_NAME_KEY);
        if (eventName.equals(MeasurementEvent.APP_LINK_NAVIGATE_IN_EVENT_NAME)) {
          Bundle eventArgs = intent.getBundleExtra(MeasurementEvent.MEASUREMENT_EVENT_ARGS_KEY);
          String targetURL = eventArgs.getString("targetURL");
          String referrerName = eventArgs.getString("refererAppName");
          // Integrate to your logging/analytics component.
        }
      }
    },
    new IntentFilter(MeasurementEvent.MEASUREMENT_EVENT_NOTIFICATION_NAME)
);
```

### App Links Event Fields

App Links Measurement Events sends additional information from App Links Intents in flattened string key value pairs. Here are some of the useful fields for the two events.

* `al_nav_in`
  * `inputURL`: the URL that opens the app.
  * `inputURLScheme`: the scheme of `inputURL`.
  * `refererURL`: the URL that the referrer app added into `al_applink_data`: `referer_app_link`.
  * `refererAppName`: the app name that the referrer app added to `al_applink_data`: `referer_app_link`.
  * `sourceApplication`: the bundle of referrer application.
  * `targetURL`: the `target_url` field in `al_applink_data`.
  * `version`: App Links API  version.

* `al_nav_out`
  * `outputURL`: the URL used to open the other app (or browser). If there is an eligible app to open, this will be the custom scheme url/intent in `al_applink_data`.
  * `outputURLScheme`: the scheme of `outputURL`.
  * `sourceURL`: the URL of the page hosting App Links meta tags.
  * `sourceURLHost`: the hostname of `sourceURL`.
  * `success`: `“1”` to indicate success in opening the App Link in another app or browser; `“0”` to indicate failure to open the App Link.
  * `type`: `“app”` for open in app, `“web”` for open in browser; `“fail”` when the success field is `“0”`.
  * `version`: App Links API version.

## License
Bolts-Android is MIT licensed, as found in the LICENSE file.

 [build-status-svg]: http://img.shields.io/travis/BoltsFramework/Bolts-Android/master.svg?style=flat
 [build-status-link]: https://travis-ci.org/BoltsFramework/Bolts-Android
 [coverage-status-svg]: https://coveralls.io/repos/BoltsFramework/Bolts-Android/badge.svg?branch=master&service=github
 [coverage-status-link]: https://coveralls.io/github/BoltsFramework/Bolts-Android?branch=master
 [maven-tasks-svg]: https://img.shields.io/maven-central/v/com.parse.bolts/bolts-tasks.svg?label=bolts-tasks&style=flat
 [maven-tasks-link]: https://maven-badges.herokuapp.com/maven-central/com.parse.bolts/bolts-tasks
 [maven-applinks-svg]: https://img.shields.io/maven-central/v/com.parse.bolts/bolts-applinks.svg?label=bolts-applinks&style=flat
 [maven-applinks-link]: https://maven-badges.herokuapp.com/maven-central/com.parse.bolts/bolts-applinks
 [license-svg]: https://img.shields.io/badge/license-MIT-lightgrey.svg?style=flat
 [license-link]: https://github.com/BoltsFramework/Bolts-Android/blob/master/LICENSE

 [latest]: https://search.maven.org/remote_content?g=com.parse.bolts&a=bolts-tasks&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
