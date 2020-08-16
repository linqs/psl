package org.linqs.psl.util;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class VizDataCollection {
    private static final Logger log = LoggerFactory.getLogger(VizDataCollection.class);

    private static Runtime runtime = null;
    private static VisualizationData vizData = null;

    private static String outputPath = null;

    static {
        init();
    }

    private VizDataCollection() {}

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }
        vizData = new VisualizationData();
        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }

    public static void outputJSON() {
        String[] keyNames = {"truthMap", "rules", "groundRules", "groundAtoms"};
        JSONObject fullJson = new JSONObject(vizData, keyNames);

        PrintStream stream = System.out;

        if (outputPath != null) {
            try {
                stream = new PrintStream(outputPath);
                if (outputPath.endsWith(".gz")) {
                    GZIPOutputStream gzipStream = new GZIPOutputStream(stream, true);
                    byte[] jsonByteArray = fullJson.toString().getBytes();
                    gzipStream.write(jsonByteArray, 0, jsonByteArray.length);
                    gzipStream.close();
                } else {
                    stream.println(fullJson.toString());
                }
                stream.close();
            } catch (IOException ex) {
                throw new RuntimeException();
            }
        } else {
            stream.println(fullJson.toString());
        }
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputJSON();
        }
    }

    public static class VisualizationData {
        public Map<String, Float> truthMap;
        public Map<String, Map<String, Object>> rules;
        public Map<String, Map<String, Object>> groundRules;
        public Map<String, Map<String, Object>> groundAtoms;

        public VisualizationData() {
            truthMap = new HashMap<String, Float>();
            rules = new HashMap<String, Map<String, Object>>();
            groundRules = new HashMap<String, Map<String, Object>>();
            groundAtoms = new HashMap<String, Map<String, Object>>();
        }
    }

    public static void setOutputPath(String path) {
        outputPath = path;
    }
    // Takes in a prediction truth pair and adds it to the Truth Map.
    public static void addTruth(GroundAtom target, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        vizData.truthMap.put(groundAtomID, truthVal);
    }

    public static void dissatisfactionPerGroundRule(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String strGroundRuleId = Integer.toString(System.identityHashCode(groundRule));
            if (groundRule instanceof WeightedGroundRule) {
                Map<String, Object> groundRuleObj = vizData.groundRules.get(strGroundRuleId);
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", weightedGroundRule.getIncompatibility());
            }
        }
    }
    // TODO: Arithmetic Ground Rules Collection
    public static synchronized void addGroundRule(AbstractLogicalRule parentRule,
            GroundRule groundRule, Map<Variable, Integer> variableMap,  Constant[] constantsList) {
        if (groundRule == null) {
            return;
        }

        // Adds a groundAtom element to Ground Atom Map.
        ArrayList<Integer> atomHashList = new ArrayList<Integer>();
        HashSet<GroundAtom> atomSet = new HashSet<GroundAtom>(groundRule.getAtoms());
        int atomCount = 0;
        for (GroundAtom groundAtom : atomSet) {
            atomHashList.add(System.identityHashCode(groundAtom));
            Map<String, Object> groundAtomElement = new HashMap<String, Object>();
            groundAtomElement.put("text", groundAtom.toString());
            groundAtomElement.put("prediction", groundAtom.getValue());
            vizData.groundAtoms.put(Integer.toString(System.identityHashCode(groundAtom)), groundAtomElement);
            atomCount++;
        }

        // Adds a rule element to RuleMap
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        rulesElementItem.put("weighted", parentRule.isWeighted());
        vizData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to Ground Rule Map.
        Map<String, String> varConstMap = new HashMap<String, String>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }

        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        Map<String, Object> constants = new HashMap<String, Object>();
        for (Map.Entry varConstElement : varConstMap.entrySet()) {
          String key = (String)varConstElement.getKey();
          String val = (String)varConstElement.getValue();
          constants.put(key,val);
        }

        groundRulesElement.put("constants", constants);
        groundRulesElement.put("groundAtoms", atomHashList);
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        vizData.groundRules.put(groundRuleStringID, groundRulesElement);
    }
}
