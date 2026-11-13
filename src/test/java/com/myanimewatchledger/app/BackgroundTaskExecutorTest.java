package com.myanimewatchledger.app;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class BackgroundTaskExecutorTest {
 @Test void runsOnOwnedBackgroundThread() throws Exception {try(var e=new BackgroundTaskExecutor(2)){
  assertTrue(e.submit(()->Thread.currentThread().getName()).get(2,TimeUnit.SECONDS).startsWith("malw-background-"));}}
 @Test void propagatesFailure(){try(var e=new BackgroundTaskExecutor(1)){
  var f=e.submit(()->{throw new IllegalStateException("Expected");});
  assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->f.get(2,TimeUnit.SECONDS)).getCause());}}
 @Test void rejectsAfterShutdown(){var e=new BackgroundTaskExecutor(1);e.close();assertThrows(IllegalStateException.class,()->e.submit(()->"x"));}
 @Test void rejectsInvalidThreadCount(){assertThrows(IllegalArgumentException.class,()->new BackgroundTaskExecutor(0));}
}
