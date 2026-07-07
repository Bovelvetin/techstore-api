package cl.techstore.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditPublisherService {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisherService.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    public AuditPublisherService() {
        this.sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Async
    public void publishAuditEvent(String accion, Long productoId, String nombre, String usuario) {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("aws.sqs.queue-url no configurada, se omite el evento de auditoria");
            return;
        }
        try {
            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("accion", accion);
            evento.put("productoId", productoId);
            evento.put("nombre", nombre);
            evento.put("usuario", usuario);
            evento.put("fecha", Instant.now().toString());

            String json = objectMapper.writeValueAsString(evento);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(json)
                    .build());

            log.info("Evento de auditoria publicado: {}", json);
        } catch (Exception e) {
            log.error("Error publicando evento de auditoria a SQS", e);
        }
    }
}
