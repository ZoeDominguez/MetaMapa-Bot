package com.metamapa.telegram.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.List;
import java.util.Map;

@Component
public class VerHechoHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;

    public VerHechoHandler(RestTemplate rest) {
        this.rest = rest;

        // ENV real
        String url = System.getenv("FUENTES_API_URL");
        // .env
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
        return command != null && command.trim().toLowerCase().startsWith("/hecho ");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendMessage handle(Update u) {
        Long chatId = u.getMessage().getChatId();
        String[] parts = u.getMessage().getText().trim().split("\\s+");
        if (parts.length < 2) return new SendMessage(chatId.toString(), "Uso: /hecho <id>");

        String id = parts[1].trim();

        try {
            Map<String, Object> h = (Map<String, Object>) (Map<?, ?>)
                    rest.getForObject(baseUrl + "/hecho/" + id, Map.class);

            if (h == null || h.isEmpty())
                return new SendMessage(chatId.toString(), "No encontré el hecho " + id + ".");

            String titulo = s(h.get("titulo"), "(sin título)");
            String nombreColeccion = s(h.get("nombreColeccion"), null);
            String categoria = s(h.get("categoria"), null);
            String ubicacion = s(h.get("ubicacion"), null);
            String fecha = s(h.get("fecha"), null);
            String origen = s(h.get("origen"), null);

            StringBuilder sb = new StringBuilder();
            sb.append("*").append(titulo).append("*\n");
            sb.append("ID: ").append(s(h.get("id"), id)).append("\n");
            if (nombreColeccion != null) sb.append("Colección: ").append(nombreColeccion).append("\n");
            if (categoria != null)       sb.append("Categoría: ").append(categoria).append("\n");
            if (ubicacion != null)       sb.append("Ubicación: ").append(ubicacion).append("\n");
            if (fecha != null)           sb.append("Fecha: ").append(fecha).append("\n");
            if (origen != null)          sb.append("Origen: ").append(origen).append("\n");

            ResponseEntity<List> r = rest.getForEntity(baseUrl + "/hecho/" + id + "/pdis", List.class);
            List<Map<String, Object>> pdis = (List<Map<String, Object>>) (List<?>) r.getBody();

            if (pdis != null && !pdis.isEmpty()) {
                sb.append("\n*PDIs (").append(pdis.size()).append("):*\n");
                int n = 1;
                for (Map<String, Object> p : pdis) {
                    String pdiId   = s(p.get("id"), "-");
                    String descr   = s(p.get("descripcion"), "(sin descripción)");
                    String lugar   = s(p.get("lugar"), null);
                    String momento = s(p.get("momento"), null);
                    String cont    = s(p.get("contenido"), null);

                    sb.append(n++).append(". ").append(descr).append("\n");
                    sb.append("   ID: ").append(pdiId).append("\n");
                    if (lugar != null)   sb.append("   Lugar: ").append(lugar).append("\n");
                    if (momento != null) sb.append("   Momento: ").append(momento).append("\n");
                    if (cont != null) {
                        String l = cont.trim().toLowerCase();
                        boolean isUrl = l.startsWith("http://") || l.startsWith("https://");
                        boolean isImg = isUrl && (l.endsWith(".jpg")||l.endsWith(".jpeg")||l.endsWith(".png")||l.endsWith(".gif")||l.endsWith(".webp"));
                        if (isImg) sb.append("   🖼️ ").append(cont).append("\n");
                        else if (isUrl) sb.append("   🔗 ").append(cont).append("\n");
                        else sb.append("   Contenido: ").append(cont).append("\n");
                    }
                }
            } else {
                sb.append("\n*PDIs:* (no hay)\n");
            }

            SendMessage msg = new SendMessage(chatId.toString(), sb.toString());
            msg.setParseMode("Markdown");
            msg.setDisableWebPagePreview(false);
            return msg;

        } catch (Exception e) {
            return new SendMessage(chatId.toString(), "Error al obtener el hecho " + id + ": " + e.getMessage());
        }
    }

    private static String s(Object o, String def) { return o == null ? def : String.valueOf(o); }
}
