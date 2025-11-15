package com.metamapa.telegram.bot;

import com.metamapa.telegram.handler.BotCommandHandler; 
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.util.List; 

@Component
public class MetaMapaBot extends TelegramLongPollingBot {

    private final List<BotCommandHandler> handlers; 
    private final Dotenv dotenv;

    @Autowired
    public MetaMapaBot(List<BotCommandHandler> handlers) { 
        super();
        this.handlers = handlers;
        this.dotenv = Dotenv.load();
        System.out.println("Cargados " + handlers.size() + " manejadores de comandos.");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return; 
        }

        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        BotApiMethod<?> response = null;

        for (BotCommandHandler handler : handlers) {
            if (handler.canHandle(messageText)) {
                response = handler.handle(update); 
                break; 
            }
        }

        if (response == null) {
            response = new SendMessage(chatId.toString(), "Comando no reconocido.");
        }

        try {
            execute(response);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String getBotUsername() {
        return dotenv.get("NOMBRE_BOT");
    }

    @Override
    public String getBotToken() {
        return dotenv.get("TOKEN_BOT");
    }

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            System.out.println("✅ Bot de Telegram (MetaMapaBot) registrado exitosamente.");
        } catch (TelegramApiException e) {
            System.err.println("❌ Error al registrar el bot de Telegram:");
            e.printStackTrace();
        }
    }
}