package com.metamapa.telegram.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

@Component
public class SolicitarBorradoHandler implements BotCommandHandler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();


    private HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception {
        int attempts = 0;
        int maxAttempts = 3;
        long backoffMs = 800; // 0.8s → 1.6s → 3.2s

        while (true) {
            attempts++;
            try {
                HttpRequest withTimeout = HttpRequest.newBuilder(req.uri())
                        .timeout(Duration.ofSeconds(25)) // <-- antes tenías 10s
                        .method(req.method(), req.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                        .headers(req.headers().map().entrySet().stream()
                                .flatMap(e -> e.getValue().stream().map(v -> new String[]{e.getKey(), v}))
                                .flatMap(Arrays::stream) // flatten pairs
                                .toArray(String[]::new))
                        .build();

                return HTTP.send(withTimeout, HttpResponse.BodyHandlers.ofString());

            } catch (java.net.http.HttpTimeoutException te) {
                if (attempts >= maxAttempts) throw te;
                Thread.sleep(backoffMs);
                backoffMs *= 2;
            }
        }
    }


    private final String baseUrl =
            System.getenv().getOrDefault("SOLICITUDES_BASE_URL",
                    "https://solicitudes-tpdds.onrender.com/solicitudes");
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public boolean canHandle(String command) {
        if (command == null) return false;
        String token = command.trim().split("\\s+")[0].toLowerCase();
        int at = token.indexOf('@');
        if (at > 0) token = token.substring(0, at);
        return token.equals("/solicitar_borrado") || token.equals("/solicitud_borrado");
    }

    @Override
    public SendMessage handle(Update update) {
        var chatId = update.getMessage().getChatId().toString();
        var text   = update.getMessage().getText().trim();

        var parts = text.split("\\s+");
        if (parts.length < 3) {
            return new SendMessage(chatId,
                    "Uso: /solicitar_borrado <hechoId> <descripcion (≥500 caracteres)>");
        }
        var hechoId = parts[1];
        var descripcion = text.substring(text.indexOf(hechoId) + hechoId.length()).trim();

        if (descripcion.length() < 500) {
            return new SendMessage(chatId,
                    "❗ La descripción debe tener al menos 500 caracteres.");
        }

        try {
            String json = String.format(
                    "{\"descripcion\":\"%s\",\"estado\":\"CREADA\",\"hecho_id\":\"%s\"}",
                    descripcion.replace("\"","\\\""),
                    hechoId
            );

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl)) // POST /solicitudes
                    .header("Content-Type","application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            var resp = sendWithRetry(req);
            int status = resp.statusCode();
            String body = resp.body();

            if (status == 200 || status == 201) {
                JsonNode node = MAPPER.readTree(body);

                String id = node.path("id").asText("¿sin id?");
                String estado = node.path("estado").asText("DESCONOCIDO");
                String hechoResp = node.path("hechoId").asText(hechoId);

                String msg = "📨 Solicitud creada con éxito.\n\n"
                        + "📝 ID: " + id + "\n"
                        + "🔗 Hecho: " + hechoResp + "\n"
                        + "📌 Estado inicial: " + estado + "\n\n"
                        + "Tu solicitud quedó registrada correctamente.";

                return new SendMessage(chatId, msg);
            } else {
                // Intentamos extraer un mensaje de error amigable
                String errorMsg = extraerMensajeError(body);
                String msg = "❗ No se pudo crear la solicitud (HTTP " + status + ").\n"
                        + errorMsg;
                return new SendMessage(chatId, msg);
            }

        } catch (Exception e) {
            return new SendMessage(chatId,
                    "❗ Error al crear la solicitud: " + e.getMessage());
        }
    }

    private String extraerMensajeError(String body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) { }
        return body != null && !body.isBlank() ? body : "Error desconocido.";
    }

}
