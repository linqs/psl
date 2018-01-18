/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.application.learning.weight.search.objective;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedRule;

import java.util.List;
import java.util.Map;

/**
 * An objective based on the continuious error between the targets and truth.
 * The type of error can be changed through the config.
 */
public class ContinuousObjective implements ObjectiveFunction {
   public static final String STAT_MAE = "MAE";
   public static final String STAT_MSE = "MSE";

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "continuousobjective";

	/**
	 * A comma-separated list of possible weights.
	 */
	public static final String STAT_KEY = CONFIG_PREFIX + ".statistic";
	public static final String STAT_DEFAULT = STAT_MSE;

   private String stat;

   public ContinuousObjective(ConfigBundle config) {
      stat = config.getString(STAT_KEY, STAT_DEFAULT).toUpperCase();
      if (!(stat.equals(STAT_MAE) || stat.equals(STAT_MSE))) {
         throw new IllegalArgumentException("Unknown continuious statistic: " + stat);
      }
   }

	public double compute(List<WeightedRule> mutableRules,
			double[] observedIncompatibility, double[] expectedIncompatibility,
			TrainingMap trainingMap) {
      double error = 0;
      boolean square = stat.equals(STAT_MSE);

      for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
         if (square) {
            error += Math.pow(entry.getKey().getValue() - entry.getValue().getValue(), 2);
         } else {
            error += Math.abs(entry.getKey().getValue() - entry.getValue().getValue());
         }
      }

      return error / trainingMap.getTrainingMap().size();
	}
}
