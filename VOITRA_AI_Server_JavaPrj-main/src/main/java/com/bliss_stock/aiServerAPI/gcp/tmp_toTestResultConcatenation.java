package com.bliss_stock.aiServerAPI.gcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*
 * This file can be deleted when concatenation of diarized results are satisfactory. 
 */

public class tmp_toTestResultConcatenation {
    public static void main(String[] args) {
        System.out.println("Hello world");

        Map<Integer, ArrayList<HashMap<String, String>>> res = new HashMap<>(); // Not string but Map.

        for(int i = 1; i<4; i++){
            System.out.println("BUILDING GROUP " + i);
            res.put(i, buildArrayList("SEGMENT " + i));
        }

        System.out.println(concatenateArr(res));
    }

    public static void buildArrayLists(int number){
        for(int i = 0; i<number; i++){
            System.out.println("BUILDING GROUP " + i);
            System.out.println(buildArrayList("Group " + i));
        }
    }

    public static ArrayList<HashMap<String, String>> buildArrayList(String resGroup){

        ArrayList<HashMap<String, String>> resultArray = new ArrayList<HashMap<String, String>>(); // STRING to HASHMAP

        String speaker = "0";
        String text = resGroup ;
        String start = String.valueOf(1);
        String stop = String.valueOf(10);

        HashMap<String, String> resultInfo = new HashMap<String, String>(); // THIS IS MOVED.

        for (int i = 1; i < 4; i++) {
            resultInfo.put("stop", stop);
            resultInfo.put("start", start);
            resultInfo.put("text", text);
            // resultInfo.put("speaker", speaker);
            resultArray.add(resultInfo);

            resultInfo = new HashMap<String, String>(); // THIS IS MOVED.

            text = resGroup + "_" + i;
            speaker = String.valueOf(2%i);
            start = stop;
            stop = String.valueOf(Integer.valueOf(start) + 3*i);
        }

       return resultArray;
    }

    public static ArrayList<String> concatenateArr(Map<Integer, ArrayList<HashMap<String, String>>> results){
        ArrayList<String> concatenated = new ArrayList<>();
        double timeToAdd = 0;

        for(int i = 1; i<= results.size(); i++){
            ArrayList<HashMap<String, String>> modified = results.get(i);
            if(i != 1){
                for(int j = 0; j<modified.size(); j++){
                    modified
                        .get(j)
                        .put(
                            "start", 
                            String.valueOf(
                                Integer.valueOf(modified.get(j).get("start")) 
                                + timeToAdd
                            ));
                    modified
                        .get(j)
                        .put(
                            "stop", 
                            String.valueOf(
                                Integer.valueOf(modified.get(j).get("stop")) 
                                + timeToAdd
                            ));
                }
            }
            timeToAdd = Double.valueOf(
                modified
                    .get(modified.size()-1)
                    .get("stop")
            );
            concatenated.add(String.valueOf(modified));
        }

        return concatenated;
    }
}
