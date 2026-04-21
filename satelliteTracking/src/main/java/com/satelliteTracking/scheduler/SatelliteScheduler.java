package com.satelliteTracking.scheduler;

import com.satelliteTracking.dto.SatellitePassDTO;
import com.satelliteTracking.model.ObserverLocation;
import com.satelliteTracking.model.OrbitalParameters;
import com.satelliteTracking.model.TelegramSubscription;
import com.satelliteTracking.repository.OrbitalParametersRepository;
import com.satelliteTracking.service.SatellitePassService;
import com.satelliteTracking.service.TelegramNotificationService;
import com.satelliteTracking.service.TleDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(value = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SatelliteScheduler {

    private static final long TLE_UPDATE_HOURS = 12;

    private final TleDataService tleDataService;
    private final SatellitePassService passService;
    private final TelegramNotificationService telegramNotificationService;
    private final OrbitalParametersRepository orbitalParametersRepository;

    public SatelliteScheduler(TleDataService tleDataService,
                              SatellitePassService passService,
                              TelegramNotificationService telegramNotificationService,
                              OrbitalParametersRepository orbitalParametersRepository) {
        this.tleDataService = tleDataService;
        this.passService = passService;
        this.telegramNotificationService = telegramNotificationService;
        this.orbitalParametersRepository = orbitalParametersRepository;
    }

    /**
     * Avvio immediato dopo startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialSatelliteLoad() {
        System.out.println("🚀 [Satellite Update] Avvio iniziale...");
        updateSatellites();
    }

    /**
     * 🔄 UPDATE TLE ogni 12 ore
     * (unico punto di verità, niente doppio check inutile)
     */
    @Scheduled(fixedRate = 43200000, initialDelay = 60000) // 12h
    public void updateSatellites() {

        OrbitalParameters lastUpdate = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();

        if (lastUpdate != null) {
            long hours = Duration.between(lastUpdate.getFetchedAt(), LocalDateTime.now()).toHours();

            if (hours < TLE_UPDATE_HOURS) {
                System.out.println("⏭️ Skip TLE update (ultimo aggiornamento " + hours + "h fa)");
                return;
            }

            System.out.println("🔄 Update TLE necessario (" + hours + "h dall'ultimo)");
        } else {
            System.out.println("🔄 Primo download TLE");
        }

        try {
            tleDataService.updateTle();

            passService.clearPassesCache();
            precomputeUpcomingPasses();

        } catch (Exception e) {
            System.err.println("❌ Scheduler error TLE: " + e.getMessage());
        }
    }

    /**
     * 📩 Poll Telegram ogni 10 secondi
     */
    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void pollTelegramMessages() {
        try {
            telegramNotificationService.pollTelegramUpdates();
        } catch (Exception ignored) {}
    }

    /**
     * 🧠 Precalcolo passaggi ogni ora
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 120000)
    public void precomputeUpcomingPasses() {
        System.out.println("🔄 [Pass Precalculator] Pre-calcolo passaggi...");

        try {
            List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(3, 10.0);
            System.out.println("✅ Default cache: " + passes.size());

            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();

            if (!subs.isEmpty()) {
                passService.precomputePassesForSubscriptions(subs);
                System.out.println("✅ Cache utenti aggiornata: " + subs.size());
            }

        } catch (Exception e) {
            System.err.println("❌ Errore pre-calcolo: " + e.getMessage());
        }
    }

    /**
     * 📢 Notifiche Telegram
     */
    @Scheduled(fixedRate = 3600000)
    public void sendTelegramNotificationsForUpcomingPasses() {

        System.out.println("📢 [Telegram Scheduler] Scan passaggi...");

        try {
            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();

            for (TelegramSubscription sub : subs) {

                if (!sub.getNotificationsEnabled()) continue;

                try {
                    ObserverLocation location = new ObserverLocation(
                            sub.getLatitude(),
                            sub.getLongitude(),
                            sub.getAltitude(),
                            sub.getLocationName()
                    );

                    List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(
                            3,
                            sub.getMinElevation(),
                            location,
                            sub.getObservingCondition(),
                            sub.getMaxMagnitude()
                    );

                    if (sub.getLastNotificationSent() != null) {
                        long minutes = Duration.between(sub.getLastNotificationSent(), LocalDateTime.now()).toMinutes();
                        if (minutes < 30) continue;
                    }

                    final int maxAuto = 10;
                    List<SatellitePassDTO> autoPasses = passes.stream().limit(maxAuto).toList();

                    for (SatellitePassDTO pass : autoPasses) {
                        telegramNotificationService.sendNotificationToUser(sub, pass);
                    }

                    if (passes.size() > maxAuto) {
                        telegramNotificationService.sendSystemMessage(
                                sub.getChatId(),
                                "ℹ️ Inviati i primi " + maxAuto + " su " + passes.size() +
                                        " passaggi. Usa /allpasses per il resto."
                        );
                    }

                } catch (Exception e) {
                    System.err.println("⚠️ Errore sub " + sub.getId() + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Telegram scheduler error: " + e.getMessage());
        }
    }
}