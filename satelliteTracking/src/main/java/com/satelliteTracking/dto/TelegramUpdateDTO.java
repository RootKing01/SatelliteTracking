package com.satelliteTracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO per gli aggiornamenti in arrivo da Telegram Bot API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdateDTO {
    
    @JsonProperty("update_id")
    private Long updateId;
    
    @JsonProperty("message")
    private MessageDTO message;
    
    public static class MessageDTO {
        @JsonProperty("message_id")
        private Long messageId;
        
        @JsonProperty("from")
        private UserDTO from;
        
        @JsonProperty("chat")
        private ChatDTO chat;
        
        @JsonProperty("date")
        private Long date;
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("entities")
        private EntityDTO[] entities;
        
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        
        public UserDTO getFrom() { return from; }
        public void setFrom(UserDTO from) { this.from = from; }
        
        public ChatDTO getChat() { return chat; }
        public void setChat(ChatDTO chat) { this.chat = chat; }
        
        public Long getDate() { return date; }
        public void setDate(Long date) { this.date = date; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public EntityDTO[] getEntities() { return entities; }
        public void setEntities(EntityDTO[] entities) { this.entities = entities; }
    }
    
    public static class UserDTO {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("is_bot")
        private Boolean isBot;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("username")
        private String username;
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public Boolean getIsBot() { return isBot; }
        public void setIsBot(Boolean isBot) { this.isBot = isBot; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
    
    public static class ChatDTO {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("username")
        private String username;
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
    
    public static class EntityDTO {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("offset")
        private Integer offset;
        
        @JsonProperty("length")
        private Integer length;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Integer getOffset() { return offset; }
        public void setOffset(Integer offset) { this.offset = offset; }
        
        public Integer getLength() { return length; }
        public void setLength(Integer length) { this.length = length; }
        
        public boolean isCommand() {
            return "bot_command".equals(type) && offset == 0;
        }
    }
    
    // Getters e setters
    public Long getUpdateId() { return updateId; }
    public void setUpdateId(Long updateId) { this.updateId = updateId; }
    
    public MessageDTO getMessage() { return message; }
    public void setMessage(MessageDTO message) { this.message = message; }
}
