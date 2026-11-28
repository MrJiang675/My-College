package com.argus.rag.ingestion.service.pipeline.parser;
import java.util.concurrent.Semaphore;
import com.argus.rag.common.exception.BusinessException;
/** Shared by short extraction and queued ingestion, across all heavy parser instances. */
final class ParseBudget implements AutoCloseable {
 private static final Semaphore GATE=new Semaphore(1);
 static ParseBudget acquire(){if(!GATE.tryAcquire())throw new BusinessException("解析任务繁忙，请稍后重试。");return new ParseBudget();}
 public void close(){GATE.release();}
}
