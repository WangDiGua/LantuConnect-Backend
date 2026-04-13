package com.lantu.connect.gateway.protocol;

import org.springframework.stereotype.Component;

@Component
public class BailianCompatibleAdapter extends OpenAiCompatibleAdapter {
    @Override
    public String protocol() {
        return "bailian_compatible";
    }
}

