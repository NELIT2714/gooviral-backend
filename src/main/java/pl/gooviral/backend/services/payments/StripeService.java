package pl.gooviral.backend.services.payments;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentLink;
import com.stripe.param.PaymentLinkCreateParams;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StripeService {

    public Mono<PaymentLink> createPayment() {
        return Mono.fromCallable(() -> {
            PaymentLinkCreateParams params = PaymentLinkCreateParams.builder()
                .addLineItem(
                    PaymentLinkCreateParams.LineItem.builder()
                        .setPrice("price_1SEV8iIUQlx8lp2SeHPxHU0e")
                        .setQuantity(1L)
                        .build()
                )
                .setCurrency("USD")
                .build();

            return PaymentLink.create(params);
        });
    }
}