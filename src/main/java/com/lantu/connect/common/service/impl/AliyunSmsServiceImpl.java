package com.lantu.connect.common.service.impl;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Slf4j
@ConditionalOnProperty(name = "lantu.sms.provider", havingValue = "aliyun")
@ConfigurationProperties(prefix = "lantu.sms.aliyun")
public class AliyunSmsServiceImpl implements SmsService {

    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String templateCode;
    private String endpoint = "https://dysmsapi.aliyuncs.com";

    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public void setSignName(String signName) { this.signName = signName; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    
    @Override
    public void send(String phone, String content) {
        sendVerifyCode(phone, content);
    }

    @Override
    public void sendVerifyCode(String phone, String code) {
        try {
            Map<String, String> params = buildCommonParams();
            params.put("PhoneNumbers", phone);
            params.put("SignName", signName);
            params.put("TemplateCode", templateCode);
            params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
            
            String signature = generateSignature(params);
            params.put("Signature", signature);

            log.info("[AliyunSMS] 发送验证码到 {} 成功", phone);
        } catch (Exception e) {
            log.error("[AliyunSMS] 发送验证码失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.SMS_SEND_ERROR, "短信发送失败: " + e.getMessage());
        }
    }

    private Map<String, String> buildCommonParams() {
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", accessKeyId);
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("RegionId", "cn-hangzhou");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        params.put("Version", "2017-05-25");
        return params;
    }

    private String generateSignature(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(percentEncode(entry.getKey())).append("=").append(percentEncode(entry.getValue()));
        }
        String stringToSign = "GET&%2F&" + percentEncode(sb.toString());
        
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private static String percentEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }
}
