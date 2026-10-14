package com.incidentreport.common.message;

import com.google.gson.JsonElement;

public class Message {
    private String type;
    private String requestId;
    private JsonElement payload; // Dùng JsonElement để payload có thể là bất kỳ JSON object nào

    public Message(String type, String requestId, JsonElement payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public JsonElement getPayload() { return payload; }
    public void setPayload(JsonElement payload) { this.payload = payload; }
}
