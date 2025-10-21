package com.metamapa.telegram.bot;

import com.metamapa.telegram.DTO.HechoDTO;
import com.metamapa.telegram.handler.BotCommandHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.List;

@Component
public class AgregadorBot implements BotCommandHandler {

    private final RestTemplate restTemplate;
    private final String API_BASE_URL = "https://two025-tp-entrega-2-zoedominguez-bsuh.onrender.com/colecciones/";

    @Autowired
    public AgregadorBot(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean canHandle(String command) {
        return command != null && command.startsWith("/hechos");
    }

    @Override
    public SendMessage handle(Update update) {
        final String messageTextReceived = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        String responseText;
        String coleccionId = "";

        try {
            coleccionId = messageTextReceived.substring(8).trim();

            if (coleccionId.isEmpty()) {
                responseText = "Por favor, especifica un ID de colección. Ejemplo: /hechos ejemplo";
            } else {

                String url = API_BASE_URL + coleccionId + "/hechos";

                HechoDTO[] hechosArray = restTemplate.getForObject(url, HechoDTO[].class);

                if (hechosArray == null || hechosArray.length == 0) {
                    responseText = "No se encontraron hechos para la colección: '" + coleccionId + "'.";
                } else {
                    List<HechoDTO> hechos = Arrays.asList(hechosArray);
                    StringBuilder sb = new StringBuilder("Hechos para '" + coleccionId + "':\n\n");
                    for (HechoDTO hecho : hechos) {
                        sb.append("• Título: ").append(hecho.titulo()).append("\n");
                        sb.append("  (ID: ").append(hecho.id()).append(")\n\n");
                    }
                    responseText = sb.toString();
                }
            }
        } catch (HttpClientErrorException.NotFound e) {
            responseText = "Error: No se encontró una colección con el ID: '" + coleccionId + "'.";
        } catch (RestClientException e) {
            responseText = "Error de conexión: No se pudo contactar al servidor de colecciones.";
            e.printStackTrace();
        } catch (Exception e) {
            responseText = "Ocurrió un error inesperado al procesar tu solicitud.";
            e.printStackTrace();
        }

        message.setText(responseText);
        return message;
    }
}