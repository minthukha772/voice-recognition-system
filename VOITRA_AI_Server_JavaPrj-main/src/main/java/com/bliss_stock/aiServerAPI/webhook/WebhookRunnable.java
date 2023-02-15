package com.bliss_stock.aiServerAPI.webhook;
import com.bliss_stock.aiServerAPI.audioControl.AudioStorage;
import com.bliss_stock.aiServerAPI.common.AutoLogger;
import com.bliss_stock.aiServerAPI.common.WebhookTimeGetter;
import com.bliss_stock.aiServerAPI.gcp.CloudAsync;
import com.bliss_stock.aiServerAPI.gcp.CloudDiarization;
import com.bliss_stock.aiServerAPI.model.InitialResult;
import com.bliss_stock.aiServerAPI.model.Speech2text;
import com.bliss_stock.aiServerAPI.queueOperator.QueueOperator;
import com.svix.EndpointListOptions;
import com.svix.Svix;
import com.svix.models.ListResponseEndpointOut;
import java.text.ParseException;
import java.util.*;

import static java.lang.Integer.parseInt;

public class WebhookRunnable implements Runnable {
    private Speech2text param;
    private String wf;
    private InitialResult s;
    private Date requestStartTime = WebhookTimeGetter.getTimeNow();
    private String appId = "app_2Hq8YhkyHUo2hUFdCG4YgpWswZq";
    private Svix sv = new Svix("testsk_ILZZRaKTH75-QE73j3VWXbZACLZhvyWe.eu");
    private AutoLogger autoLogger;
    private final QueueOperator diarizer;
    private final int vacantQueueIndex;
    private boolean success; // For logging the return status.
    
    // For multiple files.
    private boolean isMultiple;
    private int currentFileNumber;
    private int totalFileNumber;
    private double total_time_taken;
    public static Map<String, Map<Integer, ArrayList<HashMap<String, String>>>> resultMap = new HashMap<>();
    public static Map<String, Map<Integer, ArrayList<String>>> resultMapWithoutDiarization = new HashMap<>();

    public WebhookRunnable(Speech2text parameters, InitialResult success, String wav_file, AutoLogger autoLogger, QueueOperator diarizer, int vacantQueueIndex, boolean isMultiple) throws ParseException {
        this.s = success;
        this.param = parameters;
        this.wf = wav_file;
        this.autoLogger = autoLogger;
        this.diarizer = diarizer;
        this.vacantQueueIndex = vacantQueueIndex;
        this.isMultiple = isMultiple;
    }
    
    public WebhookRunnable(Speech2text parameters, InitialResult success, String wav_file, AutoLogger autoLogger, QueueOperator diarizer, int vacantQueueIndex, boolean isMultiple, int currentFileNumber, int totalFileNumber) throws ParseException {
        this(parameters, success, wav_file, autoLogger, diarizer, vacantQueueIndex, isMultiple);
        this.currentFileNumber = currentFileNumber;
        this.totalFileNumber = totalFileNumber;
    }

    public void sendGcpWebhook() throws Exception {
        autoLogger.logStart();
//        autoLogger.logCpuUsage();

        String bucketName = "voitra-stt";
        Date requestStartTime;
        requestStartTime = WebhookTimeGetter.getTimeNow();
        Date requestEndTime;
        total_time_taken = 0;
        String withoutDiarizationResult;
        ArrayList<HashMap<String, String>> withDiarizationResult = new ArrayList<HashMap<String, String>>();
        String fileName = wf.split("/")[wf.split("/").length - 1];
        if (s.getSuccess()) {
            if (param.getDiarization().equals("1")) {
                int maxspeaker = param.getNumber().length()>0 ? Integer.parseInt(param.getNumber()): 6;//
                System.out.println("Starting GCP Request With Diarization");

                autoLogger.logMemoryBefore();
                if (maxspeaker > 1) {
                    System.out.println("WebhookRunnable.java,senGCPWebhook() diarization file name: " + fileName);
                    withDiarizationResult = CloudDiarization.transcribeDiarizationGcs(
                            "gs://" + bucketName + "/GCP_AUDIO/" + fileName,
                            param.getLang(),
                            "LINEAR16",
                            16000,
                            2,
                            maxspeaker);
                } else {
                    System.out.println("WebhookRunnable.java,senGCPWebhook() diarization file name: " + fileName);
                    withDiarizationResult = CloudDiarization.transcribeDiarization(
                            "gs://" + bucketName + "/GCP_AUDIO/" + fileName,
                            param.getLang(),
                            "LINEAR16",
                            16000);
                }
                autoLogger.gcpCompleted();
//                delete audio after request
//                diarization = 1, multiple = true
                if(isMultiple){
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id()+"_nc_SEGMENTS");
                }
//                    delete audio file used for manipulation
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id() + "_nc.wav");
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id() + ".wav");
                autoLogger.logMemoryAfter();
                requestEndTime = WebhookTimeGetter.getTimeNow();

                double differenceInMilliSeconds = Math.abs(requestEndTime.getTime() - requestStartTime.getTime());
                total_time_taken = differenceInMilliSeconds / 1000;

                ListResponseEndpointOut listResponseEndpointOut = sv.getEndpoint().list(appId, new EndpointListOptions());

                if (listResponseEndpointOut.getData().size() > 0) {
                    for (int i = 0; i < listResponseEndpointOut.getData().size(); i++) {
                        Webhook.endpointDelete(listResponseEndpointOut.getData().get(i).getId());
                    }
                }
                
                if(!isMultiple){
                    Webhook.endpointCreate(param.getCallback());
                    Webhook.createMessageDiarization(
                       parseInt(param.getAudio_id()),
                       total_time_taken,
                       param.getCallback(),
                       withDiarizationResult,
                       "transcription.success");
                } else {
                    WebhookRunnable.resultMap.putIfAbsent(param.getAudio_id(), new HashMap<>());
                    WebhookRunnable.resultMap.get(param.getAudio_id()).put(currentFileNumber, withDiarizationResult);
                    if(WebhookRunnable.resultMap.get(param.getAudio_id()).size() == totalFileNumber){
                        autoLogger.logMsg(param.getAudio_id() + " has all " + totalFileNumber + " files");
                        ArrayList<String> concatenatedResult = concatenateResults(WebhookRunnable.resultMap.get(param.getAudio_id()));
                        System.out.println(concatenatedResult);
                        Webhook.endpointCreate(param.getCallback());
                        Webhook.createMessageDiarization(
                           parseInt(param.getAudio_id()),
                           total_time_taken,
                           param.getCallback(),
                           concatenatedResult,
                           "transcription.success");
                        WebhookRunnable.resultMap.remove(param.getAudio_id());
                    }
                    
                }
                success = true;

            } else {

                System.out.println("Starting GCP Request");
                autoLogger.logMemoryBefore();
//                autoLogger.logCpuUsage();
                withoutDiarizationResult = CloudAsync.asyncRecognizeGcs(
                        "gs://" + bucketName + "/GCP_AUDIO/" + fileName,
                        param.getLang(),
                        "LINEAR16",
                        16000);
                autoLogger.gcpCompleted();
//                diarization = 0, isMultiple= true
                if(isMultiple){
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id()+"_nc_rs_SEGMENTS");
                }
//                    delete audio file used for manipulation
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id() + "_rs.wav");
                    AudioStorage.delete("/usr/local/src/static/wav_files/audio_" + param.getAudio_id() + ".wav");
                autoLogger.logMemoryAfter();
//                autoLogger.logCpuUsage();
                requestEndTime = WebhookTimeGetter.getTimeNow();
                double differenceInMilliSeconds = Math.abs(requestStartTime.getTime() - requestEndTime.getTime());
                total_time_taken = differenceInMilliSeconds / 1000;

                ListResponseEndpointOut listResponseEndpointOut = sv.getEndpoint().list(appId, new EndpointListOptions());

                if (listResponseEndpointOut.getData().size() > 0) {
                    for (int i = 0; i < listResponseEndpointOut.getData().size(); i++) {
                        Webhook.endpointDelete(listResponseEndpointOut.getData().get(i).getId());
                    }
                }
                
                if(!isMultiple){
                    Webhook.endpointCreate(param.getCallback());
                    Webhook.createMessage(
                       parseInt(param.getAudio_id()),
                       total_time_taken,
                       param.getCallback(),
                       withoutDiarizationResult,
                       "transcription.success");
                } else {
                    WebhookRunnable.resultMapWithoutDiarization.putIfAbsent(param.getAudio_id(), new HashMap<>());
                    ArrayList<String> resArr = new ArrayList<>();
                    resArr.add(withoutDiarizationResult);
                    WebhookRunnable.resultMapWithoutDiarization.get(param.getAudio_id()).put(currentFileNumber, resArr);
                    if(WebhookRunnable.resultMapWithoutDiarization.get(param.getAudio_id()).size() == totalFileNumber){
                        String concatenatedResult = concatenateStrings(WebhookRunnable.resultMapWithoutDiarization.get(param.getAudio_id()));
                        autoLogger.logMsg("Using webhook");
                        Webhook.endpointCreate(param.getCallback());
                        Webhook.createMessage(
                           parseInt(param.getAudio_id()),
                           total_time_taken,
                           param.getCallback(),
                           concatenatedResult,
                           "transcription.success");
                        WebhookRunnable.resultMapWithoutDiarization.remove(param.getAudio_id());
                    }
    
                }

                success = true;
            }
        } else {
            
                Webhook.createMessage(
                   parseInt(param.getAudio_id()),
                   total_time_taken,
                   param.getCallback(),
                   "",
                   "transcription.failed");
                success = false;
            
            
            autoLogger.transcriptionFailed();
            autoLogger.logMemoryAfter();
        }


        autoLogger.logEnd(withDiarizationResult.toString(), success ? "true" : "false");

    }
    
    private ArrayList<String> concatenateResults(Map<Integer, ArrayList<HashMap<String, String>>> results){

        autoLogger.logMsg("Concatenating Diarized Results");

        
        ArrayList<String> concatenated = new ArrayList<>();
        
        double timeToAdd = 0;
        
        for(int i = 1; i<= results.size(); i++){
            
            ArrayList<HashMap<String, String>> modified = results.get(i);
            
            if(i != 1){

                autoLogger.logMsg("Size: " + modified.size());
                // autoLogger.logMsg("Modified: " + modified);

                for(int j = 0; j<modified.size(); j++){

                    autoLogger.logMsg("Iterating index " + j);

                    modified
                        .get(j)
                        .put(
                            "start", 
                            String.valueOf(
                                Double.valueOf(modified.get(j).get("start")) 
                                + timeToAdd
                            ));
                    modified
                        .get(j)
                        .put(
                            "stop", 
                            String.valueOf(
                                Double.valueOf(modified.get(j).get("stop")) 
                                + timeToAdd
                            ));
                    
                    
                }

                autoLogger.logMsg("After concatenation");
            }

            if(modified.size() > 0){
                timeToAdd = Double.valueOf(
                    modified
                        .get(modified.size()-1)
                        .get("stop")
                );
            }


            autoLogger.logMsg("Time to Add: " + timeToAdd);

            // if(i == results.size()){
            //     autoLogger.logMsg("Contatenated Results: " + concatenated.toString());
            // }

            concatenated.add(String.valueOf(modified).substring(1,String.valueOf(modified).length()-1));
        }


        return concatenated;
    }
    
    private String concatenateStrings (Map<Integer, ArrayList<String>> results){

        autoLogger.logMsg("Concatenating Nondiarized Results");

        StringBuilder res = new StringBuilder();
        for(int i = 1, len = results.size(); i<=len; i++){
            res.append(results.get(i));
        }
        return res.toString().replaceAll("[\"\\[\\]\"]", "");
    }

    @Override
    public void run() {
        try {
            sendGcpWebhook();

        } catch (Exception e) {
            e.printStackTrace();
            try {
                autoLogger.exceptionThrown("RETRYING");
                sendGcpWebhook();
                autoLogger.exceptionThrown(e.getMessage());
                autoLogger.exceptionThrown("GCP_RETRIED");
            } catch (Exception err) {
                autoLogger.exceptionThrown(e.getMessage() + ", cause: " + e.getCause() + ", stacktrace: " + Arrays.toString(e.getStackTrace()));
                autoLogger.exceptionThrown("GCP_FAILED");
                Webhook.createMessage(Integer.parseInt(param.getAudio_id()),total_time_taken, param.getCallback(), "", "transcription.failed");
            }
        }
        synchronized (this.diarizer) {
            this.diarizer.queueStatus.put(vacantQueueIndex, true);
            this.diarizer.threadCount -= 1;
            this.diarizer.operateQueue();
        }

    }

}

