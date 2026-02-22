package com.satelliteTracking.service;

import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.TelegramSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servizio per gestire le notifiche push via Telegram Bot
 * Invia messaggi diretti agli utenti su Telegram
 * Non richiede app custom - usa solo Telegram che l'utente ha già
 */
@Service
public class TelegramNotificationService {
    
    @Value("${telegram.bot.token:}")
    private String telegramBotToken;
    
    private static final String TELEGRAM_API_URL = "https://api.telegram.org";
    
    private final TelegramSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    
    public TelegramNotificationService(TelegramSubscriptionRepository subscriptionRepository,
                                      RestTemplate restTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.restTemplate = restTemplate;
    }
    
    /**
     * Registra un nuovo utente Telegram per le notifiche
     */
    public TelegramSubscription registerTelegramUser(Long chatId, String userIdentifier,
                                                     Double latitude, Double longitude,
                                                     Double altitude, String locationName) {
        // Controlla se l'utente esiste già
        Optional<TelegramSubscription> existing = subscriptionRepository.findByChatId(chatId);
        
        TelegramSubscription subscription;
        if (existing.isPresent()) {
            subscription = existing.get();
            subscription.setUpdatedAt(LocalDateTime.now());
            subscription.setLatitude(latitude);
            subscription.setLongitude(longitude);
            subscription.setAltitude(altitude);
            subscription.setLocationName(locationName);
        } else {
            subscription = new TelegramSubscription(chatId, userIdentifier, latitude, longitude);
            subscription.setAltitude(altitude);
            subscription.setLocationName(locationName);
        }
        
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Aggiorna le preferenze di notifica per un utente Telegram
     */
    public TelegramSubscription updatePreferences(Long chatId, String observingCondition,
                                                  Double maxMagnitude, Double minElevation) {
        Optional<TelegramSubscription> opt = subscriptionRepository.findByChatId(chatId);
        
        if (opt.isPresent()) {
            TelegramSubscription subscription = opt.get();
            subscription.setObservingCondition(observingCondition);
            subscription.setMaxMagnitude(maxMagnitude);
            subscription.setMinElevation(minElevation);
            subscription.setUpdatedAt(LocalDateTime.now());
            return subscriptionRepository.save(subscription);
        }
        
        return null;
    }
    
    /**
     * Invia notifica Telegram a un utente specifico
     */
    public boolean sendNotificationToUser(TelegramSubscription subscription,
                                         String satelliteName, LocalDateTime riseTime,
                                         Double maxElevation, Double magnitude) {
        if (!subscription.getNotificationsEnabled() || telegramBotToken.isEmpty()) {
            return false;
        }
        
        try {
            String message = buildNotificationMessage(
                satelliteName, riseTime, 
                maxElevation, magnitude, 
                subscription.getLocationName()
            );
            
            boolean success = sendTelegramMessage(subscription.getChatId(), message);
            
            if (success) {
                subscription.setLastNotificationSent(LocalDateTime.now());
                subscriptionRepository.save(subscription);
                System.out.println("✅ Telegram notifica inviata a " + subscription.getUserIdentifier() + 
                                 " per " + satelliteName);
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("❌ Errore invio notifica Telegram: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Invia notifiche a più utenti
     */
    public int sendBulkNotifications(String satelliteName, LocalDateTime riseTime,
                                    Double maxElevation, Double magnitude,
                                    String observingCondition, Double maxMagnitudeFilter) {
        List<TelegramSubscription> subscriptions = subscriptionRepository.findByNotificationsEnabledTrue();
        int sentCount = 0;
        
        for (TelegramSubscription sub : subscriptions) {
            if (matchesUserPreferences(sub, observingCondition, maxMagnitudeFilter, magnitude)) {
                if (sendNotificationToUser(sub, satelliteName, riseTime, maxElevation, magnitude)) {
                    sentCount++;
                }
            }
        }
        
        System.out.println("📢 Telegram bulk notification: " + sentCount + " notifiche inviate per " + satelliteName);
        return sentCount;
    }
    
    /**
     * Controlla se il pass rispecchia le preferenze dell'utente
     */
    private boolean matchesUserPreferences(TelegramSubscription sub, String observingCondition,
                                          Double maxMagnitudeFilter, Double passMagnitude) {
        if (!sub.getObservingCondition().equalsIgnoreCase("any") &&
            !sub.getObservingCondition().equalsIgnoreCase(observingCondition)) {
            return false;
        }
        
        if (passMagnitude > sub.getMaxMagnitude()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Costruisce il messaggio Telegram con formattazione
     */
    private String buildNotificationMessage(String satelliteName, LocalDateTime riseTime,
                                           Double maxElevation, Double magnitude,
                                           String locationName) {
        return "🛰️ *Satellite Tracker Alert*\n" +
               "\n" +
               "*Satellite:* " + satelliteName + "\n" +
               "*Location:* " + locationName + "\n" +
               "*Rise Time:* " + String.format("%02d:%02d UTC", riseTime.getHour(), riseTime.getMinute()) + "\n" +
               "*Max Elevation:* " + String.format("%.1f°", maxElevation) + "\n" +
               "*Magnitude:* " + String.format("%.1f", magnitude) + "\n" +
               "\n📱 [Open Web App](" + "https://satellite-tracker.app" + ")"

;
    }
    
    /**
     * Invia messaggio via Telegram Bot API
     */
    private boolean sendTelegramMessage(Long chatId, String message) {
        if (telegramBotToken.isEmpty()) {
            System.err.println("⚠️  Telegram bot token non configurato");
            return false;
        }
        
        try {
            String url = String.format("%s/bot%s/sendMessage", TELEGRAM_API_URL, telegramBotToken);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "Markdown");
            payload.put("disable_web_page_preview", true);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForObject(url, entity, Map.class);
            
            return true;
        } catch (Exception e) {
            System.err.println("❌ Errore comunicazione Telegram: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Ottiene tutte le sottoscrizioni
     */
    public List<TelegramSubscription> getAllSubscriptions() {
        return subscriptionRepository.findByNotificationsEnabledTrue();
    }
    
    /**
     * Disabilita notifiche per un utente
     */
    public TelegramSubscription disableNotifications(Long chatId) {
        Optional<TelegramSubscription> opt = subscriptionRepository.findByChatId(chatId);
        
        if (opt.isPresent()) {
            TelegramSubscription subscription = opt.get();
            subscription.setNotificationsEnabled(false);
            subscription.setUpdatedAt(LocalDateTime.now());
            return subscriptionRepository.save(subscription);
        }
        
        return null;
    }
    
    /**
     * Abilita notifiche per un utente
     */
    public TelegramSubscription enableNotifications(Long chatId) {
        Optional<TelegramSubscription> opt = subscriptionRepository.findByChatId(chatId);
        
        if (opt.isPresent()) {
            TelegramSubscription subscription = opt.get();
            subscription.setNotificationsEnabled(true);
            subscription.setUpdatedAt(LocalDateTime.now());
            return subscriptionRepository.save(subscription);
        }
        
        return null;
    }
}
