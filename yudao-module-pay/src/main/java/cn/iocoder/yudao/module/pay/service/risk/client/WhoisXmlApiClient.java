package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            return buildErrorPayload("domain is empty");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[WhoisXmlApiClient][lookupDomain] apiKey is empty, skip lookup for domain={}", domain);
            return buildErrorPayload("whoisxml apiKey is empty");
        }

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("apiKey", apiKey);
        queryParams.put("domainName", domain);
        queryParams.put("outputFormat", "JSON");

        try (HttpResponse response = HttpUtil.createGet(baseUrl)
                .timeout(timeoutMillis)
                .form(queryParams)
                .execute()) {
            String body = response.body();
            if (response.getStatus() >= 400) {
                log.warn("[WhoisXmlApiClient][lookupDomain] http status={} domain={} body={}",
                        response.getStatus(), domain, body);
                JsonNode errorBody = tryParseJson(body);
                if (errorBody != null) {
                    return errorBody;
                }
                return buildErrorPayload("http status " + response.getStatus() + ", body: " + body);
            }
            JsonNode payload = JsonUtils.parseTree(body);
            return payload == null ? buildErrorPayload("response body is not valid json") : payload;
        } catch (Exception e) {
            log.warn("[WhoisXmlApiClient][lookupDomain] lookup failed for domain={}", domain, e);
            return buildErrorPayload(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JsonNode tryParseJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.parseTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode buildErrorPayload(String msg) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        ObjectNode errorMessage = root.putObject("ErrorMessage");
        errorMessage.put("msg", msg == null ? "" : msg);
        return root;
    }
}
