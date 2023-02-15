package com.bliss_stock.aiServerAPI.audioControl;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import com.bliss_stock.aiServerAPI.audioControl.AudioInfo;
import com.bliss_stock.aiServerAPI.common.Log;
import com.bliss_stock.aiServerAPI.controller.Speech2textController;

public class AudioManipulator {
    static String SOURCE_PATH = "/usr/local/src/static";
    static Log myLog = Speech2textController.myLog;
    public static String removeSilence(String filePath) {
        File wav_file = new File(filePath);
        String fileName = wav_file.getName().substring(0, wav_file.getName().indexOf("."));
        String wavFilePath = SOURCE_PATH + "/wav_files/" + fileName + "_rs.wav";

        String cmd = "ffmpeg -i " + filePath + " -af silenceremove=" + "detection=peak:" + "stop_mode=all:" + "start_mode=all:" + "stop_threshold=-60dB:" + "start_threshold=-60dB:" + "start_periods=0:" + "stop_periods=-1 " + wavFilePath;
        System.out.println("removeSilence command: " + cmd);
        try{
            Process removeSiln = Runtime.getRuntime().exec(cmd);
            if (removeSiln.waitFor() == 0) {
                System.out.println("Silence removal successful");
            } else {
                System.out.println("Silence removal failed");
                wavFilePath = filePath;
            }
            System.gc();
            Runtime.getRuntime().gc();
            return wavFilePath;
        }
        catch(Exception e){
            myLog.logger.info("AudioManipulator.java, removeSilence() Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    public static HashMap<String, String> segmentAudio(String filePath) {
        try{
            int segmentationTime = 300;
            File wav_file = new File(filePath);
            String fileName = wav_file.getName().substring(0, wav_file.getName().indexOf("."));
            double duration = AudioInfo.getDuration(filePath);
            String segmentDir = SOURCE_PATH + "/wav_files/" + fileName + "_SEGMENTS/";
            Runtime.getRuntime().exec("mkdir " + segmentDir);
            Process segmentation = null;
//        testing with 3minT1.mp3 and 3minT1_silence_detections-d2.txt
            ArrayList<Double> silenceArr = new ArrayList<>() {
                {
                    add(0.0);
                }
            };
            ArrayList<Double> silenceArrOpti = new ArrayList<>();
            ArrayList<Double> silenceUnique = new ArrayList<Double>() {
                {
                    add(0.0);
                }
            };
            DecimalFormat df3 = new DecimalFormat("#.###");
            df3.setRoundingMode(RoundingMode.DOWN);
            DecimalFormat df6 = new DecimalFormat("#.######");
            df6.setRoundingMode(RoundingMode.DOWN);
            silenceArr.addAll(AudioInfo.getSilence(filePath));
//        System.out.println("silenceArr: " + silenceArr);
            for (int i = 0; i < silenceArr.size(); i++) {
                double dec = Double.parseDouble(df6.format(silenceArr.get(i) / segmentationTime));
                silenceArrOpti.add(dec);
            }
            for (int i = 0; i < silenceArrOpti.size(); i++) {
                if (i < silenceArrOpti.size() - 1 && Math.floor(silenceArrOpti.get(i + 1)) > Math.floor(silenceArrOpti.get(i)))
                    silenceUnique.add(Double.parseDouble(df3.format(silenceArrOpti.get(i)*segmentationTime)));
            }
            System.out.println("silenceUnique:" + silenceUnique);
            double durationToClip = 0;
            for (int i = 0; i < silenceUnique.size(); i++) {
                System.out.println("==============================Start loop-" + (i + 1) + "===============================");
                durationToClip = i < silenceUnique.size() - 1 ? Double.parseDouble(df3.format(silenceUnique.get(i + 1) - silenceUnique.get(i))) : Double.parseDouble(df3.format(duration - silenceUnique.get(silenceUnique.size() - 1)));
                String cmd = "ffmpeg -ss " + silenceUnique.get(i) +
                        " -i " + filePath + " -t " + durationToClip + " " + segmentDir + (i + 1) + "_" + fileName + ".wav";
                System.out.println("Segmentaion cmd for loop [" + (i + 1) + "]: " + cmd);
                segmentation = Runtime.getRuntime().exec(cmd);
                System.out.println("==============================End loop-" + (i + 1) + "===============================");
            }

            if (segmentation.waitFor() == 0) {
                System.out.println("Segmentation successful");
            } else {
                System.out.println("Segmentation failed");
            }
            int NoOfFiles = new File(segmentDir).list().length;
            HashMap<String, String> returnVal = new HashMap<>(){
                {
                    put("dirName", segmentDir);
                    put("noOfFiles", String.valueOf(NoOfFiles));
                    put("fileName", fileName);
                }
            };
            System.gc();
            Runtime.getRuntime().gc();
            return returnVal;
        }
        catch(Exception e){
            myLog.logger.info("AudioManipulator.java, segmentAudio() Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String noiseCancellingAFFTDN(String input_file) {
        String ncAfftdnPath = "";
        File inputFile = new File(input_file);
        String outputFile = SOURCE_PATH + "/wav_files/" + inputFile.getName().substring(0, inputFile.getName().indexOf(".")) + "_nc.wav";
        System.out.println("NC AFFTDN Command: " + "ffmpeg -i " + input_file + " -af afftdn=nr=97:nf=-40:nt=c:bn=66|165 " + outputFile);
        try{
            Process ncAfftdn = Runtime.getRuntime().exec("ffmpeg -i " + input_file + " -af afftdn=nr=97:nf=-40:nt=c:bn=66|165 " + outputFile);
            ncAfftdnPath = outputFile;
            if(ncAfftdn.waitFor()== 0){
                System.out.println("Noise cancellation successful.");
            }
            else{
                System.out.println("Noise cancellation failed.");
                ncAfftdnPath = input_file;
            }

            System.gc();
            Runtime.getRuntime().gc();
            return ncAfftdnPath;
        }
        catch(Exception e){
            myLog.logger.info("AudioManipulator.java, noiseCancellingAFFTDN() Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
