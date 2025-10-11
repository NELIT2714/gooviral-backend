package app.gooviral.backend.controllers;

import app.gooviral.backend.dto.NewFeedback;
import app.gooviral.backend.services.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> createFeedback(@Valid @RequestBody NewFeedback feedbackDTO) {
        return Mono.fromRunnable(() -> feedbackService.sendFeedback(feedbackDTO))
            .subscribeOn(Schedulers.boundedElastic())
            .then(Mono.fromSupplier(() -> ResponseEntity.ok(Map.of("status", true))));
    }
}
