package com.metamapa.telegram.handler;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import java.util.Collections;
import java.util.List;

public interface BotCommandHandler {

    boolean canHandle(String command);

    // El método original se queda igual para no romper a los demás
    BotApiMethod<?> handle(Update update);

    // NUEVO MÉTODO con implementación por defecto
    default List<BotApiMethod<?>> handleBatch(Update update) {
        BotApiMethod<?> singleResponse = handle(update);
        if (singleResponse == null) {
            return Collections.emptyList();
        }
        return List.of(singleResponse);
    }
}