/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.evaluation.statistics;

public class MulticlassPredictionStatistics implements PredictionStatistics {

	private final ConfusionMatrix cm;
	
	private int numEx;
	private int numCor;
	private int numErr;
	private int[] tp;
	private int[] fp;
	private int[] fn;
	
	private double acc;
	private double f1;
	private double[] precision;
	private double[] recall;
	private double[] f1class;
	
	public MulticlassPredictionStatistics(final ConfusionMatrix cm) {
		this.cm = cm;
		computeStats();
	}
	
	/**
	 * Returns the total number of errors.
	 * 
	 * @return Number of errors = sum of off-diagonal entries in confusion matrix.
	 */
	public double getAccuracy() {
		return acc;
	}
	
	/**
	 * Returns the overall F1, computed as the weighted average of the per-class F1 scores.
	 * The weights are determined by the true label distribution.
	 * 
	 * @return Overall F1
	 */
	public double getF1() {
		return f1;
	}
	
	/**
	 * Returns the per-class F1.
	 * 
	 * @param label Index of the label (i.e., class).
	 * @return
	 */
	public double getF1(int label) {
		return f1class[label];
	}
	
	/**
	 * Returns the per-class precision.
	 * 
	 * Let CM denote the confusion matrix.
	 * Let TP = CM[label][label].
	 * Let FP = sum_{i != label} CM[i][label].
	 * Per-class precision = TP / (TP + FP).
	 * 
	 * @param label
	 * @return
	 */
	public double getPrecision(int label) {
		return precision[label];
	}
	
	/**
	 * Returns the per-class recall.
	 * 
	 * Let CM denote the confusion matrix.
	 * Let TP = CM[label][label].
	 * Let FN = sum_{j != label} CM[label][j].
	 * Per-class precision = TP / (TP + FN).
	 * 
	 * @param label
	 * @return
	 */
	public double getRecall(int label) {
		return recall[label];
	}
	
	/**
	 * Returns a clone (deep copy) of the confusion matrix.
	 * 
	 * @return
	 */
	public ConfusionMatrix getConfusionMatrix() {
		return cm.clone();
	}
	
	@Override
	public double getError() {
		return numErr;
	}

	@Override
	public int getNumAtoms() {
		return numEx;
	}
	
	private void computeStats() {
		int numClass = cm.getNumClasses();
		
		/* Compute counts. */
		numCor = 0;
		numErr = 0;
		tp = new int[numClass];
		fp = new int[numClass];
		fn = new int[numClass];
		for (int i = 0; i < numClass; i++) {
			for (int j = 0; j < numClass; j++) {
				int val = cm.get(i,j);
				if (i == j) {
					numCor += val;
					tp[i] = val;
				}
				else {
					numErr += val;
					fp[j] += val;
					fn[i] += val;
				}
			}
		}
		numEx = numCor + numErr;
		
		/* Compute statistics. */
		acc = numCor / (double)numEx;
		f1 = 0.0;
		precision = new double[numClass];
		recall = new double[numClass];
		f1class = new double[numClass];
		for (int i = 0; i < numClass; i++) {
			/* Compute per-class stats. */
			precision[i] = (tp[i]+fp[i]) == 0 ? 1.0 : tp[i] / (double)(tp[i]+fp[i]);
			recall[i] = (tp[i]+fn[i]) == 0 ? 1.0 : tp[i] / (double)(tp[i]+fn[i]);
			f1class[i] = (precision[i]+recall[i]) == 0 ? 0.0 : 2.0*precision[i]*recall[i] / (precision[i]+recall[i]);
			/* Overall F1 is weighted average (by class frequency in ground truth) of per-class F1. */
			f1 += f1class[i] * (tp[i]+fn[i]) / (double)numEx;
		}
	}

}
