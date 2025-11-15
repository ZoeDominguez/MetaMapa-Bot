package com.metamapa.telegram.handler;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VerHechoHandler implements BotCommandHandler {

    private final RestTemplate rest;
    private final String baseUrl;
    private static final DateTimeFormatter MOMENTO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    private static final int MAX_MESSAGE_LENGTH = 3500;

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
    public SendMessage handle(Update u) {
        return null;
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<BotApiMethod<?>> handleBatch(Update u) {
        Long chatId = u.getMessage().getChatId();
        List<BotApiMethod<?>> mensajes = new ArrayList<>();
        
        String[] parts = u.getMessage().getText().trim().split("\\s+");
        if (parts.length < 2) {
            mensajes.add(new SendMessage(chatId.toString(), "Uso: /hecho <id>"));
            return mensajes;
        }

        String id = parts[1].trim();

        try {

            Map<String, Object> h = (Map<String, Object>) (Map<?, ?>)
                    rest.getForObject(baseUrl + "/hecho/" + id, Map.class);

            if (h == null || h.isEmpty()) {
                mensajes.add(new SendMessage(chatId.toString(), "No encontré el hecho " + id + "."));
                return mensajes;
            }

            String titulo = s(h.get("titulo"), "(sin título)");
            String nombreColeccion = s(h.get("nombreColeccion"), null);
            String categoria = s(h.get("categoria"), null);
            String ubicacion = s(h.get("ubicacion"), null);
            String fecha = s(h.get("fecha"), null);
            String origen = s(h.get("origen"), null);


            StringBuilder sbHeader = new StringBuilder();
            sbHeader.append("<b>").append(esc(titulo)).append("</b>\n");
            sbHeader.append("ID: ").append(esc(s(h.get("id"), id))).append("\n");
            if (nombreColeccion != null) sbHeader.append("Colección: ").append(esc(nombreColeccion)).append("\n");
            if (categoria != null)       sbHeader.append("Categoría: ").append(esc(categoria)).append("\n");
            if (ubicacion != null)       sbHeader.append("Ubicación: ").append(esc(ubicacion)).append("\n");
            if (fecha != null)           sbHeader.append("Fecha: ").append(esc(fecha)).append("\n");
            if (origen != null)          sbHeader.append("Origen: ").append(esc(origen)).append("\n");

            SendMessage msgHeader = new SendMessage(chatId.toString(), sbHeader.toString());
            msgHeader.setParseMode("HTML");
            mensajes.add(msgHeader);

            boolean pdisOk = false;
            try {
                ResponseEntity<List> r = rest.getForEntity(baseUrl + "/hecho/" + id + "/pdis", List.class);
                List<Map<String, Object>> pdis = (List<Map<String, Object>>) (List<?>) r.getBody();

                if (pdis != null && !pdis.isEmpty()) {
                    pdisOk = true;
                    

                    StringBuilder sbPdis = new StringBuilder();
                    sbPdis.append("\n<b>PDIs (").append(pdis.size()).append("):</b>\n");

                    int n = 1;
                    for (Map<String, Object> p : pdis) {
                        StringBuilder unPdi = new StringBuilder();
                        
                        String pdiId     = s(p.get("id"), "-");
                        String descr     = s(p.get("descripcion"), "(sin descripción)");
                        String lugar     = s(p.get("lugar"), null);
                        Object momento   = p.get("momento");
                        String urlImagen = s(p.get("url_imagen"), null);
                        String textoImagen = s(p.get("texto_imagen"), null);
                        Object etiquetas = p.get("etiquetas");

                        unPdi.append(n++).append(". ").append(esc(descr)).append("\n");
                        unPdi.append("   ID: ").append(esc(pdiId)).append("\n");
                        if (lugar != null)   unPdi.append("   Lugar: ").append(esc(lugar)).append("\n");
                        if (momento != null) unPdi.append("   Momento: ").append(esc(formatMomento(momento))).append("\n");

                        if (textoImagen != null && !textoImagen.isBlank()) {
                            unPdi.append("   📝 ").append(esc(textoImagen)).append("\n");
                        }

                        if (etiquetas instanceof List<?> list && !list.isEmpty()) {
                            unPdi.append("   🏷️ Etiquetas: ");
                            unPdi.append(esc(String.join(", ", list.stream().map(Object::toString).toList()))).append("\n");
                        }

                        if (urlImagen != null && looksLikeUrl(urlImagen)) {
                            unPdi.append(urlImagen).append("\n");
                        }
                        unPdi.append("\n");

                        // Si agregar este PDI supera el límite, enviamos lo que tenemos y empezamos otro mensaje
                        if (sbPdis.length() + unPdi.length() > MAX_MESSAGE_LENGTH) {
                            SendMessage msgChunk = new SendMessage(chatId.toString(), sbPdis.toString());
                            msgChunk.setParseMode("HTML");
                            msgChunk.setDisableWebPagePreview(false);
                            mensajes.add(msgChunk);

                            // Reiniciamos buffer
                            sbPdis = new StringBuilder("(continuación PDIs...)\n\n");
                        }
                        
                        // Agregamos el PDI al buffer actual
                        sbPdis.append(unPdi);
                    }

                    // Al terminar el loop, si quedó algo en el buffer, lo agregamos
                    if (sbPdis.length() > 0) {
                        SendMessage msgFinal = new SendMessage(chatId.toString(), sbPdis.toString());
                        msgFinal.setParseMode("HTML");
                        msgFinal.setDisableWebPagePreview(false);
                        mensajes.add(msgFinal);
                    }
                }
            } catch (HttpStatusCodeException e) {
                mensajes.add(new SendMessage(chatId.toString(), "\n⚠️ No se pudieron cargar los puntos de interés (Información no disponible)."));
            } catch (ResourceAccessException e) {
                mensajes.add(new SendMessage(chatId.toString(), "\n⚠️ El servicio de PDIs está tardando demasiado en responder."));
            } catch (Exception e) {
                mensajes.add(new SendMessage(chatId.toString(), "\n⚠️ Ocurrió un problema al consultar los detalles adicionales."));
            }

            if (!pdisOk && mensajes.size() == 1) {
                SendMessage msgNoPdis = new SendMessage(chatId.toString(), "\n<b>PDIs:</b> (no hay)");
                msgNoPdis.setParseMode("HTML");
                mensajes.add(msgNoPdis);
            }

            return mensajes;

        } catch (Exception e) {
            return List.of(new SendMessage(chatId.toString(),
                    "Error al obtener el hecho " + id + ": " + e.getMessage()));
        }
    }

    // helpers 

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