package app.gooviral.backend.controllers;

import app.gooviral.backend.services.payments.StripeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/v1/payments")
public class PaymentsController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsController.class);

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
            .onErrorResume(e -> {
                logger.error("Failed to create payment", e);
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "status", false
                )));
            });
    }

    @PostMapping("/stripe/webhook")
    public Mono<Void> handleWebhook(@RequestBody Mono<String> body,
                                    @RequestHeader("Stripe-Signature") String sigHeader) {
        return body.doOnNext(payload -> stripeService.handleEvent(payload, sigHeader))
            .then();
    }
}