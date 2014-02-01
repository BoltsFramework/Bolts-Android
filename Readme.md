Bolts
============

Bolts is a collection of low-level libraries designed to make developing mobile
apps easier. Bolts was designed by Parse and Facebook for our own internal use,
and we have decided to open source these libraries to make them available to
others. Using these libraries does not require using any Parse services. Nor
do they require having a Parse or Facebook developer account.

The first component in Bolts is "tasks", which make organization of complex
asynchronous code more manageable. A task is kind of like a JavaScript Promise,
but available for iOS and Android.

For more information, see the [Bolts Android API Reference](http://boltsframework.github.io/docs/android/).

# Tasks

To build a truly responsive Android application, you must keep long-running operations off of the UI thread, and be careful to avoid blocking anything the UI thread might be waiting on. This means you will need to execute various operations in the background. To make this easier, we've added a class called `Task`. A task represents an asynchronous operation. Typically, a `Task` is returned from an asynchronous function and gives the ability to continue processing the result of the task. When a task is returned from a function, it's already begun doing its job. A task is not tied to a particular threading model: it represents the work being done, not where it is executing. Tasks have many advantages over other methods of asynchronous programming, such as callbacks and `AsyncTask`.
* They consume fewer system resources, since they don't occupy a thread while waiting on other tasks.
* They are independent of threading model, so you don't have to worry about reaching the maximum number of allowed threads, as can happen with `AsyncTask`.
* Performing several tasks in a row will not create nested "pyramid" code as you would get when using only callbacks.
* Tasks are fully composable, allowing you to perform branching, parallelism, and complex error handling, without the spaghetti code of having many named callbacks.
* You can arrange task-based code in the order that it executes, rather than having to split your logic across scattered callback functions.

For the examples in this doc, assume there are async versions of some common Parse methods, called `saveAsync` and `findAsync` which return a `Task`. In a later section, we'll show how to define these functions yourself.

## The `continueWith` Method

Every `Task` has a method named `continueWith` which takes a `Continuation`. A continuation is an interface that you implement which has one method, named `then`. The `then` method is called when the task is complete. You can then inspect the task to check if it was successful and to get its result.

```java
saveAsync(obj).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    if (task.isCancelled()) {
      // the save was cancelled.
    } else if (task.isFaulted()) {
      // the save failed.
      Exception error = task.getError()
    } else {
      // the object was saved successfully.
      ParseObject object = task.getResult();
    }
    return null;
  }
});
```

Tasks are strongly-typed using Java Generics, so getting the syntax right can be a little tricky at first. Let's look closer at the types involved with an example.

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

In many cases, you only want to do more work if the previous task was successful, and propagate any errors or cancellations to be dealt with later. To do this, use the `onSuccess` method instead of `continueWith`.

```java
saveAsync(obj).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(Task<ParseObject> task) throws Exception {
    // the object was saved successfully.
    return null;
  }
});
```

## Chaining Tasks Together

Tasks are a little bit magical, in that they let you chain them without nesting. If you use `continueWithTask` instead of `continueWith`, then you can return a new task. The task returned by `continueWithTask` will not be considered finished until the new task returned from within `continueWithTask` is. This lets you perform multiple actions without incurring the pyramid code you would get with callbacks. Likewise, `onSuccessTask` is a version of `onSuccess` that returns a new task. So, use `continueWith`/`onSuccess` to do more synchronous work, or `continueWithTask`/`onSuccessTask` to do more asynchronous work.

```java
final ParseQuery<ParseObject> query = new ParseQuery.getQuery("Student");
query.orderByDescending("gpa");
findAsync(query).onSuccessTask(new Continuation<List<ParseObject>, ParseObject>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(0).put("valedictorian", true);
    return saveAsync(students.get(0));
  }
}).onSuccessTask(new Continuation<ParseObject, List<ParseObject>>() {
  public Task<List<ParseObject>> then(Task<ParseObject> task) throws Exception{
    ParseObject valedictorian = task.getResult();
    return findAsync(query);
  }
}).onSuccessTask(new Continuation<List<ParseObject>, ParseObject>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(1).set("salutatorian", true);
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

By carefully choosing whether to call `continueWith` or `onSuccess`, you can control how errors are propagated in your application. Using `continueWith` lets you handle errors by transforming them or dealing with them. You can think of failed tasks kind of like throwing an exception. In fact, if you throw an exception inside a continuation, the resulting task will be faulted with that exception.

```java
final ParseQuery<ParseObject> query = new ParseQuery.getQuery("Student");
query.orderByDescending("gpa");
findAsync(query).onSuccessTask(new Continuation<List<ParseObject>, ParseObject>() {
  public Task<ParseObject> then(Task<List<ParseObject>> task) throws Exception {
    List<ParseObject> students = task.getResult();
    students.get(0).put("valedictorian", true);
    // Force this callback to fail.
    throw new RuntimeException("There was an error.");
  }
}).onSuccessTask(new Continuation<ParseObject, List<ParseObject>>() {
  public Task<List<ParseObject>> then(Task<ParseObject> task) throws Exception {
    // Now this continuation will be skipped.
    ParseObject valedictorian = task.getResult();
    return findAsync(query);
  }
}).continueWithTask(new Continuation<List<ParseObject>, ParseObject>() {
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
    students.get(1).set("salutatorian", true);
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

When you're getting started, you can just use the tasks returned from methods like `findAsync` or `saveAsync`. However, for more advanced scenarios, you may want to make your own tasks. To do that, you create a `TaskCompletionSource`. This object will let you create a new Task, and control whether it gets marked as finished or cancelled. After you create a `Task`, you'll need to call `setResult`, `setError`, or `setCancelled` to trigger its continuations.

```java
public Task<String> succeedAsync() {
  // Java Generics syntax can be confusing sometimes. :)
  // This creates a TCS for a Task<String>.
  Task<String>.TaskCompletionSource successful = Task.<String> create();
  successful.setResult("The good result.");
  return successful.getTask();
}

public Task<String> failAsync() {
  Task<String>.TaskCompletionSource failed = Task.<String> create();
  failed.setError(new RuntimeException("An error message."));
  return failed.getTask();
}
```

If you know the result of a task at the time it is created, there are some convenience methods you can use.

```java
Task<String> successful = Task.forResult("The good result.");

Task<String> failed = Task.forError(new RuntimeException("An error message."));
```

## Creating Async Methods

With these tools, it's easy to make your own asynchronous functions that return tasks. For example, you can define `fetchAsync` easily.

```java
public Task<Void> fetchAsync(ParseObject obj) {
  Task<Void>.TaskCompletionSource task = Task.<Void> create();
  obj.fetchInBackground(new GetCallback() {
    public void done(ParseObject object, ParseException e) {
     if (e == null) {
       task.setResult(object);
     } else {
       task.setError(e);
     }
   }
  });
  return task.getTask();
}
```

It's similarly easy to create `saveAsync`, `findAsync` or `deleteAsync`. We've also provided some convenience functions to help you create tasks from straight blocks of code. `callInBackground` runs a task on our background thread pool, while `call` tries to execute its block immediately.

```java
Task.callInBackground(new Callable<Void>() {
  public Void call() {
    // Do a bunch of stuff.
  }
}).continueWith(...);
```

## Tasks in Series

Tasks are convenient when you want to do a series of tasks in a row, each one waiting for the previous to finish. For example, imagine you want to delete all of the comments on your blog.

```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Void>() {
  public Task<Void> then(List<ParseObject> results) throws Exception {
    // Create a trivial completed task as a base case.
    Task<Void> task = Task.forResult(null);
    for (ParseObject result : results) {
      // For each item, extend the task with a function to delete the item.
      task = task.continueWithTask(new Continuation<Void, Void>() {
        public Void then(Void ignored) throws Exception {
          // Return a task that will be marked as completed when the delete is finished.
          return deleteAsync(result);
        }
      });
    }
    return task;
  }
}).continueWith(new Continuation<Void, Void>() {
  public Void then(Void ignored) throws Exception {
    // Every comment was deleted.
    return null;
  }
});
```

## Tasks in Parallel

You can also perform several tasks in parallel, using the `whenAll` method. You can start multiple operations at once, and use `Task.whenAll` to create a new task that will be marked as completed when all of its input tasks are completed. The new task will be successful only if all of the passed-in tasks succeed. Performing operations in parallel will be faster than doing them serially, but may consume more system resources and bandwidth.
    
```java
ParseQuery<ParseObject> query = ParseQuery.getQuery("Comments");
query.whereEqualTo("post", 123);

findAsync(query).continueWithTask(new Continuation<List<ParseObject>, Void>() {
  public Task<Void> then(List<ParseObject> results) throws Exception {
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
  public Void then(Void ignored) throws Exception {
    // Every comment was deleted.
    return null;
  }
});
```

## Task Executors

All of the `continueWith` and `onSuccess` methods can take an instance of `java.util.concurrent.Executor` as an optional second argument. This allows you to control how the continuation is executed. The default executor will use its own thread pool, but you can provide your own executor to schedule work onto a different thread. For example, if you want to continue with work on the UI thread:

```java
import java.util.concurrent.Executor;
import android.os.Handler;
import android.os.Looper;

// Add this member to your class.
static Executor uiThreadExecutor = new Executor() {
  public void execute(Runnable command) {
    new Handler(Looper.getMainLooper()).post(command);
  }
};
```

```java
// And use the uiThreadExecutor like this. The executor applies only to the new
// continuation being passed into continueWith.
fetchAsync(object).continueWith(new Continuation<ParseObject, Void>() {
  public Void then(ParseObject object) throws Exception {
    TextView textView = (TextView)findViewById(R.id.name);
    textView.setText(object.get("name"));
    return null;
  }
}, uiThreadExecutor);
```

## Capturing Variables

One difficulty in breaking up code across multiple callbacks is that they have different variable scopes. Java allows functions to "capture" variables from outer scopes, but only if they are marked as `final`, making them immutable. This is inconvenient. That's why we've added another convenience class called `Capture`, which lets you share a mutable variable with your callbacks. Just call `get` and `set` on the variable to change its value.

```java
// Capture a variable to be modified in the Task callbacks.
final Capture<Integer> successfulSaveCount = new Capture<Integer>(0);

saveAsync(obj1).onSuccessTask(new Continuation<ParseObject, ParseObject>() {
  public Task<ParseObject> then(ParseObject obj1) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj2);
  }
}).onSuccessTask(new Continuation<ParseObject, ParseObject>() {
  public Task<ParseObject> then(ParseObject obj2) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj3);
  }
}).onSuccessTask(new Continuation<ParseObject, ParseObject>() {
  public Task<ParseObject> then(ParseObject obj3) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return saveAsync(obj4);
  }
}).onSuccess(new Continuation<ParseObject, Void>() {
  public Void then(ParseObject obj4) throws Exception {
    successfulSaveCount.set(successfulSaveCount.get() + 1);
    return null;
  }
}).continueWith(new Continuation<Void, Integer>() {
  public Integer then(Void ignored) throws Exception {
    // successfulSaveCount now contains the number of saves that succeeded.
    return successfulSaveCount.get();
  }
});
```

