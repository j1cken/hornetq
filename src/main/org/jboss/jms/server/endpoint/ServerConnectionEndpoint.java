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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;

import org.jboss.aop.AspectManager;
import org.jboss.jms.client.delegate.ClientSessionDelegate;
import org.jboss.jms.client.remoting.CallbackManager;
import org.jboss.jms.delegate.ConnectionEndpoint;
import org.jboss.jms.delegate.IDBlock;
import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.jms.destination.JBossDestination;
import org.jboss.jms.message.JBossMessage;
import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.JMSCondition;
import org.jboss.jms.server.SecurityManager;
import org.jboss.jms.server.ServerPeer;
import org.jboss.jms.server.endpoint.advised.SessionAdvised;
import org.jboss.jms.tx.ClientTransaction;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.jms.tx.TransactionRequest;
import org.jboss.jms.tx.ClientTransaction.SessionTxState;
import org.jboss.jms.wireformat.Dispatcher;
import org.jboss.jms.wireformat.JMSWireFormat;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.contract.Binding;
import org.jboss.messaging.core.contract.Delivery;
import org.jboss.messaging.core.contract.MessageReference;
import org.jboss.messaging.core.contract.MessageStore;
import org.jboss.messaging.core.contract.PostOffice;
import org.jboss.messaging.core.contract.Queue;
import org.jboss.messaging.core.impl.tx.Transaction;
import org.jboss.messaging.core.impl.tx.TransactionRepository;
import org.jboss.messaging.util.ExceptionUtil;
import org.jboss.messaging.util.GUIDGenerator;
import org.jboss.messaging.util.Util;
import org.jboss.remoting.Client;
import org.jboss.remoting.callback.ServerInvokerCallbackHandler;

/**
 * Concrete implementation of ConnectionEndpoint.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class ServerConnectionEndpoint implements ConnectionEndpoint
{
   // Constants ------------------------------------------------------------------------------------
   
   private static final Logger log = Logger.getLogger(ServerConnectionEndpoint.class);
   
   // Static ---------------------------------------------------------------------------------------

   private static boolean trace = log.isTraceEnabled();

   // Attributes -----------------------------------------------------------------------------------

   private String id;

   private volatile boolean closed;
   private volatile boolean started;

   private String clientID;
   private String username;
   private String password;

   private String remotingClientSessionID;
   private String jmsClientVMID;

   // the server itself
   private ServerPeer serverPeer;

   // access to server's extensions
   private PostOffice postOffice;
   private SecurityManager sm;
   private ConnectionManager cm;
   private TransactionRepository tr;
   private MessageStore ms;
   private ServerInvokerCallbackHandler callbackHandler;

   // Map<sessionID - ServerSessionEndpoint>
   private Map sessions;

   // Set<?>
   private Set temporaryDestinations;

   private int prefetchSize;
   private int defaultTempQueueFullSize;
   private int defaultTempQueuePageSize;
   private int defaultTempQueueDownCacheSize;
   private int dupsOKBatchSize;
   
   private ServerConnectionFactoryEndpoint cfendpoint;

   private byte usingVersion;

   // a non-null value here means connection is a fail-over connection
   private Integer failedNodeID;

   // Constructors ---------------------------------------------------------------------------------

   /**
    * @param failedNodeID - zero or positive values mean connection creation attempt is result of
    *        failover. Negative values are ignored (mean regular connection creation attempt).
    */
   public ServerConnectionEndpoint(ServerPeer serverPeer, String clientID,
                                   String username, String password, int prefetchSize,
                                   int defaultTempQueueFullSize,
                                   int defaultTempQueuePageSize,
                                   int defaultTempQueueDownCacheSize,
                                   int failedNodeID,
                                   ServerConnectionFactoryEndpoint cfendpoint,
                                   String remotingSessionID,
                                   String clientVMID,
                                   byte versionToUse,
                                   ServerInvokerCallbackHandler callbackHandler,
                                   int dupsOKBatchSize) throws Exception
   {
      this.serverPeer = serverPeer;

      this.cfendpoint = cfendpoint;
      
      sm = serverPeer.getSecurityManager();
      tr = serverPeer.getTxRepository();
      cm = serverPeer.getConnectionManager();
      ms = serverPeer.getMessageStore();
      postOffice = serverPeer.getPostOfficeInstance();
 
      started = false;

      this.id = GUIDGenerator.generateGUID();
      this.clientID = clientID;
      this.prefetchSize = prefetchSize;
      
      this.defaultTempQueueFullSize = defaultTempQueueFullSize;
      this.defaultTempQueuePageSize = defaultTempQueuePageSize;
      this.defaultTempQueueDownCacheSize = defaultTempQueueDownCacheSize;
      
      this.dupsOKBatchSize = dupsOKBatchSize;

      sessions = new HashMap();
      temporaryDestinations = new HashSet();
      
      this.username = username;
      this.password = password;

      if (failedNodeID > 0)
      {
         this.failedNodeID = new Integer(failedNodeID);
      }
      
      this.remotingClientSessionID = remotingSessionID;
      
      this.jmsClientVMID = clientVMID;
      this.usingVersion = versionToUse; 
      
      this.serverPeer.getConnectionManager().
         registerConnection(jmsClientVMID, remotingClientSessionID, this);
      
      this.callbackHandler = callbackHandler;
      
      Client callbackClient = callbackHandler.getCallbackClient();
      
      if (callbackClient != null)
      {
         // TODO not sure if this is the best way to do this, but the callbackClient needs to have
         //      its "subsystem" set, otherwise remoting cannot find the associated
         //      ServerInvocationHandler on the callback server
         callbackClient.setSubsystem(CallbackManager.JMS_CALLBACK_SUBSYSTEM);
         
         // We explictly set the Marshaller since otherwise remoting tries to resolve the marshaller
         // every time which is very slow - see org.jboss.remoting.transport.socket.ProcessInvocation
         // This can make a massive difference on performance. We also do this in
         // JMSRemotingConnection.setupConnection
         
         callbackClient.setMarshaller(new JMSWireFormat());
         callbackClient.setUnMarshaller(new JMSWireFormat());
      }
      else
      {
         log.trace("ServerInvokerCallbackHandler callback Client is not available: " +
                   "must be using pull callbacks");
      }
   }

   // ConnectionDelegate implementation ------------------------------------------------------------
   
   public SessionDelegate createSessionDelegate(boolean transacted,
                                                int acknowledgmentMode,
                                                boolean isXA)
      throws JMSException
   {
      try
      {
         log.trace(this + " creating " + (transacted ? "transacted" : "non transacted") +
            " session, " + Util.acknowledgmentMode(acknowledgmentMode) + ", " +
            (isXA ? "XA": "non XA"));
         
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }
                  
         String sessionID = GUIDGenerator.generateGUID();
           
         // create the corresponding server-side session endpoint and register it with this
         // connection endpoint instance
         ServerSessionEndpoint ep = new ServerSessionEndpoint(sessionID, this);
         
         synchronized (sessions)
         {
            sessions.put(sessionID, ep);
         }
         
         SessionAdvised advised;
         
         // Need to synchronized to prevent a deadlock
         // See http://jira.jboss.com/jira/browse/JBMESSAGING-797
         synchronized (AspectManager.instance())
         {       
            advised = new SessionAdvised(ep);
         }
         
         SessionAdvised sessionAdvised = advised;
         
         serverPeer.addSession(sessionID, ep);

         Dispatcher.instance.registerTarget(sessionID, sessionAdvised);

         log.trace("created and registered " + ep);
         
         ClientSessionDelegate d = new ClientSessionDelegate(sessionID, dupsOKBatchSize);

         log.trace("created " + d);
         
         return d;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " createSessionDelegate");         
      }
   }
         
   public String getClientID() throws JMSException
   {
      try
      {
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }
         return clientID;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " getClientID");
      }  
   }
   
   public void setClientID(String clientID) throws JMSException
   {
      try
      {
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }

         if (this.clientID != null)
         {
            throw new IllegalStateException("Cannot set clientID, already set as " + this.clientID);
         }

         log.trace(this + "setting client ID to " + clientID);

         this.clientID = clientID;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " setClientID");
      } 
   }
      
   public void start() throws JMSException
   {
      try
      {
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }
         setStarted(true);
         log.trace(this + " started");
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " start");
      } 
   }   
   
   public synchronized void stop() throws JMSException
   {
      try
      {
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }
         
         setStarted(false);
         
         log.trace("Connection " + id + " stopped");
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " stop");
      } 
   }
   
   public void close() throws JMSException
   {      
      try
      {
         if (trace) { log.trace(this + " close()"); }
         
         if (closed)
         {
            log.warn("Connection is already closed");
            return;
         }
   
         //We clone to avoid deadlock http://jira.jboss.org/jira/browse/JBMESSAGING-836
         Map sessionsClone;
         synchronized (sessions)
         {
            sessionsClone = new HashMap(sessions);
         }
         
         for(Iterator i = sessionsClone.values().iterator(); i.hasNext(); )
         {
            ServerSessionEndpoint sess = (ServerSessionEndpoint)i.next();
   
            sess.localClose();
         }
         
         sessions.clear();
                  
         synchronized (temporaryDestinations)
         {
            for(Iterator i = temporaryDestinations.iterator(); i.hasNext(); )
            {
               JBossDestination dest = (JBossDestination)i.next();
   
               if (dest.isQueue())
               {     
               	// Temporary queues must be unbound on ALL nodes of the cluster
               	
               	postOffice.removeBinding(dest.getName(), postOffice.isClustered());               	
               }
               else
               {
                  //No need to unbind - this will already have happened, and removeAllReferences
                  //will have already been called when the subscriptions were closed
                  //which always happens before the connection closed (depth first close)     
               	//note there are no durable subs on a temporary topic
               	
               	//Sanity check
               	
                  Collection queues = serverPeer.getPostOfficeInstance().getQueuesForCondition(new JMSCondition(false, dest.getName()), true);
               	
                  if (!queues.isEmpty())
               	{
                  	//This should never happen
                  	throw new IllegalStateException("Cannot delete temporary destination if it has consumer(s)");
               	}
               }
            }
            
            temporaryDestinations.clear();
         }
   
         cm.unregisterConnection(jmsClientVMID, remotingClientSessionID);
   
         Dispatcher.instance.unregisterTarget(id, this);

         closed = true;
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " close");
      } 
   }
   
   public long closing() throws JMSException
   {
      log.trace(this + " closing (noop)");    
      
      return -1;
   }

   public void sendTransaction(TransactionRequest request,
                               boolean checkForDuplicates) throws JMSException
   {    
      try
      {      
         if (closed)
         {
            throw new IllegalStateException("Connection is closed");
         }
                              
         if (request.getRequestType() == TransactionRequest.ONE_PHASE_COMMIT_REQUEST)
         {
            if (trace) { log.trace(this + " received ONE_PHASE_COMMIT request"); }
            
            Transaction tx = tr.createTransaction();
            processTransaction(request.getState(), tx, checkForDuplicates);
            tx.commit();
         }        
         else if (request.getRequestType() == TransactionRequest.TWO_PHASE_PREPARE_REQUEST)
         {                        
            if (trace) { log.trace(this + " received TWO_PHASE_COMMIT prepare request"); }
            
            Transaction tx = tr.createTransaction(request.getXid());
            processTransaction(request.getState(), tx, checkForDuplicates);
            tx.prepare();            
         }
         else if (request.getRequestType() == TransactionRequest.TWO_PHASE_COMMIT_REQUEST)
         {   
            if (trace) { log.trace(this + " received TWO_PHASE_COMMIT commit request"); }
             
            Transaction tx = tr.getPreparedTx(request.getXid());            
            if (trace) { log.trace("Committing " + tx); }
            tx.commit();   
         }
         else if (request.getRequestType() == TransactionRequest.TWO_PHASE_ROLLBACK_REQUEST)
         {
            if (trace) { log.trace(this + " received TWO_PHASE_COMMIT rollback request"); }
             
            // for 2pc rollback - we just don't cancel any messages back to the channel; this is
            // driven from the client side.
             
            Transaction tx =  tr.getPreparedTx(request.getXid());

            if (trace) { log.trace(this + " rolling back " + tx); }

            tx.rollback();
         }      
                 
         if (trace) { log.trace(this + " processed transaction successfully"); }
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " sendTransaction");
      } 
   }
   
   /**
    * Get array of XA transactions in prepared state-
    * This would be used by the transaction manager in recovery or by a tool to apply
    * heuristic decisions to commit or rollback particular transactions
    */
   public MessagingXid[] getPreparedTransactions() throws JMSException
   {
      try
      {
         List xids = tr.recoverPreparedTransactions();
         
         return (MessagingXid[])xids.toArray(new MessagingXid[xids.size()]);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " getPreparedTransactions");
      }
   }
   
   public IDBlock getIdBlock(int size) throws JMSException
   {
      try
      {
         return serverPeer.getMessageIDManager().getIDBlock(size);
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMSInvocation(t, this + " getIdBlock");
      }
   }
   
   // Public ---------------------------------------------------------------------------------------
   
   public String getUsername()
   {
      return username;
   }
   
   public String getPassword()
   {
      return password;
   }

   public SecurityManager getSecurityManager()
   {
      return sm;
   }

   public ServerInvokerCallbackHandler getCallbackHandler()
   {
      return callbackHandler;
   }

   public ServerPeer getServerPeer()
   {
      return serverPeer;
   }

   public ServerConnectionFactoryEndpoint getConnectionFactoryEndpoint()
   {
      return cfendpoint;
   }

   public Collection getSessions()
   {
      ArrayList list = new ArrayList();
      synchronized (sessions)
      {
         list.addAll(sessions.values());
      }
      return list;
   }

   public String toString()
   {
      return "ConnectionEndpoint[" + id + "]";
   }

   // Package protected ----------------------------------------------------------------------------

   byte getUsingVersion()
   {
      return usingVersion;
   }
   
   int getPrefetchSize()
   {
      return prefetchSize;
   }
   
   int getDefaultTempQueueFullSize()
   {
      return defaultTempQueueFullSize;
   }
   
   int getDefaultTempQueuePageSize()
   {
      return defaultTempQueuePageSize;
   }
     
   int getDefaultTempQueueDownCacheSize()
   {
      return defaultTempQueueDownCacheSize;
   }
   
   String getConnectionID()
   {
      return id;
   }
   
   boolean isStarted()
   {
      return started;    
   }
   
   void removeSession(String sessionId) throws Exception
   {
      synchronized (sessions)
      {
         if (sessions.remove(sessionId) == null)
         {
            throw new IllegalStateException("Cannot find session with id " + sessionId + " to remove");
         }
      }
   }

   void addTemporaryDestination(Destination dest)
   {
      synchronized (temporaryDestinations)
      {
         temporaryDestinations.add(dest);
      }
   }
   
   void removeTemporaryDestination(Destination dest)
   {
      synchronized (temporaryDestinations)
      {
         temporaryDestinations.remove(dest);
      }
   }
   
   boolean hasTemporaryDestination(Destination dest)
   {
      synchronized (temporaryDestinations)
      {
         return temporaryDestinations.contains(dest);
      }
   }
   
   String getRemotingClientSessionID()
   {
      return remotingClientSessionID;
   }
   
   void sendMessage(JBossMessage msg, Transaction tx, boolean checkForDuplicates) throws Exception
   {
      if (trace) { log.trace(this + " sending message " + msg + (tx == null ? " non-transactionally" : " in " + tx)); }

      JBossDestination dest = (JBossDestination)msg.getJMSDestination();
      
      // This allows the no-local consumers to filter out the messages that come from the same
      // connection.

      // TODO Do we want to set this for ALL messages. Optimisation is possible here.
      msg.setConnectionID(id);

      if (checkForDuplicates)
      {
         // Message is already stored... so just ignoring the call
         if (serverPeer.getPersistenceManagerInstance().referenceExists(msg.getMessageID()))
         {
            return;
         }
      }

      // We must reference the message *before* we send it the destination to be handled. This is
      // so we can guarantee that the message doesn't disappear from the store before the
      // handling is complete. Each channel then takes copies of the reference if they decide to
      // maintain it internally
      
      MessageReference ref = null; 
      
      try
      {         
         ref = ms.reference(msg);
         
         long schedDeliveryTime = msg.getScheduledDeliveryTime();
         
         if (schedDeliveryTime > 0)
         {
            ref.setScheduledDeliveryTime(schedDeliveryTime);
         }
         
         if (dest.isDirect())
         {
         	//Route directly to queue - temp kludge for clustering
         	
         	Binding binding = postOffice.getBindingForQueueName(dest.getName());
         	
         	if (binding == null)
         	{
         		throw new IllegalArgumentException("Cannot find binding for queue " + dest.getName());
         	}
         	
         	Queue queue = binding.queue;
         	
         	Delivery del = queue.handle(null, ref, tx);
         	
         	if (del == null)
         	{
         		throw new JMSException("Failed to route " + ref + " to " + dest.getName());
         	}
         }
         else if (dest.isQueue())
         {
            if (!postOffice.route(ref, new JMSCondition(true, dest.getName()), tx))
            {
               throw new JMSException("Failed to route " + ref + " to " + dest.getName());
            }
         }
         else
         {
            postOffice.route(ref, new JMSCondition(false, dest.getName()), tx);   
         }
      }
      finally
      {
         if (ref != null)
         {
            ref.releaseMemoryReference();
         }
      }
         
      if (trace) { log.trace("sent " + msg); }
   }
   
   // Protected ------------------------------------------------------------------------------------

   /**
    * Give access to children enpoints to the failed node ID, in case this is a failover connection.
    * Return null if the connection is regular (not failover).
    */
   Integer getFailedNodeID()
   {
      return failedNodeID;
   }

   /**
    * Tell children enpoints (and anybody from this package, for that matter) whether this
    * connection is a regular or failover connection.
    */
   boolean isFailoverConnection()
   {
      return failedNodeID != null;
   }
     
   // Private --------------------------------------------------------------------------------------
   
   private void setStarted(boolean s) throws Throwable
   {
      //We clone to avoid deadlock http://jira.jboss.org/jira/browse/JBMESSAGING-836
      Map sessionsClone = null;
      
      synchronized(sessions)
      {
         sessionsClone = new HashMap(sessions);
      }
      
      for (Iterator i = sessionsClone.values().iterator(); i.hasNext(); )
      {
         ServerSessionEndpoint sd = (ServerSessionEndpoint)i.next();
         
         sd.setStarted(s);
      }
      started = s;      
   }   
    
   private void processTransaction(ClientTransaction txState,
                                   Transaction tx, boolean checkForDuplicates) throws Throwable
   {
      if (trace) { log.trace(this + " processing transaction " + tx); }

      // used on checkForDuplicates...
      // we only check the first iteration
      boolean firstIteration = true;
        
      for (Iterator i = txState.getSessionStates().iterator(); i.hasNext(); )
      {
         SessionTxState sessionState = (SessionTxState)i.next();

         // send the messages

         for (Iterator j = sessionState.getMsgs().iterator(); j.hasNext(); )
         {
            JBossMessage message = (JBossMessage)j.next();
            if (checkForDuplicates && firstIteration)
            {
               firstIteration = false;
               if (serverPeer.getPersistenceManagerInstance().
                  referenceExists(message.getMessageID()))
               {
                  // This means the transaction was previously completed...
                  // we are done here then... no need to even check for ACKs or anything else
                  log.trace("Transaction " + tx + " was previously completed, ignoring call");
                  return;
               }
            }
            sendMessage(message, tx, false);
         }

         // send the acks
                  
         // We need to lookup the session in a global map maintained on the server peer. We can't
         // just assume it's one of the sessions in the connection. This is because in the case
         // of a connection consumer, the message might be delivered through one connection and
         // the transaction committed/rolledback through another. ConnectionConsumers suck.
         
         ServerSessionEndpoint session = serverPeer.getSession(sessionState.getSessionId());
         
         if (session == null)
         {               
            throw new IllegalStateException("Cannot find session with id " +
               sessionState.getSessionId());
         }

         session.acknowledgeTransactionally(sessionState.getAcks(), tx);
      }
            
      if (trace) { log.trace(this + " processed transaction " + tx); }
   }   

   // Inner classes --------------------------------------------------------------------------------
}
