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
public class CambiarEstadoHandler implements BotCommandHandler {

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
                // Aumentá el timeout de CADA request (render puede tardar en “despertar”)
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

        return token.equals("/cambiar_estado");
    }

    @Override
    public SendMessage handle(Update update) {
        var chatId = update.getMessage().getChatId().toString();
        var text = update.getMessage().getText().trim();

        // Formato: /cambiar_estado <solicitudId> <ACEPTADA|RECHAZADA|CREADA>
        var parts = text.split("\\s+");
        if (parts.length < 3) {
            return new SendMessage(chatId,
                    "Uso: /cambiar_estado <solicitudId> <ACEPTADA|RECHAZADA|CREADA>");
        }
        var solicitudId = parts[1];
        var estado = parts[2].toUpperCase();

        try {
            var uri = URI.create(baseUrl + "/" + solicitudId + "/estado?estado=" + estado);
            var req = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();

            var resp = sendWithRetry(req);
            int status = resp.statusCode();
            String body = resp.body();

            if (status == 200) {
                JsonNode node = MAPPER.readTree(body);

                String id = node.path("id").asText(solicitudId);
                String hechoId = node.path("hechoId").asText("desconocido");
                String nuevoEstado = node.path("estado").asText(estado);

                String msg = "✅ Estado actualizado correctamente.\n\n"
                        + "📝 Solicitud: " + id + "\n"
                        + "🔗 Hecho: " + hechoId + "\n"
                        + "📌 Nuevo estado: " + nuevoEstado + "\n";

                if ("ACEPTADA".equalsIgnoreCase(nuevoEstado)) {
                    msg += "\nEl buscador será actualizado en breve para ocultar este hecho.";
                }

                return new SendMessage(chatId, msg);

            } else if (status == 404) {
                return new SendMessage(chatId,
                        "❗ No se encontró la solicitud " + solicitudId + ".");
            } else if (status == 400) {
                String errorMsg = extraerMensajeError(body);
                return new SendMessage(chatId,
                        "❗ No se pudo cambiar el estado (HTTP 400).\n" + errorMsg);
            } else {
                String errorMsg = extraerMensajeError(body);
                return new SendMessage(chatId,
                        "❗ Error al cambiar estado (HTTP " + status + ").\n" + errorMsg);
            }

        } catch (Exception e) {
            return new SendMessage(chatId,
                    "❗ Error al cambiar estado: " + e.getMessage());
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