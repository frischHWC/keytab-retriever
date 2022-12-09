/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.frisch.keytabretriever;

import org.apache.log4j.Logger;


import com.cloudera.frisch.keytabretriever.config.PropertiesLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Main {

    private final static Logger logger = Logger.getLogger(Main.class);

    public static void main(String [] args) {

        logger.info("Starting Application");
        long start = System.currentTimeMillis();

        logger.debug("Debug is activated");

        logger.info("Project is : " + PropertiesLoader.getProperty("project.name"));

        Connection c = null;
        try {
            Class.forName(PropertiesLoader.getProperty("cloudera-scm.driver"));
            String jdbcUrl = PropertiesLoader.getProperty("cloudera-scm.host") + ":" + PropertiesLoader.getProperty("cloudera-scm.port")
                + "/" + PropertiesLoader.getProperty("cloudera-scm.db");
            c = DriverManager
                .getConnection("jdbc:" + PropertiesLoader.getProperty("cloudera-scm.db.type") + "://" + jdbcUrl,
                    PropertiesLoader.getProperty("cloudera-scm.user"), PropertiesLoader.getProperty("cloudera-scm.password"));
        } catch (Exception e) {
            logger.error("Could not open connection due to: ", e);
        }
        logger.info("Opened database successfully");

        List<KeytabDescription> keytabList = new ArrayList<>();

        try {
            ResultSet rs = c.prepareStatement("SELECT * FROM credentials").executeQuery();
            while(rs.next()) {
                keytabList.add(new KeytabDescription(rs.getString("principal"), rs.getBytes("keytab"),
                    rs.getString("principal").split("@")[0].replace("/", "-")));
            }
            c.close();
        } catch(SQLException e) {
            logger.error("Could not retrieve all keytabs");
        }

        // Write keytabs locally
        try {
            Files.createDirectories(Paths.get("/etc/security/keytabs/"));
        } catch (IOException e) {
            logger.error("Could not create /etc/security/keytabs/ to store keytabs");
        }

        keytabList.forEach(k -> {
            String keytabPath = "/etc/security/keytabs/" + k.keytabName + ".keytab";
            try {
                // Create file
                File keytabFile = new File(keytabPath);
                if(keytabFile.exists()) {
                    keytabFile.delete();
                }
                keytabFile.createNewFile();

                // Set rights on file
                Path path = Paths.get(keytabPath);
                FileSystem fileSystem = path.getFileSystem();
                UserPrincipalLookupService service = fileSystem.getUserPrincipalLookupService();
                UserPrincipal userPrincipal = service.lookupPrincipalByName("root");
                try {
                    userPrincipal = service.lookupPrincipalByName(k.principal.split("/")[0]);
                } catch (UserPrincipalNotFoundException e) {
                    logger.warn("Could not find user: " + k.principal.split("/")[0] + " setting root instead");
                }
                Files.setOwner(path, userPrincipal);
                Set<PosixFilePermission> permissions = new HashSet<>();
                permissions.add(PosixFilePermission.OWNER_READ);
                Files.setPosixFilePermissions(path, permissions);

                // Write to keytab
                FileOutputStream keytabWriter = new FileOutputStream(keytabPath);
                keytabWriter.write(k.keytab);
                keytabWriter.close();
            } catch (IOException e) {
                logger.error("Could not write keytab to file: " + keytabPath + " due to error: ", e);
            }
        });

        // Set Rigths



        logger.info("Application Finished");
        logger.info("Application took : " + (System.currentTimeMillis()-start) + " ms to run");

    }

}
