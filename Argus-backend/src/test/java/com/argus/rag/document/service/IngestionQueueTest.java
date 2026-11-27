package com.argus.rag.document.service;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;
class IngestionQueueTest {
 @Test void singleWorkerAndBoundedQueue() throws Exception{
  var executor=new IngestionExecutorConfiguration().ingestionExecutor();
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{
   executor.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
   for(int i=0;i<8;i++)executor.execute(()->{});
   assertThrows(TaskRejectedException.class,()->executor.execute(()->{}));
   assertEquals(1,executor.getActiveCount());
  }finally{release.countDown();executor.shutdown();}
 }
}
