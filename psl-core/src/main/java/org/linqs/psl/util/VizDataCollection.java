package org.linqs.psl.util;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.atom.RandomVariableAtom;

public class VizDataCollection {

    private static VizDataCollection vizData = null;
    private static Runtime runtime = null;

    private JSONArray jsonArray;

    static {
        init();
    }

    // Static only.
    private VizDataCollection() {
        jsonArray = new JSONArray();
    }

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }

        vizData = new VizDataCollection();
        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }



    //All the methods can be void as we will just be outputting to JSON

    //Running org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPriorTest

    //Takes in a prediction truth pair and adds it to our map
    public static void PredictionTruth(GroundAtom target, float predictVal, float truthVal ) {
        valueObj.put("Truth", truthVal);
        valueObj.put("Prediction", predictVal);
        valueObj.put("Predicate", target.toString());
        vizData.jsonArray.put(valueObj);


    }

    //We want to make:
    // A jsonArray filled with jsonObjects
    // each object represents a predicate, prediction val, and truth value
    //e.x.
        // [
        //     {predicate: Friends((bob,george), prediction: 0.00003, truth: 1}
        //     {predicate: Friends((alice,george), prediction: 0.00003, truth: 1}
        //     etc...
        // ]
    public static void OutputJSON() {
        //Debug
        // System.out.println(vizData.jsonArray);
        try (FileWriter file = new FileWriter("output.json")) {

            // file.write(vizData.vizJson.toJSONString());
            file.write(vizData.jsonArray.toString(4));
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            System.out.println("ShutdownHook running");
            OutputJSON();
        }
    }


    // public static void GroundingsPerRule() {
    //
    // }
    //
    // public static void TotalRuleSatDis() {
    //
    // }
    //
    // public static void DebugOutput() {
    //
    // }
    //
    // //These two may want to use as helper functions
    // // e.x. this is where we turn the rules into non dnf form
    // public static void SingleRuleHandler() {
    //
    // }
    //
    // public static void SingleAtomHandler() {
    //
    // }


}
