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
public class AgregarHechoHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;

    public AgregarHechoHandler(RestTemplate rest) {
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
        return command != null && command.trim().toLowerCase().startsWith("/agregar_hecho");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendMessage handle(Update u) {
        Long chatId = u.getMessage().getChatId();
        String text = u.getMessage().getText().trim();

        String usage = "Uso:\n/agregar_hecho <coleccion> | <titulo> | [categoria] | [ubicacion] | [fechaISO] | [origen]";
        String[] cmdAndArgs = text.split("\\s+", 2);
        if (cmdAndArgs.length < 2) return new SendMessage(chatId.toString(), usage);

        String[] raw = cmdAndArgs[1].split("\\|");
        String coleccion = raw.length > 0 ? nz(raw[0]) : null;
        String titulo    = raw.length > 1 ? nz(raw[1]) : null;
        String categoria = raw.length > 2 ? nz(raw[2]) : null;
        String ubicacion = raw.length > 3 ? nz(raw[3]) : null;
        String fechaIso  = raw.length > 4 ? nz(raw[4]) : null;
        String origen    = raw.length > 5 ? nz(raw[5]) : null;

        if (coleccion == null || titulo == null) return new SendMessage(chatId.toString(), usage);

        try {
            Map<String,Object> body = new HashMap<>();
            body.put("nombre_coleccion", coleccion);
            body.put("titulo", titulo);
            if (categoria != null) body.put("categoria", categoria);
            if (ubicacion != null) body.put("ubicacion", ubicacion);
            if (fechaIso  != null) body.put("fecha", fechaIso);
            if (origen    != null) body.put("origen", origen);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<Map> resp = rest.postForEntity(baseUrl + "/hecho", new HttpEntity<>(body, headers), Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                Map<String,Object> creado = resp.getBody();
                String id = String.valueOf(creado != null ? creado.getOrDefault("id", creado.get("hechoId")) : "?");
                String ok = "Hecho creado\nID: " + id + "\nTítulo: " + titulo + "\nColección: " + coleccion;
                return new SendMessage(chatId.toString(), ok);
            }
            return new SendMessage(chatId.toString(), "Error " + resp.getStatusCode());

        } catch (Exception e) {
            return new SendMessage(chatId.toString(), "No pude crear el hecho.\n" + e.getMessage());
        }
    }

    private static String nz(String s){ return (s==null)?null : s.trim().isEmpty()?null : s.trim(); }
}
