package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acquisition function store. Supports UCB, TS, EI and PI as of now.
 */
public class AcquisitionFunctionsStore {
    private static final Logger log = LoggerFactory.getLogger(AcquisitionFunctionsStore.class);
    private static final AcquisitionFunction UCB_OBJ = new UCB();
    private static final AcquisitionFunction TS_OBJ = new ThompsonSampling();
    private static final AcquisitionFunction PI_OBJ = new PI();
    private static final AcquisitionFunction EI_OBJ = new EI();

    public static AcquisitionFunction getAcquisitionFunction(String acqFunName){
        switch (acqFunName){
            case "UCB": log.info("Choosing UCB acquisition function.");return UCB_OBJ;
            case "TS": log.info("Choosing TS acquisition function.");return TS_OBJ;
            case "PI": log.info("Choosing PI acquisition function.");return PI_OBJ;
            case "EI": log.info("Choosing EI acquisition function.");return EI_OBJ;
            default: throw new RuntimeException("The acquisition function is not supported : " + acqFunName);
        }
    }
}
