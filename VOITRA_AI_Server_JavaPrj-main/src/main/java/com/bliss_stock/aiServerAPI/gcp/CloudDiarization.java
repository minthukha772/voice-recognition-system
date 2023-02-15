package com.bliss_stock.aiServerAPI.gcp;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeakerDiarizationConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.WordInfo;
import com.google.protobuf.Duration;
import com.bliss_stock.aiServerAPI.audioControl.AudioConverter;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;
import java.util.HashMap; // import the HashMap class
import java.util.ArrayList; // import the ArrayList class


public class CloudDiarization {
    // Transcribe the give gcs file using speaker diarization
    public static ArrayList<HashMap<String, String>> transcribeDiarizationGcs(
            String gcsUri,
            String customLang,
            String customEncoding,
            int customHertz,
            int SpeakerCountMin,
            int SpeakerCountMax
    )
            throws Exception {
        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.

        //Before
        // ArrayList<String> resultArray = new ArrayList<String>();

        //Now
        ArrayList<HashMap<String, String>> resultArray = new ArrayList<HashMap<String, String>>();

        try {
            SpeechClient speechClient = SpeechClient.create();
            SpeakerDiarizationConfig speakerDiarizationConfig =
                    SpeakerDiarizationConfig.newBuilder()
                            .setEnableSpeakerDiarization(true)
                            .setMinSpeakerCount(SpeakerCountMin)
                            .setMaxSpeakerCount(SpeakerCountMax)
                            .build();
            // Configure request to enable Speaker diarization
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            // Assumed that only LINEAR16 and FLAC exist.
                            .setLanguageCode(customLang)
                            .setSampleRateHertz(customHertz)
                            .setDiarizationConfig(speakerDiarizationConfig)
                            .build();
            // Set the remote path for the audio file
            RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gcsUri).build();

            // Use non-blocking call for getting file transcription
            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future =
                    speechClient.longRunningRecognizeAsync(config, audio);
            while (!future.isDone()) {
                System.out.println("Waiting for response...");
                Thread.sleep(10000);
            }
            //System.out.println("Waiting for response...");

            // Speaker Tags are only included in the last result object, which has only one alternative.
            LongRunningRecognizeResponse response = future.get();

//            System.out.println("response below: ");
//            System.out.println(response);

            SpeechRecognitionAlternative alternative =
                    response.getResults(response.getResultsCount() - 1).getAlternatives(0);

            // The alternative is made up of WordInfo objects that contain the speaker_tag.
            WordInfo wordInfo = alternative.getWords(0);
            // Create an ArrayList object
            HashMap<String, String> resultInfo = new HashMap<String, String>();
            int currentSpeakerTag = wordInfo.getSpeakerTag();
            String text = "";
            String speaker = String.valueOf(alternative.getWords(0).getSpeakerTag());
            String start = String.valueOf(TimeInSeconds(alternative.getWords(0).getStartTime()));
            String stop = String.valueOf(TimeInSeconds(alternative.getWords(0).getEndTime()));


            for (int i = 0; i < alternative.getWordsCount(); i++) {
                wordInfo = alternative.getWords(i);
                if (currentSpeakerTag == wordInfo.getSpeakerTag()) {
                    speaker = String.valueOf(wordInfo.getSpeakerTag());
                    //start = String.valueOf(TimeInSeconds(alternative.getWords(i).getStartTime()));
                    stop = String.valueOf(TimeInSeconds(alternative.getWords(i).getEndTime()));
                    text += wordInfo.getWord().split("[|]")[0];

                } else {
                    resultInfo.put("speaker", speaker);
                    resultInfo.put("start", start);
                    resultInfo.put("stop", stop);
                    resultInfo.put("text", text);
                    resultArray.add(resultInfo);

                    resultInfo = new HashMap<String, String>();

                    text = "";
                    speaker = String.valueOf(wordInfo.getSpeakerTag());
                    start = String.valueOf(TimeInSeconds(alternative.getWords(i).getStartTime()));
                    stop = String.valueOf(TimeInSeconds(alternative.getWords(i).getEndTime()));
                    text += wordInfo.getWord().split("[|]")[0];

                    currentSpeakerTag = wordInfo.getSpeakerTag();

                }
            }
            resultInfo.put("speaker", speaker);
            resultInfo.put("start", start);
            resultInfo.put("stop", stop);
            resultInfo.put("text", text);
            resultArray.add(resultInfo);
            System.gc();
            Runtime.getRuntime().gc();
            return resultArray;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            String error = String.valueOf(e.getCause());
            System.out.println(error);
            if (error.contains("InvalidArgumentException")) {
                //System.out.println("Handle here!!!!");
                System.out.println("Handling InvalidArgumentException");
                String[] array = gcsUri.split("GCP_AUDIO/");
                String obj_name = array[1];
                System.out.println(obj_name);

                String SOURCE_PATH = System.getProperty("user.dir") + "/src/static";
                //String obj_name = u + wf;
                DownloadObject d_obj = new DownloadObject();
                d_obj.downloadObject("voitra", "voitra-stt", "GCP_AUDIO/" + obj_name, SOURCE_PATH + "/audio_files/" + obj_name);

                String file_to_convert_from = SOURCE_PATH + "/audio_files/" + obj_name;
                String file_to_convert_to = SOURCE_PATH + "/wav_files/" + obj_name.substring(0, obj_name.lastIndexOf(".")) + ".wav";
                AudioConverter audioConverter = new AudioConverter(file_to_convert_from, file_to_convert_to, 16000);
                audioConverter.convertAudio();

                UploadObject.uploadObject("voitra", "voitra-stt", "GCP_AUDIO/" + obj_name, SOURCE_PATH + "/wav_files/" + obj_name);

                resultArray = CloudDiarization.transcribeDiarizationGcs(
                        "gs://voitra-stt/GCP_AUDIO/" + obj_name,
                        customLang,
                        "LINEAR16",
                        16000,
                        2,
                        SpeakerCountMax);
            }
            System.gc();
            Runtime.getRuntime().gc();
            return resultArray;
        }
    }

    public static ArrayList<HashMap<String, String>> transcribeDiarization(
            String gcsUri,
            String customLang,
            String customEncoding,
            int customHertz
    )
            throws IOException, ExecutionException, InterruptedException, UnsupportedAudioFileException {
        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.
        ArrayList<HashMap<String, String>> resultArray = new ArrayList<HashMap<String, String>>();
        try {
            SpeechClient speechClient = SpeechClient.create();
            SpeakerDiarizationConfig speakerDiarizationConfig =
                    SpeakerDiarizationConfig.newBuilder()
                            .setEnableSpeakerDiarization(true)
                            .build();
            // Configure request to enable Speaker diarization
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            // Assumed that only LINEAR16 and FLAC exist.
                            .setLanguageCode(customLang)
                            .setSampleRateHertz(customHertz)
                            .setDiarizationConfig(speakerDiarizationConfig)
                            .build();
            // Set the remote path for the audio file
            RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gcsUri).build();

            // Use non-blocking call for getting file transcription
            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future =
                    speechClient.longRunningRecognizeAsync(config, audio);
            // while (!future.isDone()) {
            //     System.out.println("Waiting for response...");
            //     Thread.sleep(20000);
            // }
            System.out.println("Waiting for response...");

            // Speaker Tags are only included in the last result object, which has only one alternative.
            LongRunningRecognizeResponse response = future.get();

//            System.out.println("response below: ");
//            System.out.println(response);

            SpeechRecognitionAlternative alternative =
                    response.getResults(response.getResultsCount() - 1).getAlternatives(0);

            // The alternative is made up of WordInfo objects that contain the speaker_tag.
            WordInfo wordInfo = alternative.getWords(0);
            //ArrayList<String> resultArray = new ArrayList<String>(); // Create an ArrayList object
            HashMap<String, String> resultInfo = new HashMap<String, String>();
            int currentSpeakerTag = wordInfo.getSpeakerTag();
            String text = "";
            String speaker = String.valueOf(alternative.getWords(0).getSpeakerTag());
            String start = String.valueOf(TimeInSeconds(alternative.getWords(0).getStartTime()));
            String stop = String.valueOf(TimeInSeconds(alternative.getWords(0).getEndTime()));


            for (int i = 0; i < alternative.getWordsCount(); i++) {

                wordInfo = alternative.getWords(i);
                if (currentSpeakerTag == wordInfo.getSpeakerTag()) {
                    speaker = String.valueOf(wordInfo.getSpeakerTag());
                    //start = String.valueOf(TimeInSeconds(alternative.getWords(i).getStartTime()));
                    stop = String.valueOf(TimeInSeconds(alternative.getWords(i).getEndTime()));
                    text += wordInfo.getWord().split("[|]")[0];

                } else {
                    resultInfo.put("speaker", speaker);
                    resultInfo.put("start", start);
                    resultInfo.put("stop", stop);
                    resultInfo.put("text", text);
                    resultArray.add(resultInfo);
                    
                    resultInfo = new HashMap<String, String>();
                    text = "";
                    speaker = String.valueOf(wordInfo.getSpeakerTag());
                    start = String.valueOf(TimeInSeconds(alternative.getWords(i).getStartTime()));
                    stop = String.valueOf(TimeInSeconds(alternative.getWords(i).getEndTime()));
                    text += wordInfo.getWord().split("[|]")[0];

                    currentSpeakerTag = wordInfo.getSpeakerTag();

                }
            }
            resultInfo.put("speaker", speaker);
            resultInfo.put("start", start);
            resultInfo.put("stop", stop);
            resultInfo.put("text", text);
            resultArray.add(resultInfo);

            System.gc();
            Runtime.getRuntime().gc();
            return resultArray;
            
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            String error = String.valueOf(e.getCause());
            System.out.println(error);
            if (error.contains("InvalidArgumentException")) {
                //System.out.println("Handle here!!!!");
                System.out.println("Handling InvalidArgumentException");
//gs://voitra-stt/GCP_AUDIO/f097bf9f-f9d6-4e61-8020-6c6eaa0cb48bf097bf9f-f9d6-4e61-8020-6c6eaa0cb48b-20220913_632011012935e.wav
                String[] array = gcsUri.split("GCP_AUDIO/");
                String obj_name = array[1];
                System.out.println(obj_name);

                String SOURCE_PATH = System.getProperty("user.dir") + "/src/static";
                //String obj_name = u + wf;
                DownloadObject d_obj = new DownloadObject();
                d_obj.downloadObject("voitra", "voitra-stt", "GCP_AUDIO/" + obj_name, SOURCE_PATH + "/audio_files/" + obj_name);

                String file_to_convert_from = SOURCE_PATH + "/audio_files/" + obj_name;
                String file_to_convert_to = SOURCE_PATH + "/wav_files/" + obj_name.substring(0, obj_name.lastIndexOf(".")) + ".wav";
                AudioConverter audioConverter = new AudioConverter(file_to_convert_from, file_to_convert_to, 16000);
                audioConverter.convertAudio();

                UploadObject.uploadObject("voitra", "voitra-stt", "GCP_AUDIO/" + obj_name, SOURCE_PATH + "/wav_files/" + obj_name);

                resultArray = CloudDiarization.transcribeDiarization(
                        "gs://voitra-stt/GCP_AUDIO/" + obj_name,
                        "ja-JP",
                        "LINEAR16",
                        16000);
            }
            System.gc();
            Runtime.getRuntime().gc();
            return resultArray;
        }
    }


    private static double TimeInSeconds(Duration time) {
        // Concatenates seconds and nanoseconds.
        return Double.parseDouble(
                new DecimalFormat("0.000")
                        .format((time.getSeconds() + (time.getNanos() / 1E+9))));
    }
}