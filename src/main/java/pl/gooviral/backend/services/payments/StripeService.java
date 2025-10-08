package pl.gooviral.backend.services.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentLink;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentLinkCreateParams;
import org.springframework.stereotype.Service;
import pl.gooviral.backend.config.StripeConfig;
import reactor.core.publisher.Mono;

@Service
public class StripeService {

    private final StripeConfig stripeConfig;

    public StripeService(StripeConfig stripeConfig) {
        this.stripeConfig = stripeConfig;
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

        String customerEmail = null;
        if (node.has("customer_details") && node.get("customer_details").has("email")) {
            customerEmail = node.get("customer_details").get("email").asText();
        }
        if (customerEmail == null && node.has("customer_email")) {
            customerEmail = node.get("customer_email").asText();
        }

        System.out.println("✅ Покупатель: " + customerEmail);
    }
}