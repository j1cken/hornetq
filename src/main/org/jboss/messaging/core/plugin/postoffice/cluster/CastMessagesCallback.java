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
package org.jboss.messaging.core.plugin.postoffice.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.messaging.core.Message;
import org.jboss.messaging.core.tx.TxCallback;

/**
 * A CastMessageCallback
 * 
 * When we want to send persistent message(s) across the group to remote subscriptions
 * and there is at least one remote durable subscription we do the following:
 * 1) Cast the message(s) to group members. They are held in a "holding area" on the remote node
 * and not immediately sent to the channels.
 * 2) Persist the message(s) in the durable subscription(s) on the sending node.
 * 3) Cast another message to the group members telling them to process the messages in the "holding" area
 * for a particular transaction id
 * This allows us to avoid an expensive 2PC protocol which involve extra database updates
 * When the sending node starts up, it records a flag in the database, on clean shutdown it deletes the flag.
 * If the server finds the flag in the database at start-up it knows it crashed the last time, so it
 * sends a "check" message to all members of the group.
 * On receipt of the "check" message, the receiving node checks in it's holding area for any holding messages for
 * that node id, if they exist AND they also exist in the db (i.e. they were persisted ok) then they are processed
 * otherwise they are discarded.
 * 
 * The execution order of callbacks must be as follows:
 * 
 * CastMessagesCallback.beforeCommit() - cast message(s) to holding areas
 * JDBCPersistenceManager.TransactionCallback.beforeCommit() - persist message(s) in database
 * CastMessagesCallback.afterCommit() - send "commit" message to holding areas
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
class CastMessagesCallback implements TxCallback
{           
   private static final Logger log = Logger.getLogger(CastMessagesCallback.class);
   
   private List persistent;
   
   private List nonPersistent;
   
   private String nodeId;
   
   private long txId;
   
   private PostOfficeInternal office;
   
   private boolean multicast;
   
   private String toNodeId;
   
   void addMessage(String routingKey, Message message, Map queueNameToNodeIdMap, String lastNodeId)
   {
      //If we only ever send messages to the same node for this tx, then we can unicast rather than multicast
      //This is how we determine that
      if (lastNodeId == null)
      {
         multicast = true;
      }
      else
      {
         if (!lastNodeId.equals(toNodeId))
         {
            multicast = true;
         }
         else
         {
            toNodeId = lastNodeId;
         }
      }
      
      MessageHolder holder = new MessageHolder(routingKey, message, queueNameToNodeIdMap);
      
      if (message.isReliable())
      {
         if (persistent == null)
         {
            persistent = new ArrayList();
         }
         persistent.add(holder);
      }
      else
      {
         if (nonPersistent == null)
         {
            nonPersistent = new ArrayList();
         }
         nonPersistent.add(holder);
      }
   }
   
   CastMessagesCallback(String nodeId, long txId, PostOfficeInternal office)
   {
      this.nodeId = nodeId;
      
      this.txId = txId;
      
      this.office = office;
   }

   public void afterCommit(boolean onePhase) throws Exception
   {            
      if (nonPersistent != null)
      {
         // Cast the non persistent - this don't need to go into a holding area on the receiving node
         ClusterRequest req = new MessagesRequest(nonPersistent);
         
         sendRequest(req);         
      }
      
      if (persistent != null)
      {
         // Cast a commit message
         ClusterRequest req = new SendTransactionRequest(nodeId, txId);
         
         sendRequest(req);
      }
      
      nonPersistent = persistent = null;
   }
   
   public void afterPrepare() throws Exception
   { 
   }

   public void afterRollback(boolean onePhase) throws Exception
   {
   }

   public void beforeCommit(boolean onePhase) throws Exception
   {
      if (persistent != null)
      {
         //We send the persistent messages which go into the "holding area" on
         //the receiving nodes
         ClusterRequest req = new SendTransactionRequest(nodeId, txId, persistent);
         
         sendRequest(req);
      }
   }

   public void beforePrepare() throws Exception
   {
   }

   public void beforeRollback(boolean onePhase) throws Exception
   {
   }
   
   private void sendRequest(ClusterRequest req) throws Exception
   {
      if (multicast)
      {
         office.asyncSendRequest(req);
      }
      else
      {
         //FIXME temp commented out until unicast works
         //office.asyncSendRequest(req, toNodeId);
         office.asyncSendRequest(req);
      }
   }
      
}    
