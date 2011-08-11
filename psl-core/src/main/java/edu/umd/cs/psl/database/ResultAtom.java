package edu.umd.cs.psl.database;


public class ResultAtom {

	public enum Status { FACT, CERTAINTY, RV }
		
	private final double[] values;
	
	private final double[] confidences;
	
	private Status status;
	
	public ResultAtom(double[] values, double[] confidences) {
		this.values=values;
		this.confidences=confidences;
		this.status = Status.RV;
	}
	
	public ResultAtom(double[] values, double[] confidences, Status status) {
		this.values=values;
		this.confidences=confidences;
		setStatus(status);
	}
	
	public ResultAtom() {
		this(null,null);
	}
	
	public ResultAtom(Status status) {
		this();
		setStatus(status);
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status=status;		
	}

	public double[] getValues() {
		return values;
	}

	public double[] getConfidences() {
		return confidences;
	}
	
	
	
}
