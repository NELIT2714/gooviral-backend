package app.gooviral.backend.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${payments.stripe.secret_key}")
    private String secretKey;

    @Value("${payments.stripe.webhook_secret_key}")
    private String webhookSecretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    public String getWebhookSecretKey() {
        return webhookSecretKey;
    }
}
