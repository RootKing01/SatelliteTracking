package com.satelliteTracking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entità per gestire le iscrizioni alle notifiche via Telegram Bot
 * Traccia user Telegram che si registrano al bot per ricevere avvisi satelliti
 */
@Entity
@Table(name = "telegram_subscriptions")
public class TelegramSubscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long chatId; // Telegram chat ID (univoco per utente)
    
    @Column(nullable = false, unique = true)
    private String userIdentifier; // Username o ID utente
    
    @Column(nullable = false)
    private Double latitude; // Posizione osservatore
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(nullable = false)
    private Double altitude = 0.0;
    
    @Column(nullable = false)
    private String locationName = "Custom Location";
    
    // Preferenze notifiche
    @Column(nullable = false)
    private String observingCondition = "any"; // "night", "twilight", "any"
    
    @Column(nullable = false)
    private Double maxMagnitude = 6.0; // Magnitudine massima
    
    @Column(nullable = false)
    private Double minElevation = 10.0; // Elevazione minima
    
    @Column(nullable = false)
    private Boolean notificationsEnabled = true;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    private LocalDateTime lastNotificationSent;
    
    // Constructor
    public TelegramSubscription() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastNotificationSent = LocalDateTime.now();
    }
    
    public TelegramSubscription(Long chatId, String userIdentifier, Double latitude, Double longitude) {
        this();
        this.chatId = chatId;
        this.userIdentifier = userIdentifier;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getChatId() {
        return chatId;
    }
    
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    public String getUserIdentifier() {
        return userIdentifier;
    }
    
    public void setUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public Double getAltitude() {
        return altitude;
    }
    
    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public String getObservingCondition() {
        return observingCondition;
    }
    
    public void setObservingCondition(String observingCondition) {
        this.observingCondition = observingCondition;
    }
    
    public Double getMaxMagnitude() {
        return maxMagnitude;
    }
    
    public void setMaxMagnitude(Double maxMagnitude) {
        this.maxMagnitude = maxMagnitude;
    }
    
    public Double getMinElevation() {
        return minElevation;
    }
    
    public void setMinElevation(Double minElevation) {
        this.minElevation = minElevation;
    }
    
    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }
    
    public void setNotificationsEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getLastNotificationSent() {
        return lastNotificationSent;
    }
    
    public void setLastNotificationSent(LocalDateTime lastNotificationSent) {
        this.lastNotificationSent = lastNotificationSent;
    }
}
