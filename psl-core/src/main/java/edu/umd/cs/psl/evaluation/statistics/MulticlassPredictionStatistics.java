package edu.umd.cs.psl.evaluation.statistics;

public class MulticlassPredictionStatistics implements PredictionStatistics {

	private final int[][] cm;
	
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
	
	public MulticlassPredictionStatistics(final int[][] confusionMatrix) {
		cm = confusionMatrix;
		computeStats();
	}
	
	public double getAccuracy() {
		return acc;
	}
	
	public double getF1() {
		return f1;
	}
	
	public double getF1(int label) {
		return f1class[label];
	}
	
	public double getPrecision(int label) {
		return precision[label];
	}
	
	public double getRecall(int label) {
		return recall[label];
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
		int numClass = cm.length;
		
		/* Compute counts. */
		numCor = 0;
		numErr = 0;
		tp = new int[cm.length];
		fp = new int[cm.length];
		fn = new int[cm.length];
		for (int i = 0; i < numClass; i++) {
			for (int j = 0; j < numClass; j++) {
				if (i == j) {
					numCor += cm[i][j];
					tp[i] = cm[i][j];
				}
				else {
					numErr += cm[i][j];
					fp[j] += cm[i][j];
					fn[i] += cm[i][j];
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
			precision[i] = (tp[i]+fp[i]) == 0 ? 0.0 : tp[i] / (double)(tp[i]+fp[i]);
			recall[i] = (tp[i]+fn[i]) == 0 ? 0.0 : tp[i] / (double)(tp[i]+fn[i]);
			f1class[i] = (precision[i]+recall[i]) == 0 ? 0.0 : 2.0*precision[i]*recall[i] / (precision[i]+recall[i]);
			/* Overall F1 is weighted average (by class frequency in ground truth) of per-class F1. */
			f1 += f1class[i] * (tp[i]+fn[i]) / (double)numEx;
		}
	}

}
