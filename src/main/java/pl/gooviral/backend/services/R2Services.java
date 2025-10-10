package pl.gooviral.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

@Service
public class R2Services {

    @Value("${cloudflare.account_id}")
    private String accountID;

    @Value("${cloudflare.r2.access_key}")
    private String accessKey;

    @Value("${cloudflare.r2.secret_key}")
    private String secretKey;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.link_days_valid}")
    private String linkDaysValid;

    @Value("${cloudflare.r2.file_name}")
    private String fileName;

    public Mono<String> getDownloadUrl() {
        return Mono.fromCallable(() -> {
            Region region = Region.of("auto");
            String endpoint = "https://%s.r2.cloudflarestorage.com".formatted(accountID);

            AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

            try (S3Presigner presigner = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(region)
                .endpointOverride(URI.create(endpoint))
                .build()) {

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofDays(Integer.parseInt(linkDaysValid)))
                    .build();

                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
                return presignedRequest.url().toString();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }


}
