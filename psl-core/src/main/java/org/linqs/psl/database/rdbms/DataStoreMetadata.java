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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.Partition;
import org.linqs.psl.util.ListUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataStoreMetadata {
    private static final Logger log = LoggerFactory.getLogger(DataStoreMetadata.class);

    public static final String METADATA_TABLENAME = "pslmetadata";
    public static final String ANONYMOUS_PARTITION_PREFIX = "AnonymousPartition_";
    public static final String PARTITION_NAMESPACE = "partition";
    public static final String NAME_KEY = "name";

    private RDBMSDataStore dataStore;
    private Map<String, Integer> partitions;
    private int nextPartition;

    public DataStoreMetadata(RDBMSDataStore dataStore) {
        this.dataStore = dataStore;
        initialize();
        partitions = fetchPartitions();

        nextPartition = 1;
        for (Integer partition : partitions.values()) {
            if (partition.intValue() >= nextPartition) {
                nextPartition = partition.intValue() + 1;
            }
        }
    }

    private void initialize() {
        if (exists()) {
            return;
        }

        List<String> sql = new ArrayList<String>();
        sql.add("CREATE TABLE IF NOT EXISTS " + METADATA_TABLENAME + " (");
        sql.add("  namespace VARCHAR(255),");
        sql.add("  keytype VARCHAR(255),");
        sql.add("  keyvalue VARCHAR(255),");
        sql.add("  data VARCHAR(255),");
        sql.add("  PRIMARY KEY(namespace, keytype, keyvalue)");
        sql.add(")");

        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
        ) {
            statement.execute();
        } catch (SQLException ex) {
            throw new RuntimeException("Error creating metadata table.", ex);
        }
    }

    private boolean exists() {
        try (
            Connection connection = dataStore.getConnection();
            ResultSet resultSet = connection.getMetaData().getTables(null, null, METADATA_TABLENAME, null);
        ) {
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error finding metadata table.", ex);
        }

        return false;
    }

    private void addRow(String namespace, String type, String keyvalue, String value) {
        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO " + METADATA_TABLENAME + " VALUES(?, ?, ?, ?)");
        ) {
            statement.setString(1, namespace);
            statement.setString(2, type);
            statement.setString(3, keyvalue);
            statement.setString(4, value);
            statement.execute();
        } catch (SQLException ex) {
            throw new RuntimeException("Error adding row to metadata table.", ex);
        }
    }

    private String getValue(String namespace, String type, String keyvalue) {
        List<String> sql = new ArrayList<String>();
        sql.add("SELECT data");
        sql.add("FROM " + METADATA_TABLENAME);
        sql.add("WHERE");
        sql.add("  namespace = ?");
        sql.add("  AND keytype = ?");
        sql.add("  AND keyvalue = ?");

        ResultSet resultSet = null;
        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
        ) {
            statement.setString(1, namespace);
            statement.setString(2, type);
            statement.setString(3, keyvalue);
            statement.execute();

            resultSet = statement.getResultSet();
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error fetching value from metadata table.", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }

        return null;
    }

    private void removeRow(String namespace, String type, String keyvalue) {
        List<String> sql = new ArrayList<String>();
        sql.add("DELETE");
        sql.add("FROM " + METADATA_TABLENAME);
        sql.add("WHERE");
        sql.add("  namespace = ?");
        sql.add("  AND keytype = ?");
        sql.add("  AND keyvalue = ?");

        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
        ) {
            statement.setString(1, namespace);
            statement.setString(2, type);
            statement.setString(3, keyvalue);
            statement.execute();
        } catch (SQLException ex) {
            throw new RuntimeException("Error removing metadata row", ex);
        }
    }

    public Map<String, String> getAllValuesByType(String namespace, String type) {
        List<String> sql = new ArrayList<String>();
        sql.add("SELECT keyvalue, data");
        sql.add("FROM " + METADATA_TABLENAME);
        sql.add("WHERE");
        sql.add("  namespace = ?");
        sql.add("  AND keytype = ?");

        ResultSet resultSet = null;
        Map<String, String> values = new HashMap<String,String>();

        try (
            Connection connection = dataStore.getConnection();
            PreparedStatement statement = connection.prepareStatement(ListUtils.join(System.lineSeparator(), sql));
        ) {
            statement.setString(1, namespace);
            statement.setString(2, type);
            statement.execute();

            resultSet = statement.getResultSet();
            while (resultSet.next()) {
                values.put(resultSet.getString(1), resultSet.getString(2));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error retrieving metadata values", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }

        return values;
    }

    private Map<String, Integer> fetchPartitions() {
        Map<String, Integer> names = new HashMap<String, Integer>();

        Map<String, String> values = getAllValuesByType(PARTITION_NAMESPACE, NAME_KEY);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            names.put(entry.getKey(), Integer.parseInt(entry.getValue()));
        }

        return names;
    }

    private Partition addPartition(int id, String name) {
        addRow(PARTITION_NAMESPACE, NAME_KEY, name, "" + id);
        return new Partition(id, name);
    }

    public Partition getPartition(String name) {
        String idString = getValue(PARTITION_NAMESPACE, NAME_KEY, name);

        if (idString != null) {
            return new Partition(Integer.parseInt(idString), name);
        } else {
            return addPartition(nextPartition++, name);
        }
    }

    public Partition getNewPartition() {
        return getPartition(ANONYMOUS_PARTITION_PREFIX + nextPartition);
    }

    public Set<Partition> getAllPartitions() {
        Set<Partition> partitions = new HashSet<Partition>();

        for (Map.Entry<String, String> entry : getAllValuesByType(PARTITION_NAMESPACE, NAME_KEY).entrySet()) {
            partitions.add(new Partition(Integer.parseInt(entry.getValue()), entry.getKey()));
        }

        return partitions;
    }

    public void removePartition(Partition partition) {
        removeRow(PARTITION_NAMESPACE, NAME_KEY, partition.getName());
        partitions.remove(partition.getName());
    }
}
