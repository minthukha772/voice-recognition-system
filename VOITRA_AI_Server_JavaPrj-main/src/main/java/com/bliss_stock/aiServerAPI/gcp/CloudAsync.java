package com.bliss_stock.aiServerAPI.gcp;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.retrying.TimedRetryAlgorithm;
import com.google.cloud.speech.v1.*;

import com.bliss_stock.aiServerAPI.audioControl.AudioConverter;
import org.threeten.bp.Duration;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.bliss_stock.aiServerAPI.controller.Speech2textController.myLog;

public class CloudAsync {
  /**
   * Performs non-blocking speech recognition on remote FLAC file and prints the transcription.
   *
   * @param gcsUri the path to the remote LINEAR16 audio file to transcribe.
   */
  public static String asyncRecognizeGcs(
          String gcsUri,
          String customLang,
          String customEncoding,
          int customHertz)
          throws Exception {
    // Configure polling algorithm
    String transcriptionResult = "";
    try {

      SpeechSettings.Builder speechSettings = SpeechSettings.newBuilder();
      TimedRetryAlgorithm timedRetryAlgorithm =
              OperationTimedPollAlgorithm.create(
                      RetrySettings.newBuilder()
                              .setInitialRetryDelay(Duration.ofMillis(500L))
                              .setRetryDelayMultiplier(1.5)
                              .setMaxRetryDelay(Duration.ofMillis(5000L))
                              .setInitialRpcTimeout(Duration.ZERO) // ignored
                              .setRpcTimeoutMultiplier(1.0) // ignored
                              .setMaxRpcTimeout(Duration.ZERO) // ignored
                              .setTotalTimeout(Duration.ofHours(24L)) // set polling timeout to 24 hours
                              .build());
      speechSettings.longRunningRecognizeOperationSettings().setPollingAlgorithm(timedRetryAlgorithm);

      // Instantiates a client with GOOGLE_APPLICATION_CREDENTIALS
      SpeechClient speech = SpeechClient.create(speechSettings.build());

      // Configure remote file request for FLAC
      RecognitionConfig config =
              RecognitionConfig.newBuilder()
                      .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // How to make this dynamic??
                      .setLanguageCode(customLang)
                      .setSampleRateHertz(customHertz)
                      .setEnableAutomaticPunctuation(true)
                      .build();
      RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gcsUri).build();

      // Use non-blocking call for getting file transcription
      OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
              speech.longRunningRecognizeAsync(config, audio);
//       while (!response.isDone()) {
// //        System.out.println("Waiting for response...");
//         Thread.sleep(20000);
//       }

      List<SpeechRecognitionResult> results = response.get().getResultsList();

      for (SpeechRecognitionResult result : results) {
        // There can be several alternative transcripts for a given chunk of speech. Just use the
        // first (most likely) one here.
        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
        transcriptionResult += alternative.getTranscript();
      }
      System.gc();
      Runtime.getRuntime().gc();
      return transcriptionResult;
    }
    catch (ExecutionException e) {
      myLog.logger.info("GCP ERROR in CloudAsync.java: " + e.getMessage());
      String error = String.valueOf(e.getCause());
      System.out.println(error);
      if (error.contains("InvalidArgumentException")){
        System.out.println("Handling InvalidArgumentException");
        String[] array = gcsUri.split("GCP_AUDIO/");
        String obj_name = array[1];
        System.out.println(obj_name);

        String SOURCE_PATH = "/usr/local/src/static";
        //String obj_name = u + wf;
        DownloadObject d_obj = new DownloadObject();
        d_obj.downloadObject("voitra", "voitra-stt", "GCP_AUDIO/" +obj_name, SOURCE_PATH + "/audio_files/"+ obj_name);

        String file_to_convert_from = SOURCE_PATH + "/audio_files/" + obj_name;
        String file_to_convert_to = SOURCE_PATH + "/wav_files/" + obj_name.substring(0, obj_name.lastIndexOf(".")) + ".wav";
        AudioConverter audioConverter = new AudioConverter(file_to_convert_from, file_to_convert_to, 16000);
        audioConverter.convertAudio();

        UploadObject.uploadObject("voitra", "voitra-stt", "GCP_AUDIO/" + obj_name, SOURCE_PATH + "/wav_files/" + obj_name);

        transcriptionResult = CloudAsync.asyncRecognizeGcs(
                "gs://voitra-stt/GCP_AUDIO/" + obj_name,
                customLang,
                "LINEAR16",
                16000);
      }
      System.gc();
      Runtime.getRuntime().gc();
      return transcriptionResult;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
