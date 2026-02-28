package com.satelliteTracking.service;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.dto.TelegramUpdateDTO;
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
    
    // Memorizza l'ultimo update_id processato per il polling
    private Long lastUpdateId = 0L;
    
    private final TelegramSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    private final GeocodingService geocodingService;
    private final SatellitePassService satellitePassService;
    
    public TelegramNotificationService(TelegramSubscriptionRepository subscriptionRepository,
                                      RestTemplate restTemplate,
                                      GeocodingService geocodingService,
                                      SatellitePassService satellitePassService) {
        this.subscriptionRepository = subscriptionRepository;
        this.restTemplate = restTemplate;
        this.geocodingService = geocodingService;
        this.satellitePassService = satellitePassService;
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
    /**
     * Invia notifica per un passaggio satellitare
     */
    public boolean sendNotificationToUser(TelegramSubscription subscription,
                                         SatellitePassDTO pass) {
        return sendNotificationToUser(subscription,
            pass.satelliteName(),
            pass.riseTime(),
            pass.maxElevation(),
            pass.maxElevationAzimuth(),
            pass.estimatedMagnitude(),
            subscription.getLocationName()
        );
    }
    
    public boolean sendNotificationToUser(TelegramSubscription subscription,
                                         String satelliteName, LocalDateTime riseTime,
                                         Double maxElevation, Double maxElevationAzimuth,
                                         Double magnitude, String locationName) {
        if (!subscription.getNotificationsEnabled() || telegramBotToken.isEmpty()) {
            return false;
        }
        
        try {
            String message = buildNotificationMessage(
                satelliteName, riseTime, 
                maxElevation, maxElevationAzimuth,
                magnitude, 
                locationName
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
     * DEPRECATO - Usa sendNotificationToUser con maxElevationAzimuth
     */
    @Deprecated
    public boolean sendNotificationToUser(TelegramSubscription subscription,
                                         String satelliteName, LocalDateTime riseTime,
                                         Double maxElevation, Double magnitude) {
        return sendNotificationToUser(subscription, satelliteName, riseTime, maxElevation, 0.0, magnitude, subscription.getLocationName());
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
                                           Double maxElevation, Double maxElevationAzimuth,
                                           Double magnitude, String locationName) {
        String direction = azimuthToDirection(maxElevationAzimuth);
        
        return "🛰️ *Satellite Tracker Alert*\n" +
               "\n" +
               "*Satellite:* " + satelliteName + "\n" +
               "*Location:* " + locationName + "\n" +
               "*Rise Time:* " + String.format("%02d:%02d UTC", riseTime.getHour(), riseTime.getMinute()) + "\n" +
               "*Max Elevation:* " + String.format("%.1f°", maxElevation) + "\n" +
               "*Direction:* " + direction + " (azimuth " + String.format("%.0f", maxElevationAzimuth) + "°)\n" +
               "*Magnitude:* " + String.format("%.1f", magnitude) + "\n" +
               "\n📱 [Open Web App](" + "https://satellite-tracker.app" + ")"
;
    }
    
    /**
     * Converte azimuth in direzione cardinale
     */
    private String azimuthToDirection(double azimuth) {
        if (azimuth < 22.5 || azimuth >= 337.5) return "N (Nord)";
        if (azimuth < 67.5) return "NE (Nord-Est)";
        if (azimuth < 112.5) return "E (Est)";
        if (azimuth < 157.5) return "SE (Sud-Est)";
        if (azimuth < 202.5) return "S (Sud)";
        if (azimuth < 247.5) return "SW (Sud-Ovest)";
        if (azimuth < 292.5) return "W (Ovest)";
        return "NW (Nord-Ovest)";
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
    
    /**
     * Polling per ricevere messaggi/comandi da Telegram
     * Chiama getUpdates API e processa i messaggi ricevuti
     */
    public void pollTelegramUpdates() {
        if (telegramBotToken.isEmpty()) {
            return;
        }
        
        try {
            String url = String.format("%s/bot%s/getUpdates?offset=%d&timeout=10", 
                                     TELEGRAM_API_URL, telegramBotToken, lastUpdateId + 1);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("result");
                
                for (Map<String, Object> update : updates) {
                    Integer updateId = (Integer) update.get("update_id");
                    lastUpdateId = updateId.longValue();
                    
                    Map<String, Object> message = (Map<String, Object>) update.get("message");
                    if (message != null) {
                        processIncomingMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️  Errore polling Telegram: " + e.getMessage());
        }
    }
    
    /**
     * Processa un messaggio/comando ricevuto da Telegram
     */
    private void processIncomingMessage(Map<String, Object> message) {
        try {
            Map<String, Object> from = (Map<String, Object>) message.get("from");
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            String text = (String) message.get("text");
            
            if (text == null || from == null || chat == null) {
                return;
            }
            
            Long chatId = ((Number) chat.get("id")).longValue();
            String username = (String) from.get("username");
            if (username == null) {
                username = (String) from.get("first_name");
            }
            
            System.out.println("📩 Messaggio ricevuto da " + username + " (chatId: " + chatId + "): " + text);
            
            // Gestisci comandi
            if (text.startsWith("/start")) {
                handleStartCommand(chatId, username);
            } else if (text.startsWith("/help")) {
                handleHelpCommand(chatId);
            } else if (text.startsWith("/info")) {
                handleInfoCommand(chatId);
            } else if (text.startsWith("/stop")) {
                handleStopCommand(chatId);
            } else {
                // Tratta come nome di città
                handleCityInput(chatId, text);
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Errore processamento messaggio: " + e.getMessage());
        }
    }
    
    /**
     * Handler per comando /start
     * Registra automaticamente l'utente con posizione di default
     */
    private void handleStartCommand(Long chatId, String username) {
        // Registra con posizione di default (San Marcellino)
        TelegramSubscription subscription = registerTelegramUser(
            chatId, 
            username != null ? username : "User_" + chatId,
            41.01,  // San Marcellino, Caserta
            14.30, 
            30.0, 
            "San Marcellino, Caserta"
        );
        
        String welcomeMessage = "🛰️ *Benvenuto su Satellite Tracker!*\n" +
                              "\n" +
                              "Registrazione completata! ✅\n" +
                              "\n" +
                              "*La tua posizione:* " + subscription.getLocationName() + "\n" +
                              "*Chat ID:* `" + chatId + "`\n" +
                              "\n" +
                              "Riceverai notifiche automatiche quando satelliti visibili passeranno sopra la tua posizione.\n" +
                              "\n" +
                              "*Comandi disponibili:*\n" +
                              "/help - Mostra aiuto\n" +
                              "/info - Vedi le tue impostazioni\n" +
                              "/stop - Disattiva notifiche\n" +
                              "\n" +
                              "Per cambiare posizione o preferenze usa gli endpoint API:\n" +
                              "`PUT /api/telegram/preferences/" + chatId + "`";
        
        sendTelegramMessage(chatId, welcomeMessage);
        System.out.println("✅ Nuovo utente registrato: " + username + " (chatId: " + chatId + ")");
    }
    
    /**
     * Handler per comando /help
     */
    private void handleHelpCommand(Long chatId) {
        String helpMessage = "🛰️ *Satellite Tracker - Aiuto*\n" +
                           "\n" +
                           "*Comandi:*\n" +
                           "/start - Registrati al servizio\n" +
                           "/help - Mostra questo messaggio\n" +
                           "/info - Vedi le tue impostazioni\n" +
                           "/stop - Disattiva notifiche\n" +
                           "\n" +
                           "*Configurazione:*\n" +
                           "Usa gli endpoint API per:\n" +
                           "• Cambiare posizione\n" +
                           "• Impostare magnitudine massima\n" +
                           "• Impostare elevazione minima\n" +
                           "• Scegliere condizioni (night/twilight/any)\n" +
                           "\n" +
                           "📚 Documentazione: [GitHub](https://github.com/RootKing01/SatelliteTracking)";
        
        sendTelegramMessage(chatId, helpMessage);
    }
    
    /**
     * Handler per comando /info
     */
    private void handleInfoCommand(Long chatId) {
        Optional<TelegramSubscription> opt = subscriptionRepository.findByChatId(chatId);
        
        if (opt.isEmpty()) {
            sendTelegramMessage(chatId, "❌ Non sei registrato! Usa /start per registrarti.");
            return;
        }
        
        TelegramSubscription sub = opt.get();
        
        String infoMessage = "🛰️ *Le tue impostazioni*\n" +
                           "\n" +
                           "*Chat ID:* `" + sub.getChatId() + "`\n" +
                           "*Username:* " + sub.getUserIdentifier() + "\n" +
                           "*Posizione:* " + sub.getLocationName() + "\n" +
                           "*Coordinate:* " + String.format("%.2f°, %.2f°", sub.getLatitude(), sub.getLongitude()) + "\n" +
                           "*Altitudine:* " + sub.getAltitude() + "m\n" +
                           "\n" +
                           "*Filtri:*\n" +
                           "• Condizione: " + sub.getObservingCondition() + "\n" +
                           "• Magnitudine max: " + sub.getMaxMagnitude() + "\n" +
                           "• Elevazione min: " + sub.getMinElevation() + "°\n" +
                           "\n" +
                           "*Notifiche:* " + (sub.getNotificationsEnabled() ? "✅ Attive" : "❌ Disattivate");
        
        sendTelegramMessage(chatId, infoMessage);
    }
    
    /**
     * Handler per comando /stop
     */
    private void handleStopCommand(Long chatId) {
        TelegramSubscription sub = disableNotifications(chatId);
        
        if (sub != null) {
            sendTelegramMessage(chatId, "🔕 Notifiche disattivate.\n\nPer riattivarle contatta l'amministratore.");
            System.out.println("🔕 Notifiche disattivate per chatId: " + chatId);
        } else {
            sendTelegramMessage(chatId, "❌ Non sei registrato!");
        }
    }
    
    /**
     * Processa gli aggiornamenti in arrivo dal bot Telegram
     * Gestisce comandi e messaggi
     */
    public void processUpdate(TelegramUpdateDTO update) {
        if (update == null || update.getMessage() == null || update.getMessage().getChat() == null) {
            return;
        }
        
        TelegramUpdateDTO.MessageDTO message = update.getMessage();
        Long chatId = message.getChat().getId();
        String text = message.getText();
        
        if (text == null) {
            return;
        }
        
        System.out.println("📨 Messaggio da chat " + chatId + ": " + text);
        
        // Gestisci comandi
        if (text.startsWith("/start")) {
            handleStartCommand(chatId, message.getFrom());
        } else if (text.startsWith("/help")) {
            sendTelegramMessage(chatId, 
                "🛰️ *Satellite Tracker Bot*\n\n" +
                "*/start* - Registra la tua posizione\n" +
                "*/help* - Mostra questo messaggio\n\n" +
                "Invia il nome di una città per registrare la tua posizione!"
            );
        } else {
            // Tratta il messaggio come nome di città
            handleCityInput(chatId, text);
        }
    }
    
    /**
     * Gestisce il comando /start
     */
    private void handleStartCommand(Long chatId, TelegramUpdateDTO.UserDTO user) {
        String userName = user != null && user.getFirstName() != null ? user.getFirstName() : "Utente";
        
        sendTelegramMessage(chatId,
            "🛰️ *Benvenuto nel Satellite Tracker!*\n\n" +
            "Ciao " + userName + "! 👋\n\n" +
            "Per iniziare a ricevere notifiche di passaggi satellitari, dimmi in quale città ti trovi.\n\n" +
            "_Ad esempio: Milano, Roma, Napoli, etc._"
        );
    }
    
    /**
     * Gestisce l'input del nome della città
     */
    private void handleCityInput(Long chatId, String cityName) {
        sendTelegramMessage(chatId, "🌍 Ricerca della città: " + cityName + "...");
        
        // Geocodifica la città
        Map<String, Object> geoResult = geocodingService.geocodeCity(cityName);
        
        if (geoResult.containsKey("error")) {
            sendTelegramMessage(chatId, 
                "❌ *Errore*: " + geoResult.get("error") + "\n\n" +
                "Riprova con un nome di città valido o /help per l'aiuto."
            );
            return;
        }
        
        // Estrai coordinate
        double latitude = ((Number) geoResult.get("latitude")).doubleValue();
        double longitude = ((Number) geoResult.get("longitude")).doubleValue();
        double altitude = ((Number) geoResult.get("altitude")).doubleValue();
        String displayName = (String) geoResult.get("displayName");
        
        try {
            // Calcola i passaggi visibili nei prossimi 3 ore
            com.satelliteTracking.model.ObserverLocation location = 
                new com.satelliteTracking.model.ObserverLocation(
                    latitude, longitude, altitude, displayName
                );
            
            List<SatellitePassDTO> visiblePasses = satellitePassService.findVisibleUpcomingPasses(
                3,      // 3 ore
                30.0,   // minima elevazione 30°
                location,
                "any",  // qualunque condizione
                6.0     // magnitudine massima
            );
            
            // Mostra i satelliti trovati
            String passesMessage = formatSatellitePasses(visiblePasses, displayName);
            sendTelegramMessage(chatId, passesMessage);
            
            // Chiedi se registrarsi
            sendTelegramMessage(chatId,
                "📝 Vuoi registrare questa posizione per ricevere notifiche automatiche?\n\n" +
                "Se sì, rispondi: *registra*\n" +
                "Se no, scrivi un'altra città per cercare satelliti."
            );
            
            // Registra l'utente
            registerTelegramUser(
                chatId,
                "user_" + chatId,
                latitude,
                longitude,
                altitude,
                displayName
            );
            
        } catch (Exception e) {
            System.err.println("❌ Errore elaborazione città: " + e.getMessage());
            sendTelegramMessage(chatId, "❌ Errore durante l'elaborazione: " + e.getMessage());
        }
    }
    
    /**
     * Formatta i passaggi satellitari per il messaggio Telegram
     */
    private String formatSatellitePasses(List<SatellitePassDTO> passes, String cityName) {
        if (passes.isEmpty()) {
            return "🌍 *" + cityName + "*\n\n" +
                   "Nessun satellite visibile nei prossimi 3 ore con:\n" +
                   "  • Elevazione minima: 30°\n" +
                   "  • Magnitudine: ≤ 6.0";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("🌍 *").append(cityName).append("*\n");
        sb.append("📡 *").append(passes.size()).append(" satelliti visibili nelle prossime 3 ore*\n\n");
        
        int count = 1;
        for (SatellitePassDTO pass : passes.stream().limit(10).toList()) {
            String direction = azimuthToDirection(pass.maxElevationAzimuth());
            long minutesUntilRise = java.time.temporal.ChronoUnit.MINUTES.between(
                LocalDateTime.now(), pass.riseTime()
            );
            
            sb.append(count).append(". *").append(pass.satelliteName()).append("*\n");
            sb.append("   ⏰ Tra ").append(minutesUntilRise).append(" min (")
              .append(String.format("%02d:%02d", pass.riseTime().getHour(), pass.riseTime().getMinute()))
              .append(" UTC)\n");
            sb.append("   📈 Elev: ").append(String.format("%.0f°", pass.maxElevation()))
              .append(" | Dir: ").append(direction).append("\n");
            sb.append("   ⭐ Mag: ").append(String.format("%.1f", pass.estimatedMagnitude())).append("\n\n");
            count++;
        }
        
        if (passes.size() > 10) {
            sb.append("_...e ").append(passes.size() - 10).append(" altri satelliti_\n\n");
            sb.append("💡 Per vedere la lista completa, visita:\n");
            sb.append("http://localhost:8080/api/satellites/passes/upcoming");
        }
        
        return sb.toString();
    }
}
