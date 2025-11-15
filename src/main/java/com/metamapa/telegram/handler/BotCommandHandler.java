package com.metamapa.telegram.handler;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;


public interface BotCommandHandler {

    boolean canHandle(String command);

    BotApiMethod<?> handle(Update update);
}