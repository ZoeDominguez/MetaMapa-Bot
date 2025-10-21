package com.metamapa.telegram.handler;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;


public interface BotCommandHandler {

    boolean canHandle(String command);

    SendMessage handle(Update update);
}