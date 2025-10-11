package app.gooviral.backend.services.payments;

import app.gooviral.backend.config.StripeConfig;
import app.gooviral.backend.services.EmailService;
import app.gooviral.backend.services.R2Services;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    @Value("${spring.mail.mail_subject}")
    private String subject;

    @Value("${payments.stripe.price_id}")
    private String priceId;

    @Value("${cloudflare.r2.link_days_valid}")
    private String linkDaysValid;

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    private final StripeConfig stripeConfig;
    private final R2Services r2Services;
    private final EmailService emailService;
    private final SpringTemplateEngine templateEngine;

    public StripeService(StripeConfig stripeConfig, R2Services r2Services, EmailService emailService, SpringTemplateEngine templateEngine) {
        this.stripeConfig = stripeConfig;
        this.r2Services = r2Services;
        this.emailService = emailService;
        this.templateEngine = templateEngine;
    }

    public Mono<PaymentLink> createPayment() {
        return Mono.fromCallable(() -> {
            PaymentLinkCreateParams params = PaymentLinkCreateParams.builder()
                .addLineItem(
                    PaymentLinkCreateParams.LineItem.builder()
                        .setPrice(priceId)
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
                .subscribe(downloadUrl -> sendDownloadLink(customerEmail, downloadUrl),
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

    public void sendDownloadLink(String email, String downloadUrl) {
        Context userContext = getContext(downloadUrl);
        String userHtml = templateEngine.process("send-link", userContext);

        Mono.fromRunnable(() -> emailService.sendEmail(email, subject, userHtml))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> logger.error("Failed to send email", e))
            .subscribe();
    }

    private Context getContext(String downloadUrl) {
        Context userContext = new Context();
        userContext.setVariable("downloadLink", downloadUrl);
        userContext.setVariable("currentYear", Year.now());
        userContext.setVariable("linkDaysValid", linkDaysValid);
        return userContext;
    }

}