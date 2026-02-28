package com.satelliteTracking.controller;

import com.satelliteTracking.dto.TelegramUpdateDTO;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook per ricevere gli aggiornamenti del bot Telegram
 * Telegram invia gli aggiornamenti a questo endpoint
 */
@RestController
@RequestMapping("/api/telegram-webhook")
@CrossOrigin(origins = "*")
public class TelegramWebhookController {
    
    private final TelegramNotificationService telegramNotificationService;
    
    public TelegramWebhookController(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }
    
    /**
     * Riceve gli aggiornamenti da Telegram
     * 
     * POST /api/telegram-webhook/update
     * {
     *   "update_id": 123456789,
     *   "message": {
     *     "message_id": 1,
     *     "from": {"id": xxx, "first_name": "...", ...},
     *     "chat": {"id": xxx, "type": "private", ...},
     *     "date": 1234567890,
     *     "text": "/start",
     *     "entities": [{"type": "bot_command", "offset": 0, "length": 6}]
     *   }
     * }
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> handleUpdate(@RequestBody TelegramUpdateDTO update) {
        try {
            System.out.println("🔔 Webhook Telegram ricevuto - UpdateID: " + update.getUpdateId());
            
            if (update.getUpdateId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing update_id"));
            }
            
            // Processa l'aggiornamento
            telegramNotificationService.processUpdate(update);
            
            return ResponseEntity.ok(Map.of("status", "ok"));
            
        } catch (Exception e) {
            System.err.println("❌ Errore elaborazione webhook Telegram: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Endpoint di test per verificare che il webhook sia raggiungibile
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Webhook Telegram è attivo e raggiungibile"
        ));
    }
}
