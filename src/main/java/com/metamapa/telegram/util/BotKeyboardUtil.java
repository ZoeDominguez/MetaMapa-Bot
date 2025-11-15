package com.metamapa.telegram.util;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class BotKeyboardUtil {

    public static InlineKeyboardMarkup pagination(String keyword, String tag, int currentPage, int totalPages) {
        
        String tagValue = (tag == null) ? "_" : tag;
        
        List<InlineKeyboardButton> row = new ArrayList<>();

        // Botón Anterior
        if (currentPage > 0) {
            InlineKeyboardButton prev = new InlineKeyboardButton();
            prev.setText("◀ Anterior");
            prev.setCallbackData("BUSCAR|" + keyword + "|" + tagValue + "|" + (currentPage - 1));
            row.add(prev);
        }

        // Botón Siguiente
        if (currentPage < (totalPages - 1)) {
            InlineKeyboardButton next = new InlineKeyboardButton();
            next.setText("▶ Siguiente");
            next.setCallbackData("BUSCAR|" + keyword + "|" + tagValue + "|" + (currentPage + 1));
            row.add(next);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

   
        if (row.isEmpty()) {
            markup.setKeyboard(new ArrayList<>()); // Setea []
        } else {
            markup.setKeyboard(List.of(row)); // Setea [ [btn1, btn2] ]
        }
    
        return markup;
    }
}