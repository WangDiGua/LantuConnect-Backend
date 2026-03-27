package com.lantu.connect.common.service;

public interface SmsService {

    void send(String phone, String content);

    void sendVerifyCode(String phone, String code);
}
