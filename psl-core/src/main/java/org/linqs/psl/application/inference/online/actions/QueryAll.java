package org.linqs.psl.application.inference.online.actions;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryAll extends OnlineAction{

    private String predicateName;
    private Constant[] arguments;
    private float newValue;
    private static final Logger log = LoggerFactory.getLogger(QueryAll.class);

    public QueryAll() {
    }

    @Override
    public String getName() {
        return "QueryAll";
    }

    @Override
    public void initAction(String[] tokenized_command) throws IllegalArgumentException {
    }
}
