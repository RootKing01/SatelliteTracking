package com.satelliteTracking.config;

import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Configurazione per la libreria Orekit
 * Orekit richiede dati astronomici per calcoli precisi
 */
@Configuration
public class OrekitConfig {

    @PostConstruct
    public void initOrekit() {
        try {
            DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
            
            // Prova prima a usare una directory locale se esiste
            File orekitData = new File("/orekit-data");
            if (orekitData.exists() && orekitData.isDirectory()) {
                manager.addProvider(new DirectoryCrawler(orekitData));
                System.out.println("✅ Orekit initialized with local data: " + orekitData.getAbsolutePath());
            } else {
                // Senza dati Orekit, usa calcoli semplificati
                System.out.println("⚠️  Orekit data not found - using simplified calculations");
                System.out.println("ℹ️  For precise calculations, mount orekit-data in /orekit-data");
                System.out.println("ℹ️  Download from: https://gitlab.orekit.org/orekit/orekit-data");
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Orekit initialization warning: " + e.getMessage());
            System.err.println("ℹ️  Satellite pass calculations will use simplified model");
        }
    }

    /**
     * Bean per RestTemplate - utilizzato da TelegramNotificationService
     * per effettuare chiamate HTTP all'API di Telegram
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
