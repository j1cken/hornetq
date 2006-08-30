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
package org.jboss.messaging.core.plugin.exchange;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.jboss.jms.server.QueuedExecutorPool;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.Filter;
import org.jboss.messaging.core.MessageReference;
import org.jboss.messaging.core.plugin.IdManager;
import org.jboss.messaging.core.plugin.contract.MessageStore;
import org.jboss.messaging.core.plugin.contract.PersistenceManager;
import org.jboss.messaging.core.plugin.exchange.request.BindRequest;
import org.jboss.messaging.core.plugin.exchange.request.MessageRequest;
import org.jboss.messaging.core.plugin.exchange.request.UnbindRequest;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.Message;
import org.jgroups.MessageListener;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.util.Util;

/**
 * A ClusteredExchangeSupport
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public abstract class ClusteredExchangeSupport extends ExchangeSupport
{
   private static final Logger log = Logger.getLogger(ClusteredExchangeSupport.class);
      
   //TODO - make configurable
   private static final int GET_STATE_TIMEOUT = 5000;
   
   private static final int CAST_TIMEOUT = 5000;
                    
   protected Channel controlChannel;
   
   protected Channel dataChannel;
   
   private MessageDispatcher controlMessageDispatcher;
   
   private MessageListener controlMessageListener;
   
   private Receiver dataReceiver;
   
   private RequestHandler requestHandler;
   
   private Object setStateLock = new Object();
   
   private boolean stateSet;
   
   private String groupName;
   
   //protected boolean clustered;
   
   public ClusteredExchangeSupport() throws Exception
   {                  
   }
   
   /*
    * This constructor should only be used for testing
    */
   protected ClusteredExchangeSupport(DataSource ds, TransactionManager tm)
   {
      super(ds, tm);
   }
   
   protected void injectAttributes(Channel controlChannel, Channel dataChannel,
                                   String groupName, String exchangeName, String nodeID,
                                   MessageStore ms, IdManager im, QueuedExecutorPool pool) throws Exception
   {
      super.injectAttributes(exchangeName, nodeID, ms, im, pool);
      
      this.controlChannel = controlChannel;
      
      this.dataChannel = dataChannel;
      
      this.groupName = groupName;
      
      //We don't want to receive local messages on any of the channels
      controlChannel.setOpt(Channel.LOCAL, Boolean.FALSE);
      dataChannel.setOpt(Channel.LOCAL, Boolean.FALSE);

      this.controlMessageListener = new ControlMessageListener();
        
      this.requestHandler = new ExchangeRequestHandler();
      
      this.controlMessageDispatcher = new MessageDispatcher(controlChannel, controlMessageListener,
                                                            null, requestHandler, true);      
      this.dataReceiver = new DataMessageListener();
      
      dataChannel.setReceiver(dataReceiver);
   }
   
   // ServiceMBeanSupport overrides ---------------------------------
   
   protected void startService() throws Exception
   {
      controlChannel.connect(groupName);
      log.info("Connected to control channel");
      
      dataChannel.connect(groupName);
      log.info("Connected to data channel");
      
      super.startService();            
   }
   
   protected void stopService() throws Exception
   {
      super.stopService();
      
      controlChannel.close();
      
      dataChannel.close();
   }
   
   // Exchange implementation ---------------------------------------        
   
   public Binding bindQueue(String queueName, String condition, Filter filter, boolean noLocal, boolean durable,
                            MessageStore ms, PersistenceManager pm,
                            int fullSize, int pageSize, int downCacheSize) throws Exception
   {
      Binding binding = super.bindQueue(queueName, condition, filter, noLocal, durable,
                                        ms, pm, fullSize, pageSize, downCacheSize);
      
      sendBindMessage(queueName, condition, filter == null ? null : filter.getFilterString(), noLocal,
                      binding.getChannelId(), durable);

      return binding;
   }
   
   public Binding unbindQueue(String queueName) throws Throwable
   {
      Binding binding = super.unbindQueue(queueName);
      
      sendUnbindMessage(binding.getQueueName());
    
      return binding;
   }
   
   //ExchangeSupport overrides -------------------------------------------------
   
   protected void loadBindings() throws Exception
   {
      // TODO I need to know whether this call times out - how do I know this??
      boolean isState = controlChannel.getState(null, GET_STATE_TIMEOUT);
      
      log.info(this + " load bindings, isState=" + isState);
                            
      if (!isState)
      {       
         //Must be first member in group or non clustered- we load the state ourself from the database
         log.info("loading bindings from db");
         super.loadBindings();      
         log.info("loaded bindings from db");
      }
      else
      {
         //The state will be set in due course via the MessageListener - we must wait until this happens
         
         synchronized (setStateLock)
         {
            //TODO we should implement a timeout on this
            while (!stateSet)
            {
               log.info("Waiting for state to arrive");
               setStateLock.wait();
            } 
         }
      }
      
      log.info(this + " loadBindings complete");
   }
   
   // Protected ---------------------------------------------------------------------------------------
   
   /*
    * Asynchronously cast the message.
    * I.e. we just hand the message to JGroups and return immediately
    * JGroups will then take care of delivering the message
    */
   protected void asyncCastMessage(String routingKey, org.jboss.messaging.core.Message msg) throws Exception
   {            
      MessageRequest request = new MessageRequest(routingKey, msg);
      
      //TODO - handle serialization more efficiently

      dataChannel.send(new Message(null, null, request));
   }
   
   protected abstract void routeFromCluster(MessageReference ref, String routingKey) throws Exception;
   
   
   // Private ------------------------------------------------------------------------------------------
      
   /*
    * Called when another node adds a binding
    */
   private void addBindingFromCluster(String nodeId, String queueName, String condition,
                                      String filterString, boolean noLocal, long channelID, boolean durable)
      throws Exception
   {
      lock.writeLock().acquire();
      
      log.info("node " + this.nodeId + " received request to add binding from node " + nodeId);
      
      try
      {         
         // We currently only allow one binding per name per node
         Map nameMap = (Map)nameMaps.get(nodeId);
         
         Binding binding = null;
         
         if (nameMap != null)
         {
            binding = (Binding)nameMap.get(queueName);
         }
         
         if (binding != null)
         {
            throw new IllegalArgumentException("Binding already exists for node Id " + nodeId + " queue name " + queueName);
         }
         
         binding = new SimpleBinding(nodeId, queueName, condition, filterString,
                                     noLocal, channelID, durable); 
         
         log.info("Created binding");
         
         binding.activate();
         
         addBinding(binding);         
         
         log.info("Added it");
      }
      finally
      {
         lock.writeLock().release();
      }
   }
   
   /*
    * Called when another node removes a binding
    */
   private void removeBindingFromCluster(String nodeId, String queueName) throws Exception
   {
      lock.writeLock().acquire();
      
      try
      {         
         // We currently only allow one binding per name per node
         Map nameMap = (Map)nameMaps.get(nodeId);
         
         Binding binding = null;
         
         if (nameMap != null)
         {
            binding = (Binding)nameMap.remove(queueName);
         }
         
         if (binding == null)
         {
            throw new IllegalArgumentException("Cannot find binding with node Id " + nodeId + " queue name: " + queueName);
         }
         
      }
      finally
      {
         lock.writeLock().release();
      }
   }
   
   /*
    * Multicast a bind request to all clustered exchange instances in the group
    */
   private void sendBindMessage(String queueName, String condition,
                                String filterString, boolean noLocal,
                                long channelId, boolean durable) throws Exception
   {
      // TODO handle serialization more efficiently
      
      BindRequest request =
         new BindRequest(nodeId, queueName, condition, filterString, noLocal, channelId, durable);
      
      Message message = new Message(null, null, request);
      
      controlMessageDispatcher.castMessage(null, message, GroupRequest.GET_ALL, CAST_TIMEOUT);
      
      //We don't actually care if some of the members didn't receive the message (e.g. by crashing)
      //so we ignore the return value
      
      // TODO - How do we know if the call timed out???
      //We need to handle this
      
   }
   
   /*
    * Multicast a unbind request to all clustered exchange instances in the group
    */
   private void sendUnbindMessage(String queueName) throws Exception
   {
      //TODO handle serialization more efficiently
      
      UnbindRequest request = new UnbindRequest(nodeId, queueName);
      
      Message message = new Message(null, null, request);
      
      controlMessageDispatcher.castMessage(null, message, GroupRequest.GET_ALL, CAST_TIMEOUT);
      
      //We don't actually care if some of the members didn't receive the message (e.g. by crashing)
      //so we ignore the return value
      
      //TODO - How do we know if the call timed out???
      //We need to handle this
      
   }
   
   //TODO - Sort out serialization properly
   
   private byte[] getBindingListAsBytes() throws Exception
   {
      List bindings = new ArrayList();
      
      Iterator iter = nameMaps.values().iterator();
      
      while (iter.hasNext())
      {
         Map map  = (Map)iter.next();
         
         Iterator iter2 = map.values().iterator();
         
         while (iter2.hasNext())
         {
            bindings.add(iter2.next());
         }
      }
      
      byte[] bytes = Util.objectToByteBuffer(bindings);
      
      return bytes;
   }
   
   private void processBindingListBytes(byte[] bytes) throws Exception
   {
      nameMaps.clear();
      
      conditionMap.clear();
      
      List bindings = (List)Util.objectFromByteBuffer(bytes);
      
      Iterator iter = bindings.iterator();
      
      while (iter.hasNext())
      {
         Binding binding = (Binding)iter.next();
         
         addBinding(binding);
      }
   }
   
   // Inner classes -------------------------------------------------------------------
    
   /*
    * This class is used to manage state on the control channel
    */
   private class ControlMessageListener implements MessageListener
   {
      public byte[] getState()
      {
         //TODO should we propagate the exceptions up??
         
         log.info(this + " getState called");
         try
         {
            lock.writeLock().acquire();
         }
         catch (InterruptedException e)
         {
            log.error("Thread Interrupted", e);
         }
         try
         {
            return getBindingListAsBytes();
         }
         catch (Exception e)
         {
            log.error("Failed to get state", e);
            return null;
         }     
         finally
         {
            lock.writeLock().release();
         }
      }
      
      public void receive(Message message)
      {         
         log.info("Received message on control channel: " + message);
      }
      
      public void setState(byte[] bytes)
      {
         // TODO should we propagate the exceptions up??
         
         log.info(this + " setState called");
         log.info("state is: " + bytes);
         
         if (bytes != null)
         {
            
            try
            {
               lock.writeLock().acquire();         
            }
            catch (InterruptedException e)
            {
               log.error("Thread interrupted", e);
            }
            try
            {
               processBindingListBytes(bytes);               
            }
            catch (Exception e)
            {
               log.error("Failed to deserialize", e);
            }
            finally
            {
               lock.writeLock().release();
            }
         }
         
         log.info("Set the state");
         
         synchronized (setStateLock)
         {
            stateSet = true;
            setStateLock.notify();
         }
         
         log.info("Notified");
         
      }      
   }
   
   
   /*
    * This class is used to listen for messages on the data channel
    */
   private class DataMessageListener implements Receiver
   {
      public void block()
      {   
         //NOOP
      }

      public void suspect(Address address)
      { 
         //NOOP
      }

      public void viewAccepted(View view)
      { 
         //NOOP
      }

      public byte[] getState()
      {         
         //NOOP
         return null;
      }
      
      public void receive(Message message)
      {
         try
         {
            log.info("Received message:" + message);
            
            //TODO handle deserialization more efficiently
            
            Object object = message.getObject();
            
            log.info("Object is: " + object);
            
            if (object instanceof MessageRequest)
            {
               MessageRequest request = (MessageRequest)object;
               
               //Need to reference the message
               MessageReference ref = null;
               try
               {
                  ref = ms.reference(request.getMessage());
                  
                  log.info("Routing it internally");
                  routeFromCluster(ref, request.getRoutingKey());
               }
               finally
               {
                  if (ref != null)
                  {
                     ref.releaseMemoryReference();
                  }
               }
            }
         }
         catch (Exception e)
         {
            log.error("Failed to route from cluster", e);
            //TODO should we propagate exceptions up?
         }
         
      }
      
      public void setState(byte[] bytes)
      {
         //NOOP         
      }      
   }
   
   /*
    * This class is used to handle synchronous requests
    */
   private class ExchangeRequestHandler implements RequestHandler
   {
      public Object handle(Message message)
      {
         //TODO should we propagate the exceptions up??
         
         //TODO handle deserialization more efficiently
         
         Object request = message.getObject();
         
         log.info("Received request: " + request);
         
         if (request instanceof BindRequest)
         {
            BindRequest br = (BindRequest)request;
            
            try
            {            
               addBindingFromCluster(br.getNodeId(), br.getQueueName(), br.getCondition(),
                                     br.getFilterString(), br.isNoLocal(), br.getChannelId(), br.isDurable());
            }
            catch (Exception e)
            {
               log.error("Failed to add binding from cluster", e);
            }
         }
         else if (request instanceof UnbindRequest)
         {
            UnbindRequest ubr = (UnbindRequest)request;
            
            try
            {
               removeBindingFromCluster(ubr.getNodeId(), ubr.getQueueName());
            }
            catch (Exception e)
            {
               log.error("Failed to remove binding from cluster", e);
            }
         }
         else
         {
            throw new IllegalArgumentException("Invalid request: " + request);
         }
            
         return null;
      }
      
   }   
}