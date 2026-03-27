package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
@Slf4j
public class IpInfoClient {

    @Value("${yudao.pay.risk-assess.ipinfo.base-url:https://ipinfo.io}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.ipinfo.token:}")
    private String token;

    @Value("${yudao.pay.risk-assess.http-timeout-millis:5000}")
    private Integer timeoutMillis;

    public JsonNode fetchIpInfo(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IP_MISSING);
        }
        if (token == null || token.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IPINFO_TOKEN_MISSING);
        }

        // Java 8 下 URLEncoder.encode(String, String) 需要的是 encoding 字符串
        // 并且会抛出受检异常 UnsupportedEncodingException
        String encodedIp;
        try {
            encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("[IpInfoClient][fetchIpInfo] ip({}) 编码异常", ip, e);
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IPINFO_CALL_FAILED, e.getMessage());
        }
        String url = baseUrl + "/" + encodedIp + "?token=" + token;

        try (HttpResponse response = HttpUtil.createGet(url)
                .timeout(timeoutMillis)
                .execute()) {
            String body = response.body();
            return JsonUtils.parseTree(body);
        } catch (Exception e) {
            log.error("[IpInfoClient][fetchIpInfo] ip({}) 调用 ipinfo 异常", ip, e);
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IPINFO_CALL_FAILED, e.getMessage());
        }
    }

}

