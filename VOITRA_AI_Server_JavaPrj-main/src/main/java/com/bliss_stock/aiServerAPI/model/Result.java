package com.bliss_stock.aiServerAPI.model;

import org.springframework.http.HttpStatus;

public class Result {


    public Result(Boolean success, String result, String token, String audio_id, int httpCode){
        this.token = token;
        this.audio_id = audio_id;
        this.success = success;
        this.result = result;
        this.httpCode = httpCode;
    }
    private Boolean success;
    private String result, token, audio_id;
    private int httpCode;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAudio_id() {
        return audio_id;
    }

    public void setAudio_id(String audio_id) {
        this.audio_id = audio_id;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }


}

