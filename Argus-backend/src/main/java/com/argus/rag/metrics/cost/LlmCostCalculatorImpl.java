package com.argus.rag.metrics.cost;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmCostCalculatorImpl implements LlmCostCalculator {

    /**
     * 模型费率配置：每千 token 的费用（元）
     * key: modelName, value: [inputPricePerKToken, outputPricePerKToken]
     */
    private final Map<String, BigDecimal[]> modelPricing = new ConcurrentHashMap<>();

    // 每千 token 默认费率
    private static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.0008");
    private static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.002");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    public LlmCostCalculatorImpl() {
        // 官方高峰缓存未命中估算价，非实际账单，可按部署时费率覆盖。
        modelPricing.put("deepseek-flash",new BigDecimal[]{new BigDecimal("0.002"),new BigDecimal("0.008")});
        // 初始化默认费率（可后续通过配置文件或数据库加载）
        modelPricing.put("qwen-plus", new BigDecimal[]{
                new BigDecimal("0.0008"), new BigDecimal("0.002")
        });
        modelPricing.put("qwen-turbo", new BigDecimal[]{
                new BigDecimal("0.0003"), new BigDecimal("0.0006")
        });
        modelPricing.put("qwen-max", new BigDecimal[]{
                new BigDecimal("0.002"), new BigDecimal("0.006")
        });
    }

    @org.springframework.beans.factory.annotation.Autowired
    void deepSeekPricing(@org.springframework.beans.factory.annotation.Value("${argus.deepseek.pricing.input-per-1k:0.002}") BigDecimal input,
                         @org.springframework.beans.factory.annotation.Value("${argus.deepseek.pricing.output-per-1k:0.008}") BigDecimal output){modelPricing.put("deepseek-flash",new BigDecimal[]{input,output});}

    @Override
    public BigDecimal calculate(String modelName, int promptTokens, int completionTokens) {
        BigDecimal[] pricing = modelPricing.getOrDefault(
                modelName, new BigDecimal[]{DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE}
        );

        BigDecimal inputCost = pricing[0]
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = pricing[1]
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }
}
