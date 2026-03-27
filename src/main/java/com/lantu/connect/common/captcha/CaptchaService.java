package com.lantu.connect.common.captcha;

public interface CaptchaService {

    CaptchaResult generate();

    boolean verify(String captchaId, String code);
}
