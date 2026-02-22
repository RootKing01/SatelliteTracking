package com.satelliteTracking.controller;

import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telegram")
@CrossOrigin(origins = "*")
public class TelegramNotificationController {
    
    private final TelegramNotificationService telegramNotificationService;
    
    public TelegramNotificationController(TelegramNotificationService telegramNotificationService) {
        this.telegramNotificationService = telegramNotificationService;
    }
    
    /**
     * Registra un nuovo utente Telegram
     * 
     * POST /api/telegram/register
     * {
     *   "chatId": 123456789,
     *   "userIdentifier": "username",
     *   "latitude": 41.01,
     *   "longitude": 14.30,
     *   "altitude": 50,
     *   "locationName": "San Marcellino"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerTelegramUser(@RequestBody Map<String, Object> request) {
        try {
            Long chatId = ((Number) request.get("chatId")).longValue();
            String userIdentifier = (String) request.get("userIdentifier");
            Double latitude = ((Number) request.get("latitude")).doubleValue();
            Double longitude = ((Number) request.get("longitude")).doubleValue();
            Double altitude = ((Number) request.getOrDefault("altitude", 0)).doubleValue();
            String locationName = (String) request.getOrDefault("locationName", "Custom Location");
            
            TelegramSubscription subscription = telegramNotificationService.registerTelegramUser(
                chatId, userIdentifier, latitude, longitude, altitude, locationName
            );
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Utente Telegram registrato per notifiche",
                "subscriptionId", subscription.getId(),
                "chatId", subscription.getChatId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Aggiorna preferenze notifiche
     * 
     * PUT /api/telegram/preferences/{chatId}
     * {
     *   "observingCondition": "night",
     *   "maxMagnitude": 4.0,
     *   "minElevation": 15.0
     * }
     */
    @PutMapping("/preferences/{chatId}")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable Long chatId,
            @RequestBody Map<String, Object> request) {
        try {
            String observingCondition = (String) request.getOrDefault("observingCondition", "any");
            Double maxMagnitude = ((Number) request.getOrDefault("maxMagnitude", 6.0)).doubleValue();
            Double minElevation = ((Number) request.getOrDefault("minElevation", 10.0)).doubleValue();
            
            TelegramSubscription subscription = telegramNotificationService.updatePreferences(
                chatId, observingCondition, maxMagnitude, minElevation
            );
            
            if (subscription != null) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Preferenze aggiornate",
                    "observingCondition", subscription.getObservingCondition(),
                    "maxMagnitude", subscription.getMaxMagnitude(),
                    "minElevation", subscription.getMinElevation()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Ottiene lista di tutti gli utenti registrati
     * 
     * GET /api/telegram/subscriptions
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptions() {
        try {
            List<TelegramSubscription> subscriptions = telegramNotificationService.getAllSubscriptions();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", subscriptions.size(),
                "subscriptions", subscriptions
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Disabilita notifiche per un utente
     * 
     * POST /api/telegram/{chatId}/disable
     */
    @PostMapping("/{chatId}/disable")
    public ResponseEntity<Map<String, Object>> disableNotifications(@PathVariable Long chatId) {
        try {
            TelegramSubscription subscription = telegramNotificationService.disableNotifications(chatId);
            
            if (subscription != null) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Notifiche disabilitate",
                    "enabled", subscription.getNotificationsEnabled()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Abilita notifiche per un utente
     * 
     * POST /api/telegram/{chatId}/enable
     */
    @PostMapping("/{chatId}/enable")
    public ResponseEntity<Map<String, Object>> enableNotifications(@PathVariable Long chatId) {
        try {
            TelegramSubscription subscription = telegramNotificationService.enableNotifications(chatId);
            
            if (subscription != null) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Notifiche abilitate",
                    "enabled", subscription.getNotificationsEnabled()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
