/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.parser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.actions.controls.Sync;
import org.linqs.psl.application.inference.online.messages.actions.controls.WriteInferredPredicates;
import org.linqs.psl.application.inference.online.messages.actions.model.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.GetAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.template.ActivateRule;
import org.linqs.psl.application.inference.online.messages.actions.template.AddRule;
import org.linqs.psl.application.inference.online.messages.actions.template.DeactivateRule;
import org.linqs.psl.application.inference.online.messages.actions.template.DeleteRule;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.parser.antlr.OnlinePSLBaseVisitor;
import org.linqs.psl.parser.antlr.OnlinePSLLexer;
import org.linqs.psl.parser.antlr.OnlinePSLParser;
import org.linqs.psl.parser.antlr.OnlinePSLParser.ActionContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.AddAtomContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.AddRuleContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.DeleteRuleContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.ActivateRuleContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.DeactivateRuleContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.DeleteAtomContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.ExitContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.GetAtomContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.NumberContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.ObserveAtomContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.OnlineProgramContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.UpdateObservationContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.StopContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.SyncContext;
import org.linqs.psl.parser.antlr.OnlinePSLParser.WriteInferredPredicatesContext;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

public class OnlineActionLoader extends OnlinePSLBaseVisitor<Object> {
    /**
     * Parse and return a single OnlineAction.
     * If exactly one action is not specified, an exception is thrown.
     */
    public static OnlineMessage loadAction(String input) {
        List<OnlineMessage> actions = load(new StringReader(input));

        if (actions.size() != 1) {
            throw new IllegalArgumentException(String.format("Expected 1 action, found %d.", actions.size()));
        }

        return actions.get(0);
    }

    /**
     * Convenience interface to load().
     */
    public static List<OnlineMessage> load(String input) {
        return load(new StringReader(input));
    }

    /**
     * Parse and return a list of onlineActions.
     * If exactly one rule is not specified, an exception is thrown.
     */
    public static List<OnlineMessage> load(Reader input) {
        OnlinePSLParser parser = null;
        try {
            parser = getParser(input);
        } catch (IOException ex) {
            // Cancel the lex and rethrow.
            throw new ParseCancellationException("Failed to lex action.", ex);
        }

        OnlineProgramContext onlineProgram = null;
        onlineProgram = parser.onlineProgram();

        OnlineActionLoader visitor = new OnlineActionLoader();
        return visitor.visitOnlineProgram(onlineProgram, parser);
    }

    /**
     * Get a parser over the given input.
     */
    private static OnlinePSLParser getParser(Reader input) throws IOException {
        OnlinePSLLexer lexer = new OnlinePSLLexer(CharStreams.fromReader(input));

        // We need to add a error listener to the lexer so we halt on lex errors.
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object offendingSymbol,
                    int line,
                    int charPositionInLine,
                    String msg,
                    RecognitionException ex) throws ParseCancellationException {
                throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg, ex);
            }
        });
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        OnlinePSLParser parser = new OnlinePSLParser(tokens);
        parser.setErrorHandler(new BailErrorStrategy());

        return parser;
    }

    private OnlineActionLoader() {}

    public List<OnlineMessage> visitOnlineProgram(OnlineProgramContext ctx, OnlinePSLParser parser) {
        List<OnlineMessage> actions = new LinkedList<OnlineMessage>();
        for (ActionContext actionCtx : ctx.action()) {
            try {
                actions.add((OnlineMessage)visit(actionCtx));
            } catch (RuntimeException ex) {
                throw new ParseCancellationException("Failed to compile online action: [" + parser.getTokenStream().getText(actionCtx) + "]", ex);
            }
        }
        return actions;
    }

    @Override
    public OnlineMessage visitAction(ActionContext ctx) {
        if (ctx.addAtom() != null) {
            return visitAddAtom(ctx.addAtom());
        } else if (ctx.addRule() != null) {
            return visitAddRule(ctx.addRule());
        } else if (ctx.deleteRule() != null) {
            return visitDeleteRule(ctx.deleteRule());
        } else if (ctx.activateRule() != null) {
            return visitActivateRule(ctx.activateRule());
        } else if (ctx.deactivateRule() != null) {
            return visitDeactivateRule(ctx.deactivateRule());
        } else if (ctx.deleteAtom() != null) {
            return visitDeleteAtom(ctx.deleteAtom());
        } else if (ctx.exit() != null) {
            return visitExit(ctx.exit());
        } else if (ctx.observeAtom() != null) {
            return visitObserveAtom(ctx.observeAtom());
        } else if (ctx.getAtom() != null) {
            return visitGetAtom(ctx.getAtom());
        } else if (ctx.stop() != null) {
            return visitStop(ctx.stop());
        } else if (ctx.sync() != null) {
            return visitSync(ctx.sync());
        } else if (ctx.updateObservation() != null) {
            return visitUpdateObservation(ctx.updateObservation());
        } else if (ctx.writeInferredPredicates() != null) {
            return visitWriteInferredPredicates(ctx.writeInferredPredicates());
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public AddAtom visitAddAtom(AddAtomContext ctx) {
        Atom atom = ModelLoader.loadAtom(ctx.atom().getText());
        Constant[] constants = new Constant[atom.getArguments().length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = (Constant)atom.getArguments()[i];
        }
        String partition = ctx.PARTITION().getText();
        if (ctx.number() == null) {
            return new AddAtom(partition, (StandardPredicate)atom.getPredicate(), constants);
        }

        return new AddAtom(partition, (StandardPredicate)atom.getPredicate(), constants, visitNumber(ctx.number()));
    }

    @Override
    public DeleteAtom visitDeleteAtom(DeleteAtomContext ctx) {
        Atom atom = ModelLoader.loadAtom(ctx.atom().getText());
        Constant[] constants = new Constant[atom.getArguments().length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = (Constant)atom.getArguments()[i];
        }
        String partition = ctx.PARTITION().getText();

        return new DeleteAtom(partition, (StandardPredicate)atom.getPredicate(), constants);
    }

    @Override
    public ObserveAtom visitObserveAtom(ObserveAtomContext ctx) {
        Atom atom = ModelLoader.loadAtom(ctx.atom().getText());
        Constant[] constants = new Constant[atom.getArguments().length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = (Constant)atom.getArguments()[i];
        }
        float value = visitNumber(ctx.number());

        return new ObserveAtom((StandardPredicate)atom.getPredicate(), constants, value);
    }

    @Override
    public UpdateObservation visitUpdateObservation(UpdateObservationContext ctx) {
        Atom atom = ModelLoader.loadAtom(ctx.atom().getText());
        Constant[] constants = new Constant[atom.getArguments().length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = (Constant)atom.getArguments()[i];
        }
        float value = visitNumber(ctx.number());

        return new UpdateObservation((StandardPredicate)atom.getPredicate(), constants, value);
    }

    @Override
    public GetAtom visitGetAtom(GetAtomContext ctx) {
        Atom atom = ModelLoader.loadAtom(ctx.atom().getText());
        Constant[] constants = new Constant[atom.getArguments().length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = (Constant)atom.getArguments()[i];
        }

        return new GetAtom((StandardPredicate)atom.getPredicate(), constants);
    }

    @Override
    public AddRule visitAddRule(AddRuleContext ctx) {
        Rule rule = ModelLoader.loadRule(ctx.pslRule().getText());

        return new AddRule(rule);
    }

    @Override
    public DeleteRule visitDeleteRule(DeleteRuleContext ctx) {
        Rule rule = ModelLoader.loadRule(ctx.pslRule().getText());

        return new DeleteRule(rule);
    }

    @Override
    public ActivateRule visitActivateRule(ActivateRuleContext ctx) {
        Rule rule = ModelLoader.loadRule(ctx.pslRule().getText());

        return new ActivateRule(rule);
    }

    @Override
    public DeactivateRule visitDeactivateRule(DeactivateRuleContext ctx) {
        Rule rule = ModelLoader.loadRule(ctx.pslRule().getText());

        return new DeactivateRule(rule);
    }

    @Override
    public Exit visitExit(ExitContext ctx) {
        return new Exit();
    }

    @Override
    public Stop visitStop(StopContext ctx) {
        return new Stop();
    }

    @Override
    public Sync visitSync(SyncContext ctx) {
        return new Sync();
    }

    @Override
    public WriteInferredPredicates visitWriteInferredPredicates(WriteInferredPredicatesContext ctx) {
        String outputDirectoryPath = ctx.STRING_LITERAL().getText().substring(1, ctx.STRING_LITERAL().getText().length() - 1);
        return new WriteInferredPredicates(outputDirectoryPath);
    }

    @Override
    public Float visitNumber(NumberContext ctx) {
        return Float.parseFloat(ctx.getText());
    }
}
