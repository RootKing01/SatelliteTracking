package com.satelliteTracking.scheduler;
import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.repository.SatelliteRepository;
import com.satelliteTracking.service.CelestrakService;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(value = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SatelliteScheduler {

    private final CelestrakService celestrakService;
    private final SatellitePassService passService;
    private final TelegramNotificationService telegramNotificationService;
    private final OrbitalParametersRepository orbitalParametersRepository;

    public SatelliteScheduler(CelestrakService celestrakService,
                              SatellitePassService passService,
                              TelegramNotificationService telegramNotificationService,
                              OrbitalParametersRepository orbitalParametersRepository) {
        this.celestrakService = celestrakService;
        this.passService = passService;
        this.telegramNotificationService = telegramNotificationService;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialSatelliteLoad() {
        System.out.println("🚀 [Satellite Update] Avvio iniziale - caricamento satelliti prima dello scheduler...");
        updateSatellites();
    }

    @Scheduled(initialDelay = 10800000, fixedRate = 10800000) // Ogni 3 ore dopo il caricamento iniziale
    public void updateSatellites() {
        // Controlla se ci sono già dati recenti
        OrbitalParameters lastUpdate = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();
        
        if (lastUpdate != null) {
            long hoursSinceLastUpdate = ChronoUnit.HOURS.between(lastUpdate.getFetchedAt(), LocalDateTime.now());
            
            if (hoursSinceLastUpdate < 3) {
                System.out.println("⏭️  [Satellite Update] Saltato download - dati aggiornati " + 
                                 hoursSinceLastUpdate + " ore fa (threshold: 3 ore)");
                return;
            }
            
            System.out.println("🔄 [Satellite Update] Download necessario - ultimo aggiornamento " + 
                             hoursSinceLastUpdate + " ore fa");
        } else {
            System.out.println("🔄 [Satellite Update] Primo download - database vuoto");
        }
        
        celestrakService.fetchAndSaveStations();
        // Dopo aggiornamento TLE: invalida cache e ricalcola subito per tutte le posizioni
        passService.clearPassesCache();
        precomputeUpcomingPasses();
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
     * Task schedulato per pre-calcolare i passaggi visibili.
     * Popola la cache ogni ora per la posizione di default e per tutti i subscriber Telegram attivi,
     * così che l'invio delle notifiche trovi sempre la cache già pronta.
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 120000) // Ogni 1 ora, prima esecuzione dopo 2 min
    public void precomputeUpcomingPasses() {
        System.out.println("🔄 [Pass Precalculator] Pre-calcolo passaggi per tutte le posizioni...");
        try {
            // Posizione di default (San Marcellino)
            List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(3, 10.0);
            System.out.println("✅ [Pass Precalculator] Default: " + passes.size() + " passaggi");

            // Posizioni dei subscriber Telegram attivi (con i loro parametri esatti)
            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();
            if (!subs.isEmpty()) {
                passService.precomputePassesForSubscriptions(subs);
                System.out.println("✅ [Pass Precalculator] Cache aggiornata per " + subs.size() + " subscriber(s)");
            }
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
            System.out.println("📊 Trovate " + subscriptions.size() + " subscription nel database");
            
            for (TelegramSubscription sub : subscriptions) {
                System.out.println("🔍 Controllo subscription ID=" + sub.getId() + 
                                 " user=" + sub.getUserIdentifier() + 
                                 " enabled=" + sub.getNotificationsEnabled());
                
                if (!sub.getNotificationsEnabled()) {
                    System.out.println("⏭️  Notifiche disabilitate per " + sub.getUserIdentifier());
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
                        3,      // Solo prossime 3 ore
                        sub.getMinElevation(),
                        location,
                        sub.getObservingCondition(),
                        sub.getMaxMagnitude()
                    );

                    System.out.println("Telegram scan for user " + sub.getUserIdentifier() +
                                     " (chatId: " + sub.getChatId() + "): " + passes.size() +
                                     " passes (minElevation=" + sub.getMinElevation() + ", condition=" + sub.getObservingCondition() +
                                     ", maxMagnitude=" + sub.getMaxMagnitude() + ")");
                    
                    LocalDateTime now = LocalDateTime.now();
                    long minutesSinceLastNotification = java.time.temporal.ChronoUnit.MINUTES
                        .between(sub.getLastNotificationSent(), now);
                    
                    System.out.println("⏰ Ultimo notifica: " + minutesSinceLastNotification + " minuti fa (threshold: 60 min)");
                    
                    // Evita notifiche duplicate globali (almeno 30 minuti tra batch di notifiche)
                    if (minutesSinceLastNotification >= 30) {
                        // Invia notifiche automatiche solo per i primi N passaggi per evitare rate limit Telegram
                        final int maxAutoNotifications = 10;
                        List<SatellitePassDTO> autoPasses = passes.stream().limit(maxAutoNotifications).toList();

                        int notificationsSent = 0;
                        for (SatellitePassDTO pass : autoPasses) {
                            System.out.println("📤 Tentativo invio notifica per " + pass.satelliteName());
                            boolean sent = telegramNotificationService.sendNotificationToUser(sub, pass);
                            
                            if (sent) {
                                System.out.println("✅ Notifica inviata per " + pass.satelliteName() + 
                                                 " che passerà tra " + 
                                                 java.time.temporal.ChronoUnit.MINUTES.between(now, pass.riseTime()) + 
                                                 " minuti");
                                notificationsSent++;
                            } else {
                                System.out.println("❌ Invio fallito per " + pass.satelliteName());
                            }
                        }

                        if (passes.size() > maxAutoNotifications) {
                            int remaining = passes.size() - maxAutoNotifications;
                            telegramNotificationService.sendSystemMessage(
                                sub.getChatId(),
                                "ℹ️ Ti ho inviato i primi *" + maxAutoNotifications + "* passaggi automatici su *" + passes.size() + "*.\n" +
                                "Per ricevere l'elenco completo delle prossime 3 ore usa il comando: /allpasses\n" +
                                "(restanti: " + remaining + ")"
                            );
                        }

                        System.out.println("📊 Notifiche inviate in batch: " + notificationsSent + "/" + autoPasses.size() +
                                         " (totali disponibili: " + passes.size() + ")");
                    } else {
                        System.out.println("⏭️  Troppo presto per notifiche (ultimo batch: " + minutesSinceLastNotification + " min fa)");
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