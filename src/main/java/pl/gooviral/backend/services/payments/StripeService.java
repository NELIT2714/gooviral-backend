package pl.gooviral.backend.services.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentLink;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentLinkCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.gooviral.backend.config.StripeConfig;
import pl.gooviral.backend.services.EmailService;
import pl.gooviral.backend.services.R2Services;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    private final StripeConfig stripeConfig;
    private final R2Services r2Services;
    private final EmailService emailService;

    public StripeService(StripeConfig stripeConfig, R2Services r2Services, EmailService emailService) {
        this.stripeConfig = stripeConfig;
        this.r2Services = r2Services;
        this.emailService = emailService;
    }

    public Mono<PaymentLink> createPayment() {
        return Mono.fromCallable(() -> {
            PaymentLinkCreateParams params = PaymentLinkCreateParams.builder()
                .addLineItem(
                    PaymentLinkCreateParams.LineItem.builder()
                        .setPrice("price_1SEV8iIUQlx8lp2SeHPxHU0e")
                        .setQuantity(1L)
                        .build()
                )
                .setAfterCompletion(
                    PaymentLinkCreateParams.AfterCompletion.builder()
                        .setType(PaymentLinkCreateParams.AfterCompletion.Type.REDIRECT)
                        .setRedirect(
                            PaymentLinkCreateParams.AfterCompletion.Redirect.builder()
                                .setUrl("https://gooviral.app/")
                                .build()
                        )
                        .build()
                )
                .setCurrency("USD")
                .build();

            return PaymentLink.create(params);
        });
    }

    public void handleEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecretKey());
        } catch (SignatureVerificationException e) {
            throw new RuntimeException("Invalid Stripe signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            handleCheckoutSessionCompleted(event);
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        ObjectMapper mapper = new ObjectMapper();
        String rawJson = event.getDataObjectDeserializer().getRawJson();

        JsonNode node;
        try {
            node = mapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        final String customerEmail;
        if (node.has("customer_details") && node.get("customer_details").has("email")) {
            customerEmail = node.get("customer_details").get("email").asText();
        } else if (node.has("customer_email")) {
            customerEmail = node.get("customer_email").asText();
        } else {
            customerEmail = null;
        }

        String paymentIntentId = node.has("payment_intent") ? node.get("payment_intent").asText() : null;

        if (customerEmail != null) {
            r2Services.getDownloadUrl()
                .subscribe(downloadUrl ->
                        emailService.sendDownloadLink(customerEmail, downloadUrl),
                    error -> logger.error("Failed to generate download URL", error)
                );
        } else {
            logger.warn("Customer email not found in Stripe event, issuing refund");

            if (paymentIntentId != null) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("payment_intent", paymentIntentId);

                    Refund refund = Refund.create(params);
                    System.out.println(refund);
                    logger.info("Refund issued for payment intent: {}", paymentIntentId);
                } catch (StripeException e) {
                    logger.error("Failed to issue refund for payment intent: {}", paymentIntentId, e);
                }
            } else {
                logger.error("Payment intent not found, cannot issue refund");
            }
        }
    }


}