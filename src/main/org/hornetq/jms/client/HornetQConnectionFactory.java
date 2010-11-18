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

package org.hornetq.jms.client;

import java.io.Serializable;
import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;

import org.hornetq.api.core.Pair;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.logging.Logger;
import org.hornetq.jms.referenceable.ConnectionFactoryObjectFactory;
import org.hornetq.jms.referenceable.SerializableObjectRefAddr;

/**
 * HornetQ implementation of a JMS ConnectionFactory.
 * 
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt> $Id$
 */
public class HornetQConnectionFactory implements Serializable, Referenceable
{
   // Constants ------------------------------------------------------------------------------------

   private final static long serialVersionUID = -2810634789345348326L;

   private static final Logger log = Logger.getLogger(HornetQConnectionFactory.class);

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private final ClientSessionFactory sessionFactory;

   private String clientID;

   private int dupsOKBatchSize = HornetQClient.DEFAULT_ACK_BATCH_SIZE;

   private int transactionBatchSize = HornetQClient.DEFAULT_ACK_BATCH_SIZE;

   private boolean readOnly;

   // Constructors ---------------------------------------------------------------------------------

   public HornetQConnectionFactory()
   {
      sessionFactory = HornetQClient.createClientSessionFactory();
   }

   public HornetQConnectionFactory(final ClientSessionFactory sessionFactory)
   {
      this.sessionFactory = sessionFactory;
   }

   public HornetQConnectionFactory(final String discoveryAddress, final int discoveryPort)
   {
      sessionFactory = HornetQClient.createClientSessionFactory(discoveryAddress, discoveryPort);
   }

   public HornetQConnectionFactory(final List<Pair<TransportConfiguration, TransportConfiguration>> staticConnectors)
   {
      sessionFactory = HornetQClient.createClientSessionFactory(staticConnectors);
   }

   public HornetQConnectionFactory(final TransportConfiguration connectorConfig,
                                   final TransportConfiguration backupConnectorConfig)
   {
      sessionFactory = HornetQClient.createClientSessionFactory(connectorConfig, backupConnectorConfig);
   }

   public HornetQConnectionFactory(final TransportConfiguration connectorConfig)
   {
      this(connectorConfig, null);
   }

   // ConnectionFactory implementation -------------------------------------------------------------

   public Connection createConnection() throws JMSException
   {
      return createConnection(null, null);
   }

   public Connection createConnection(final String username, final String password) throws JMSException
   {
      return createConnectionInternal(username, password, false, HornetQConnection.TYPE_GENERIC_CONNECTION);
   }

   // QueueConnectionFactory implementation --------------------------------------------------------

   public QueueConnection createQueueConnection() throws JMSException
   {
      return createQueueConnection(null, null);
   }

   public QueueConnection createQueueConnection(final String username, final String password) throws JMSException
   {
      return (QueueConnection)createConnectionInternal(username, password, false, HornetQConnection.TYPE_QUEUE_CONNECTION);
   }

   // TopicConnectionFactory implementation --------------------------------------------------------

   public TopicConnection createTopicConnection() throws JMSException
   {
      return createTopicConnection(null, null);
   }

   public TopicConnection createTopicConnection(final String username, final String password) throws JMSException
   {
      return (TopicConnection)createConnectionInternal(username, password, false, HornetQConnection.TYPE_TOPIC_CONNECTION);
   }

   // XAConnectionFactory implementation -----------------------------------------------------------

   public XAConnection createXAConnection() throws JMSException
   {
      return createXAConnection(null, null);
   }

   public XAConnection createXAConnection(final String username, final String password) throws JMSException
   {
      return (XAConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_GENERIC_CONNECTION);
   }

   // XAQueueConnectionFactory implementation ------------------------------------------------------

   public XAQueueConnection createXAQueueConnection() throws JMSException
   {
      return createXAQueueConnection(null, null);
   }

   public XAQueueConnection createXAQueueConnection(final String username, final String password) throws JMSException
   {
      return (XAQueueConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_QUEUE_CONNECTION);
   }

   // XATopicConnectionFactory implementation ------------------------------------------------------

   public XATopicConnection createXATopicConnection() throws JMSException
   {
      return createXATopicConnection(null, null);
   }

   public XATopicConnection createXATopicConnection(final String username, final String password) throws JMSException
   {
      return (XATopicConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_TOPIC_CONNECTION);
   }

   // Referenceable implementation -----------------------------------------------------------------

   public Reference getReference() throws NamingException
   {
      return new Reference(this.getClass().getCanonicalName(),
                           new SerializableObjectRefAddr("HornetQ-CF", this),
                           ConnectionFactoryObjectFactory.class.getCanonicalName(),
                           null);
   }

   // Public ---------------------------------------------------------------------------------------

   public synchronized String getConnectionLoadBalancingPolicyClassName()
   {
      return sessionFactory.getConnectionLoadBalancingPolicyClassName();
   }

   public synchronized void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName)
   {
      checkWrite();
      sessionFactory.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   public synchronized List<Pair<TransportConfiguration, TransportConfiguration>> getStaticConnectors()
   {
      return sessionFactory.getStaticConnectors();
   }

   public synchronized void setStaticConnectors(final List<Pair<TransportConfiguration, TransportConfiguration>> staticConnectors)
   {
      checkWrite();
      sessionFactory.setStaticConnectors(staticConnectors);
   }
   
   public synchronized String getLocalBindAddress()
   {
      return sessionFactory.getLocalBindAddress();
   }

   public synchronized void setLocalBindAddress(final String localBindAddress)
   {
      checkWrite();
      sessionFactory.setLocalBindAddress(localBindAddress);
   }

   public synchronized String getDiscoveryAddress()
   {
      return sessionFactory.getDiscoveryAddress();
   }

   public synchronized void setDiscoveryAddress(final String discoveryAddress)
   {
      checkWrite();
      sessionFactory.setDiscoveryAddress(discoveryAddress);
   }

   public synchronized int getDiscoveryPort()
   {
      return sessionFactory.getDiscoveryPort();
   }

   public synchronized void setDiscoveryPort(final int discoveryPort)
   {
      checkWrite();
      sessionFactory.setDiscoveryPort(discoveryPort);
   }

   public synchronized long getDiscoveryRefreshTimeout()
   {
      return sessionFactory.getDiscoveryRefreshTimeout();
   }

   public synchronized void setDiscoveryRefreshTimeout(final long discoveryRefreshTimeout)
   {
      checkWrite();
      sessionFactory.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
   }

   public synchronized long getDiscoveryInitialWaitTimeout()
   {
      return sessionFactory.getDiscoveryInitialWaitTimeout();
   }

   public synchronized void setDiscoveryInitialWaitTimeout(final long discoveryInitialWaitTimeout)
   {
      checkWrite();
      sessionFactory.setDiscoveryInitialWaitTimeout(discoveryInitialWaitTimeout);
   }

   public synchronized String getClientID()
   {
      return clientID;
   }

   public synchronized void setClientID(final String clientID)
   {
      checkWrite();
      this.clientID = clientID;
   }

   public synchronized int getDupsOKBatchSize()
   {
      return dupsOKBatchSize;
   }

   public synchronized void setDupsOKBatchSize(final int dupsOKBatchSize)
   {
      checkWrite();
      this.dupsOKBatchSize = dupsOKBatchSize;
   }

   public synchronized int getTransactionBatchSize()
   {
      return transactionBatchSize;
   }

   public synchronized void setTransactionBatchSize(final int transactionBatchSize)
   {
      checkWrite();
      this.transactionBatchSize = transactionBatchSize;
   }

   public synchronized long getClientFailureCheckPeriod()
   {
      return sessionFactory.getClientFailureCheckPeriod();
   }

   public synchronized void setClientFailureCheckPeriod(final long clientFailureCheckPeriod)
   {
      checkWrite();
      sessionFactory.setClientFailureCheckPeriod(clientFailureCheckPeriod);
   }

   public synchronized long getConnectionTTL()
   {
      return sessionFactory.getConnectionTTL();
   }

   public synchronized void setConnectionTTL(final long connectionTTL)
   {
      checkWrite();
      sessionFactory.setConnectionTTL(connectionTTL);
   }

   public synchronized long getCallTimeout()
   {
      return sessionFactory.getCallTimeout();
   }

   public synchronized void setCallTimeout(final long callTimeout)
   {
      checkWrite();
      sessionFactory.setCallTimeout(callTimeout);
   }

   public synchronized int getConsumerWindowSize()
   {
      return sessionFactory.getConsumerWindowSize();
   }

   public synchronized void setConsumerWindowSize(final int consumerWindowSize)
   {
      checkWrite();
      sessionFactory.setConsumerWindowSize(consumerWindowSize);
   }

   public synchronized int getConsumerMaxRate()
   {
      return sessionFactory.getConsumerMaxRate();
   }

   public synchronized void setConsumerMaxRate(final int consumerMaxRate)
   {
      checkWrite();
      sessionFactory.setConsumerMaxRate(consumerMaxRate);
   }

   public synchronized int getConfirmationWindowSize()
   {
      return sessionFactory.getConfirmationWindowSize();
   }

   public synchronized void setConfirmationWindowSize(final int confirmationWindowSize)
   {
      checkWrite();
      sessionFactory.setConfirmationWindowSize(confirmationWindowSize);
   }

   public synchronized int getProducerMaxRate()
   {
      return sessionFactory.getProducerMaxRate();
   }

   public synchronized void setProducerMaxRate(final int producerMaxRate)
   {
      checkWrite();
      sessionFactory.setProducerMaxRate(producerMaxRate);
   }

   public synchronized int getProducerWindowSize()
   {
      return sessionFactory.getProducerWindowSize();
   }

   public synchronized void setProducerWindowSize(final int producerWindowSize)
   {
      checkWrite();
      sessionFactory.setProducerWindowSize(producerWindowSize);
   }

   /**
    * @param cacheLargeMessagesClient
    */
   public synchronized void setCacheLargeMessagesClient(final boolean cacheLargeMessagesClient)
   {
      checkWrite();
      sessionFactory.setCacheLargeMessagesClient(cacheLargeMessagesClient);
   }

   public synchronized boolean isCacheLargeMessagesClient()
   {
      return sessionFactory.isCacheLargeMessagesClient();
   }

   public synchronized int getMinLargeMessageSize()
   {
      return sessionFactory.getMinLargeMessageSize();
   }

   public synchronized void setMinLargeMessageSize(final int minLargeMessageSize)
   {
      checkWrite();
      sessionFactory.setMinLargeMessageSize(minLargeMessageSize);
   }

   public synchronized boolean isBlockOnAcknowledge()
   {
      return sessionFactory.isBlockOnAcknowledge();
   }

   public synchronized void setBlockOnAcknowledge(final boolean blockOnAcknowledge)
   {
      checkWrite();
      sessionFactory.setBlockOnAcknowledge(blockOnAcknowledge);
   }

   public synchronized boolean isBlockOnNonDurableSend()
   {
      return sessionFactory.isBlockOnNonDurableSend();
   }

   public synchronized void setBlockOnNonDurableSend(final boolean blockOnNonDurableSend)
   {
      checkWrite();
      sessionFactory.setBlockOnNonDurableSend(blockOnNonDurableSend);
   }

   public synchronized boolean isBlockOnDurableSend()
   {
      return sessionFactory.isBlockOnDurableSend();
   }

   public synchronized void setBlockOnDurableSend(final boolean blockOnDurableSend)
   {
      checkWrite();
      sessionFactory.setBlockOnDurableSend(blockOnDurableSend);
   }

   public synchronized boolean isAutoGroup()
   {
      return sessionFactory.isAutoGroup();
   }

   public synchronized void setAutoGroup(final boolean autoGroup)
   {
      checkWrite();
      sessionFactory.setAutoGroup(autoGroup);
   }

   public synchronized boolean isPreAcknowledge()
   {
      return sessionFactory.isPreAcknowledge();
   }

   public synchronized void setPreAcknowledge(final boolean preAcknowledge)
   {
      checkWrite();
      sessionFactory.setPreAcknowledge(preAcknowledge);
   }

   public synchronized long getRetryInterval()
   {
      return sessionFactory.getRetryInterval();
   }

   public synchronized void setRetryInterval(final long retryInterval)
   {
      checkWrite();
      sessionFactory.setRetryInterval(retryInterval);
   }

   public synchronized long getMaxRetryInterval()
   {
      return sessionFactory.getMaxRetryInterval();
   }

   public synchronized void setMaxRetryInterval(final long retryInterval)
   {
      checkWrite();
      sessionFactory.setMaxRetryInterval(retryInterval);
   }

   public synchronized double getRetryIntervalMultiplier()
   {
      return sessionFactory.getRetryIntervalMultiplier();
   }

   public synchronized void setRetryIntervalMultiplier(final double retryIntervalMultiplier)
   {
      checkWrite();
      sessionFactory.setRetryIntervalMultiplier(retryIntervalMultiplier);
   }

   public synchronized int getReconnectAttempts()
   {
      return sessionFactory.getReconnectAttempts();
   }

   public synchronized void setReconnectAttempts(final int reconnectAttempts)
   {
      checkWrite();
      sessionFactory.setReconnectAttempts(reconnectAttempts);
   }
   
   public synchronized boolean isFailoverOnInitialConnection()
   {
      return sessionFactory.isFailoverOnInitialConnection();
   }

   public synchronized void setFailoverOnInitialConnection(final boolean failover)
   {
      checkWrite();
      sessionFactory.setFailoverOnInitialConnection(failover);
   }

   public synchronized boolean isFailoverOnServerShutdown()
   {
      return sessionFactory.isFailoverOnServerShutdown();
   }

   public synchronized void setFailoverOnServerShutdown(final boolean failoverOnServerShutdown)
   {
      checkWrite();
      sessionFactory.setFailoverOnServerShutdown(failoverOnServerShutdown);
   }

   public synchronized boolean isUseGlobalPools()
   {
      return sessionFactory.isUseGlobalPools();
   }

   public synchronized void setUseGlobalPools(final boolean useGlobalPools)
   {
      checkWrite();
      sessionFactory.setUseGlobalPools(useGlobalPools);
   }

   public synchronized int getScheduledThreadPoolMaxSize()
   {
      return sessionFactory.getScheduledThreadPoolMaxSize();
   }

   public synchronized void setScheduledThreadPoolMaxSize(final int scheduledThreadPoolMaxSize)
   {
      checkWrite();
      sessionFactory.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
   }

   public synchronized int getThreadPoolMaxSize()
   {
      return sessionFactory.getThreadPoolMaxSize();
   }

   public synchronized void setThreadPoolMaxSize(final int threadPoolMaxSize)
   {
      checkWrite();
      sessionFactory.setThreadPoolMaxSize(threadPoolMaxSize);
   }

   public synchronized int getInitialMessagePacketSize()
   {
      return sessionFactory.getInitialMessagePacketSize();
   }

   public synchronized void setInitialMessagePacketSize(final int size)
   {
      checkWrite();
      sessionFactory.setInitialMessagePacketSize(size);
   }

   public ClientSessionFactory getCoreFactory()
   {
      return sessionFactory;
   }

   public void setGroupID(final String groupID)
   {
      sessionFactory.setGroupID(groupID);
   }

   public String getGroupID()
   {
      return sessionFactory.getGroupID();
   }
   
   public void close()
   {
      sessionFactory.close();
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected synchronized HornetQConnection createConnectionInternal(final String username,
                                                                     final String password,
                                                                     final boolean isXA,
                                                                     final int type) throws JMSException
   {
      readOnly = true;

      // Note that each JMS connection gets it's own copy of the connection factory
      // This means there is one underlying remoting connection per jms connection (if not load balanced)
      ClientSessionFactory factory = sessionFactory.copy();

      HornetQConnection connection = null;
      
      if (isXA)
      {
         if (type == HornetQConnection.TYPE_GENERIC_CONNECTION)
         {
            connection = new HornetQXAConnection(username,
                                                password,
                                                type,
                                                clientID,
                                                dupsOKBatchSize,
                                                transactionBatchSize,
                                                factory);
         }
         else if (type == HornetQConnection.TYPE_QUEUE_CONNECTION)
         {
            connection = new HornetQXAQueueConnection(username,
                                                      password,
                                                      type,
                                                      clientID,
                                                      dupsOKBatchSize,
                                                      transactionBatchSize,
                                                      factory);
         }
         else if (type == HornetQConnection.TYPE_TOPIC_CONNECTION)
         {
            connection = new HornetQXATopicConnection(username,
                                                      password,
                                                      type,
                                                      clientID,
                                                      dupsOKBatchSize,
                                                      transactionBatchSize,
                                                      factory);
         }
      }
      else
      {
         if (type == HornetQConnection.TYPE_GENERIC_CONNECTION)
         {
            connection = new HornetQConnection(username,
                                               password,
                                               type,
                                               clientID,
                                               dupsOKBatchSize,
                                               transactionBatchSize,
                                               factory);
         }
         else if (type == HornetQConnection.TYPE_QUEUE_CONNECTION)
         {
            connection = new HornetQQueueConnection(username,
                                                    password,
                                                    type,
                                                    clientID,
                                                    dupsOKBatchSize,
                                                    transactionBatchSize,
                                                    factory);
         }
         else if (type == HornetQConnection.TYPE_TOPIC_CONNECTION)
         {
            connection = new HornetQTopicConnection(username,
                                                    password,
                                                    type,
                                                    clientID,
                                                    dupsOKBatchSize,
                                                    transactionBatchSize,
                                                    factory);
         }         
      }

      try
      {
         connection.authorize();
      }
      catch (JMSException e)
      {
         try
         {
            connection.close();
         }
         catch (JMSException me)
         {
         }
         throw e;
      }

      return connection;
   }

   // Private --------------------------------------------------------------------------------------

   private void checkWrite()
   {
      if (readOnly)
      {
         throw new IllegalStateException("Cannot set attribute on HornetQConnectionFactory after it has been used");
      }
   }

   // Inner classes --------------------------------------------------------------------------------

}
