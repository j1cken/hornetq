/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.test.messaging.core.plugin.postoffice.cluster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.messaging.core.Delivery;
import org.jboss.messaging.core.DeliveryObserver;
import org.jboss.messaging.core.Filter;
import org.jboss.messaging.core.Receiver;
import org.jboss.messaging.core.SimpleDelivery;
import org.jboss.messaging.core.message.Message;
import org.jboss.messaging.core.message.MessageReference;
import org.jboss.messaging.core.plugin.postoffice.cluster.ClusterRouter;
import org.jboss.messaging.core.plugin.postoffice.cluster.ClusteredQueue;
import org.jboss.messaging.core.plugin.postoffice.cluster.QueueStats;
import org.jboss.messaging.core.plugin.postoffice.cluster.RoundRobinRouter;
import org.jboss.messaging.core.tx.Transaction;
import org.jboss.test.messaging.core.SimpleReceiver;
import org.jboss.test.messaging.core.plugin.base.PostOfficeTestBase;
import org.jboss.test.messaging.util.CoreMessageFactory;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 2386 $</tt>
 * 
 * TODO Need to add tests for failed over queues to this
 *
 * $Id: DefaultRouterTest.java 2386 2007-02-21 18:07:44Z timfox $
 *
 */
public class RoundRobinRouterTest extends PostOfficeTestBase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   // Constructors --------------------------------------------------

   public RoundRobinRouterTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void setUp() throws Exception
   {
      super.setUp();
   }

   public void tearDown() throws Exception
   {      
      super.tearDown();
   }
   
   public void testSize() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();
      
      ClusteredQueue queue1 = new SimpleQueue(true);
      dr.add(queue1);
      
      assertEquals(1, dr.getNumberOfReceivers());
      assertEquals(1, dr.getQueues().size());
      
      ClusteredQueue queue2 = new SimpleQueue(false);
      dr.add(queue2);
      
      assertEquals(2, dr.getNumberOfReceivers());
      assertEquals(2, dr.getQueues().size());
      
      ClusteredQueue queue3 = new SimpleQueue(false);
      dr.add(queue3);
      
      assertEquals(3, dr.getNumberOfReceivers());
      assertEquals(3, dr.getQueues().size());
      
      dr.remove(queue3);
      
      assertEquals(2, dr.getNumberOfReceivers());
      assertEquals(2, dr.getQueues().size());
      
      dr.remove(queue2);
      
      assertEquals(1, dr.getNumberOfReceivers());
      assertEquals(1, dr.getQueues().size());
      
      dr.remove(queue1);
      
      assertEquals(0, dr.getNumberOfReceivers());
      assertTrue(dr.getQueues().isEmpty());
      
   }
   
   // The router only has a local queue
   public void testRouterOnlyLocal() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();
                    
      ClusteredQueue queue = new SimpleQueue(true);
      
      SimpleReceiver receiver1 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      queue.add(receiver1);
               
      dr.add(queue);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver1);
   }
   
   //The router has only one non local queues
   public void testRouterOnlyOneNonLocal() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();
                    
      ClusteredQueue queue = new SimpleQueue(false);
      
      SimpleReceiver receiver1 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      queue.add(receiver1);
      
      dr.add(queue);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver1);              
   }
   
   //The router has multiple non local queues and no local queue
   public void testRouterMultipleNonLocal() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();
                   
      ClusteredQueue remote1 = new SimpleQueue(false);
     
      SimpleReceiver receiver1 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote1.add(receiver1);
      
      dr.add(remote1);
      
      
      ClusteredQueue remote2 = new SimpleQueue(false);
      
      SimpleReceiver receiver2 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote2.add(receiver2);
      
      dr.add(remote2);
      
      
      ClusteredQueue remote3 = new SimpleQueue(false);
      
      SimpleReceiver receiver3 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote3.add(receiver3);
      
      dr.add(remote3);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver2);
      
      sendAndCheck(dr, receiver3);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver2);
      
      sendAndCheck(dr, receiver3);
   }
   
   
   // The router has one local with consumer and one non local queue with consumer
   public void testRouterOneLocalOneNonLocal() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();
                             
      ClusteredQueue remote1 = new SimpleQueue(false);
     
      SimpleReceiver receiver1 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote1.add(receiver1);
      
      dr.add(remote1);
      
      ClusteredQueue queue = new SimpleQueue(true);
      
      SimpleReceiver receiver2 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      queue.add(receiver2);
      
      dr.add(queue);

      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver2);
      
      sendAndCheck(dr, receiver1);  
      
      sendAndCheck(dr, receiver2); 
      
      sendAndCheck(dr, receiver1); 
   }
   
   // The router has multiple non local queues with consumers and one local queue
   public void testRouterMultipleNonLocalOneLocal() throws Exception
   {
      RoundRobinRouter dr = new RoundRobinRouter();            
                  
      ClusteredQueue remote1 = new SimpleQueue(false);
      
      SimpleReceiver receiver1 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote1.add(receiver1);
      
      dr.add(remote1);
      
      
      ClusteredQueue remote2 = new SimpleQueue(false);
      
      SimpleReceiver receiver2 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote2.add(receiver2);
      
      dr.add(remote2);
      
      
      ClusteredQueue remote3 = new SimpleQueue(false);
      
      SimpleReceiver receiver3 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      remote3.add(receiver3);
      
      dr.add(remote3);
      
      
      ClusteredQueue queue = new SimpleQueue(true);
            
      SimpleReceiver receiver4 = new SimpleReceiver("blah", SimpleReceiver.ACCEPTING);
      
      queue.add(receiver4);
      
      dr.add(queue);
      
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver2);
      
      sendAndCheck(dr, receiver3);
      
      sendAndCheck(dr, receiver4);
      
      sendAndCheck(dr, receiver1);
      
      sendAndCheck(dr, receiver2);
      
      sendAndCheck(dr, receiver3);
      
      sendAndCheck(dr, receiver4);
      
      sendAndCheck(dr, receiver1);
   }
   
   private long nextId;
   
   private void sendAndCheck(ClusterRouter router, SimpleReceiver receiver) throws Exception
   {
      Message msg = CoreMessageFactory.createCoreMessage(nextId++, false, null);      
      
      MessageReference ref = ms.reference(msg);         
      
      Delivery del = router.handle(null, ref, null);
      
      assertNotNull(del);
      
      assertTrue(del.isSelectorAccepted());
            
      Thread.sleep(250);
      
      List msgs = receiver.getMessages();
      
      assertNotNull(msgs);
      
      assertEquals(1, msgs.size());
      
      Message msgRec = (Message)msgs.get(0);
      
      assertTrue(msg == msgRec);  
      
      receiver.clear();
   }
   
   // Private -------------------------------------------------------
   
   
   // Inner classes -------------------------------------------------
   
   class SimpleQueue implements ClusteredQueue
   {
      private boolean local;
      
      private Receiver receiver;
      
      private List refs = new ArrayList();
        
      SimpleQueue(boolean local)
      {
         this.local = local;
      }

      public int getNodeId()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public QueueStats getStats()
      {
         // TODO Auto-generated method stub
         return null;
      }

      public boolean isLocal()
      {
         return local;
      }

      public Filter getFilter()
      {
         // TODO Auto-generated method stub
         return null;
      }

      public String getName()
      {
         // TODO Auto-generated method stub
         return null;
      }

      public boolean isClustered()
      {
         // TODO Auto-generated method stub
         return false;
      }

      public boolean acceptReliableMessages()
      {
         // TODO Auto-generated method stub
         return false;
      }

      public void activate()
      {
         // TODO Auto-generated method stub
         
      }

      public List browse()
      {
         List msgs = new ArrayList();
         
         Iterator iter = refs.iterator();
         
         while (iter.hasNext())
         {
            MessageReference ref = (MessageReference)iter.next();
            
            msgs.add(ref);
         }
         
         return msgs;
      }

      public List browse(Filter filter)
      {
         // TODO Auto-generated method stub
         return null;
      }

      public void clear()
      {
         // TODO Auto-generated method stub
         
      }

      public void close()
      {
         // TODO Auto-generated method stub
         
      }

      public void deactivate()
      {
         // TODO Auto-generated method stub
         
      }

      public void deliver()
      {
         // TODO Auto-generated method stub
         
      }

      public List delivering(Filter filter)
      {
         // TODO Auto-generated method stub
         return null;
      }

      public long getChannelID()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public boolean isActive()
      {
         // TODO Auto-generated method stub
         return false;
      }

      public boolean isRecoverable()
      {
         // TODO Auto-generated method stub
         return false;
      }

      public void load() throws Exception
      {
         // TODO Auto-generated method stub
         
      }

      public int getMessageCount()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public void removeAllReferences() throws Throwable
      {
         // TODO Auto-generated method stub
         
      }

      public List undelivered(Filter filter)
      {
         // TODO Auto-generated method stub
         return null;
      }

      public void unload() throws Exception
      {
         // TODO Auto-generated method stub
         
      }

      public Delivery handle(DeliveryObserver observer, MessageReference reference, Transaction tx)
      {
         if (receiver != null)
         {
            // Send to receiver
            return receiver.handle(observer, reference, tx);
         }
         else
         {
            // Store internally
            refs.add(reference);
            
            return new SimpleDelivery(observer, reference);
         }
         
      
      }

      public void acknowledge(Delivery d, Transaction tx) throws Throwable
      {
         // TODO Auto-generated method stub
         
      }

      public void cancel(Delivery d) throws Throwable
      {
         // TODO Auto-generated method stub
         
      }

      public boolean add(Receiver receiver)
      {
         this.receiver = receiver;
         
         return true;
      }

      public boolean contains(Receiver receiver)
      {
         // TODO Auto-generated method stub
         return false;
      }

      public Iterator iterator()
      {
         // TODO Auto-generated method stub
         return null;
      }

      public int getNumberOfReceivers()
      {
         if (receiver != null)
         {
            return 1;
         }
         else
         {
            return 0;
         }
      }

      public boolean remove(Receiver receiver)
      {
         // TODO Auto-generated method stub
         return false;
      }

      public List recoverDeliveries(List messageIds)
      {
         // TODO Auto-generated method stub
         return null;
      }

      public void addDelivery(Delivery del)
      {
         // TODO Auto-generated method stub
         
      }

      public int getDeliveringCount()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public int getMaxSize()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public int getMessagesAdded()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      public void setMaxSize(int newSize)
      {
         // TODO Auto-generated method stub
         
      }

      public int getScheduledCount()
      {
         // TODO Auto-generated method stub
         return 0;
      }

      
   }
   

}



