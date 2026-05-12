package com.devlil0.spending_system.service;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsappSendMsgService {

    public final RestClient restClient;

    @Value("${evolution.instance}")
    private String instance;

    public void sendText(String phone, String message) {
        var body = Map.of("number", phone, "text", message);

        restClient.post()
                .uri("/message/sendText/{instance}", instance)
                .body(body)
                .retrieve()
                .toBodilessEntity();

    }


}
