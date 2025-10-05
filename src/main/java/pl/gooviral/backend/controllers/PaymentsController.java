package pl.gooviral.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gooviral.backend.services.payments.StripeService;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/v1/payments")
public class PaymentsController {

    private final StripeService stripeService;

    public PaymentsController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/stripe")
    public Mono<ResponseEntity<Map<String, Object>>> createPayment() {
        return stripeService.createPayment()
            .map(paymentLink -> {
                Map<String, Object> response = Map.of(
                    "status", true,
                    "payment_url", paymentLink.getUrl() + "?locale=en"
                );
                return ResponseEntity.ok(response);
            })
            .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body(Map.of(
                "status", false
            ))));
    }
}
