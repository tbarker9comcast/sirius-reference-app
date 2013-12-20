/*
 * Copyright 2013 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.xfinity.sirius.refapplication.sirius;

import com.comcast.xfinity.sirius.api.RequestHandler;
import com.comcast.xfinity.sirius.api.SiriusConfiguration;
import com.comcast.xfinity.sirius.api.impl.SiriusFactory;
import com.comcast.xfinity.sirius.api.impl.SiriusImpl;

import java.io.*;
import java.util.ArrayList;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SiriusImplementation {

    private String defaultWriteAheadLog;
    private String clusterConfig;
    private RefRequestHandler requestHandler;
    protected Logger logger = LoggerFactory.getLogger(this.getClass());


    public SiriusImpl startSirius(int port, String customWriteAheadLog, String akkaExternalConfig) throws Exception{
        if(customWriteAheadLog != null){
            if(!customWriteAheadLog.isEmpty()){
                defaultWriteAheadLog = createSiriusLog(customWriteAheadLog,port);
                clusterConfig = createClusterConfig("resources/config/",port);
                requestHandler = createRequestHandler();
                return new SiriusImplementation().initializeSirius(requestHandler, defaultWriteAheadLog,
                                                clusterConfig, port, akkaExternalConfig);
            }
        }
        defaultWriteAheadLog = createSiriusLog("resources/uberStore/",port);
        clusterConfig = createClusterConfig("resources/config/",port);
        requestHandler = createRequestHandler();
        SiriusImpl siriusImp;
        try{
            siriusImp = new SiriusImplementation().initializeSirius(requestHandler, defaultWriteAheadLog, clusterConfig, port, akkaExternalConfig);
            logger.info( "Firing up Sirius on :" + port);
            return siriusImp;
        }catch (Exception e){
            copyAkkaReferenceConfig();
            siriusImp = new SiriusImplementation().initializeSirius(requestHandler, defaultWriteAheadLog, clusterConfig, port, akkaExternalConfig);
            logger.info( "Firing up Sirius on :" + port);
        }
        return siriusImp;
    }
    /**
    * Creates a SiriusImpl and waits until it has completed its bootstrap procedure and is online
    *
    * @param requestHandler An implemented RequestHandler that's required
    * @param siriusLog Location on filesystem where the Sirius WAL will be found
    * @param clusterConfig Location on the filesystem where Sirius cluster config will be found
    * @param siriusPort port for Sirius to use to communicate with other cluster members
    * @return a Sirius instance that is ready to start accepting requests
    */
    public SiriusImpl initializeSirius(RequestHandler requestHandler, String siriusLog, String clusterConfig,
                                  int siriusPort, String akkaExternalConfig) throws Exception{

        String localHost = "localhost";

        //Basic Cluster Configuration
        SiriusConfiguration siriusConfig = new SiriusConfiguration();
        siriusConfig.setProp(SiriusConfiguration.HOST(), localHost);
        siriusConfig.setProp(SiriusConfiguration.PORT(), siriusPort);
        siriusConfig.setProp(SiriusConfiguration.AKKA_SYSTEM_NAME(), "sirius-"+siriusPort);
        siriusConfig.setProp(SiriusConfiguration.CLUSTER_CONFIG(), clusterConfig);
        siriusConfig.setProp(SiriusConfiguration.PAXOS_MEMBERSHIP_CHECK_INTERVAL(), 0.1);
        siriusConfig.setProp(SiriusConfiguration.REPROPOSAL_WINDOW(), 10);
        siriusConfig.setProp(SiriusConfiguration.LOG_REQUEST_CHUNK_SIZE(), 100);
        siriusConfig.setProp(SiriusConfiguration.LOG_LOCATION(), siriusLog);
        siriusConfig.setProp(SiriusConfiguration.AKKA_EXTERN_CONFIG(),akkaExternalConfig);

        SiriusImpl siriusImpl = SiriusFactory.createInstance(requestHandler, siriusConfig);
        awaitBoot(siriusImpl, 60000L);
        return siriusImpl;
    }

    private static RefRequestHandler createRequestHandler(){
        return new RefRequestHandler();
    }

    /**
     * Creates the cluster configuration from the location and port. If a node entry exist then the file is not updated
     * however if the node entry is no in the file then the file is appended.
     * @param location
     * @param port
     * @return absolute config path
     */
    public String createClusterConfig(String location, int port) {
        File membershipFile = new File(location, "cluster.conf");
        File dir =  new File(location);
        dir.mkdirs();
        String clusterEntry = new String("akka://sirius-"+port+"@localhost:"+port+"/user/sirius");

        try {
            if(membershipFile.exists()) {
                FileReader fileReader = new FileReader(membershipFile);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                ArrayList<String> configList = new ArrayList();

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    configList.add(line);
                }
                bufferedReader.close();
                fileReader.close();
                logger.info("Reading cluster config "+ configList);
                FileOutputStream fOS = new FileOutputStream(membershipFile,true);

                ListIterator<String> iterator = configList.listIterator();
                boolean present = false;
                while(iterator.hasNext()) {
                    if(iterator.next().equalsIgnoreCase(clusterEntry)){
                        present = true;
                    }
                }
                if(!present){
                    logger.info("Writing cluster config "+clusterEntry);
                    fOS.write((clusterEntry + "\n").getBytes());
                }

                fOS.flush();
                fOS.close();
                return membershipFile.getAbsolutePath();
            }
            logger.info("Creating new cluster config file "+membershipFile.getAbsolutePath());
            membershipFile.createNewFile();
            FileOutputStream fOS = new FileOutputStream(membershipFile);
            logger.info("Writing cluster config "+clusterEntry);
            fOS.write((clusterEntry + "\n").getBytes());
            fOS.close();
        }catch(IOException e){
            logger.info(e.getMessage());
        }
        return membershipFile.getAbsolutePath();
    }

    /**
     *
     * @param
     */
    private void copyAkkaReferenceConfig(){
        File akkaConfigFile = new File("resources/config/application.conf");

        try{
            GenerateReferenceConfig.createReferenceConfig(akkaConfigFile);
        }catch(Exception e){
            logger.debug(e.getMessage());
        }

    }
    /**
     *
     * @param location
     * @param port
     * @return
     */
    private static String createSiriusLog(String location, int port){
        File uberStoreDir = new File(location);
        if (!uberStoreDir.exists()) {
          uberStoreDir.mkdirs();
        }

        File uberstoreFile = new File(uberStoreDir,"sirius-"+port);
        uberstoreFile.mkdir();

        return uberstoreFile.getAbsolutePath();
    }

    /**
     * Awaits for siriusImpl to come online, or throws an IllegalStateException if it fails
     * to come up within the provided timeout.
     *
     * Static for testing
     *
     * @param siriusImpl
     * @param timeout
     * @throws InterruptedException
     */
     public void awaitBoot(SiriusImpl siriusImpl, long timeout) throws InterruptedException {
        long waitUntil = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < waitUntil) {
            if (siriusImpl.isOnline()) {
                break;
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    logger.warn("Sleep interrupted while waiting for Sirius to boot... " +
                                " putting my head in the sand for now.");
                }
            }
        }

        if (!siriusImpl.isOnline()) {
            throw new IllegalStateException("Sirius failed to boot in " + timeout + "ms");
        }
    }

    public void shutdown(SiriusImpl siriusImpl){
        siriusImpl.shutdown();
    }
}
