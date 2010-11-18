/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration.cluster.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.hornetq.api.core.Pair;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.*;
import org.hornetq.core.config.BridgeConfiguration;
import org.hornetq.core.config.CoreQueueConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.invm.TransportConstants;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.tests.util.UnitTestCase;

/**
 * A JMSBridgeTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 14 Jan 2009 14:05:01
 *
 *
 */
public class BridgeTest extends ServiceTestBase
{
   private static final Logger log = Logger.getLogger(BridgeTest.class);

   protected boolean isNetty()
   {
      return false;
   }

   private String getConnector()
   {
      if (isNetty())
      {
         return NettyConnectorFactory.class.getName();
      }
      else
      {
         return "org.hornetq.core.remoting.impl.invm.InVMConnectorFactory";
      }
   }

   public void testSimpleBridge() throws Exception
   {
      internaltestSimpleBridge(false, false);
   }

   public void testSimpleBridgeFiles() throws Exception
   {
      internaltestSimpleBridge(false, true);
   }

   public void testSimpleBridgeLargeMessageNullPersistence() throws Exception
   {
      internaltestSimpleBridge(true, false);
   }

   public void testSimpleBridgeLargeMessageFiles() throws Exception
   {
      internaltestSimpleBridge(true, true);
   }

   public void internaltestSimpleBridge(final boolean largeMessage, final boolean useFiles) throws Exception
   {
      HornetQServer server0 = null;
      HornetQServer server1 = null;

      try
      {
         Map<String, Object> server0Params = new HashMap<String, Object>();
         server0 = createClusteredServerWithParams(isNetty(), 0, useFiles, server0Params);

         Map<String, Object> server1Params = new HashMap<String, Object>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, useFiles, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         Pair<String, String> connectorPair = new Pair<String, String>(server1tc.getName(), null);

         final int messageSize = 1024;

         final int numMessages = 10;

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("bridge1",
                                                                           queueName0,
                                                                           forwardAddress,
                                                                           null,
                                                                           null,
                                                                           1000,
                                                                           1d,
                                                                           -1,
                                                                           true,
                                                                           false,
                                                                           // Choose confirmation size to make sure acks
                                                                           // are sent
                                                                           numMessages * messageSize / 2,
                                                                           HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                           connectorPair,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_USER,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_PASSWORD);

         List<BridgeConfiguration> bridgeConfigs = new ArrayList<BridgeConfiguration>();
         bridgeConfigs.add(bridgeConfiguration);
         server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

         CoreQueueConfiguration queueConfig0 = new CoreQueueConfiguration(testAddress, queueName0, null, true);
         List<CoreQueueConfiguration> queueConfigs0 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs0.add(queueConfig0);
         server0.getConfiguration().setQueueConfigurations(queueConfigs0);

         CoreQueueConfiguration queueConfig1 = new CoreQueueConfiguration(forwardAddress, queueName1, null, true);
         List<CoreQueueConfiguration> queueConfigs1 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs1.add(queueConfig1);
         server1.getConfiguration().setQueueConfigurations(queueConfigs1);

         server1.start();
         server0.start();

         ClientSessionFactory sf0 = HornetQClient.createClientSessionFactory(server0tc);

         ClientSessionFactory sf1 = HornetQClient.createClientSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(new SimpleString(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final byte[] bytes = new byte[messageSize];

         final SimpleString propKey = new SimpleString("testkey");

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = session0.createMessage(false);

            if (largeMessage)
            {
               message.setBodyInputStream(UnitTestCase.createFakeLargeStream(1024 * 1024));
            }

            message.putIntProperty(propKey, i);

            message.getBodyBuffer().writeBytes(bytes);

            producer0.send(message);
         }

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = consumer1.receive(200);

            Assert.assertNotNull(message);

            Assert.assertEquals(i, message.getObjectProperty(propKey));

            if (largeMessage)
            {
               readMessages(message);
            }

            message.acknowledge();
         }

         Assert.assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      }
      finally
      {
         try
         {
            server0.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server1.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   /**
    * @param server1Params
    */
   private void addTargetParameters(final Map<String, Object> server1Params)
   {
      if (isNetty())
      {
         server1Params.put("port", org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + 1);
      }
      else
      {
         server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      }
   }

   /**
    * @param message
    */
   private void readMessages(final ClientMessage message)
   {
      byte byteRead[] = new byte[1024];

      for (int j = 0; j < 1024; j++)
      {
         message.getBodyBuffer().readBytes(byteRead);
      }
   }

   public void testWithFilter() throws Exception
   {
      internalTestWithFilter(false, false);
   }

   public void testWithFilterFiles() throws Exception
   {
      internalTestWithFilter(false, true);
   }

   public void testWithFilterLargeMessages() throws Exception
   {
      internalTestWithFilter(true, false);
   }

   public void testWithFilterLargeMessagesFiles() throws Exception
   {
      internalTestWithFilter(true, true);
   }

   public void internalTestWithFilter(final boolean largeMessage, final boolean useFiles) throws Exception
   {
      HornetQServer server0 = null;
      HornetQServer server1 = null;

      try
      {

         Map<String, Object> server0Params = new HashMap<String, Object>();
         server0 = createClusteredServerWithParams(isNetty(), 0, useFiles, server0Params);

         Map<String, Object> server1Params = new HashMap<String, Object>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, useFiles, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         Pair<String, String> connectorPair = new Pair<String, String>(server1tc.getName(), null);

         final String filterString = "animal='goat'";

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("bridge1",
                                                                           queueName0,
                                                                           forwardAddress,
                                                                           filterString,
                                                                           null,
                                                                           1000,
                                                                           1d,
                                                                           -1,
                                                                           true,
                                                                           false,
                                                                           1024,
                                                                           HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                           connectorPair,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_USER,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_PASSWORD);

         List<BridgeConfiguration> bridgeConfigs = new ArrayList<BridgeConfiguration>();
         bridgeConfigs.add(bridgeConfiguration);
         server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

         CoreQueueConfiguration queueConfig0 = new CoreQueueConfiguration(testAddress, queueName0, null, true);
         List<CoreQueueConfiguration> queueConfigs0 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs0.add(queueConfig0);
         server0.getConfiguration().setQueueConfigurations(queueConfigs0);

         CoreQueueConfiguration queueConfig1 = new CoreQueueConfiguration(forwardAddress, queueName1, null, true);
         List<CoreQueueConfiguration> queueConfigs1 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs1.add(queueConfig1);
         server1.getConfiguration().setQueueConfigurations(queueConfigs1);

         server1.start();
         server0.start();

         ClientSessionFactory sf0 = HornetQClient.createClientSessionFactory(server0tc);

         ClientSessionFactory sf1 = HornetQClient.createClientSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(new SimpleString(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final int numMessages = 10;

         final SimpleString propKey = new SimpleString("testkey");

         final SimpleString selectorKey = new SimpleString("animal");

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            message.putStringProperty(selectorKey, new SimpleString("monkey"));

            if (largeMessage)
            {
               message.setBodyInputStream(UnitTestCase.createFakeLargeStream(1024 * 1024));
            }

            producer0.send(message);
         }

         Assert.assertNull(consumer1.receiveImmediate());

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            message.putStringProperty(selectorKey, new SimpleString("goat"));

            if (largeMessage)
            {
               message.setBodyInputStream(UnitTestCase.createFakeLargeStream(1024 * 1024));
            }

            producer0.send(message);
         }

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = consumer1.receive(200);

            Assert.assertNotNull(message);

            Assert.assertEquals(i, message.getObjectProperty(propKey));

            message.acknowledge();

            if (largeMessage)
            {
               readMessages(message);
            }
         }

         Assert.assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();
      }

      finally
      {
         try
         {
            server0.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server1.stop();
         }
         catch (Throwable ignored)
         {
         }

      }

   }

   public void testWithTransformer() throws Exception
   {
      internaltestWithTransformer(false);
   }

   public void testWithTransformerFiles() throws Exception
   {
      internaltestWithTransformer(true);
   }

   public void internaltestWithTransformer(final boolean useFiles) throws Exception
   {
      Map<String, Object> server0Params = new HashMap<String, Object>();
      HornetQServer server0 = createClusteredServerWithParams(isNetty(), 0, false, server0Params);

      Map<String, Object> server1Params = new HashMap<String, Object>();
      addTargetParameters(server1Params);
      HornetQServer server1 = createClusteredServerWithParams(isNetty(), 1, false, server1Params);

      final String testAddress = "testAddress";
      final String queueName0 = "queue0";
      final String forwardAddress = "forwardAddress";
      final String queueName1 = "queue1";

      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
      TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);
      TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
      connectors.put(server1tc.getName(), server1tc);

      server0.getConfiguration().setConnectorConfigurations(connectors);

      Pair<String, String> connectorPair = new Pair<String, String>(server1tc.getName(), null);

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("bridge1",
                                                                        queueName0,
                                                                        forwardAddress,
                                                                        null,
                                                                        SimpleTransformer.class.getName(),
                                                                        1000,
                                                                        1d,
                                                                        -1,
                                                                        true,
                                                                        false,
                                                                        1024,
                                                                        HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                        connectorPair,
                                                                        ConfigurationImpl.DEFAULT_CLUSTER_USER,
                                                                        ConfigurationImpl.DEFAULT_CLUSTER_PASSWORD);

      List<BridgeConfiguration> bridgeConfigs = new ArrayList<BridgeConfiguration>();
      bridgeConfigs.add(bridgeConfiguration);
      server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

      CoreQueueConfiguration queueConfig0 = new CoreQueueConfiguration(testAddress, queueName0, null, true);
      List<CoreQueueConfiguration> queueConfigs0 = new ArrayList<CoreQueueConfiguration>();
      queueConfigs0.add(queueConfig0);
      server0.getConfiguration().setQueueConfigurations(queueConfigs0);

      CoreQueueConfiguration queueConfig1 = new CoreQueueConfiguration(forwardAddress, queueName1, null, true);
      List<CoreQueueConfiguration> queueConfigs1 = new ArrayList<CoreQueueConfiguration>();
      queueConfigs1.add(queueConfig1);
      server1.getConfiguration().setQueueConfigurations(queueConfigs1);

      server1.start();
      server0.start();

      ClientSessionFactory sf0 = HornetQClient.createClientSessionFactory(server0tc);

      ClientSessionFactory sf1 = HornetQClient.createClientSessionFactory(server1tc);

      ClientSession session0 = sf0.createSession(false, true, true);

      ClientSession session1 = sf1.createSession(false, true, true);

      ClientProducer producer0 = session0.createProducer(new SimpleString(testAddress));

      ClientConsumer consumer1 = session1.createConsumer(queueName1);

      session1.start();

      final int numMessages = 10;

      final SimpleString propKey = new SimpleString("wibble");

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session0.createMessage(false);

         message.putStringProperty(propKey, new SimpleString("bing"));

         message.getBodyBuffer().writeString("doo be doo be doo be doo");

         producer0.send(message);
      }

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer1.receive(200);

         Assert.assertNotNull(message);

         SimpleString val = (SimpleString)message.getObjectProperty(propKey);

         Assert.assertEquals(new SimpleString("bong"), val);

         String sval = message.getBodyBuffer().readString();

         Assert.assertEquals("dee be dee be dee be dee", sval);

         message.acknowledge();

      }

      Assert.assertNull(consumer1.receiveImmediate());

      session0.close();

      session1.close();

      sf0.close();

      sf1.close();

      server0.stop();

      server1.stop();
   }

   public void testBridgeWithPaging() throws Exception
   {
      HornetQServer server0 = null;
      HornetQServer server1 = null;

      final int PAGE_MAX = 100 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      try
      {

         Map<String, Object> server0Params = new HashMap<String, Object>();
         server0 = createClusteredServerWithParams(isNetty(), 0, true, PAGE_SIZE, PAGE_MAX, server0Params);

         Map<String, Object> server1Params = new HashMap<String, Object>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, true, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String forwardAddress = "forwardAddress";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         Pair<String, String> connectorPair = new Pair<String, String>(server1tc.getName(), null);

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("bridge1",
                                                                           queueName0,
                                                                           forwardAddress,
                                                                           null,
                                                                           null,
                                                                           1000,
                                                                           1d,
                                                                           -1,
                                                                           true,
                                                                           false,
                                                                           1024,
                                                                           HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                           connectorPair,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_USER,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_PASSWORD);

         List<BridgeConfiguration> bridgeConfigs = new ArrayList<BridgeConfiguration>();
         bridgeConfigs.add(bridgeConfiguration);
         server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

         CoreQueueConfiguration queueConfig0 = new CoreQueueConfiguration(testAddress, queueName0, null, true);
         List<CoreQueueConfiguration> queueConfigs0 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs0.add(queueConfig0);
         server0.getConfiguration().setQueueConfigurations(queueConfigs0);

         CoreQueueConfiguration queueConfig1 = new CoreQueueConfiguration(forwardAddress, queueName1, null, true);
         List<CoreQueueConfiguration> queueConfigs1 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs1.add(queueConfig1);
         server1.getConfiguration().setQueueConfigurations(queueConfigs1);

         server1.start();
         server0.start();

         ClientSessionFactory sf0 = HornetQClient.createClientSessionFactory(server0tc);

         ClientSessionFactory sf1 = HornetQClient.createClientSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(new SimpleString(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final int numMessages = 500;

         final SimpleString propKey = new SimpleString("testkey");

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            producer0.send(message);
         }

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = consumer1.receive(5000);

            Assert.assertNotNull(message);

            Assert.assertEquals(i, message.getObjectProperty(propKey));

            message.acknowledge();
         }

         Assert.assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      }
      finally
      {
         try
         {
            server0.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server1.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }
   
   public void testNullForwardingAddress() throws Exception
   {
      HornetQServer server0 = null;
      HornetQServer server1 = null;

      try
      {
         Map<String, Object> server0Params = new HashMap<String, Object>();
         server0 = createClusteredServerWithParams(isNetty(), 0, false, server0Params);

         Map<String, Object> server1Params = new HashMap<String, Object>();
         addTargetParameters(server1Params);
         server1 = createClusteredServerWithParams(isNetty(), 1, false, server1Params);

         final String testAddress = "testAddress";
         final String queueName0 = "queue0";
         final String queueName1 = "queue1";

         Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
         TransportConfiguration server0tc = new TransportConfiguration(getConnector(), server0Params);

         TransportConfiguration server1tc = new TransportConfiguration(getConnector(), server1Params);
         connectors.put(server1tc.getName(), server1tc);

         server0.getConfiguration().setConnectorConfigurations(connectors);

         Pair<String, String> connectorPair = new Pair<String, String>(server1tc.getName(), null);

         final int messageSize = 1024;

         final int numMessages = 10;

         BridgeConfiguration bridgeConfiguration = new BridgeConfiguration("bridge1",
                                                                           queueName0,
                                                                           null, // pass a null forwarding address to use messages' original address
                                                                           null,
                                                                           null,
                                                                           1000,
                                                                           1d,
                                                                           -1,
                                                                           true,
                                                                           false,
                                                                           // Choose confirmation size to make sure acks
                                                                           // are sent
                                                                           numMessages * messageSize / 2,
                                                                           HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                           connectorPair,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_USER,
                                                                           ConfigurationImpl.DEFAULT_CLUSTER_PASSWORD);

         List<BridgeConfiguration> bridgeConfigs = new ArrayList<BridgeConfiguration>();
         bridgeConfigs.add(bridgeConfiguration);
         server0.getConfiguration().setBridgeConfigurations(bridgeConfigs);

         CoreQueueConfiguration queueConfig0 = new CoreQueueConfiguration(testAddress, queueName0, null, true);
         List<CoreQueueConfiguration> queueConfigs0 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs0.add(queueConfig0);
         server0.getConfiguration().setQueueConfigurations(queueConfigs0);

         // on server #1, we bind queueName1 to same address testAddress 
         CoreQueueConfiguration queueConfig1 = new CoreQueueConfiguration(testAddress, queueName1, null, true);
         List<CoreQueueConfiguration> queueConfigs1 = new ArrayList<CoreQueueConfiguration>();
         queueConfigs1.add(queueConfig1);
         server1.getConfiguration().setQueueConfigurations(queueConfigs1);

         server1.start();
         server0.start();

         ClientSessionFactory sf0 = HornetQClient.createClientSessionFactory(server0tc);

         ClientSessionFactory sf1 = HornetQClient.createClientSessionFactory(server1tc);

         ClientSession session0 = sf0.createSession(false, true, true);

         ClientSession session1 = sf1.createSession(false, true, true);

         ClientProducer producer0 = session0.createProducer(new SimpleString(testAddress));

         ClientConsumer consumer1 = session1.createConsumer(queueName1);

         session1.start();

         final byte[] bytes = new byte[messageSize];

         final SimpleString propKey = new SimpleString("testkey");

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = session0.createMessage(false);

            message.putIntProperty(propKey, i);

            message.getBodyBuffer().writeBytes(bytes);

            producer0.send(message);
         }

         for (int i = 0; i < numMessages; i++)
         {
            ClientMessage message = consumer1.receive(200);

            Assert.assertNotNull(message);

            Assert.assertEquals(i, message.getObjectProperty(propKey));

            message.acknowledge();
         }

         Assert.assertNull(consumer1.receiveImmediate());

         session0.close();

         session1.close();

         sf0.close();

         sf1.close();

      }
      finally
      {
         try
         {
            server0.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server1.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      clearData();
   }

   @Override
   protected void tearDown() throws Exception
   {
      clearData();
      super.setUp();
   }

}
