package com.lantu.connect.common.captcha.controller;

import com.lantu.connect.common.captcha.CaptchaResult;
import com.lantu.connect.common.captcha.CaptchaService;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RedisAuthRateLimiter;
import com.lantu.connect.common.web.ClientIpResolver;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;
    private final ClientIpResolver clientIpResolver;
    private final RedisAuthRateLimiter redisAuthRateLimiter;

    @GetMapping("/generate")
    @RateLimiter(name = "captchaGenerate")
    public R<CaptchaResult> generate(HttpServletRequest http) {
        redisAuthRateLimiter.checkCaptchaGenerate(clientIpResolver.resolve(http));
        return R.ok(captchaService.generate());
    }

    @PostMapping("/verify")
    public R<Boolean> verify(@RequestParam String captchaId, @RequestParam String code) {
        return R.ok(captchaService.verify(captchaId, code));
    }
}
