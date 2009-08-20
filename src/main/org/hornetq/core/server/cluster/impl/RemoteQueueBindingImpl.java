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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hornetq.core.filter.Filter;
import org.hornetq.core.filter.impl.FilterImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.message.impl.MessageImpl;
import org.hornetq.core.postoffice.BindingType;
import org.hornetq.core.server.Bindable;
import org.hornetq.core.server.Queue;
import org.hornetq.core.server.ServerMessage;
import org.hornetq.core.server.cluster.RemoteQueueBinding;
import org.hornetq.utils.SimpleString;

/**
 * A RemoteQueueBindingImpl
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 21 Jan 2009 18:55:22
 *
 *
 */
public class RemoteQueueBindingImpl implements RemoteQueueBinding
{
   private static final Logger log = Logger.getLogger(RemoteQueueBindingImpl.class);

   private final SimpleString address;

   private final Queue storeAndForwardQueue;

   private final SimpleString uniqueName;

   private final SimpleString routingName;
   
   private final int remoteQueueID;

   private final Filter queueFilter;

   private final Set<Filter> filters = new HashSet<Filter>();

   private final Map<SimpleString, Integer> filterCounts = new HashMap<SimpleString, Integer>();

   private int consumerCount;

   private final SimpleString idsHeaderName;
   
   private int id;
      
   private final int distance;
   
   public RemoteQueueBindingImpl(final SimpleString address,
                                 final SimpleString uniqueName,
                                 final SimpleString routingName,
                                 final int remoteQueueID,
                                 final SimpleString filterString,
                                 final Queue storeAndForwardQueue,                     
                                 final SimpleString bridgeName,
                                 final int distance) throws Exception
   {
      this.address = address;

      this.storeAndForwardQueue = storeAndForwardQueue;

      this.uniqueName = uniqueName;

      this.routingName = routingName;
      
      this.remoteQueueID = remoteQueueID;

      if (filterString != null)
      {
         queueFilter = new FilterImpl(filterString);
      }
      else
      {
         queueFilter = null;
      }
      
      this.idsHeaderName = MessageImpl.HDR_ROUTE_TO_IDS.concat(bridgeName);
      
      this.distance = distance;
   }
   
   public int getID()
   {
      return id;
   }
   
   public void setID(final int id)
   {
      this.id = id;
   }
   
   public SimpleString getAddress()
   {
      return address;
   }

   public Bindable getBindable()
   {
      return storeAndForwardQueue;
   }
   
   public Queue getQueue()
   {
      return storeAndForwardQueue;
   }

   public SimpleString getRoutingName()
   {
      return routingName;
   }

   public SimpleString getUniqueName()
   {
      return uniqueName;
   }
   
   public SimpleString getClusterName()
   {
      return uniqueName;
   }

   public boolean isExclusive()
   {
      return false;
   }
   
   public BindingType getType()
   {
      return BindingType.REMOTE_QUEUE;
   }
   
   public Filter getFilter()
   {
      return queueFilter;
   }
   
   public int getDistance()
   {
      return distance;
   }

   public synchronized boolean isHighAcceptPriority(final ServerMessage message)
   {      
      if (consumerCount == 0)
      {
         return false;
      }

      if (filters.isEmpty())
      {
         return true;
      }
      else
      {
         for (Filter filter : filters)
         {
            if (filter.match(message))
            {
               return true;
            }
         }
      }

      return false;
   }
   
   public void willRoute(final ServerMessage message)
   {               
      //We add a header with the name of the queue, holding a list of the transient ids of the queues to route to
      
      //TODO - this can be optimised
      
      byte[] ids = (byte[])message.getProperty(idsHeaderName);
      
      if (ids == null)
      {
         ids = new byte[4];
      }
      else
      {
         byte[] newIds = new byte[ids.length + 4];
         
         System.arraycopy(ids, 0, newIds, 4, ids.length);
                          
         ids = newIds;
      }
      
      ByteBuffer buff = ByteBuffer.wrap(ids);
      
      buff.putInt(remoteQueueID);
      
      message.putBytesProperty(idsHeaderName, ids); 
   }

   public synchronized void addConsumer(final SimpleString filterString) throws Exception
   {
      if (filterString != null)
      {
         // There can actually be many consumers on the same queue with the same filter, so we need to maintain a ref
         // count

         Integer i = filterCounts.get(filterString);

         if (i == null)
         {
            filterCounts.put(filterString, 0);

            filters.add(new FilterImpl(filterString));
         }
         else
         {
            filterCounts.put(filterString, i + 1);
         }
      }

      consumerCount++;
   }

   public synchronized void removeConsumer(final SimpleString filterString) throws Exception
   {
      if (filterString != null)
      {
         Integer i = filterCounts.get(filterString);

         if (i != null)
         {
            int ii = i - 1;

            if (ii == 0)
            {
               filterCounts.remove(filterString);

               filters.remove(filterString);
            }
            else
            {
               filterCounts.put(filterString, ii);
            }
         }
      }

      consumerCount--;
   }
   
   public synchronized int consumerCount()
   {
      return consumerCount;
   }

}
