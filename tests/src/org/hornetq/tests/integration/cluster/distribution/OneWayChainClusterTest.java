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


package org.hornetq.tests.integration.cluster.distribution;

import org.hornetq.core.logging.Logger;

/**
 * A OneWayChainClusterTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 7 Feb 2009 15:23:08
 *
 *
 */
public class OneWayChainClusterTest extends ClusterTestBase
{
   private static final Logger log = Logger.getLogger(OneWayChainClusterTest.class);

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      setupServer(0, isFileStorage(), isNetty());
      setupServer(1, isFileStorage(), isNetty());  
      setupServer(2, isFileStorage(), isNetty());
      setupServer(3, isFileStorage(), isNetty());  
      setupServer(4, isFileStorage(), isNetty());
   }

   @Override
   protected void tearDown() throws Exception
   {
      closeAllConsumers();
      
      closeAllSessionFactories();
      
      stopServers(0, 1, 2, 3, 4);
      
      super.tearDown();
   }
   
   protected boolean isNetty()
   {
      return false;
   }
   
   protected boolean isFileStorage()
   {
      return false;
   }
   
   public void testBasicRoundRobin() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      addConsumer(1, 4, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);            
   }
   
   public void testBasicNonLoadBalanced() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);
      createQueue(0, "queues.testaddress", "queue1", null, false);

      createQueue(4, "queues.testaddress", "queue2", null, false);
      createQueue(4, "queues.testaddress", "queue3", null, false);

      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 0, "queue1", null);

      addConsumer(2, 4, "queue2", null);
      addConsumer(3, 4, "queue3", null);

      waitForBindings(0, "queues.testaddress", 2, 2, true);
      waitForBindings(0, "queues.testaddress", 2, 2, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveAll(10, 0, 1, 2, 3);
      verifyNotReceive(0, 1, 2, 3);       
   }
   
   public void testRoundRobinForwardWhenNoConsumersTrue() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", true, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", true, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", true, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", true, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);
      
      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 4, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);            
   }
   
   public void testRoundRobinForwardWhenNoConsumersFalseNoLocalQueue() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());
    
      createQueue(4, "queues.testaddress", "queue0", null, false);
     
      waitForBindings(0, "queues.testaddress", 1, 0, false);
      
      addConsumer(1, 4, "queue0", null);
     
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveAll(10, 1);
      verifyNotReceive(1);            
   }
   
   public void testRoundRobinForwardWhenNoConsumersFalse() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);

      waitForBindings(0, "queues.testaddress", 1, 0, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);
      
      addConsumer(0, 0, "queue0", null);
      addConsumer(1, 4, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);
 
      //Should still be round robin'd since there's no local consumer
      
      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1);            
   }
   
   public void testRoundRobinForwardWhenNoConsumersFalseLocalConsumer() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);
      
      addConsumer(0, 0, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 0, false);
            
      send(0, "queues.testaddress", 10, false, null);
      
      addConsumer(1, 4, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);
      
      verifyReceiveAll(10, 0);
      verifyNotReceive(0, 1);            
   }
      
   public void testHopsTooLow() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 3, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 3, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 3, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 3, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      addConsumer(1, 4, "queue0", null);

      send(0, "queues.testaddress", 10, false, null);  
      
      verifyReceiveAll(10, 0);
      
      verifyNotReceive(1);
   }
   
   public void testStartStopMiddleOfChain() throws Exception
   {
      setupClusterConnection("cluster0-1", 0, 1, "queues", false, 4, isNetty());
      setupClusterConnection("cluster1-2", 1, 2, "queues", false, 4, isNetty());
      setupClusterConnection("cluster2-3", 2, 3, "queues", false, 4, isNetty());
      setupClusterConnection("cluster3-4", 3, 4, "queues", false, 4, isNetty());
            
      startServers(0, 1, 2, 3, 4);

      setupSessionFactory(0, isNetty());
      setupSessionFactory(4, isNetty());

      createQueue(0, "queues.testaddress", "queue0", null, false);

      createQueue(4, "queues.testaddress", "queue0", null, false);

      addConsumer(0, 0, "queue0", null);

      addConsumer(1, 4, "queue0", null);

      waitForBindings(0, "queues.testaddress", 1, 1, true);
      waitForBindings(0, "queues.testaddress", 1, 1, false);

      send(0, "queues.testaddress", 10, false, null);
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1); 
      
      stopServers(2);
      
      startServers(2);

      Thread.sleep(2000);
        
      send(0, "queues.testaddress", 10, false, null);
       
      verifyReceiveRoundRobin(10, 0, 1);
      verifyNotReceive(0, 1); 
   }
   
}
