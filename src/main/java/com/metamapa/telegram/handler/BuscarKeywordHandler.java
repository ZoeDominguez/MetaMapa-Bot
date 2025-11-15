package com.metamapa.telegram.handler;

import com.metamapa.telegram.DTO.HechoDTO;
import com.metamapa.telegram.DTO.PageResponse;
import com.metamapa.telegram.util.BotKeyboardUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
public class BuscarKeywordHandler implements BotCommandHandler {

    private final RestTemplate restTemplate;

    @Value("${metamapa.api.url}")
    private String API_URL;

    @Autowired
    public BuscarKeywordHandler(RestTemplate restTemplate) {
        System.out.println("--- INICIALIZANDO BuscarKeywordHandler ---");
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean canHandle(String command) {
        if (command != null && command.startsWith("/buscar")) return true;
        if (command != null && command.startsWith("BUSCAR|")) return true;
        return false;
    }

    @Override
    public BotApiMethod<?> handle(Update update) {
        if (update.hasCallbackQuery()) {
            return handleCallback(update);
        }
        return handleCommand(update);
    }

    private BotApiMethod<?> handleCommand(Update update) {

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        try {
            String keyword = extraerKeyword(text);
            
            if (keyword == null) {
                SendMessage msg = new SendMessage(chatId.toString(), "Formato inválido. Ejemplos:\n/buscar \"incendio\"\n/buscar incendio\n/buscar incendio, tag: \"CABA\"");
                return msg;
            }

            String tag = extraerTag(text);
     
            return buscarYResponder(chatId.toString(), keyword, tag, 0, false, 0);

        } catch (Exception e) {
    
            e.printStackTrace();
            return new SendMessage(chatId.toString(), "Error procesando búsqueda.");
        }
    }

    private BotApiMethod<?> handleCallback(Update update) {
        // (Lógica de callback... no la tocamos)
        String[] data = update.getCallbackQuery().getData().split("\\|");
        String keyword = data[1];
        String tag = data[2].equals("_") ? null : data[2];
        int page = Integer.parseInt(data[3]);
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        return buscarYResponder(chatId.toString(), keyword, tag, page, true, messageId);
    }

    private BotApiMethod<?> buscarYResponder(
            String chatId,
            String keyword,
            String tag,
            int page,
            boolean editMessage,
            Integer messageIdToEdit) {

        if (this.restTemplate == null) {
           
            return new SendMessage(chatId, "Error: RestTemplate es nulo.");
        }

        String url = API_URL + "?keyword=" + keyword + "&page=" + page;
        if (tag != null) url += "&tag=" + tag;

        ParameterizedTypeReference<PageResponse<HechoDTO>> responseType =
                new ParameterizedTypeReference<>() {};

        PageResponse<HechoDTO> pageResp;
        try {
          
            pageResp = restTemplate.exchange(url, HttpMethod.GET, null, responseType).getBody();
        
        } catch (Exception e) {
         
            e.printStackTrace();
            return new SendMessage(chatId, "Error al conectar con la API de búsqueda.");
        }

        if (pageResp == null || pageResp.content() == null || pageResp.content().isEmpty()) {
            
            if(editMessage) {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId);
                edit.setMessageId(messageIdToEdit);
                edit.setText("No hay más resultados para *" + keyword + "*");
                edit.enableMarkdown(true);
                return edit;
            }
            return new SendMessage(chatId, "Sin resultados.");
        }

        System.out.println("--- [LOG 10] CONSTRUYENDO RESPUESTA... ---");
        
        StringBuilder sb = new StringBuilder();
        sb.append("Resultados para *").append(keyword).append("*");
        if (tag != null) sb.append(" (tag: _").append(tag).append("_)");
        sb.append("\nPágina ").append(pageResp.number() + 1).append(" de ").append(pageResp.totalPages()).append("\n\n");
        for (HechoDTO dto : pageResp.content()) {
            sb.append("• ").append(dto.titulo()).append("\n");
            if (dto.etiquetas() != null && !dto.etiquetas().isEmpty())
                sb.append("  Tags: ").append(dto.etiquetas()).append("\n");
            sb.append("\n");
        }

        InlineKeyboardMarkup kb = BotKeyboardUtil.pagination(keyword, tag, pageResp.number(), pageResp.totalPages());

        if (editMessage) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageIdToEdit);
            edit.enableMarkdown(true);
            edit.setText(sb.toString());
            edit.setReplyMarkup(kb);
            return edit;
        }

        SendMessage msg = new SendMessage(chatId, sb.toString());
        msg.enableMarkdown(true);
        msg.setReplyMarkup(kb);
        return msg;
    }

    // --- MÉTODOS DE PARSEO (Sin cambios) ---
    private String extraerKeyword(String text) {
        int i1 = text.indexOf("\"");
        int i2 = text.indexOf("\"", i1 + 1);
        if (i1 != -1 && i2 != -1) return text.substring(i1 + 1, i2);
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) return null;
        String keywordData = parts[1].trim();
        int tagIndex = keywordData.toLowerCase().indexOf("tag:");
        if (tagIndex != -1) {
            keywordData = keywordData.substring(0, tagIndex).trim();
            if (keywordData.endsWith(",")) {
                keywordData = keywordData.substring(0, keywordData.length() - 1).trim();
            }
        }
        if (!keywordData.isEmpty()) return keywordData;
        return null;
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