package com.metamapa.telegram.handler;

import com.metamapa.telegram.DTO.HechoDTO;

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
            final String commandPrefix = "/hechos "; 

        if (messageTextReceived.startsWith(commandPrefix)) {
            
            coleccionId = messageTextReceived.substring(commandPrefix.length()).trim();

            if (coleccionId.isEmpty()) {
                responseText = "Por favor, especifica un ID (nombre) de colección. Ejemplo: /hechos miColeccion";
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

        } else if (messageTextReceived.equals("/hechos")) {
            responseText = "Por favor, especifica un ID (nombre) de colección. Ejemplo: /hechos miColeccion";
        
        } else {
            responseText = "Comando no válido. Usa: /hechos <idColeccion>";
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