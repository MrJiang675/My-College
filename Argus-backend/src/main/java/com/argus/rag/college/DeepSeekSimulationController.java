package com.argus.rag.college;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.*;
import java.util.Map;
@RestController
@RequestMapping("/api/college/practice")
public class DeepSeekSimulationController {
 private final CurrentUserService users;private final DeepSeekSimulationService service;
 public DeepSeekSimulationController(CurrentUserService users,DeepSeekSimulationService service){this.users=users;this.service=service;}
 public record PracticeSimulateRequest(@Size(max=200) String experimentName,@Size(max=3000) String simulationGoal,@Size(max=16000) String materialText,@Size(max=2621440) String imageDataUrl,String reasoningMode,@Valid CampusProfile campusProfile){
  @Override public String toString(){return "PracticeSimulateRequest[redacted]";}
 }
 public record PracticeRepairRequest(@Size(max=200) String experimentName,@Size(max=3000) String simulationGoal,@Size(max=120000) String brokenHtml,@Size(max=2500) String runtimeError){@Override public String toString(){return "PracticeRepairRequest[redacted]";}}
 public record SimulationResultVO(String title,String summary,String html,String usedModel,String reasoningMode,String fixSummary){}
 @PostMapping(path="/simulate-stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public Flux<ServerSentEvent<Map<String,Object>>> simulate(@Valid @RequestBody PracticeSimulateRequest request){users.getRequiredCurrentUser();service.validate(request);return service.simulate(request);}
 @PostMapping("/repair-simulation")
 public Mono<ApiResponse<SimulationResultVO>> repair(@Valid @RequestBody PracticeRepairRequest request){users.getRequiredCurrentUser();return service.repair(request).map(ApiResponse::success);}
}
