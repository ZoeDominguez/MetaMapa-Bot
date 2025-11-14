package com.metamapa.telegram.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.metamapa.telegram.DTO.HechoDTO;

@Component
public class BuscarKeywordHandler implements BotCommandHandler {

    private final RestTemplate restTemplate;
    private final String API_URL = "https://metamapa-buscador.onrender.com/api/buscador/search";

    @Autowired
    public BuscarKeywordHandler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean canHandle(String command) {
        return command != null && command.startsWith("/buscar");
    }

    @Override
    public SendMessage handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        SendMessage response = new SendMessage();
        response.setChatId(chatId.toString());

        try {

            String keyword = extraerEntreComillas(text);

            if (keyword == null) {
                response.setText("Formato inválido. Ejemplos:\n" +
                        "/buscar \"incendio\"\n" +
                        "/buscar \"incendio\", tag: \"CABA\"");
                return response;
            }

            String tag = null;
            if (text.contains("tag:")) {
                tag = extraerTag(text);
            }

            String url = API_URL + "?keyword=" + keyword;
            if (tag != null) {
                url += "&tag=" + tag;
            }

            HechoDTO[] resultados = restTemplate.getForObject(url, HechoDTO[].class);

            if (resultados == null || resultados.length == 0) {
                response.setText("No se encontraron resultados para: \"" + keyword + "\"" +
                        (tag != null ? " con tag \"" + tag + "\"" : ""));
                return response;
            }

            StringBuilder sb = new StringBuilder("Resultados:\n\n");
            for (HechoDTO dto : resultados) {
                sb.append("• ").append(dto.titulo()).append("\n");
                if (dto.etiquetas() != null) {
                    sb.append("  Tags: ").append(dto.etiquetas()).append("\n");
                }
                sb.append("\n");
            }

            response.setText(sb.toString());
            return response;

        } catch (RestClientException e) {
            response.setText("Error conectando con el buscador.");
            return response;
        } catch (Exception e) {
            response.setText("Error procesando la búsqueda.");
            e.printStackTrace();
            return response;
        }
    }

    private String extraerEntreComillas(String text) {
        int i1 = text.indexOf("\"");
        int i2 = text.indexOf("\"", i1 + 1);
        if (i1 == -1 || i2 == -1) return null;
        return text.substring(i1 + 1, i2);
    }

    private String extraerTag(String text) {
        String lower = text.toLowerCase();
        int tagIndex = lower.indexOf("tag:");
        if (tagIndex == -1) return null;

        int i1 = text.indexOf("\"", tagIndex);
        int i2 = text.indexOf("\"", i1 + 1);
        if (i1 == -1 || i2 == -1) return null;

        return text.substring(i1 + 1, i2);
    }
}
