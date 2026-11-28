package com.argus.rag.college;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/** Shared provider settings. Never include these properties in logs/toString (contains credentials). */
@Component
@ConfigurationProperties(prefix="argus.deepseek")
public class DeepSeekProperties {
    private String apiKey="";
    private String baseUrl="https://api.deepseek.com";
    private String model="deepseek-flash";
    private String visionModel="deepseek-flash";
    private String simulationModel="deepseek-flash";
    private String simulationThinkingLevel="low";
    private int connectTimeoutSeconds=10;
    private int readTimeoutSeconds=90;
    private int simulationMaxTokens=8192;
    private int simulationMaxConcurrent=3;
    public String getApiKey(){return apiKey;}
    public void setApiKey(String v){apiKey=v;}
    public String getBaseUrl(){return baseUrl;}
    public void setBaseUrl(String v){baseUrl=v;}
    public String getModel(){return model;}
    public void setModel(String v){model=v;}
    public String getVisionModel(){return visionModel;}
    public void setVisionModel(String v){visionModel=v;}
    public String getSimulationModel(){return simulationModel;}
    public void setSimulationModel(String v){simulationModel=v;}
    public String getSimulationThinkingLevel(){return simulationThinkingLevel;}
    public void setSimulationThinkingLevel(String v){simulationThinkingLevel=v;}
    public int getConnectTimeoutSeconds(){return connectTimeoutSeconds;}
    public void setConnectTimeoutSeconds(int v){connectTimeoutSeconds=v;}
    public int getReadTimeoutSeconds(){return readTimeoutSeconds;}
    public void setReadTimeoutSeconds(int v){readTimeoutSeconds=v;}
    public int getSimulationMaxTokens(){return simulationMaxTokens;}
    public void setSimulationMaxTokens(int v){simulationMaxTokens=v;}
    public int getSimulationMaxConcurrent(){return simulationMaxConcurrent;}
    public void setSimulationMaxConcurrent(int v){simulationMaxConcurrent=v;}

    public String completionUrl(){String base=baseUrl.replaceAll("/+$","");var uri=java.net.URI.create(base);if(!java.util.Set.of("https","http").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("DeepSeek base-url 配置无效");return base+"/chat/completions";}
    public int timeout(){return Math.max(5,Math.min(180,readTimeoutSeconds));}
}
