package com.lantu.connect.common.captcha.controller;

import com.lantu.connect.common.captcha.CaptchaResult;
import com.lantu.connect.common.captcha.CaptchaService;
import com.lantu.connect.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/generate")
    public R<CaptchaResult> generate() {
        return R.ok(captchaService.generate());
    }

    @PostMapping("/verify")
    public R<Boolean> verify(@RequestParam String captchaId, @RequestParam String code) {
        return R.ok(captchaService.verify(captchaId, code));
    }
}
