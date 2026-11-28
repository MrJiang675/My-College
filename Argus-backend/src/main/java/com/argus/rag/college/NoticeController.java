package com.argus.rag.college;

import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

@RestController
@RequestMapping("/api/college/notices")
public class NoticeController {
    private static final URI LIST = URI.create("https://www.cs.tsinghua.edu.cn/index/tzgg.htm");
    private static final Pattern ITEM = Pattern.compile("<a[^>]*href=\"\\.\\./info/1087/(\\d+)\\.htm\"[^>]*title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("class=\"v_news_content\">(.*?)</div>", Pattern.DOTALL);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final CurrentUserService users;
    private final java.util.concurrent.Semaphore refreshGate=new java.util.concurrent.Semaphore(1);
    private volatile List<Notice> cached = List.of();
    private volatile Instant refresh = Instant.MIN;
    public record Notice(String id, String title, String url) {}
    public NoticeController(CurrentUserService users) { this.users = users; }

    private String fetch(URI url) throws Exception {
        var req = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(8)).GET().build();
        var res = client.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200) throw new IllegalStateException("source unavailable");
        return res.body();
    }

    @GetMapping
    public ApiResponse<List<Notice>> list() {
        users.getRequiredCurrentUser();
        if(Instant.now().isAfter(refresh)) {
            if(!refreshGate.tryAcquire())throw new BusinessException("公告正在刷新，请稍后重试或粘贴原文。");
            try {
                var matches=ITEM.matcher(fetch(LIST));
                var items=new LinkedHashMap<String,Notice>();
                while(matches.find() && items.size()<20) {
                    var id=matches.group(1);
                    items.put(id,new Notice(id,HtmlUtils.htmlUnescape(matches.group(2)),url(id).toString()));
                }
                if(items.isEmpty()) throw new IllegalStateException();
                cached=List.copyOf(items.values());
                refresh=Instant.now().plusSeconds(1800);
            } catch(Exception e) {
                if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new BusinessException("院系公告暂时无法获取，请打开原站或粘贴原文");
            } finally {refreshGate.release();}
        }
        return ApiResponse.success(cached);
    }
    private URI url(String id){return LIST.resolve("/info/1087/"+id+".htm");}

    @GetMapping("/{id:[0-9]{1,12}}/metadata")
    public ApiResponse<Map<String,String>> metadata(@PathVariable String id) {
        users.getRequiredCurrentUser();
        try {
            String html=fetch(url(id));
            var date=Pattern.compile("(?:发布时间|发布日期|发布于|date)[^0-9]{0,50}(20[0-9]{2}[-年/][0-9]{1,2}[-月/][0-9]{1,2})",Pattern.CASE_INSENSITIVE).matcher(html);
            return ApiResponse.success(Map.of("url",url(id).toString(),"publishedAt",date.find()?date.group(1):"需核实"));
        } catch(Exception unavailable) {
            if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
            return ApiResponse.success(Map.of("url",url(id).toString(),"publishedAt","需核实"));
        }
    }

    @GetMapping("/{id:[0-9]{1,12}}")
    public ApiResponse<String> read(@PathVariable String id) {
        users.getRequiredCurrentUser();
        try {
            var content=CONTENT.matcher(fetch(url(id)));
            if(!content.find())throw new IllegalStateException();
            String text=HtmlUtils.htmlUnescape(content.group(1).replaceAll("(?i)</p>|<br[^>]*>","\n").replaceAll("<[^>]+>","")).strip();
            if(text.isBlank())throw new IllegalStateException();
            return ApiResponse.success(text);
        }catch(Exception e){
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new BusinessException("未能读取公告正文，请查看原文及附件");
        }
    }
}
