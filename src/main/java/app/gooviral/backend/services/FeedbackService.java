package app.gooviral.backend.services;

import app.gooviral.backend.dto.NewFeedback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Year;

@Service
public class FeedbackService {

    @Value("${spring.mail.admin_mail}")
    private String adminMail;

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);

    private final EmailService emailService;
    private final SpringTemplateEngine templateEngine;

    public FeedbackService(EmailService emailService, SpringTemplateEngine templateEngine) {
        this.emailService = emailService;
        this.templateEngine = templateEngine;
    }

    public void sendFeedback(NewFeedback feedbackDTO) {
        Context userContext = getContext(feedbackDTO);
        String userHtml = templateEngine.process("feedback", userContext);

        Mono.fromRunnable(() -> emailService.sendEmail(adminMail, "New feedback", userHtml))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> logger.error("Failed to send email", e))
            .subscribe();
    }

    private Context getContext(NewFeedback feedbackDTO) {
        Context userContext = new Context();
        userContext.setVariable("name", feedbackDTO.getName());
        userContext.setVariable("email", feedbackDTO.getEmail());

        String messageHtml = feedbackDTO.getMessage().replace("\n", "<br>").replace("\r", "");

        userContext.setVariable("message", messageHtml);
        return userContext;
    }

}
