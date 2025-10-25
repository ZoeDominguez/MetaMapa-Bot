package com.metamapa.telegram.handler;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class VerHechoHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;
    private static final DateTimeFormatter MOMENTO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public VerHechoHandler(RestTemplate rest) {
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
        return command != null && command.trim().toLowerCase().startsWith("/hecho ");
    }

    @Override
    @SuppressWarnings("unchecked")
    public SendMessage handle(Update u) {
        Long chatId = u.getMessage().getChatId();
        String[] parts = u.getMessage().getText().trim().split("\\s+");
        if (parts.length < 2)
            return new SendMessage(chatId.toString(), "Uso: /hecho <id>");

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
            sb.append("<b>").append(esc(titulo)).append("</b>\n");
            sb.append("ID: ").append(esc(s(h.get("id"), id))).append("\n");
            if (nombreColeccion != null) sb.append("Colección: ").append(esc(nombreColeccion)).append("\n");
            if (categoria != null)       sb.append("Categoría: ").append(esc(categoria)).append("\n");
            if (ubicacion != null)       sb.append("Ubicación: ").append(esc(ubicacion)).append("\n");
            if (fecha != null)           sb.append("Fecha: ").append(esc(fecha)).append("\n");
            if (origen != null)          sb.append("Origen: ").append(esc(origen)).append("\n");

            boolean pdisOk = false;
            try {
                ResponseEntity<List> r = rest.getForEntity(baseUrl + "/hecho/" + id + "/pdis", List.class);
                List<Map<String, Object>> pdis = (List<Map<String, Object>>) (List<?>) r.getBody();

                if (pdis != null && !pdis.isEmpty()) {
                    pdisOk = true;
                    sb.append("\n<b>PDIs (").append(pdis.size()).append("):</b>\n");
                    int n = 1;
                    for (Map<String, Object> p : pdis) {
                        String pdiId     = s(p.get("id"), "-");
                        String descr     = s(p.get("descripcion"), "(sin descripción)");
                        String lugar     = s(p.get("lugar"), null);
                        Object momento   = p.get("momento");
                        String urlImagen = s(p.get("url_imagen"), null);
                        String textoImagen = s(p.get("texto_imagen"), null);
                        Object etiquetas = p.get("etiquetas");

                        sb.append(n++).append(". ").append(esc(descr)).append("\n");
                        sb.append("   ID: ").append(esc(pdiId)).append("\n");
                        if (lugar != null)   sb.append("   Lugar: ").append(esc(lugar)).append("\n");
                        if (momento != null) sb.append("   Momento: ").append(esc(formatMomento(momento))).append("\n");

                        if (textoImagen != null && !textoImagen.isBlank()) {
                            sb.append("   📝 ").append(esc(textoImagen)).append("\n");
                        }

                        if (etiquetas instanceof List<?> list && !list.isEmpty()) {
                            sb.append("   🏷️ Etiquetas: ");
                            sb.append(esc(String.join(", ", list.stream().map(Object::toString).toList()))).append("\n");
                        }

                        if (urlImagen != null && looksLikeUrl(urlImagen)) {
                            sb.append(urlImagen).append("\n");
                        }
                    }
                }
            } catch (HttpStatusCodeException e) {
                sb.append("\n<b>PDIs:</b> no disponibles (").append(e.getStatusCode().value()).append(")\n");
            } catch (ResourceAccessException e) {
                sb.append("\n<b>PDIs:</b> no disponibles (timeout/conexión)\n");
            } catch (Exception e) {
                sb.append("\n<b>PDIs:</b> no disponibles\n");
            }

            if (!pdisOk && !sb.toString().contains("<b>PDIs:</b>")) {
                sb.append("\n<b>PDIs:</b> (no hay)\n");
            }

            SendMessage msg = new SendMessage(chatId.toString(), sb.toString());
            msg.setParseMode("HTML");
            msg.setDisableWebPagePreview(false);
            return msg;

        } catch (Exception e) {
            return new SendMessage(chatId.toString(),
                    "Error al obtener el hecho " + id + ": " + e.getMessage());
        }
    }

    // --- helpers ---

    private static String s(Object o, String def) { return o == null ? def : String.valueOf(o); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String formatMomento(Object momento) {
        try {
            if (momento instanceof List<?> list && list.size() >= 5) {
                int year = (int) list.get(0);
                int month = (int) list.get(1);
                int day = (int) list.get(2);
                int hour = (int) list.get(3);
                int minute = (int) list.get(4);
                LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute);
                return MOMENTO_FMT.format(ldt);
            }
            return String.valueOf(momento);
        } catch (Exception e) {
            return String.valueOf(momento);
        }
    }

    private static boolean looksLikeUrl(String s) {
        String l = s == null ? "" : s.trim().toLowerCase();
        return l.startsWith("http://") || l.startsWith("https://");
    }
}
