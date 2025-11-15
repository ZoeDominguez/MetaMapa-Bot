package com.metamapa.telegram.handler;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

@Component
public class AgregarPdiHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;

    public AgregarPdiHandler(RestTemplate rest) {
        this.rest = rest;

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .systemProperties()
                .load();

        String url = dotenv.get("FUENTES_API_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta variable FUENTES_API_URL en .env o entorno");
        }
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public boolean canHandle(String command) {
        return command != null && command.trim().toLowerCase().startsWith("/agregar_pdi");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendMessage handle(Update u) {
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText().trim();

        String usage = """
                Uso:
                /agregar_pdi <hechoId> | <descripcion> | [lugar] | [momentoISO] | [urlImagen]
                """;

        String[] cmdAndArgs = text.split("\\s+", 2);
        if (cmdAndArgs.length < 2)
            return new SendMessage(chatId.toString(), usage);

        String[] raw = cmdAndArgs[1].split("\\|");
        String hechoId      = raw.length > 0 ? nz(raw[0]) : null;
        String descripcion  = raw.length > 1 ? nz(raw[1]) : null;
        String lugar        = raw.length > 2 ? nz(raw[2]) : null;
        String momentoIso   = raw.length > 3 ? nz(raw[3]) : null;
        String urlImagen    = raw.length > 4 ? nz(raw[4]) : null;
        String textoImagen  = raw.length > 5 ? nz(raw[5]) : null;
        String etiquetasStr = raw.length > 6 ? nz(raw[6]) : null;

        if (hechoId == null || descripcion == null)
            return new SendMessage(chatId.toString(), usage);

        List<String> etiquetas = new ArrayList<>();
        if (etiquetasStr != null && !etiquetasStr.isBlank()) {
            for (String e : etiquetasStr.split(",")) {
                if (!e.trim().isEmpty()) etiquetas.add(e.trim());
            }
        }

        try {
            // cuerpo del request en snake_case
            Map<String, Object> body = new HashMap<>();
            body.put("hecho_id", hechoId);
            body.put("descripcion", descripcion);
            if (lugar != null)        body.put("lugar", lugar);
            if (momentoIso != null)   body.put("momento", momentoIso);
            if (urlImagen != null)    body.put("url_imagen", urlImagen);
            if (textoImagen != null)  body.put("texto_imagen", textoImagen);
            if (!etiquetas.isEmpty()) body.put("etiquetas", etiquetas);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<Map> resp = rest.postForEntity(
                    baseUrl + "/pdis", new HttpEntity<>(body, headers), Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> creado = resp.getBody();
                String pdiId = String.valueOf(
                        creado != null ? creado.getOrDefault("id", creado.get("pdi_id")) : "?");
                String ok = "PDI agregado al hecho " + hechoId +
                        "\nID: " + pdiId +
                        "\nDescripción: " + descripcion;
                return new SendMessage(chatId.toString(), ok);
            }
            return new SendMessage(chatId.toString(),
                    "❌ No se pudo agregar el PDI. Hubo un problema con el servidor remoto.");

        } catch (Exception e) {
            return new SendMessage(chatId.toString(),
                    "❌ Ocurrió un error inesperado al intentar guardar el PDI.");
        }
    }

    private static String nz(String s) {
        return (s == null) ? null : s.trim().isEmpty() ? null : s.trim();
    }
}
