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
package org.jboss.messaging.core;


import org.jboss.logging.Logger;
import org.jboss.messaging.core.refqueue.BasicPrioritizedDeque;
import org.jboss.messaging.core.refqueue.BasicSynchronizedPrioritizedDeque;
import org.jboss.messaging.core.refqueue.PrioritizedDeque;
import org.jboss.messaging.core.tx.Transaction;
import org.jboss.messaging.core.tx.TxCallback;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentHashMap;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;


/**
 * In-memory (unrecoverable in case of failure) channel state implementation.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class NonRecoverableState implements State
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(NonRecoverableState.class);

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   protected PrioritizedDeque messageRefs;
   
   protected Map deliveries;

   protected Map txToAddReferenceCallbacks;
   protected Map txToRemoveDeliveryCallbacks;

   protected Channel channel;
   protected boolean acceptReliableMessages;

   // Constructors --------------------------------------------------

   public NonRecoverableState(Channel channel, boolean acceptReliableMessages)
   {
      this.channel = channel;
      this.acceptReliableMessages = acceptReliableMessages;
      messageRefs = new BasicSynchronizedPrioritizedDeque(new BasicPrioritizedDeque(10));
      deliveries = new ConcurrentHashMap();
      txToAddReferenceCallbacks = new ConcurrentHashMap();
      txToRemoveDeliveryCallbacks = new ConcurrentHashMap();
   }

   // State implementation -----------------------------------

   public boolean isRecoverable()
   {
      return false;
   }

   public boolean acceptReliableMessages()
   {
      return acceptReliableMessages;
   }

   public void add(MessageReference ref, Transaction tx) throws Throwable
   {
      if (log.isTraceEnabled()) { log.trace("adding " + ref + (tx == null ? " non-transactionally" : " in transaction: " + tx)); }

      if (tx == null)
      {
         if (ref.isReliable() && !acceptReliableMessages)
         {
            throw new IllegalStateException("Reliable reference " + ref +
                                            " cannot be added to non-recoverable state");
         }

         messageRefs.addLast(ref, ref.getPriority());         
         if (log.isTraceEnabled()) { log.trace("added " + ref + " in memory"); }
         return;
      }

      // transactional add

      if (ref.isReliable() && !acceptReliableMessages)
      {
         // this transaction has no chance to succeed, since a reliable message cannot be
         // safely stored by a non-recoverable state, so doom the transaction
         if (log.isTraceEnabled()) { log.trace("cannot handle reliable messages, dooming the transaction"); }
         tx.setRollbackOnly();
      }
      else
      {
         //Transactional so add to post commit callback
         AddReferenceCallback callback = addAddReferenceCallback(tx);
         callback.addReference(ref);
         if (log.isTraceEnabled()) { log.trace("added transactionally " + ref + " in memory"); }
      }
   }

   public void addFirst(MessageReference ref) throws Throwable
   {
      if (log.isTraceEnabled()) { log.trace("adding " + ref + "at the top of the list in memory"); }

      if (ref.isReliable() && !acceptReliableMessages)
      {
         throw new IllegalStateException("Reliable reference " + ref +
                                         " cannot be added to non-recoverable state");
      }

      messageRefs.addFirst(ref, ref.getPriority());
      
      if (log.isTraceEnabled()) { log.trace("added " + ref + " at the top of the list in memory"); }      
   }

   public boolean remove(MessageReference ref) throws Throwable
   {
      boolean removed = messageRefs.remove(ref);
      if (removed && log.isTraceEnabled()) { log.trace("removed " + ref + " from memory"); }
      
      return removed;
   }

   public MessageReference remove() throws Throwable
   {
      MessageReference result = (MessageReference)messageRefs.removeFirst();

      if (log.isTraceEnabled()) { log.trace("removing the oldest message in memory returns " + result); }
      return result;
   }

   public void add(Delivery d) throws Throwable
   {
      if (d.getReference().isReliable() && !acceptReliableMessages)
      {
         throw new IllegalStateException("Delivery " + d + " of a reliable reference " +
                                         " cannot be added to non-recoverable state");
      }

      // Note! Adding of deliveries to the state is NEVER done in a transactional context.
      // The only things that are done in a transactional context are sending of messages
      // and removing deliveries (acking).
      
      deliveries.put(d.getReference().getMessageID(), d);
      if (log.isTraceEnabled()) { log.trace("added " + d + " to memory"); }
   }

   public boolean remove(Delivery d, Transaction tx) throws Throwable
   {
      if (tx != null)
      {
         //Transactional so add a post commit callback to remove after tx commit
         RemoveDeliveryCallback callback = addRemoveDeliveryCallback(tx);
         callback.addDelivery(d);
         if (log.isTraceEnabled()) { log.trace("added " + d + " to memory on transaction " + tx); }
         return true;
      }

      boolean removed = deliveries.remove(d.getReference().getMessageID()) != null;
      if (removed && log.isTraceEnabled()) { log.trace("removed " + d + " from memory"); }
      return removed;
   }

   public List delivering(Filter filter)
   {
      List delivering = new ArrayList();
      synchronized (deliveries)
      {
         for(Iterator i = deliveries.values().iterator(); i.hasNext(); )
         {
            Delivery d = (Delivery)i.next();
            MessageReference r = d.getReference();

            // TODO: I need to dereference the message each time I apply the filter. Refactor so the message reference will also contain JMS properties
            if (filter == null || filter.accept(r.getMessage()))
            {
               delivering.add(r);
            }
         }
      }
      if (log.isTraceEnabled()) {  log.trace("the non-recoverable state has " + delivering.size() + " messages being delivered"); }
      return delivering;
   }

   public List undelivered(Filter filter)
   {
      List undelivered = new ArrayList();
      synchronized(messageRefs)
      {
         Iterator iter = messageRefs.getAll().iterator();
         while (iter.hasNext())
         {
            MessageReference r = (MessageReference)iter.next();

            // TODO: I need to dereference the message each time I apply the filter. Refactor so the message reference will also contain JMS properties
            if (filter == null || filter.accept(r.getMessage()))
            {
               undelivered.add(r);
            }
            else
            {
               if (log.isTraceEnabled()) { log.trace(r + " NOT accepted by filter so won't add to list"); }
            }
         }
      }
      if (log.isTraceEnabled()) { log.trace("undelivered() returns a list of " + undelivered.size() + " undelivered memory messages"); }
      return undelivered;
   }

   public List browse(Filter filter)
   {
      List result = delivering(filter);
      List undel = undelivered(filter);
      
      result.addAll(undel);
      return result;
   }
   
   public void clear()
   {
      messageRefs.clear();
      messageRefs = null;
   }
   
   public void load() throws Exception
   {
      //do nothing
   }

   // Public --------------------------------------------------------
   
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------

   /**
    * Add an AddReferenceCallback, if it doesn't exist already and return its handle.
    */
   protected AddReferenceCallback addAddReferenceCallback(Transaction tx)
   {            
      //TODO we could avoid this lookup by letting the tx object store the AddReferenceCallback
      AddReferenceCallback calback = (AddReferenceCallback)txToAddReferenceCallbacks.get(tx);
      if (calback == null)
      {
         calback = new AddReferenceCallback(tx);
         txToAddReferenceCallbacks.put(tx, calback);
         tx.addCallback(calback);
      }
      return calback;
   }

   /**
    * Add a RemoveDeliveryCallback, if it doesn't exist already and return its handle.
    */
   protected RemoveDeliveryCallback addRemoveDeliveryCallback(Transaction tx)
   {
      //TODO we could avoid this lookup by letting the tx object store the RemoveDeliveryCallback
      RemoveDeliveryCallback callback = (RemoveDeliveryCallback)txToRemoveDeliveryCallbacks.get(tx);
      if (callback == null)
      {
         callback = new RemoveDeliveryCallback(tx);
         txToRemoveDeliveryCallbacks.put(tx, callback);
         tx.addCallback(callback);
      }
      return callback;
   }
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------  
   
   public class AddReferenceCallback implements TxCallback
   {
      private List refs = new ArrayList();
      
      private Transaction tx;
      
      AddReferenceCallback(Transaction tx)
      {
         this.tx = tx;
      }

      void addReference(MessageReference ref)
      {
         refs.add(ref);
      }

      public void afterCommit()
      {
         for(Iterator i = refs.iterator(); i.hasNext(); )
         {
            MessageReference ref = (MessageReference)i.next();
            if (log.isTraceEnabled()) { log.trace("adding " + ref + " to non-recoverable state"); }
            messageRefs.addLast(ref, ref.getPriority());
            
            //FIXME
            //For JMS We only need to call channel.deliver(null) once for the whole transaction
            //However some of the core tests still assume all deliver() semantics i.e. delivery
            //is attempted for all messages in channel on commit, hence they will fail if we only
            //call deliver once
            channel.deliver(null);
         }

         txToAddReferenceCallbacks.remove(tx);
      } 
      
      public void afterRollback()
      {
         for(Iterator i = refs.iterator(); i.hasNext(); )
         {
            MessageReference ref = (MessageReference)i.next();
            if (log.isTraceEnabled()) { log.trace("After rollback-Releasing reference for " + ref); }
            ref.releaseReference();
         }
         txToAddReferenceCallbacks.remove(tx);
      }
   }


   public class RemoveDeliveryCallback implements TxCallback
   {
      private List dels = new ArrayList();
      
      private Transaction tx;
      
      RemoveDeliveryCallback(Transaction tx)
      {
         this.tx = tx;
      }

      void addDelivery(Delivery d)
      {
         dels.add(d);
      }

      public void afterCommit()
      {
         for(Iterator i = dels.iterator(); i.hasNext(); )
         {
            Delivery d = (Delivery)i.next();
            if (log.isTraceEnabled()) { log.trace("After commit removing " + d + " from non-recoverable state"); }
            deliveries.remove(d.getReference().getMessageID());
            d.getReference().releaseReference();
         }
         txToRemoveDeliveryCallbacks.remove(tx);
      }   
      
      public void afterRollback()
      {
         for(Iterator i = dels.iterator(); i.hasNext(); )
         {
            Delivery d = (Delivery)i.next();
            if (log.isTraceEnabled()) { log.trace("Releasing reference for " + d.getReference()); }            
         }
         txToRemoveDeliveryCallbacks.remove(tx);
      }
   }

   
}
