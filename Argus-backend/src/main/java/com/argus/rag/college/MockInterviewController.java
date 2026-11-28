package com.argus.rag.college;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/college/interview")
public class MockInterviewController {
    private final CurrentUserService users;
    private final ChatModel model;
    private final ObjectMapper json;
    private final String instructions;
    private final Semaphore capacity = new Semaphore(1);
    public record Answer(String question,String category,String userAnswer,@jakarta.validation.constraints.Size(max=1000) String targetWeakness) {
        public Answer(String question,String category,String userAnswer){this(question,category,userAnswer,null);}
    }
    public record Experience(@jakarta.validation.constraints.Size(max=120) String id,@jakarta.validation.constraints.Size(max=200) String project,
        @jakarta.validation.constraints.Size(max=3000) String text,@jakarta.validation.constraints.Size(max=2000) String team,@jakarta.validation.constraints.Size(max=1000) String source) {}
    public record Request(String resumeText,String targetRole,String interviewType,@jakarta.validation.Valid CampusProfile campusProfile,List<@jakarta.validation.Valid Answer> answers,
        @jakarta.validation.constraints.Size(max=12) List<@jakarta.validation.Valid Experience> selectedExperiences) {
        public Request(String resumeText,String targetRole,String interviewType,CampusProfile campusProfile,List<Answer> answers){this(resumeText,targetRole,interviewType,campusProfile,answers,null);}
    }
    public MockInterviewController(CurrentUserService users,@Qualifier("zhiPuAiChatModel") ChatModel model,ObjectMapper json) throws java.io.IOException {
        this.users=users;this.model=model;this.json=json;
        try(var stream=new ClassPathResource("prompts/skills/mock_interview.st").getInputStream()) {
            instructions=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @PostMapping("/generate") public ApiResponse<JsonNode> generate(@jakarta.validation.Valid @RequestBody Request request) {return execute(request,false);}
    @PostMapping("/evaluate") public ApiResponse<JsonNode> evaluate(@jakarta.validation.Valid @RequestBody Request request) {return execute(request,true);}

    private ApiResponse<JsonNode> execute(Request request,boolean evaluation) {
        users.getRequiredCurrentUser();
        if(request.resumeText()==null || request.resumeText().isBlank() || request.resumeText().length()>30000 ||
            request.targetRole()!=null && request.targetRole().length()>200 ||
            request.interviewType()!=null && !Set.of("实习技术面","保研复试","综合答辩","实习技术/业务面","保研/夏令营面试","秋招/校招综合面").contains(request.interviewType()))
            throw new BusinessException("请提供 30000 字以内的简历并选择有效面试场景。");
        if(evaluation && (request.answers()==null || request.answers().size()!=5 || request.answers().stream().anyMatch(a->
            a==null || a.question()==null || a.question().length()>1500 || a.userAnswer()==null || a.userAnswer().length()>5000 || a.category()==null || a.category().length()>100) ||
            request.answers().stream().allMatch(a->a.userAnswer().isBlank()))) throw new BusinessException("请提交五道题目并至少作答一题；每题回答不超过 5000 字。");
        final String input;
        try {input=json.writeValueAsString(request);}catch(Exception invalid){throw new BusinessException("面试材料格式不正确。");}
        if(!capacity.tryAcquire()) throw new BusinessException("模拟面试正在生成，请稍后重试。");
        try {
            String command=evaluation?"评估这份答卷；未作答必须记为未作答，分数为 0。":"仅生成五道题，不评分。";
            var options=org.springframework.ai.zhipuai.ZhiPuAiChatOptions.builder()
                .maxTokens(4096).temperature(0.2)
                .responseFormat(org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest.ResponseFormat.jsonObject()).build();
            var prompt=new Prompt(List.of(new SystemMessage(instructions+"\n本次唯一输出结构：\n"+schema(evaluation)),new UserMessage(command+"\n"+input)),options);
            var text=Mono.fromCallable(()->{try{
                String firstReply=model.call(prompt).getResult().getOutput().getText();
                if(!hasFiveItems(firstReply,evaluation)) {
                    var repair=new Prompt(List.of(new SystemMessage(instructions+"\n本次唯一结构：\n"+schema(evaluation)+"\n必须输出五条，不能因题目或回答相近而合并。逐题 index 从1到5。"),new UserMessage(command+"\n"+input+"\n上次回复没有完整五条，请重新按每题分别输出。")),options);
                    return model.call(repair).getResult().getOutput().getText();
                }
                return firstReply;
            }finally{capacity.release();}})
                .subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(45)).block();
            JsonNode result=parseResult(text,evaluation);
            if(evaluation) {
                int total=0;
                for(int i=0;i<5;i++) {
                    var feedback=(com.fasterxml.jackson.databind.node.ObjectNode)result.path("questionFeedbacks").get(i);
                    if(request.answers().get(i).userAnswer().isBlank()) feedback.put("score",0);
                    total+=feedback.path("score").asInt();
                }
                var object=(com.fasterxml.jackson.databind.node.ObjectNode)result;
                int average=Math.round(total/5f);object.put("overallScore",average);
                object.put("summaryLevel",average>=85?"优秀":average>=70?"良好":"待强化");
            }
            return ApiResponse.success(result);
        }catch(BusinessException known){throw known;}
        catch(Exception unavailable){throw new BusinessException("面试服务暂未返回可用结果，请重试；已输入的简历和回答会留在当前页。");}
        // Permit is released by the running task, even if the caller times out.
    }

    private boolean hasFiveItems(String text,boolean evaluation) {
        try {
            int first=text.indexOf('{'),last=text.lastIndexOf('}');
            var items=json.readTree(text.substring(first,last+1)).path(evaluation?"questionFeedbacks":"questions");
            return items.isArray()&&items.size()==5;
        }catch(Exception incomplete){return false;}
    }

    private String schema(boolean evaluation) {
        var root=json.createObjectNode();
        if(!evaluation) {
            var questions=root.putArray("questions");
            var categories=List.of("项目深挖","真实个人贡献","专业基础","压力情景","目标匹配");
            for(int i=0;i<5;i++)questions.addObject().put("id","q"+(i+1)).put("index",i+1)
                .put("category",categories.get(i)).put("question","根据简历提出具体问题").put("focusPoint","本题具体考察点").put("targetWeakness","指出简历中该题对应的证据薄弱处或未拆清的个人贡献");
        } else {
            root.put("overallScore",0).put("summaryLevel","待强化");
            var dimensions=root.putArray("dimensions");
            for(String name:List.of("表达逻辑","经历真实度与证据感","专业深度","目标岗位/院校匹配度"))dimensions.addObject().put("name",name).put("score",0);
            var feedback=root.putArray("questionFeedbacks");
            for(int i=0;i<5;i++)feedback.addObject().put("index",i+1).put("score",0).put("strengths","根据第"+(i+1)+"题回答分析亮点")
                .put("weaknesses","具体指出不足").put("followUpHint","根据该回答提出一个压力追问，不编造事实").put("improvedAnswerSample","不编造经历的优化回答");
            root.putArray("actionableAdvice").add("具体行动一").add("具体行动二").add("具体行动三");
        }
        return root.toString();
    }

    JsonNode parseResult(String text,boolean evaluation) {
        try {
            int first=text==null?-1:text.indexOf('{'),last=text==null?-1:text.lastIndexOf('}');
            if(first<0||last<first)throw new IllegalArgumentException();
            JsonNode result=json.readTree(text.substring(first,last+1).replaceAll(",\\s*([}\\]])","$1"));
            if(!evaluation) {
                JsonNode questions=result.path("questions");
                if(!questions.isArray() || questions.size()!=5)throw new IllegalArgumentException();
                for(int i=0;i<5;i++) {
                    var q=questions.get(i);
                    for(String field:List.of("category","question","focusPoint")) if(!q.path(field).isTextual()||q.path(field).asText().isBlank())throw new IllegalArgumentException();
                    ((com.fasterxml.jackson.databind.node.ObjectNode)q).put("index",i+1).put("id","q"+(i+1));
                    if(!q.path("targetWeakness").isTextual())((com.fasterxml.jackson.databind.node.ObjectNode)q).put("targetWeakness","请核对个人行动、量化结果和证明位置是否完整。");
                }
            } else {
                requireScore(result.path("overallScore"));
                if(!result.path("summaryLevel").isTextual() || !result.path("dimensions").isArray() || result.path("dimensions").size()!=4 ||
                    !result.path("questionFeedbacks").isArray() || result.path("questionFeedbacks").size()!=5 ||
                    !result.path("actionableAdvice").isArray() || result.path("actionableAdvice").size()!=3)throw new IllegalArgumentException();
                for(var item:result.path("dimensions")){requireScore(item.path("score"));if(!item.path("name").isTextual())throw new IllegalArgumentException();}
                for(var item:result.path("questionFeedbacks")){
                    requireScore(item.path("score"));
                    for(String field:List.of("strengths","weaknesses","improvedAnswerSample"))if(!item.path(field).isTextual())throw new IllegalArgumentException();
                    if(!item.path("followUpHint").isTextual())((com.fasterxml.jackson.databind.node.ObjectNode)item).put("followUpHint","如果请你出示个人贡献的原始证据，你会提供什么？");
                }
            }
            return result;
        }catch(Exception invalid){throw new BusinessException("模型回复结构不完整，请重新生成；不会展示未经校验的评分。");}
    }
    private void requireScore(JsonNode value){if(!value.isIntegralNumber() || value.asInt()<0 || value.asInt()>100)throw new IllegalArgumentException();}
}
