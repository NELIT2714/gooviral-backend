package pl.gooviral.backend.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.UnsupportedEncodingException;
import java.time.Year;

@Service
public class EmailService {

    @Value("${spring.mail.admin_mail}")
    private String adminMail;

    @Value("${spring.mail.admin_personal}")
    private String adminPersonal;

    @Value("${spring.mail.mail_subject}")
    private String subject;

    @Value("${cloudflare.r2.link_days_valid}")
    private String linkDaysValid;

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendDownloadLink(String email, String downloadUrl) {
        Context userContext = getContext(downloadUrl);
        String userHtml = templateEngine.process("send-link", userContext);

        Mono.fromRunnable(() -> sendEmail(email, subject, userHtml))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> logger.error("Failed to send order email", e))
            .subscribe();
    }

    private Context getContext(String downloadUrl) {
        Context userContext = new Context();
        userContext.setVariable("downloadLink", downloadUrl);
        userContext.setVariable("currentYear", Year.now());
        userContext.setVariable("linkDaysValid", linkDaysValid);
        return userContext;
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(adminMail, adminPersonal);
            // helper.setReplyTo("kontakt@oliwa-sklep.pl");

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

}
