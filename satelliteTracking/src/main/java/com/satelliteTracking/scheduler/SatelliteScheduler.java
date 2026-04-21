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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(value = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SatelliteScheduler {

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

    @EventListener(ApplicationReadyEvent.class)
    public void initialSatelliteLoad() {
        System.out.println("🚀 [Satellite Update] Avvio iniziale...");
        updateSatellites();
    }

    @Scheduled(initialDelay = 10800000, fixedRate = 10800000) // ogni 3 ore
    public void updateSatellites() {

        OrbitalParameters lastUpdate = orbitalParametersRepository.findTopByOrderByFetchedAtDesc();

        if (lastUpdate != null) {
            long hours = ChronoUnit.HOURS.between(lastUpdate.getFetchedAt(), LocalDateTime.now());

            if (hours < 3) {
                System.out.println("⏭️  Skip update - dati recenti (" + hours + "h fa)");
                return;
            }

            System.out.println("🔄 Update necessario - ultimo: " + hours + "h fa");
        } else {
            System.out.println("🔄 Primo download TLE");
        }

        try {
            // ✅ QUI È IL FIX PRINCIPALE
            tleDataService.updateTle();

            passService.clearPassesCache();
            precomputeUpcomingPasses();

        } catch (Exception e) {
            System.err.println("❌ Scheduler error: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void pollTelegramMessages() {
        try {
            telegramNotificationService.pollTelegramUpdates();
        } catch (Exception ignored) {}
    }

    @Scheduled(fixedRate = 3600000, initialDelay = 120000)
    public void precomputeUpcomingPasses() {
        System.out.println("🔄 [Pass Precalculator] Pre-calcolo passaggi...");

        try {
            List<SatellitePassDTO> passes = passService.findVisibleUpcomingPasses(3, 10.0);
            System.out.println("✅ Default: " + passes.size());

            List<TelegramSubscription> subs = telegramNotificationService.getAllSubscriptions();

            if (!subs.isEmpty()) {
                passService.precomputePassesForSubscriptions(subs);
                System.out.println("✅ Cache aggiornata per " + subs.size());
            }

        } catch (Exception e) {
            System.err.println("❌ Errore pre-calcolo: " + e.getMessage());
        }
    }

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

                    LocalDateTime now = LocalDateTime.now();
                    long minutes = ChronoUnit.MINUTES.between(sub.getLastNotificationSent(), now);

                    if (minutes < 30) continue;

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