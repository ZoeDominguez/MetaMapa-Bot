package com.metamapa.telegram.handler;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgregarPdiHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;

    public AgregarPdiHandler(RestTemplate rest) {
        this.rest = rest;

        // 1) ENV real
        String url = System.getenv("FUENTES_API_URL");
        // 2) .env (para desarrollo)
        if (url == null || url.isBlank()) {
            Dotenv d = Dotenv.configure()
                    .ignoreIfMissing()  // no rompe si no está el archivo
                    .load();
            url = d.get("FUENTES_API_URL");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Falta FUENTES_API_URL (env var o .env)");
        }
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length()-1) : url;
    }

    @Override public boolean canHandle(String command) {
        return command != null && command.trim().toLowerCase().startsWith("/agregar_pdi");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendMessage handle(Update u) {
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText().trim();

        String usage = "Uso:\n/agregar_pdi <hechoId> | <descripcion> | [lugar] | [momentoISO] | [contenido]";
        String[] cmdAndArgs = text.split("\\s+", 2);
        if (cmdAndArgs.length < 2) return new SendMessage(chatId.toString(), usage);

        String[] raw = cmdAndArgs[1].split("\\|");
        String hechoId     = raw.length > 0 ? nz(raw[0]) : null;
        String descripcion = raw.length > 1 ? nz(raw[1]) : null;
        String lugar       = raw.length > 2 ? nz(raw[2]) : null;
        String momentoIso  = raw.length > 3 ? nz(raw[3]) : null;
        String contenido   = raw.length > 4 ? nz(raw[4]) : null;

        if (hechoId == null || descripcion == null) return new SendMessage(chatId.toString(), usage);

        try {
            Map<String,Object> body = new HashMap<>();
            body.put("hechoId", hechoId);
            body.put("descripcion", descripcion);
            if (lugar != null)      body.put("lugar", lugar);
            if (momentoIso != null) body.put("momento", momentoIso);
            if (contenido != null)  body.put("contenido", contenido);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<Map> resp = rest.postForEntity(baseUrl + "/pdis", new HttpEntity<>(body, headers), Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                Map<String,Object> creado = resp.getBody();
                String pdiId = String.valueOf(creado != null ? creado.getOrDefault("id", creado.get("pdiId")) : "?");
                String ok = "PDI agregado al hecho " + hechoId + "\nPDI ID: " + pdiId + "\nDescripción: " + descripcion;
                return new SendMessage(chatId.toString(), ok);
            }
            return new SendMessage(chatId.toString(), "Error " + resp.getStatusCode());

        } catch (Exception e) {
            return new SendMessage(chatId.toString(), "No pude agregar el PDI.\n" + e.getMessage());
        }
    }

    private static String nz(String s){ return (s==null)?null : s.trim().isEmpty()?null : s.trim(); }
}
