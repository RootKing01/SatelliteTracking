package com.satelliteTracking.scheduler;
import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.CelestrakService;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SatelliteScheduler {

    private final CelestrakService celestrakService;
    private final SatellitePassService passService;
    private final TelegramNotificationService telegramNotificationService;

    public SatelliteScheduler(CelestrakService celestrakService,
                              SatellitePassService passService,
                              TelegramNotificationService telegramNotificationService) {
        this.celestrakService = celestrakService;
        this.passService = passService;
        this.telegramNotificationService = telegramNotificationService;
    }

    @Scheduled(initialDelay = 60000, fixedRate = 10800000) // Primo download dopo 1 minuto, poi ogni 3 ore
    public void updateSatellites() {
        celestrakService.fetchAndSaveStations();
    }

    /**
     * Task schedulato per ricevere messaggi/comandi da Telegram
     * Polling ogni 10 secondi per gestire i comandi degli utenti (/start, /help, ecc.)
     */
    @Scheduled(fixedRate = 10000, initialDelay = 5000) // Ogni 10 secondi, inizio dopo 5 secondi
    public void pollTelegramMessages() {
        try {
            telegramNotificationService.pollTelegramUpdates();
        } catch (Exception e) {
            // Errori gestiti silenziosamente (per non intasare i log)
        }
    }

    /**
     * Task schedulato per pre-calcolare i passaggi visibili
     * Popola la cache ogni ora con i passaggi delle prossime 3 ore
     * dalla posizione di default (San Marcellino)
     */
    @Scheduled(fixedRate = 3600000) // Ogni 1 ora
    public void precomputeUpcomingPasses() {
        System.out.println("🔄 [Pass Precalculator] Inizio pre-calcolo passaggi (3 ore)...");
        try {
            // Calcola passaggi con parametri standard
            // 3 ore, elevazione minima 10°, qualsiasi condizione, magnitudine fino a 6.0
            List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(3, 10.0);
            System.out.println("✅ [Pass Precalculator] Pre-calcolati " + passes.size() + " passaggi visibili");
        } catch (Exception e) {
            System.err.println("❌ Errore pre-calcolo passaggi: " + e.getMessage());
        }
    }

    /**
     * Task schedulato per inviare notifiche Telegram agli utenti
     * Gira ogni ora per controllare pass visibili nelle prossime 24 ore
     */
    @Scheduled(fixedRate = 3600000) // Ogni 1 ora
    public void sendTelegramNotificationsForUpcomingPasses() {
        System.out.println("📢 [Telegram Scheduler] Inizio scanning pass visibili...");
        
        try {
            List<TelegramSubscription> subscriptions = telegramNotificationService.getAllSubscriptions();
            
            for (TelegramSubscription sub : subscriptions) {
                if (!sub.getNotificationsEnabled()) {
                    continue;
                }
                
                try {
                    ObserverLocation location = new ObserverLocation(
                        sub.getLatitude(),
                        sub.getLongitude(),
                        sub.getAltitude(),
                        sub.getLocationName()
                    );
                    
                    List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(
                        24,
                        sub.getMinElevation(),
                        location,
                        sub.getObservingCondition(),
                        sub.getMaxMagnitude()
                    );
                    
                    LocalDateTime now = LocalDateTime.now();
                    
                    // Invia notifica per il primo passaggio trovato (se non è stato già notificato di recente)
                    for (SatellitePassDTO pass : passes) {
                        long minutesSinceLastNotification = java.time.temporal.ChronoUnit.MINUTES
                            .between(sub.getLastNotificationSent(), now);
                        
                        // Evita notifiche duplicate (almeno 30 minuti tra notifiche)
                        if (minutesSinceLastNotification >= 30) {
                            boolean sent = telegramNotificationService.sendNotificationToUser(
                                sub,
                                pass.satelliteName(),
                                pass.riseTime(),
                                pass.maxElevation(),
                                pass.estimatedMagnitude()
                            );
                            
                            if (sent) {
                                System.out.println("✅ Notifica inviata per " + pass.satelliteName() + 
                                                 " che passerà tra " + 
                                                 java.time.temporal.ChronoUnit.MINUTES.between(now, pass.riseTime()) + 
                                                 " minuti");
                                break; // Una notifica per utente per esecuzione
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("⚠️  Errore processing subscription " + sub.getId() + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Errore Telegram scheduler: " + e.getMessage());
        }
    }
}