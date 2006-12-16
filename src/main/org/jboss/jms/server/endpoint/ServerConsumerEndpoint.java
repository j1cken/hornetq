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
package org.jboss.jms.server.endpoint;


import java.util.ArrayList;
import java.util.List;

import javax.jms.IllegalStateException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;

import org.jboss.jms.destination.JBossDestination;
import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.message.MessageProxy;
import org.jboss.jms.selector.Selector;
import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.QueuedExecutorPool;
import org.jboss.jms.server.remoting.JMSDispatcher;
import org.jboss.jms.server.remoting.MessagingMarshallable;
import org.jboss.jms.util.ExceptionUtil;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.Channel;
import org.jboss.messaging.core.Delivery;
import org.jboss.messaging.core.DeliveryObserver;
import org.jboss.messaging.core.MessageReference;
import org.jboss.messaging.core.Receiver;
import org.jboss.messaging.core.Routable;
import org.jboss.messaging.core.SimpleDelivery;
import org.jboss.messaging.core.plugin.contract.PostOffice;
import org.jboss.messaging.core.plugin.postoffice.Binding;
import org.jboss.messaging.core.tx.Transaction;
import org.jboss.messaging.util.Future;
import org.jboss.remoting.callback.Callback;

import EDU.oswego.cs.dl.util.concurrent.Executor;
import EDU.oswego.cs.dl.util.concurrent.QueuedExecutor;

/**
 * Concrete implementation of ConsumerEndpoint. Lives on the boundary between Messaging Core and the
 * JMS Facade.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class ServerConsumerEndpoint implements Receiver, ConsumerEndpoint
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(ServerConsumerEndpoint.class);

   // Static --------------------------------------------------------  

   private static final int MESSAGES_IN_TRANSIT_WAIT_COUNT = 100;

   // Attributes ----------------------------------------------------

   private boolean trace = log.isTraceEnabled();

   private int id;

   private Channel messageQueue;
   
   private String queueName;

   private ServerSessionEndpoint sessionEndpoint;

   private boolean noLocal;

   private Selector messageSelector;

   private JBossDestination destination;

   private List toDeliver;

   // Must be volatile
   private volatile boolean clientConsumerFull;

   // Must be volatile
   private volatile boolean bufferFull;

   // Must be volatile
   private volatile boolean started;

   // No need to be volatile - is protected by lock
   private boolean closed;

   private Executor executor;

   private int prefetchSize;

   private Object lock;

   private Object messagesInTransitLock;
   
   private int messagesInTransitCount; // access only from a region guarded by messagesInTransitLock
   
   // Constructors --------------------------------------------------

   ServerConsumerEndpoint(int id, Channel messageQueue, String queueName,
                         ServerSessionEndpoint sessionEndpoint,
                         String selector, boolean noLocal, JBossDestination dest,
                         int prefetchSize)
      throws InvalidSelectorException
   {
      if (trace) { log.trace("constructing consumer endpoint " + id); }

      this.id = id;
      this.messageQueue = messageQueue;
      this.queueName = queueName;
      this.sessionEndpoint = sessionEndpoint;
      this.prefetchSize = prefetchSize;
      
      // We always created with clientConsumerFull = true. This prevents the SCD sending messages to
      // the client before the client has fully finished creating the MessageCallbackHandler.
      this.clientConsumerFull = true;

      // We allocate an executor from the rotating pool for each consumer based on it's id
      // This gives better latency than each consumer for the destination using the same
      // executor
      QueuedExecutorPool pool =
         sessionEndpoint.getConnectionEndpoint().getServerPeer().getQueuedExecutorPool();

      this.executor = (QueuedExecutor)pool.get();
             
      // Note that using a PooledExecutor with a linked queue is not sufficient to ensure that
      // deliveries for the same consumer happen serially, since even if they are queued serially
      // the actual deliveries can happen in parallel, resulting in a later one "overtaking" an
      // earlier non-deterministicly depending on thread scheduling.
      // Consequently we use a QueuedExecutor to ensure the deliveries happen sequentially. We do
      // not want each ServerConsumerEndpoint instance to have its own instance - since we would
      // end up using too many threads, neither do we want to share the same instance amongst all
      // consumers - we do not want to serialize delivery to all consumers. So we maintain a bag of
      // QueuedExecutors and give them out to consumers as required. Different consumers can end up
      // using the same queuedexecutor concurrently if there are a lot of active consumers.

      this.noLocal = noLocal;
      
      this.destination = dest;
      
      this.toDeliver = new ArrayList();
      
      this.lock = new Object();

      if (selector != null)
      {
         if (trace) log.trace("creating selector:" + selector);
         this.messageSelector = new Selector(selector);
         if (trace) log.trace("created selector");
      }
       
      this.started = this.sessionEndpoint.getConnectionEndpoint().isStarted();

      // adding the consumer to the queue
      this.messageQueue.add(this);
      
      // prompt delivery
      promptDelivery();

      messagesInTransitLock = new Object();
      messagesInTransitCount = 0;
      
      log.debug(this + " constructed");
   }

   // Receiver implementation ---------------------------------------

   /*
    * The queue ensures that handle is never called concurrently by more than one thread.
    */
   public Delivery handle(DeliveryObserver observer, MessageReference ref, Transaction tx)
   {
      if (trace) { log.trace(this + " receives " + ref + " for delivery"); }
      
      // This is ok to have outside lock - is volatile
      if (bufferFull)
      {
         // We buffer a maximum of PREFETCH_LIMIT messages at once
         
         if (trace) { log.trace(this + " has reached prefetch size will not accept any more references"); }
         
         return null;
      }
       
      // Need to synchronized around the whole block to prevent setting started = false
      // but handle is already running and a message is deposited during the stop procedure.
      synchronized (lock)
      {  
         // If the consumer is stopped then we don't accept the message, it should go back into the
         // queue for delivery later.
         if (!started)
         {
            if (trace) { log.trace(this + " NOT started yet!"); }

            return null;
         }

         if (trace) { log.trace(this + " has the main lock, preparing the message for delivery"); }

         JBossMessage message = (JBossMessage)ref.getMessage();
         
         boolean selectorRejected = !this.accept(message);
   
         SimpleDelivery delivery = new SimpleDelivery(observer, ref, false, !selectorRejected);
         
         if (selectorRejected)
         {
            return delivery;
         }
                 
         if (delivery.isDone())
         {
            return delivery;
         }
         
         long deliveryId = sessionEndpoint.addDelivery(delivery);

         // We don't send the message as-is, instead we create a MessageProxy instance. This allows
         // local fields such as deliveryCount to be handled by the proxy but global data to be
         // fielded by the same underlying Message instance. This allows us to avoid expensive
         // copying of messages
   
         MessageProxy mp = JBossMessage.createThinDelegate(deliveryId, message, ref.getDeliveryCount());
    
         // Add the proxy to the list to deliver
                           
         toDeliver.add(mp);     
          
         bufferFull = toDeliver.size() >= prefetchSize;
             
         if (!clientConsumerFull)
         {            
            try
            {
               Deliverer deliverer = new Deliverer();
               if (trace) { log.trace(this + " scheduling a new " + deliverer); }
               this.executor.execute(deliverer);
            }
            catch (InterruptedException e)
            {
               log.warn("Thread interrupted", e);
            }
         }
                             
         return delivery;              
      }
   }      
   

   // Filter implementation -----------------------------------------

   public boolean accept(Routable r)
   {
      boolean accept = true;
      if (this.destination.isQueue())
      {
         // For subscriptions message selection is handled in the Subscription itself
         // we do not want to do the check twice
         if (messageSelector != null)
         {
            accept = messageSelector.accept(r);
   
            if (trace) { log.trace("message selector " + (accept ? "accepts " :  "DOES NOT accept ") + "the message"); }
         }
      }

      if (accept)
      {
         if (noLocal)
         {
            int conId = ((JBossMessage)r).getConnectionID();
            
            if (trace) { log.trace("message connection id: " + conId + " current connection connection id: " + sessionEndpoint.getConnectionEndpoint().getConnectionID()); }   
                 
            accept = conId != sessionEndpoint.getConnectionEndpoint().getConnectionID();
                
            if (trace) { log.trace("accepting? " + accept); }            
         }
      }
      return accept;
   }


   // Closeable implementation --------------------------------------

   public void closing() throws JMSException
   {
      try
      {
         if (trace) { log.trace(this + " closing"); }
         
         stop(); 
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " closing");
      }     
   }
   
   public void close() throws JMSException
   {      
      try
      {
         localClose();
         
         sessionEndpoint.removeConsumer(id);
      }   
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " close");
      }
   }
           
   // ConsumerEndpoint implementation -------------------------------
   
   /*
    * This is called by the client consumer to tell the server to wake up and start sending more
    * messages if available
    */
   public void more() throws JMSException
   {           
      try
      {
         // Set clientConsumerFull to false.
         //
         // NOTE! This must be done using a Runnable on the delivery executor - this is to prevent
         // the following race condition:
         //  1) Messages are delivered to the client, causing it to be full.
         //  2) The messages are consumed very quickly on the client causing more() to be called.
         //  3) more() hits the server BEFORE the deliverer thread has returned from delivering to
         //     the client causing clientConsumerFull to be set to false and adding a deliverer to
         //     the queue.
         //  4) The deliverer thread returns and sets clientConsumerFull to true.
         //  5) The next deliverer runs but doesn't do anything since clientConsumerFull = true even
         //     though the client needs messages.

         executor.execute(new Runnable()
         {
            public void run()
            {
               if (trace) { log.trace(ServerConsumerEndpoint.this + " is notified that client wants more() messages"); }
               clientConsumerFull = false;
            }
         });
                           
         // Run a deliverer to deliver any existing ones
         executor.execute(new Deliverer());
         
         // TODO Why do we need to wait for it to execute? Why not just return immediately?
         
         // Now wait for it to execute
         Future result = new Future();
         this.executor.execute(new Waiter(result));
         result.getResult();
                  
         // Now we know the deliverer has delivered any outstanding messages to the client buffer
         
         messageQueue.deliver(false);
      }
      catch (InterruptedException e)
      {
         log.warn("Thread interrupted", e);
      }       
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " more");
      }
   }

   public void confirmDelivery(int count)
   {
      synchronized(messagesInTransitLock)
      {
         messagesInTransitCount -= count;

         if (trace) { log.trace("confirming delivery of " + count + " message(s), messages in transit " + messagesInTransitCount); }

         if (messagesInTransitCount < 0)
         {
            log.error(this + " has an invalid messages in transit count (" +
                      messagesInTransitCount + ")");
         }

         messagesInTransitLock.notifyAll();
      }
   }
   
   public boolean isClosed() throws JMSException
   {
      throw new IllegalStateException("isClosed should never be handled on the server side");
   }

   // Public --------------------------------------------------------
   
   public String toString()
   {
      return "ConsumerEndpoint[" + id + "]";
   }
   
   public JBossDestination getDestination()
   {
      return destination;
   }
   
   public ServerSessionEndpoint getSessionEndpoint()
   {
      return sessionEndpoint;
   }
   
   // Package protected ---------------------------------------------
   
   void localClose() throws Throwable
   {      
      synchronized (lock)
      { 
         if (trace) { log.trace(this + " grabbed the main lock in close() " + this); }

         messageQueue.remove(this); 
         
         JMSDispatcher.instance.unregisterTarget(new Integer(id));
         
         // If this is a consumer of a non durable subscription then we want to unbind the
         // subscription and delete all its data.

         if (destination.isTopic())
         {
            PostOffice postOffice = 
               sessionEndpoint.getConnectionEndpoint().getServerPeer().getPostOfficeInstance();
            
            Binding binding = postOffice.getBindingForQueueName(queueName);

            //Note binding can be null since there can many competing subscribers for the subscription  - 
            //in which case the first will have removed the subscription and subsequently
            //ones won't find it
            
            if (binding != null && !binding.getQueue().isRecoverable())
            {
               postOffice.unbindQueue(queueName);
            }
         }
                     
         closed = true;
      }
   }        
           
   void start()
   {             
      synchronized (lock)
      {
         // Can't start or stop it if it is closed.
         if (closed)
         {
            return;
         }
         
         if (started)
         {
            return;
         }
         
         started = true;
      }
      
      // Prompt delivery
      promptDelivery();
   }
   
   void stop() throws Throwable
   {     
      // We need to:
      // - Stop accepting any new messages in the SCE.
      // - Flush any messages from the SCE to the buffer.
      // If the client consumer is now full, then we need to cancel the ones in the toDeliver list.

      // We need to lock since otherwise we could set started to false but the handle method was
      // already executing and messages might get deposited after.
      synchronized (lock)
      {
         if (!started)
         {
            return;
         }
         
         started = false;
      }
      
      // Now we know no more messages will be accepted in the SCE.
            
      try
      {
         // Flush any messages waiting to be sent to the client.
         this.executor.execute(new Deliverer());
         
         if (trace) { log.trace(this + " flushed all remaining messages (if any) to the client"); }

         // Now we know any deliverer has delivered any outstanding messages to the client buffer.
      }
      catch (InterruptedException e)
      {
         log.warn("Thread interrupted", e);
      }

      // Make sure there are no messages in transit between server and client

      synchronized(messagesInTransitLock)
      {
         int loopCount = 0;
         while(messagesInTransitCount > 0 && loopCount < MESSAGES_IN_TRANSIT_WAIT_COUNT)
         {
            log.debug(this + " waiting for " + messagesInTransitCount + " message(s) in transit " +
                      "to reach the client, " + (loopCount + 1) + " lock grab attempts.");
            messagesInTransitLock.wait(500);
            loopCount ++;
         }

         if (loopCount >= MESSAGES_IN_TRANSIT_WAIT_COUNT)
         {
            throw new IllegalStateException("Maximum number of lock grab attempts exceeded, " +
                                            "giving up to wait for messages in transit");
         }

         if (trace) { log.trace(this + " has no messages in transit"); }
      }

      // Now we know that there are no in flight messages on the way to the client consumer, but
      // there may be messages still in the toDeliver list since the client consumer might be full,
      // so we need to cancel these.
            
      if (!toDeliver.isEmpty())
      { 
         synchronized (lock)
         {
            //Cancel in reverse order
            for (int i = toDeliver.size() - 1; i >= 0; i--)
            {
               MessageProxy proxy = (MessageProxy)toDeliver.get(i);

               sessionEndpoint.cancelDelivery(proxy.getDeliveryId());
            }
         }
                 
         toDeliver.clear();
         
         bufferFull = false;
      }      
      
      //Need to prompt delivery
      promptDelivery();
   }
   
   
   
   // Protected -----------------------------------------------------         
      
   // Private -------------------------------------------------------
   
   private void promptDelivery()
   {
      messageQueue.deliver(false);
   }
   
   // Inner classes -------------------------------------------------   
   
   /*
    * Delivers messages to the client 
    * TODO - We can make this a bit more intelligent by letting it measure the rate
    * the client is consuming messages and send messages at that rate.
    * This would mean the client consumer wouldn't be full so often and more wouldn't have to be called
    * This should give higher throughput.
    */
   private class Deliverer implements Runnable
   {
      public void run()
      {
         // Is there anything to deliver? This is ok outside lock - is volatile.
         if (clientConsumerFull)
         {
            if (trace) { log.trace(this + " client consumer full, do nothing"); }
            return;
         }
         
         List list = null;
             
         synchronized (lock)
         {
            if (trace) { log.trace(this + " has the main lock, attempting delivery"); }

            if (!toDeliver.isEmpty())
            {
               list = new ArrayList(toDeliver);
               toDeliver.clear();
               bufferFull = false;
            }
         }
                                                           
         if (list == null)
         {
            if (trace) { log.trace(this + " has a null list, returning"); }
            return;
         }

         ServerConnectionEndpoint connection =
            ServerConsumerEndpoint.this.sessionEndpoint.getConnectionEndpoint();
         
         ClientDelivery del = new ClientDelivery(list, id);
         MessagingMarshallable mm = new MessagingMarshallable(connection.getUsingVersion(), del);
         Callback callback = new Callback(mm);

         try
         {
            if (trace) { log.trace(ServerConsumerEndpoint.this + " handing " + list.size() + " message(s) over to the remoting layer"); }

            synchronized(messagesInTransitLock)
            {
               connection.getCallbackHandler().handleCallback(callback);
               messagesInTransitCount += list.size();
            }

            if (trace) { log.trace(ServerConsumerEndpoint.this + " handed messages over to the remoting layer"); }

            // We are NOT using Remoting's facility of acknowledging callbacks. A callback is sent
            // asynchronously, and there is no confirmation that the callback reached the client or
            // not.

            // TODO Previously, synchronous server-to-client invocations were used by the client
            // to report back whether is full or not. This cannot be achieved with asynchronous
            // callbacks, so the client must explicitely sent this information to the server,
            // with an invocation on its own.
         }
         catch(Throwable t)
         {
            log.warn("Failed to deliver the message to the client. See the server log for more details.");
            log.debug(ServerConsumerEndpoint.this + " failed to deliver the message to the client.", t);

            ConnectionManager mgr = connection.getServerPeer().getConnectionManager();

            mgr.handleClientFailure(connection.getRemotingClientSessionId());
         }
      }

      public String toString()
      {
         return "Deliverer[" + Integer.toHexString(hashCode()) + "]";
      }
   }
   
   /*
    * The purpose of this class is to put it on the QueuedExecutor and wait for it to run
    * We can then ensure that all the Runnables in front of it on the queue have also executed
    * We cannot just call shutdownAfterProcessingCurrentlyQueuedTasks() since the
    * QueueExecutor might be share by other consumers and we don't want to wait for their
    * tasks to complete
    */
   private static class Waiter implements Runnable
   {
      Future result;
      
      Waiter(Future result)
      {
         this.result = result;
      }
      
      public void run()
      {
         result.setResult(null);
      }
   }   
}
