package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class WhoisXmlApiClient {

    @Value("${yudao.pay.risk-assess.whoisxml.api-key:}")
    private String apiKey;

    @Value("${yudao.pay.risk-assess.whoisxml.base-url:https://www.whoisxmlapi.com/whoisserver/WhoisService}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.http-timeout-millis:20000}")
    private Integer timeoutMillis;

    public JsonNode lookupDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[WhoisXmlApiClient][lookupDomain] apiKey is empty, skip lookup for domain={}", domain);
            return null;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("apiKey", apiKey);
        requestBody.put("domainName", domain);
        requestBody.put("outputFormat", "JSON");

        try (HttpResponse response = HttpUtil.createPost(baseUrl)
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis)
                .body(JsonUtils.toJsonString(requestBody))
                .execute()) {
            return JsonUtils.parseTree(response.body());
        } catch (Exception e) {
            log.warn("[WhoisXmlApiClient][lookupDomain] lookup failed for domain={}", domain, e);
            return null;
        }
    }
}
