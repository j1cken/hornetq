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

package org.hornetq.core.server.cluster.impl;

import static org.hornetq.api.core.management.NotificationType.CONSUMER_CLOSED;
import static org.hornetq.api.core.management.NotificationType.CONSUMER_CREATED;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledExecutorService;

import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.Pair;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.management.ManagementHelper;
import org.hornetq.api.core.management.NotificationType;
import org.hornetq.core.client.impl.ServerLocatorInternal;
import org.hornetq.core.client.impl.TopologyMember;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.postoffice.Binding;
import org.hornetq.core.postoffice.Bindings;
import org.hornetq.core.postoffice.PostOffice;
import org.hornetq.core.postoffice.impl.PostOfficeImpl;
import org.hornetq.core.server.HornetQComponent;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.Queue;
import org.hornetq.core.server.cluster.Bridge;
import org.hornetq.core.server.cluster.ClusterConnection;
import org.hornetq.core.server.cluster.MessageFlowRecord;
import org.hornetq.core.server.cluster.RemoteQueueBinding;
import org.hornetq.core.server.group.impl.Proposal;
import org.hornetq.core.server.group.impl.Response;
import org.hornetq.core.server.management.ManagementService;
import org.hornetq.core.server.management.Notification;
import org.hornetq.utils.ExecutorFactory;
import org.hornetq.utils.TypedProperties;
import org.hornetq.utils.UUID;

/**
 * 
 * A ClusterConnectionImpl
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 21 Jan 2009 14:43:05
 *
 *
 */
public class ClusterConnectionImpl implements ClusterConnection
{
   private static final Logger log = Logger.getLogger(ClusterConnectionImpl.class);

   private final org.hornetq.utils.ExecutorFactory executorFactory;

   private final HornetQServer server;

   private final PostOffice postOffice;

   private final ManagementService managementService;

   private final SimpleString name;

   private final SimpleString address;

   private final long retryInterval;

   private final boolean useDuplicateDetection;

   private final boolean routeWhenNoConsumers;

   private final Map<String, MessageFlowRecord> records = new HashMap<String, MessageFlowRecord>();

   private final List<TransportConfiguration> conectorssss = new ArrayList<TransportConfiguration>();

   private final ScheduledExecutorService scheduledExecutor;

   private final int maxHops;

   private final UUID nodeUUID;

   private boolean backup;

   private volatile boolean started;

   private final String clusterUser;

   private final String clusterPassword;

   private final ClusterConnector clusterConnector;

   private ServerLocatorInternal serverLocator;
   
   private final TransportConfiguration connector;

   private final boolean allowDirectConnectionsOnly;

   private final Set<TransportConfiguration> allowableConnections = new HashSet<TransportConfiguration>();
   
   public ClusterConnectionImpl(final TransportConfiguration[] tcConfigs,
                                final TransportConfiguration connector,
                                final SimpleString name,
                                final SimpleString address,
                                final long retryInterval,
                                final boolean useDuplicateDetection,
                                final boolean routeWhenNoConsumers,
                                final int confirmationWindowSize,
                                final ExecutorFactory executorFactory,
                                final HornetQServer server,
                                final PostOffice postOffice,
                                final ManagementService managementService,
                                final ScheduledExecutorService scheduledExecutor,
                                final int maxHops,
                                final UUID nodeUUID,
                                final boolean backup,
                                final String clusterUser,
                                final String clusterPassword,
                                final boolean allowDirectConnectionsOnly) throws Exception
   {

      if (nodeUUID == null)
      {
         throw new IllegalArgumentException("node id is null");
      }

      this.nodeUUID = nodeUUID;

      this.connector = connector;

      this.name = name;

      this.address = address;

      this.retryInterval = retryInterval;

      this.useDuplicateDetection = useDuplicateDetection;

      this.routeWhenNoConsumers = routeWhenNoConsumers;

      this.executorFactory = executorFactory;

      this.server = server;

      this.postOffice = postOffice;

      this.managementService = managementService;

      this.scheduledExecutor = scheduledExecutor;

      this.maxHops = maxHops;

      this.backup = backup;

      this.clusterUser = clusterUser;

      this.clusterPassword = clusterPassword;

      this.allowDirectConnectionsOnly = allowDirectConnectionsOnly;

      clusterConnector = new StaticClusterConnector(tcConfigs);

      if (tcConfigs != null && tcConfigs.length > 0)
      {
         // a cluster connection will connect to other nodes only if they are directly connected
         // through a static list of connectors or broadcasting using UDP.
         if(allowDirectConnectionsOnly)
         {
            allowableConnections.addAll(Arrays.asList(tcConfigs));
         }
      }

   }

   public ClusterConnectionImpl(DiscoveryGroupConfiguration dg,
                                final TransportConfiguration connector,
                                final SimpleString name,
                                final SimpleString address,
                                final long retryInterval,
                                final boolean useDuplicateDetection,
                                final boolean routeWhenNoConsumers,
                                final int confirmationWindowSize,
                                final ExecutorFactory executorFactory,
                                final HornetQServer server,
                                final PostOffice postOffice,
                                final ManagementService managementService,
                                final ScheduledExecutorService scheduledExecutor,
                                final int maxHops,
                                final UUID nodeUUID,
                                final boolean backup,
                                final String clusterUser,
                                final String clusterPassword,
                                final boolean allowDirectConnectionsOnly) throws Exception
   {

      if (nodeUUID == null)
      {
         throw new IllegalArgumentException("node id is null");
      }

      this.nodeUUID = nodeUUID;

      this.connector = connector;

      this.name = name;

      this.address = address;

      this.retryInterval = retryInterval;

      this.useDuplicateDetection = useDuplicateDetection;

      this.routeWhenNoConsumers = routeWhenNoConsumers;

      this.executorFactory = executorFactory;

      this.server = server;

      this.postOffice = postOffice;

      this.managementService = managementService;

      this.scheduledExecutor = scheduledExecutor;

      this.maxHops = maxHops;

      this.backup = backup;

      this.clusterUser = clusterUser;

      this.clusterPassword = clusterPassword;

      this.allowDirectConnectionsOnly = allowDirectConnectionsOnly;

      clusterConnector = new DiscoveryClusterConnector(dg);
   }

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }

      started = true;
      
      if(!backup)
      {
         activate();
      }


   }

   public void stop() throws Exception
   {
      if (!started)
      {
         return;
      }

      if (serverLocator != null)
      {
         serverLocator.removeClusterTopologyListener(this);
      }

      synchronized (this)
      {
         for (MessageFlowRecord record : records.values())
         {
            try
            {
               record.close();
            }
            catch (Exception ignore)
            {
            }
         }

         if (managementService != null)
         {
            TypedProperties props = new TypedProperties();
            props.putSimpleStringProperty(new SimpleString("name"), name);
            Notification notification = new Notification(nodeUUID.toString(),
                                                         NotificationType.CLUSTER_CONNECTION_STOPPED,
                                                         props);
            managementService.sendNotification(notification);
         }

         if(serverLocator != null)
         {
            serverLocator.close();
            serverLocator = null;
         }

         started = false;
      }
   }

   public boolean isStarted()
   {
      return started;
   }

   public SimpleString getName()
   {
      return name;
   }

   public String getNodeID()
   {
      return nodeUUID.toString();
   }

   public synchronized Map<String, String> getNodes()
   {
      Map<String, String> nodes = new HashMap<String, String>();
      for (Entry<String, MessageFlowRecord> record : records.entrySet())
      {
         if (record.getValue().getBridge().getForwardingConnection() != null)
         {
            nodes.put(record.getKey(), record.getValue().getBridge().getForwardingConnection().getRemoteAddress());
         }
      }
      return nodes;
   }

   public synchronized void activate() throws Exception
   {
      if (!started)
      {
         return;
      }

      backup = false;

      serverLocator = clusterConnector.createServerLocator();


      if (serverLocator != null)
      {
         serverLocator.setNodeID(nodeUUID.toString());

         serverLocator.setReconnectAttempts(-1);

         serverLocator.setClusterConnection(true);
         serverLocator.setClusterTransportConfiguration(connector);
         serverLocator.setBackup(server.getConfiguration().isBackup());
         serverLocator.setInitialConnectAttempts(-1);

         if(retryInterval > 0)
         {
            this.serverLocator.setRetryInterval(retryInterval);
         }

         serverLocator.addClusterTopologyListener(this);

         serverLocator.start(server.getExecutorFactory().getExecutor());
      }

      if (managementService != null)
      {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), name);
         Notification notification = new Notification(nodeUUID.toString(),
                                                      NotificationType.CLUSTER_CONNECTION_STARTED,
                                                      props);
         managementService.sendNotification(notification);
      }
   }
   
   public TransportConfiguration getConnector()
   {
      return connector;
   }

   // ClusterTopologyListener implementation ------------------------------------------------------------------

   public synchronized void nodeDown(final String nodeID)
   {
      if (nodeID.equals(nodeUUID.toString()))
      {
         return;
      }
      
      //Remove the flow record for that node
      
      MessageFlowRecord record = records.get(nodeID);

      if (record != null)
      {
         try
         {
            record.reset();
         }
         catch (Exception e)
         {
            log.error("Failed to close flow record", e);
         }
      }
      
      server.getClusterManager().notifyNodeDown(nodeID);
   }

   public synchronized void nodeUP(final String nodeID,
                                   final Pair<TransportConfiguration, TransportConfiguration> connectorPair,
                                   final boolean last)
   {
      // discard notifications about ourselves unless its from our backup

      if (nodeID.equals(nodeUUID.toString()))
      {
         if(connectorPair.b != null)
         {
            server.getClusterManager().notifyNodeUp(nodeID, connectorPair, last);
         }
         return;
      }

      // we propagate the node notifications to all cluster topology listeners
      server.getClusterManager().notifyNodeUp(nodeID, connectorPair, last);

      // if the node is more than 1 hop away, we do not create a bridge for direct cluster connection
      if (allowDirectConnectionsOnly && !allowableConnections.contains(connectorPair.a))
      {
         return;
      }

      // FIXME required to prevent cluster connections w/o discovery group 
      // and empty static connectors to create bridges... ulgy!
      if (serverLocator == null)
      {
         return;
      }
      /*we dont create bridges to backups*/
      if(connectorPair.a == null)
      {
         return;
      }

      Collection<TopologyMember> topologyMembers = serverLocator.getTopology().getMembers();
      for (TopologyMember topologyMember : topologyMembers)
      {
         if(topologyMember.getConnector().a != null && !conectorssss.contains(topologyMember.getConnector().a))
         {
            if(!topologyMember.getConnector().a.equals(connector) && !topologyMember.getConnector().a.equals(connectorPair.a))
            {
               System.out.println("ClusterConnectionImpl.nodeUP");
            }
         }
      }

      try
      {
         MessageFlowRecord record = records.get(nodeID);

         if (record == null)
         {
            // New node - create a new flow record

            final SimpleString queueName = new SimpleString("sf." + name + "." + nodeID);

            Binding queueBinding = postOffice.getBinding(queueName);

            Queue queue;

            if (queueBinding != null)
            {
               queue = (Queue)queueBinding.getBindable();
            }
            else
            {
               // Add binding in storage so the queue will get reloaded on startup and we can find it - it's never
               // actually routed to at that address though
               queue = server.createQueue(queueName, queueName, null, true, false);
            }

            createNewRecord(nodeID, connectorPair.a, queueName, queue, true);
            conectorssss.add(connectorPair.a);
         }
         else
         {
            // FIXME apple and orange comparison. I don't understand it...
            //if (!connectorPair.a.equals(record.getBridge().getForwardingConnection().getTransportConnection()))
            // {
            //   // New live node - close it and recreate it - TODO - CAN THIS EVER HAPPEN?
            //}
         }
      }
      catch (Exception e)
      {
         log.error("Failed to update topology", e);
      }
   }
   
   private void createNewRecord(final String nodeID,
                                final TransportConfiguration connector,
                                final SimpleString queueName,
                                final Queue queue,
                                final boolean start) throws Exception
   {
      MessageFlowRecordImpl record = new MessageFlowRecordImpl(queue);

      Bridge bridge = new ClusterConnectionBridge(serverLocator,
                                                  nodeUUID,
                                                  nodeID,
                                                  queueName,
                                                  queue,
                                                  executorFactory.getExecutor(),
                                                  null,
                                                  null,
                                                  scheduledExecutor,
                                                  null,
                                                  useDuplicateDetection,
                                                  clusterUser,
                                                  clusterPassword,
                                                  !backup,
                                                  server.getStorageManager(),
                                                  managementService.getManagementAddress(),
                                                  managementService.getManagementNotificationAddress(),
                                                  record,
                                                  connector);

      record.setBridge(bridge);

      records.put(nodeID, record);

      if (start)
      {
         bridge.start();
      }
   }

   // Inner classes -----------------------------------------------------------------------------------

   private class MessageFlowRecordImpl implements MessageFlowRecord
   {
      private Bridge bridge;

      private final Queue queue;

      private final Map<SimpleString, RemoteQueueBinding> bindings = new HashMap<SimpleString, RemoteQueueBinding>();

      private volatile boolean firstReset = false;

      public MessageFlowRecordImpl(final Queue queue)
      {
         this.queue = queue;
      }

      public String getAddress()
      {
         return address.toString();
      }

      public int getMaxHops()
      {
         return maxHops;
      }

      public void close() throws Exception
      {
         bridge.stop();

         clearBindings();
      }

      public void reset() throws Exception
      {
         clearBindings();
      }

      public void setBridge(final Bridge bridge)
      {
         this.bridge = bridge;
      }

      public Bridge getBridge()
      {
         return bridge;
      }

      public synchronized void onMessage(final ClientMessage message)
      {
         try
         {
            // Reset the bindings
            if (message.containsProperty(PostOfficeImpl.HDR_RESET_QUEUE_DATA))
            {
               clearBindings();

               firstReset = true;

               return;
            }

            if (!firstReset)
            {
               return;
            }

            // TODO - optimised this by just passing int in header - but filter needs to be extended to support IN with
            // a list of integers
            SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_NOTIFICATION_TYPE);

            NotificationType ntype = NotificationType.valueOf(type.toString());

            switch (ntype)
            {
               case BINDING_ADDED:
               {
                  doBindingAdded(message);

                  break;
               }
               case BINDING_REMOVED:
               {
                  doBindingRemoved(message);

                  break;
               }
               case CONSUMER_CREATED:
               {
                  doConsumerCreated(message);

                  break;
               }
               case CONSUMER_CLOSED:
               {
                  doConsumerClosed(message);

                  break;
               }
               case PROPOSAL:
               {
                  doProposalReceived(message);

                  break;
               }
               case PROPOSAL_RESPONSE:
               {
                  doProposalResponseReceived(message);

                  break;
               }
               default:
               {
                  throw new IllegalArgumentException("Invalid type " + ntype);
               }
            }
         }
         catch (Exception e)
         {
            ClusterConnectionImpl.log.error("Failed to handle message", e);
         }
      }

      /*
      * Inform the grouping handler of a proposal
      * */
      private synchronized void doProposalReceived(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID))
         {
            throw new IllegalStateException("proposal type is null");
         }

         SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID);

         SimpleString val = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE);

         Integer hops = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         Response response = server.getGroupingHandler().receive(new Proposal(type, val), hops + 1);

         if (response != null)
         {
            server.getGroupingHandler().send(response, 0);
         }
      }

      /*
      * Inform the grouping handler of a response from a proposal
      *
      * */
      private synchronized void doProposalResponseReceived(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID))
         {
            throw new IllegalStateException("proposal type is null");
         }

         SimpleString type = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID);
         SimpleString val = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE);
         SimpleString alt = message.getSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_ALT_VALUE);
         Integer hops = message.getIntProperty(ManagementHelper.HDR_DISTANCE);
         Response response = new Response(type, val, alt);
         server.getGroupingHandler().proposed(response);
         server.getGroupingHandler().send(response, hops + 1);
      }

      private synchronized void clearBindings() throws Exception
      {
         for (RemoteQueueBinding binding : new HashSet<RemoteQueueBinding>(bindings.values()))
         {
            removeBinding(binding.getClusterName());
         }
      }

      private synchronized void doBindingAdded(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE))
         {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_ADDRESS))
         {
            throw new IllegalStateException("queueAddress is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME))
         {
            throw new IllegalStateException("clusterName is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_ROUTING_NAME))
         {
            throw new IllegalStateException("routingName is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_BINDING_ID))
         {
            throw new IllegalStateException("queueID is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString queueAddress = message.getSimpleStringProperty(ManagementHelper.HDR_ADDRESS);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         SimpleString routingName = message.getSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         Long queueID = message.getLongProperty(ManagementHelper.HDR_BINDING_ID);

         RemoteQueueBinding binding = new RemoteQueueBindingImpl(server.getStorageManager().generateUniqueID(),
                                                                 queueAddress,
                                                                 clusterName,
                                                                 routingName,
                                                                 queueID,
                                                                 filterString,
                                                                 queue,
                                                                 bridge.getName(),
                                                                 distance + 1);

         if (postOffice.getBinding(clusterName) != null)
         {
            // Sanity check - this means the binding has already been added via another bridge, probably max
            // hops is too high
            // or there are multiple cluster connections for the same address

            ClusterConnectionImpl.log.warn("Remote queue binding " + clusterName +
                                           " has already been bound in the post office. Most likely cause for this is you have a loop " +
                                           "in your cluster due to cluster max-hops being too large or you have multiple cluster connections to the same nodes using overlapping addresses");

            return;
         }

         bindings.put(clusterName, binding);

         try
         {
            postOffice.addBinding(binding);
         }
         catch (Exception ignore)
         {
         }

         Bindings theBindings = postOffice.getBindingsForAddress(queueAddress);

         theBindings.setRouteWhenNoConsumers(routeWhenNoConsumers);

      }

      private void doBindingRemoved(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME))
         {
            throw new IllegalStateException("clusterName is null");
         }

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         removeBinding(clusterName);
      }

      private synchronized void removeBinding(final SimpleString clusterName) throws Exception
      {
         RemoteQueueBinding binding = bindings.remove(clusterName);

         if (binding == null)
         {
            throw new IllegalStateException("Cannot find binding for queue " + clusterName);
         }

         postOffice.removeBinding(binding.getUniqueName());
      }

      private synchronized void doConsumerCreated(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE))
         {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME))
         {
            throw new IllegalStateException("clusterName is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         message.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         RemoteQueueBinding binding = bindings.get(clusterName);

         if (binding == null)
         {
            throw new IllegalStateException("Cannot find binding for " + clusterName);
         }

         binding.addConsumer(filterString);

         // Need to propagate the consumer add
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, clusterName);

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         Queue theQueue = (Queue)binding.getBindable();

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, theQueue.getConsumerCount());

         if (filterString != null)
         {
            props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filterString);
         }

         Notification notification = new Notification(null, CONSUMER_CREATED, props);

         managementService.sendNotification(notification);
      }

      private synchronized void doConsumerClosed(final ClientMessage message) throws Exception
      {
         if (!message.containsProperty(ManagementHelper.HDR_DISTANCE))
         {
            throw new IllegalStateException("distance is null");
         }

         if (!message.containsProperty(ManagementHelper.HDR_CLUSTER_NAME))
         {
            throw new IllegalStateException("clusterName is null");
         }

         Integer distance = message.getIntProperty(ManagementHelper.HDR_DISTANCE);

         SimpleString clusterName = message.getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);

         message.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         SimpleString filterString = message.getSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING);

         RemoteQueueBinding binding = bindings.get(clusterName);

         if (binding == null)
         {
            throw new IllegalStateException("Cannot find binding for " + clusterName);
         }

         binding.removeConsumer(filterString);

         // Need to propagate the consumer close
         TypedProperties props = new TypedProperties();

         props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, binding.getAddress());

         props.putSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME, clusterName);

         props.putSimpleStringProperty(ManagementHelper.HDR_ROUTING_NAME, binding.getRoutingName());

         props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance + 1);

         Queue theQueue = (Queue)binding.getBindable();

         props.putIntProperty(ManagementHelper.HDR_CONSUMER_COUNT, theQueue.getConsumerCount());

         if (filterString != null)
         {
            props.putSimpleStringProperty(ManagementHelper.HDR_FILTERSTRING, filterString);
         }
         Notification notification = new Notification(null, CONSUMER_CLOSED, props);

         managementService.sendNotification(notification);
      }

   }

   public void handleReplicatedAddBinding(final SimpleString address,
                                          final SimpleString uniqueName,
                                          final SimpleString routingName,
                                          final long queueID,
                                          final SimpleString filterString,
                                          final SimpleString queueName,
                                          final int distance) throws Exception
   {
      Binding queueBinding = postOffice.getBinding(queueName);

      if (queueBinding == null)
      {
         throw new IllegalStateException("Cannot find s & f queue " + queueName);
      }

      Queue queue = (Queue)queueBinding.getBindable();

      RemoteQueueBinding binding = new RemoteQueueBindingImpl(server.getStorageManager().generateUniqueID(),
                                                              address,
                                                              uniqueName,
                                                              routingName,
                                                              queueID,
                                                              filterString,
                                                              queue,
                                                              queueName,
                                                              distance);

      if (postOffice.getBinding(uniqueName) != null)
      {
         ClusterConnectionImpl.log.warn("Remoting queue binding " + uniqueName +
                                        " has already been bound in the post office. Most likely cause for this is you have a loop " +
                                        "in your cluster due to cluster max-hops being too large or you have multiple cluster connections to the same nodes using overlapping addresses");

         return;
      }

      postOffice.addBinding(binding);

      Bindings theBindings = postOffice.getBindingsForAddress(address);

      theBindings.setRouteWhenNoConsumers(routeWhenNoConsumers);
   }

   // for testing only
   public Map<String, MessageFlowRecord> getRecords()
   {
      return records;
   }
   
   public String description()
   {
      String out = name + " connected to\n";
      for (Entry<String, MessageFlowRecord> messageFlow : records.entrySet())
      {
         String nodeID = messageFlow.getKey();
         Bridge bridge = messageFlow.getValue().getBridge();
         
         out += "\t" + nodeID + " -- " + bridge.isStarted() + "\n";
      }
      
      return out;
   }

   interface ClusterConnector
   {
      ServerLocatorInternal createServerLocator();
   }

   private class StaticClusterConnector implements ClusterConnector
   {
      private final TransportConfiguration[] tcConfigs;

      public StaticClusterConnector(TransportConfiguration[] tcConfigs)
      {
         this.tcConfigs = tcConfigs;
      }

      public ServerLocatorInternal createServerLocator()
      {
         if(tcConfigs != null && tcConfigs.length > 0)
         {
            return (ServerLocatorInternal) HornetQClient.createServerLocatorWithHA(tcConfigs);
         }
         else
         {
            return null;
         }
      }
   }

   private class DiscoveryClusterConnector implements ClusterConnector
   {
      private final DiscoveryGroupConfiguration dg;

      public DiscoveryClusterConnector(DiscoveryGroupConfiguration dg)
      {
         this.dg = dg;
      }

      public ServerLocatorInternal createServerLocator()
      {
         return (ServerLocatorInternal) HornetQClient.createServerLocatorWithHA(dg);
      }
   }
}
