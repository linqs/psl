/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.DateAttribute;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helpers to deal with external database functions.
 */
public class ExternalFunctions {
	public static final String ALIAS_FUNCTION_NAME = "extFunctionCall";

	// Map for external function registration
	private static final BiMap<ExternalFunction, String> externalFunctions = HashBiMap.create();

	public static void registerFunctionAlias(Connection connection) {
		try (Statement stmt = connection.createStatement()) {
         stmt.executeUpdate("CREATE ALIAS IF NOT EXISTS "
               + ALIAS_FUNCTION_NAME + " FOR \""
               + ExternalFunctions.class.getCanonicalName()
               + ".registeredExternalFunctionCall\" ");
		} catch (SQLException ex) {
			throw new RuntimeException("Could not register function alias.", ex);
		}
	}

	/**
	 * Registers and returns an ID for a given ExternalFunction. If this function
	 * was already registered, returns the same ID that was returned initially.
	 * @param extFun	the ExternalFunction to register
	 * @return			the String ID for this function
	 */
	public static String getExternalFunctionID(ExternalFunction extFun) {
		if (externalFunctions.containsKey(extFun)) {
			return externalFunctions.get(extFun);
		}

		String id = "extFun_" + externalFunctions.size();
		externalFunctions.put(extFun, id);
		return id;
	}

	/**
	 * Used by the RDBMS to make an external function call.
	 * @param databaseID	the ID for the {@link RDBMSDatabase} associated with this function call
	 * @param functionID	the ID of the {@link ExternalFunction} to execute
	 * @param args			the arguments for the ExternalFunction
	 * @return				the result from the ExternalFunction
	 */
	public static double registeredExternalFunctionCall(String databaseID, String functionID, String... args) {
		ReadOnlyDatabase db = RDBMSDataStore.getDatabase(databaseID);

		ExternalFunction extFun = externalFunctions.inverse().get(functionID);
		if (extFun == null) {
			throw new IllegalArgumentException("Unknown external function alias: " + functionID);
		}

		if (args.length != extFun.getArgumentTypes().length) {
			throw new IllegalArgumentException(String.format(
					"Number of arguments (%d) does not match arity of external function (%d)!",
					args.length, extFun.getArgumentTypes().length));
		}

		Constant[] arguments = new Constant[args.length];
		for (int i = 0; i < args.length; i++) {
			if (args[i] == null) {
				throw new IllegalArgumentException("Argument cannot be null!");
			}

			ConstantType type = extFun.getArgumentTypes()[i];
			switch (type) {
				case Double:
					arguments[i] = new DoubleAttribute(Double.parseDouble(args[i]));
					break;
				case Integer:
					arguments[i] = new IntegerAttribute(Integer.parseInt(args[i]));
					break;
				case String:
					arguments[i] = new StringAttribute(args[i]);
					break;
				case Long:
					arguments[i] = new LongAttribute(Long.parseLong(args[i]));
					break;
				case Date:
					arguments[i] = new DateAttribute(new DateTime(args[i]));
					break;
				case UniqueIntID:
					arguments[i] = new UniqueIntID(Integer.parseInt(args[i]));
					break;
				case UniqueStringID:
					arguments[i] = new UniqueStringID(args[i]);
					break;
				case DeferredFunctionalUniqueID:
					throw new UnsupportedOperationException(
							"DeferredFunctionalUniqueID has not been resolved by invocation time." +
							" If possible, use UniqueIntID or UniqueStringID.");
				default:
					throw new IllegalArgumentException("Unknown argument type: " + type.getName());
			}
		}

		return extFun.getValue(db, arguments);
	}
}
