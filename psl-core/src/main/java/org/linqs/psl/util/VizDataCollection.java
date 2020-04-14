package org.linqs.psl.util;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

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

    //We want to make:
    // A jsonArray filled with jsonObjects
    // each object represents a predicate, prediction val, and truth value
    //e.x.
        // [
        //     {predicate: Friends((bob,george), prediction: 0.00003, truth: 1}
        //     {predicate: Friends((alice,george), prediction: 0.00003, truth: 1}
        //     etc...
        // ]
    public static void outputJSON() {
        //Debug
        // System.out.println(vizData.jsonArray);
        try (FileWriter file = new FileWriter("output.json")) {
            file.write(vizData.jsonArray.toString(4));
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputJSON();
        }
    }

    //Tests:
    //predictionTruth -> ContinuousEvaluatorTest, GaussianProcessPriorTest, InitialWeightHyperbandTest (not called from cli)

    //All the methods can be void as we will just be outputting to JSON

    //Takes in a prediction truth pair and adds it to our map
    public static void predictionTruth(GroundAtom target, float predictVal, float truthVal ) {
        JSONObject valueObj = new JSONObject();
        valueObj.put("Truth", truthVal);
        valueObj.put("Prediction", predictVal);
        valueObj.put("Predicate", target.toString());
        vizData.jsonArray.put(valueObj);
    }

    // public static void groundingsPerRule() {
    //
    // }

    public static void totalRuleSatDis(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String row = "";
            double satisfaction = 0.0;
            JSONObject valueObj = new JSONObject();

            valueObj.put("Rule", groundRule.baseToString());

            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                valueObj.put("Satisfaction", 1.0 - weightedGroundRule.getIncompatibility());
            } else {
                UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
                valueObj.put("Satisfaction", 1.0 - unweightedGroundRule.getInfeasibility());
            }

            vizData.jsonArray.put(valueObj);
        }
    }
    //
    // public static void debugOutput() {
    //
    // }
    //
    // //These two may want to use as helper functions
    // // e.x. this is where we turn the rules into non dnf form
    // public static void singleRuleHandler() {
    //
    // }
    //
    // public static void singleAtomHandler() {
    //
    // }
}
