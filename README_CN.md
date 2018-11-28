Bolts
============
[![Build Status][build-status-svg]][build-status-link]
[![Coverage Status][coverage-status-svg]][coverage-status-link]
[![Maven Central][maven-tasks-svg]][maven-tasks-link]
[![Maven Central][maven-applinks-svg]][maven-applinks-link]
[![License][license-svg]][license-link]

Bolts是一个为了方便移动APP开发而设计的低级库集合。Bolts由Parse和Facebook设计用来我们内部使用的，现在我们决定开源这些库让其他人也可以用。使用这些库不需要添加使用任何Parse的服务，也不需要拥有一个Parse或者Facebook的开发者账号。

Bolts包含：

* "Tasks",它使得复杂的异步代码更易于管理。一个任务类似于JavaScript的Promise，但是它可以用于ios和Android。
* 一个[App Links protocol](http://www.applinks.org)的实现，帮助您链接到其他应用程序中的内容，并处理传入的多层链接。

想了解更多信息，请参考[Bolts Android API Reference](http://boltsframework.github.io/Bolts-Android/)。

[English document](README.md)

# 下载

下载[最新的jar包][latest]或者在Gradle中定义：

```groovy
dependencies {
  compile 'com.parse.bolts:bolts-tasks:1.4.0'
  compile 'com.parse.bolts:bolts-applinks:1.4.0'
}
```
开发版本的快照可以在[Sonatype的`snapshots`仓库][snap]找到。

# Tasks

要创建一个真正的响应式Android程序，你必须让需要耗时操作脱离UI线程，并且要小心翼翼的避免阻塞UI线程。为了更容易得实现这个操作，我们增加了一个叫`Task`的类。一个`Task`代表一个异步操作。一个`Task`通常由一个异步方法返回并且拥有可以继续对这个`Task`进行操作的能力。当一个`Task`从方法中返回时已经开始执行它的工作了。一个`Task`并不与特定的线程模型有关：它代表正在做的工作，而不是在哪里做。相比于`callbacks`和`AsyncTask`之类的其他异步编程方法，`Tasks`拥有很多优势：

* 只消耗少量的系统资源， 因为当等待其他`Task`的时候并不会占用线程。
* 在一条线上执行多个任务，而不是像只使用`callbacks`一样创建`金字塔`代码。
* `Task`是完全可组合的，允许你执行分支，并行和复杂的错误处理，而没有很多命名回调的`意大利面条`式代码。
* 
* 您可以按照执行的顺序排列基于任务的代码，而不必将其分散到不同的回调函数中。

这个文档的例子假定已经有了一些名为`saveAsync`和`findAsync`的异步解析方法，它们返回一个`Task`。在后面的部分我们会教你如何自己定义这些方法。

## `continueWith` 方法

每个`Task`都有个叫`continueWith`的方法，它接收一个`Continuation`对象。`Continuation`是一个只有一个`then`方法的接口，`then`方法在`Task`结束时调用。你能在`then`方法中检查任务是否成功，也可以拿到任务执行的结果。

```java
saveAsync(obj).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    if (task.isCancelled()) {
      // 取消了保存
    } else if (task.isFaulted()) {
      // 保存失败
      Exception error = task.getError();
    } else {
      // 保存成功
      ParseObject object = task.getResult();
    }
    return null;
  }
});
```

`Task`使用了Java的泛型，是强类型的，所以在开始使用的时候要小心一点以免出现语法错误。让我们通过一个例子来更进一步地看下这些类型吧。

```java
/**
 异步获取一个字符串。
 */
public Task<String> getStringAsync() {
  //我们假设getIntAsync()方法返回一个Task<Integer>。
  return getIntAsync().continueWith(
    //这个Continuation接收一个Integer作为输入，提供一个String作为输出。
    //它必须接收一个Integer因为这是上一个Task的返回值。
    new Continuation<Integer, String>() {
      //为了方便将getIntAsync()返回的Task传递给 "then"。
      public String then(Task<Integer> task) throws Exception {
        Integer number = task.getResult();
        return String.format("%d", Locale.US, number);
      }
    }
  );
}
```

有很多情况是你只需要处理成功回调并且晚点处理失败和取消，可以使用`onSuccess`方法替代`continueWith`。

```java
saveAsync(obj).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    // 对象保存成功
    return null;
  }
});
```

## 链式任务

`Task`有点神奇，因为它让你不用嵌套就能将它们串联在一起。如果你使用`continueWithTask`而不是`continueWith`，你就能返回一个新的`Task`。这个由`continueWithTask`返回的`Task`直到内层的`continueWithTask`返回了新的`Task`才会被认为完成了。这可以让你执行多个操作，却不用忍受使用回调产生的`金字塔`代码。类似的，`onSuccessTask`是`onSuccess`的返回`Task`版本。因此，使用`continueWith`/`onSuccess`做更多的同步工作，或者使用`continueWithTask`/`onSuccessTask`做更多的异步工作。

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
    // 所有任务完成
    return null;
  }
});
```

## 错误处理

在选择是否调用`continueWith`或者`onSuccess`时要小心一点，你可以控制错误如何在你的应用中传递。使用`continueWith`来传递错误或者处理错误。你可以把失败的任务看做抛出一个异常。事实上，你可以在continuation里面抛出一个异常，然后会产生一个与此相关的`Task`。

```java
final ParseQuery<ParseObject> query = ParseQuery.getQuery("Student");
query.orderByDescending("gpa");
findAsync(query).onSuccessTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(0).put("valedictorian", true);
    // 强制把回调变成异常。
    throw new RuntimeException("There was an error.");
  }
}).onSuccessTask(new Continuation<ParseObject, Task<List<ParseObject>>>() {
  public Task<List<ParseObject>> then(Task<ParseObject> task) throws Exception {
    // 跳过这个 continuation。
    ParseObject valedictorian = task.getResult();
    return findAsync(query);
  }
}).continueWithTask(new Continuation<List<ParseObject>, Task<ParseObject>>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    if (task.isFaulted()) {
      //错误处理器将被调用。
      //这个异常会变成 "There was an error"。
      //我们通过返回一个新的值来处理异常
      //这个任务完成时的返回值是null。
      return null;
    }

    // 这里也会跳过
    List<ParseObject> students = task.getResult();
    students.get(1).put("salutatorian", true);
    return saveAsync(students.get(1));
  }
}).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    //所有的操作都完成啦！这里被调用了。
    //这个任务的结果是null
    return null;
  }
});
```

在一串成功回调的最后加一个错误处理器，这样比较方便。

## 创建任务

当你开始使用时，你只需使用`findAsync`或`saveAsync`等方法返回的`Task`。但是，对于更高级的场景，你可能想要创建你自己的`Task`。你可以通过创建一个`TaskCompletionSource`来实现。你可以通过该对象创建一个新的`Task`，并控制是否将其标记为已完成或已取消。在你创建了一个`Task`后，你需要调用`setResult`，`setError`，或者`setCancelled`来触发它的continuation.

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

如果你在`Task`创建时就知道`Task`执行的结果，你可以使用一些更简便的方法。

```java
Task<String> successful = Task.forResult("The good result.");

Task<String> failed = Task.forError(new RuntimeException("An error message."));
```

## 创建异步方法

使用这些工具很容易创建自己的带返回`Task`的异步方法。比如你能更简单地定义`fetchAsync`。

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

同样地，创建`saveAsync`，`findAsync`或`deleteAsync`也很容易。我们也提供了一些便捷的方法帮助你从直接代码块创建`Task`。`callInBackground`在我们的后台线程池运行一个`Task`，而`call`则是尝试立即执行它的代码块。

```java
Task.callInBackground(new Callable<Void>() {
  public Void call() {
    // 做一堆东西。
  }
}).continueWith(...);
```

## 顺序任务

当你想要按顺序执行一系列的异步操作时，Task是非常方便的，每个操作都会等待上一个操作完成。比如说，想象你要删除所有你博客中的评论。

```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Task<Void>>() {
  public Task<Void> then(Task<List<ParseObject>> results) throws Exception {
    //创建一个简单的已完成task作为基础案例。
    Task<Void> task = Task.forResult(null);
    for (final ParseObject result : results) {
      //对于每个item，扩展一个带删除item方法的task。
      task = task.continueWithTask(new Continuation<Void, Task<Void>>() {
        public Task<Void> then(Task<Void> ignored) throws Exception {
          //返回一个task,当删除完成时task会被标记为结束
          return deleteAsync(result);
        }
      });
    }
    return task;
  }
}).continueWith(new Continuation<Void, Void>() {
  public Void then(Task<Void> ignored) throws Exception {
    //所有评论都删除了
    return null;
  }
});
```

## 并行任务

你也可以使用`whenAll`方法来并行执行几个`Task`。你可以一次性开始多个操作并且使用`Task.whenAll`来创建一个新的`Task`，当所有输入的`Task`都完成时，这个`Task`将被标记为完成。
只有当所有传入的`Task`都成功之后这个新`Task`才算成功。虽然并行执行操作比顺序执行更快，但是可能会消耗更多的系统资源和带宽。
    
```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Task<Void>>() {
  public Task<Void> then(Task<List<ParseObject>> results) throws Exception {
    // 将每个删除的任务收集到数组中。
    ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
    for (ParseObject result : results) {
      //立即开始删除并且将它的task添加到列表中
      tasks.add(deleteAsync(result));
    }
    // 返回一个新的task，当所有删除操作完成后这个task会被标记为完成
    return Task.whenAll(tasks);
  }
}).onSuccess(new Continuation<Void, Void>() {
  public Void then(Task<Void> ignored) throws Exception {
    // 所有的评论都删除了
    return null;
  }
});
```

## Task Executors

所有的`continueWith`和`onSuccess`方法都可以使用一个`java.util.concurrent.Executor`作为可选的第二个参数。`Executor`允许你控制continuation的执行方式。`Task.call()`会调用当前线程的`Callable`而`Task.callInBackground`会使用它自己的线程池，但是你也能指定你自己的executor来调度不同的线程。比如你想在特定的线程池执行工作：

```java
static final Executor NETWORK_EXECUTOR = Executors.newCachedThreadPool();
static final Executor DISK_EXECUTOR = Executors.newCachedThreadPool();
```

```java
final Request request = ...
Task.call(new Callable<HttpResponse>() {
  @Override
  public HttpResponse call() throws Exception {
    //工作被指定在NETWORK_EXECUTOR执行
    return client.execute(request);
  }
}, NETWORK_EXECUTOR).continueWithTask(new Continuation<HttpResponse, Task<byte[]>>() {
  @Override
  public Task<byte[]> then(Task<HttpResponse> task) throws Exception {
    //由于没有指定executor,这里继续在NETWORK_EXECUTOR上执行
    return processResponseAsync(response);
  }
}).continueWithTask(new Continuation<byte[], Task<Void>>() {
  @Override
  public Task<Void> then(Task<byte[]> task) throws Exception {
    //我们不想让磁盘读写阻塞NETWORK_EXECUTOR，所有指定使用DISK_EXECUTOR
    return writeToDiskAsync(task.getResult());
  }
}, DISK_EXECUTOR);
```

一般情况下，像在主线程分发这种，我们提供了默认的Executor，包含`Task.UI_THREAD_EXECUTOR`和`Task.BACKGROUND_EXECUTOR`。举个例子：

```java
fetchAsync(object).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> object) throws Exception {
    TextView textView = (TextView)findViewById(R.id.name);
    textView.setText(object.get("name"));
    return null;
  }
}, Task.UI_THREAD_EXECUTOR);
```

## 捕获变量

在多个回调中分解代码的一个困难在于它们具有不同的变量作用域。 Java允许函数从外部范围“捕获”变量，但只有当它们被标记为`final`时，才使它们变得不可变，这是不方便的。 这就是为什么我们添加了另一个便捷的类`Capture`，它可以让你与回调共享一个变量。只需要调用变量的`get`和`set`方法来改变它的值。

```java
//在Task的回调中捕获一个变量
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
    //现在successfulSaveCount中有保存成功的数量
    return successfulSaveCount.get();
  }
});
```

## 取消Task

要想取消一个`Task`,首先创建一个`CancellationTokenSource`，然后把`CancellationTokenSource`的响应token传递到你要取消的`Task`中，然后调用`CancellationTokenSource`的`cancel()`方法即可。这将取消任何正在执行的token绑定的`Task`。

```java
CancellationTokenSource cts = new CancellationTokenSource();

Task<Integer> stringTask = getIntAsync(cts.getToken());

cts.cancel();
```

要想使用token来取消一个异步的调用，首先你得修改任务的方法，让它可以接收一个`CancellationToken`并且使用`isCancellationRequested()`方法来决定何时停止操作。

```java
/**
 异步获取`Integer`
 */
public Task<Integer> getIntAsync(final CancellationToken ct) {
  // 创建一个任务
  final TaskCompletionSource<Integer> tcs = new TaskCompletionSource<>();

  new Thread() {
    @Override
    public void run() {
      // 在开始时检查是否取消
      if (ct.isCancellationRequested()) {
        tcs.setCancelled();
        return;
      }

      int result = 0;
      while (result < 100) {
        // 在一个循环中轮询isCancellationRequested
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

[App Links](http://www.applinks.org) 提供了一个跨平台的机制让开发者可以为他们的内容定义和发布一个多层链接的scheme，允许其他APP直接连接。不管你是构建一个接收传入链接的还是可能链接到其他app内容的app，Bolts都会提供工具来简化[App链接协议](http://www.applinks.org/documentation)的实现。

## 处理App Link

最常见的情况是让你的app可以接收App Link。
App Link让你的用户可以在他们的设备上快速访问最丰富的、最原生的内容呈现。Bolts通过提供一系列处理传入`Intent`的工具来简化处理传入的App Link。

比方说，你可以在你的`Activity`中使用`AppLinks`工具类来解析传递过来的`Intent`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);

  //在某种程度上你的AndroidMainifest.xml中的intent filter可能已经被path过滤了一遍

  // 使用App Link中的target URL 来定位内容。
  Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(getIntent());
  if (targetUrl != null) {
    //这是被app link启动的activity。

    //targetUrl是外部共享的URL。在大多数情况下，你会在数据中嵌入你的内容标识符
    
    //如果你需要访问从你的网站或者app中传到meta标签中的数据，可以在AppLinkData中拿到
    Bundle applinkData = AppLinks.getAppLinkData(getIntent());
    String id = applinkData.getString("id");
    
    //你也可以从AppLinkData中得到referrer data
    Bundle referrerAppData = applinkData.getBundle("referer_app_link");

    //Apps也可以很轻松的检查App Link中的Extras。
    Bundle extras = AppLinks.getAppLinkExtras(getIntent());
    String fbAccessToken = extras.getString("fb_access_token");
  } else {
    //不是app链接，你已有的代码放这里
  }
}
```

## 导航到一个url

通过App Link，你的应用程序可以在用户导航到链接时提供最佳用户体验（由接收应用定义）。 Bolts简化了这个过程，自动执行跟踪链接所需的步骤：

1. 在指定的URL从HTML获取应用链接元数据解析的应用链接

2. 逐步执行与正在使用的设备相关的App Link targets，检查设备上是否存在可以处理targets的应用

3. 如果应用程序存在，请使用指定的al_applink_data构建“Intent”，并导航到“Intent”

4. 否则，请使用指定的原始网址打开浏览器

在最简单的情况下，只需一行代码即可导航到可能具有App Link的URL：

```java
AppLinkNavigation.navigateInBackground(getContext(), url);
```

### 添加应用和导航数据

在大多数情况下，导航时需要传递给应用程序的数据将包含在URL本身中，因此无论应用程序是否安装在设备上，用户都将看到正确的内容。 然而有时候应用程序会传送与应用间导航相关的资料，或是想利用应用程序使用的信息来修改App链接协议，以调整应用程序的行为（比如一个显示回引用应用程序的链接）。

如果你想要充分利用这些特性，你可以拆分导航过程。 首先，你必须拥有您要导航的应用程序链接：

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

然后，你可以使用你想导航的附加数据构建一个APP Link请求：

```java
Bundle extras = new Bundle();
extras.putString("fb_access_token", "t0kEn");
Bundle appLinkData = new Bundle();
appLinkData.putString("id", "12345");
AppLinkNavigation navigation = new AppLinkNavigation(link, extras, appLinkData);
return navigation.navigate();
```

### 解决应用程序链接元数据

Bolts允许自定义App Link方案，可以用作性能优化（例如缓存元数据）或作为允许开发人员使用集中式索引来获得App Link元数据的机制。 一个自定义的App Link解析器只需要能够获取URL并返回一个包含适用于此设备的AppLink.Target的有序列表的AppLink。 Bolts创造性地提供了其中之一，使用隐藏的“WebView”在设备上执行此方案。

你可以重写`AppLinkNavigation`中的一个方法来使用任意的实现了`AppLinkResolver`接口的解析器：

```java
AppLinkNavigation.navigateInBackground(url, resolver);
```

或者，你可以使用内置API替换掉默认的解析器：

```java
AppLinkNavigation.setDefaultResolver(resolver);
AppLinkNavigation.navigateInBackground(url);
```

## 分析

Bolts介绍测量事件。 应用程序链接将两个Measurement Events广播到应用程序，该应用程序可以捕获并与应用程序中的现有分析组件集成。(启用Analytics需要[Android Support Library v4](http://developer.android.com/tools/support-library/index.html))

*  `al_nav_out` — 当您的应用发送App链接URL时引发。
*  `al_nav_in` — 当您的应用打开传入的应用链接Url或`Intent`时触发。

### 监听App链接测量事件

还有其他分析工具与Bolts的应用程序链接事件相集成，但你也可以自己收听这些事件：

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
          // 继承你自己的 日志/分子 组件
        }
      }
    },
    new IntentFilter(MeasurementEvent.MEASUREMENT_EVENT_NOTIFICATION_NAME)
);
```

### 应用链接事件字段

应用程序链接测量事件以字符串键值对形式从应用程序链接Intent中发送附加信息。 下面是一些对两个事件的有用字段:

* `al_nav_in`
  * `inputURL`: 打开app的URL。
  * `inputURLScheme`: `inputURL`的scheme。
  * `refererURL`: 引用app添加进`al_applink_data`: `referer_app_link`的URL
  * `refererAppName`: 引用app添加进`al_applink_data`: `referer_app_link`的名字.
  * `sourceApplication`: 来源app的bundle
  * `targetURL`: `al_applink_data`中的`target_url`字段.
  * `version`: App Links API 版本.

* `al_nav_out`
  * `outputURL`: 用于打开其他应用程序（或浏览器）的URL。 如果有对应的的应用程序要打开，这将是“al_applink_data”中的自定义scheme的url / intent。
  * `outputURLScheme`: `outputURL`的scheme。
  * `sourceURL`: 应用程序链接meta标签中的页面URL
  * `sourceURLHost`: sourceURL的host名字。
  * `success`: “1”表示在其他应用程序或浏览器中打开应用程序链接成功; `“0”`表示无法打开App Link。
  * `type`: `“app”`表示在应用程序中打开，`“web”`表示在浏览器中打开; `“fail”`表示成功字段为“0”`。
  * `version`: App Links API版本。


[build-status-svg]: http://img.shields.io/travis/BoltsFramework/Bolts-Android/master.svg?style=flat
 [build-status-link]: https://travis-ci.org/BoltsFramework/Bolts-Android
 [coverage-status-svg]: https://coveralls.io/repos/BoltsFramework/Bolts-Android/badge.svg?branch=master&service=github
 [coverage-status-link]: https://coveralls.io/github/BoltsFramework/Bolts-Android?branch=master
 [maven-tasks-svg]: https://img.shields.io/maven-central/v/com.parse.bolts/bolts-tasks.svg?label=bolts-tasks&style=flat
 [maven-tasks-link]: https://maven-badges.herokuapp.com/maven-central/com.parse.bolts/bolts-tasks
 [maven-applinks-svg]: https://img.shields.io/maven-central/v/com.parse.bolts/bolts-applinks.svg?label=bolts-applinks&style=flat
 [maven-applinks-link]: https://maven-badges.herokuapp.com/maven-central/com.parse.bolts/bolts-applinks
 [license-svg]: https://img.shields.io/badge/license-BSD-lightgrey.svg?style=flat
 [license-link]: https://github.com/BoltsFramework/Bolts-Android/blob/master/LICENSE

 [latest]: https://search.maven.org/remote_content?g=com.parse.bolts&a=bolts-tasks&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/


