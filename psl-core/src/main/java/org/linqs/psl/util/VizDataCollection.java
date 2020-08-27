package org.linqs.psl.util;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    public static void outputJSON() throws IOException {
        FilterOutputStream stream = System.out;

        if (outputPath != null) {
            try {
                stream = new GZIPOutputStream(new PrintStream(outputPath));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        writeToStream(stream);

        if (outputPath != null) {
            stream.close();
        }
    }

    /**
     * Write to stream with JSON formatting.
     */
    private static void writeToStream(FilterOutputStream stream) throws IOException {
        // JSON format reference: https://www.json.org/json-en.html.
        stream.write("{ \"truthMap\" :".getBytes());

        // Write each map as a JSON object, each JSON object is comma delimited.
        writeMap(stream, vizData.truthMap, "truthMap");
        stream.write(", \"rules\" :".getBytes());
        writeMap(stream, vizData.rules, "rules");
        stream.write(", \"groundRules\" :".getBytes());
        writeMap(stream, vizData.groundRules, "groundRules");
        stream.write(", \"groundAtoms\" :".getBytes());
        writeMap(stream, vizData.groundAtoms, "groundAtoms");

        stream.write('}');
    }

    /**
     * Write map to stream with JSON formatting.
     */
    @SuppressWarnings("unchecked")
    private static void writeMap(FilterOutputStream stream, Object map, String z) throws IOException {
        stream.write('{');

        Map<String, Object> stringObjMap = (Map<String, Object>) map;
        Iterator<Map.Entry<String, Object>> iterator = stringObjMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            stream.write((" \"" + entry.getKey() + "\" :").getBytes());

            // Values of the map will either be a Float or Map
            if (entry.getValue() instanceof Float) {
                stream.write(entry.getValue().toString().getBytes());
            } else {
                // Assumption that the JSON Objects carry small amounts of data
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                JSONObject jsonObject = new JSONObject(data);
                stream.write(jsonObject.toString().getBytes());
            }

            if (iterator.hasNext()) {
                stream.write(',');
            }
        }

        stream.write('}');
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            try {
                outputJSON();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
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

    /**
     * Takes in a prediction truth pair and adds it to the Truth Map.
     */
    public static void addTruth(GroundAtom target, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        vizData.truthMap.put(groundAtomID, truthVal);
    }

    public static void dissatisfactionPerGroundRule(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String strGroundRuleId = Integer.toString(System.identityHashCode(groundRule));
            Map<String, Object> groundRuleObj = vizData.groundRules.get(strGroundRuleId);
            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", weightedGroundRule.getIncompatibility());
            }
        }
    }

    // TODO: Collect Abstract Arithmetic Ground  Rules
    public static synchronized void addGroundRule(AbstractRule parentRule,
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

        // Adds a rule element to RuleMap.
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
