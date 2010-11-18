/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.core.paging.cursor.impl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.core.filter.Filter;
import org.hornetq.core.journal.IOAsyncTask;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.paging.PageTransactionInfo;
import org.hornetq.core.paging.PagedMessage;
import org.hornetq.core.paging.PagingStore;
import org.hornetq.core.paging.cursor.PageCache;
import org.hornetq.core.paging.cursor.PageCursorProvider;
import org.hornetq.core.paging.cursor.PagePosition;
import org.hornetq.core.paging.cursor.PageSubscription;
import org.hornetq.core.paging.cursor.PagedReference;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.server.MessageReference;
import org.hornetq.core.server.Queue;
import org.hornetq.core.server.ServerMessage;
import org.hornetq.core.transaction.Transaction;
import org.hornetq.core.transaction.TransactionOperationAbstract;
import org.hornetq.core.transaction.TransactionPropertyIndexes;
import org.hornetq.core.transaction.impl.TransactionImpl;
import org.hornetq.utils.ConcurrentHashSet;
import org.hornetq.utils.Future;
import org.hornetq.utils.LinkedListIterator;

/**
 * A PageCursorImpl
 *
 * A page cursor will always store its 
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 * 
 */
public class PageSubscriptionImpl implements PageSubscription
{
   // Constants -----------------------------------------------------
   private static final Logger log = Logger.getLogger(PageSubscriptionImpl.class);

   // Attributes ----------------------------------------------------

   private final boolean isTrace = false; // PageCursorImpl.log.isTraceEnabled();

   private static void trace(final String message)
   {
      // PageCursorImpl.log.info(message);
      System.out.println(message);
   }

   private volatile boolean autoCleanup = true;

   private final StorageManager store;

   private final long cursorId;

   private Queue queue;

   private final boolean persistent;

   private final Filter filter;

   private final PagingStore pageStore;

   private final PageCursorProvider cursorProvider;

   private final Executor executor;

   private volatile PagePosition lastAckedPosition;

   private List<PagePosition> recoveredACK;

   private final SortedMap<Long, PageCursorInfo> consumedPages = Collections.synchronizedSortedMap(new TreeMap<Long, PageCursorInfo>());

   // We only store the position for redeliveries. They will be read from the SoftCache again during delivery.
   private final ConcurrentLinkedQueue<PagePosition> redeliveries = new ConcurrentLinkedQueue<PagePosition>();

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public PageSubscriptionImpl(final PageCursorProvider cursorProvider,
                               final PagingStore pageStore,
                               final StorageManager store,
                               final Executor executor,
                               final Filter filter,
                               final long cursorId,
                               final boolean persistent)
   {
      this.pageStore = pageStore;
      this.store = store;
      this.cursorProvider = cursorProvider;
      this.cursorId = cursorId;
      this.executor = executor;
      this.filter = filter;
      this.persistent = persistent;
   }

   // Public --------------------------------------------------------

   public Queue getQueue()
   {
      return queue;
   }

   public boolean isPaging()
   {
      return pageStore.isPaging();
   }

   public void setQueue(Queue queue)
   {
      this.queue = queue;
   }

   public void disableAutoCleanup()
   {
      autoCleanup = false;
   }

   public void enableAutoCleanup()
   {
      autoCleanup = true;
   }

   public PageCursorProvider getProvider()
   {
      return cursorProvider;
   }

   public void bookmark(PagePosition position) throws Exception
   {
      PageCursorInfo cursorInfo = getPageInfo(position);

      if (position.getMessageNr() > 0)
      {
         cursorInfo.confirmed.addAndGet(position.getMessageNr());
      }

      ack(position);
   }

   public void scheduleCleanupCheck()
   {
      if (autoCleanup)
      {
         executor.execute(new Runnable()
         {

            public void run()
            {
               try
               {
                  cleanupEntries();
               }
               catch (Exception e)
               {
                  PageSubscriptionImpl.log.warn("Error on cleaning up cursor pages", e);
               }
            }
         });
      }
   }

   /** 
    * It will cleanup all the records for completed pages
    * */
   public void cleanupEntries() throws Exception
   {
      Transaction tx = new TransactionImpl(store);

      boolean persist = false;

      final ArrayList<PageCursorInfo> completedPages = new ArrayList<PageCursorInfo>();

      // First get the completed pages using a lock
      synchronized (this)
      {
         for (Entry<Long, PageCursorInfo> entry : consumedPages.entrySet())
         {
            PageCursorInfo info = entry.getValue();
            if (info.isDone() && !info.isPendingDelete() && lastAckedPosition != null)
            {
               if (entry.getKey() == lastAckedPosition.getPageNr())
               {
                  // PageSubscriptionImpl.trace("We can't clear page " + entry.getKey() +
                  // " now since it's the current page");
               }
               else
               {
                  info.setPendingDelete();
                  completedPages.add(entry.getValue());
               }
            }
         }
      }

      for (int i = 0; i < completedPages.size(); i++)
      {
         PageCursorInfo info = completedPages.get(i);

         for (PagePosition pos : info.acks)
         {
            if (pos.getRecordID() > 0)
            {
               store.deleteCursorAcknowledgeTransactional(tx.getID(), pos.getRecordID());
               if (!persist)
               {
                  // only need to set it once
                  tx.setContainsPersistent();
                  persist = true;
               }
            }
         }
      }

      tx.addOperation(new TransactionOperationAbstract()
      {

         @Override
         public void afterCommit(final Transaction tx)
         {
            executor.execute(new Runnable()
            {

               public void run()
               {
                  synchronized (PageSubscriptionImpl.this)
                  {
                     for (PageCursorInfo completePage : completedPages)
                     {
                        if (isTrace)
                        {
                           PageSubscriptionImpl.trace("Removing page " + completePage.getPageId());
                        }
                        if (consumedPages.remove(completePage.getPageId()) == null)
                        {
                           PageSubscriptionImpl.log.warn("Couldn't remove page " + completePage.getPageId() +
                                                         " from consumed pages on cursor for address " +
                                                         pageStore.getAddress());
                        }
                     }
                  }

                  cursorProvider.scheduleCleanup();
               }
            });
         }
      });

      tx.commit();

   }

   private PagedReference getReference(PagePosition pos) throws Exception
   {
      return cursorProvider.newReference(pos, cursorProvider.getMessage(pos), this);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#iterator()
    */
   public LinkedListIterator<PagedReference> iterator()
   {
      return new CursorIterator();
   }

   private PagedReference internalGetNext(final PagePosition pos)
   {
      PagePosition retPos = pos.nextMessage();

      PageCache cache = cursorProvider.getPageCache(pos);

      if (cache == null)
      {
         return null;
      }

      if (!cache.isLive() && retPos.getMessageNr() >= cache.getNumberOfMessages())
      {
         retPos = pos.nextPage();

         cache = cursorProvider.getPageCache(retPos);

         if (cache == null)
         {
            return null;
         }

         if (retPos.getMessageNr() >= cache.getNumberOfMessages())
         {
            return null;
         }
      }

      PagedMessage serverMessage = cache.getMessage(retPos.getMessageNr());

      if (serverMessage != null)
      {
         return cursorProvider.newReference(retPos, serverMessage, this);
      }
      else
      {
         return null;
      }
   }

   private boolean routed(PagedMessage message)
   {
      long id = getId();

      for (long qid : message.getQueueIDs())
      {
         if (qid == id)
         {
            return true;
         }
      }
      return false;
   }

   /**
    * 
    */
   private synchronized PagePosition getStartPosition()
   {
      // Get the first page not marked for deletion
      // It's important to verify if it's not marked for deletion as you may have a pending request on the queue
      for (Map.Entry<Long, PageCursorInfo> entry : consumedPages.entrySet())
      {
         if (!entry.getValue().isPendingDelete())
         {
            if (entry.getValue().acks.isEmpty())
            {
               return new PagePositionImpl(entry.getKey(), -1);
            }
            else
            {
               // The list is not ordered...
               // This is only done at creation of the queue, so we just scan instead of keeping the list ordened
               PagePosition retValue = null;

               for (PagePosition pos : entry.getValue().acks)
               {
                  System.out.println("Analizing " + pos);
                  if (retValue == null || retValue.getMessageNr() > pos.getMessageNr())
                  {
                     retValue = pos;
                  }
               }

               System.out.println("Returning initial position " + retValue);

               return retValue;
            }
         }
      }

      return new PagePositionImpl(pageStore.getFirstPage(), -1);
   }

   public void ackTx(final Transaction tx, final PagePosition position) throws Exception
   {
      // if the cursor is persistent
      if (persistent)
      {
         store.storeCursorAcknowledgeTransactional(tx.getID(), cursorId, position);
      }
      installTXCallback(tx, position);

   }

   public void ackTx(final Transaction tx, final PagedReference reference) throws Exception
   {
      ackTx(tx, reference.getPosition());

      PageTransactionInfo txInfo = getPageTransaction(reference);
      if (txInfo != null)
      {
         txInfo.storeUpdate(store, pageStore.getPagingManager(), tx);
      }
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#confirm(org.hornetq.core.paging.cursor.PagePosition)
    */
   public void ack(final PagedReference reference) throws Exception
   {
      ack(reference.getPosition());
      PageTransactionInfo txInfo = getPageTransaction(reference);
      if (txInfo != null)
      {
         txInfo.storeUpdate(this.store, pageStore.getPagingManager());
      }
   }

   public void ack(final PagePosition position) throws Exception
   {
      // if we are dealing with a persistent cursor
      if (persistent)
      {
         store.storeCursorAcknowledge(cursorId, position);
      }

      store.afterCompleteOperations(new IOAsyncTask()
      {

         public void onError(final int errorCode, final String errorMessage)
         {
         }

         public void done()
         {
            processACK(position);
         }
      });
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#getFirstPage()
    */
   public long getFirstPage()
   {
      if (consumedPages.isEmpty())
      {
         return 0;
      }
      else
      {
         return consumedPages.firstKey();
      }
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#returnElement(org.hornetq.core.paging.cursor.PagePosition)
    */
   public void redeliver(final PagePosition position)
   {
      synchronized (redeliveries)
      {
         redeliveries.add(position);
      }
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageSubscription#queryMessage(org.hornetq.core.paging.cursor.PagePosition)
    */
   public PagedMessage queryMessage(PagePosition pos)
   {
      try
      {
         return cursorProvider.getMessage(pos);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   /** 
    * Theres no need to synchronize this method as it's only called from journal load on startup
    */
   public void reloadACK(final PagePosition position)
   {
      if (recoveredACK == null)
      {
         recoveredACK = new LinkedList<PagePosition>();
      }

      recoveredACK.add(position);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#recoverPreparedACK(org.hornetq.core.paging.cursor.PagePosition)
    */
   public void reloadPreparedACK(final Transaction tx, final PagePosition position)
   {
      installTXCallback(tx, position);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#positionIgnored(org.hornetq.core.paging.cursor.PagePosition)
    */
   public void positionIgnored(final PagePosition position)
   {
      processACK(position);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#isComplete(long)
    */
   public boolean isComplete(long page)
   {
      PageCursorInfo info = consumedPages.get(page);
      return info != null && info.isDone();
   }

   /**
    * All the data associated with the cursor should go away here
    */
   public void close() throws Exception
   {
      final long tx = store.generateUniqueID();

      final ArrayList<Exception> ex = new ArrayList<Exception>();

      final AtomicBoolean isPersistent = new AtomicBoolean(false);

      // We can't delete the records at the caller's thread
      // because an executor may be holding the synchronized on PageCursorImpl
      // what would lead to a dead lock
      // so, we delete it inside the executor also
      // and wait for the result
      // The caller will be treating eventual IO exceptions and dispatching to the original thread's caller
      executor.execute(new Runnable()
      {

         public void run()
         {
            try
            {
               synchronized (PageSubscriptionImpl.this)
               {
                  for (PageCursorInfo cursor : consumedPages.values())
                  {
                     for (PagePosition info : cursor.acks)
                     {
                        if (info.getRecordID() != 0)
                        {
                           isPersistent.set(true);
                           store.deleteCursorAcknowledgeTransactional(tx, info.getRecordID());
                        }
                     }
                  }
               }
            }
            catch (Exception e)
            {
               ex.add(e);
               PageSubscriptionImpl.log.warn(e.getMessage(), e);
            }
         }
      });

      Future future = new Future();

      executor.execute(future);

      while (!future.await(5000))
      {
         PageSubscriptionImpl.log.warn("Timeout on waiting cursor " + this + " to be closed");
      }

      if (isPersistent.get())
      {
         // Another reason to perform the commit at the main thread is because the OperationContext may only send the
         // result to the client when
         // the IO on commit is done
         if (ex.size() == 0)
         {
            store.commit(tx);
         }
         else
         {
            store.rollback(tx);
            throw ex.get(0);
         }
      }

      cursorProvider.close(this);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.paging.cursor.PageCursor#getId()
    */
   public long getId()
   {
      return cursorId;
   }

   public boolean isPersistent()
   {
      return persistent;
   }

   public void processReload() throws Exception
   {
      if (recoveredACK != null)
      {
         if (isTrace)
         {
            PageSubscriptionImpl.trace("********** processing reload!!!!!!!");
         }
         Collections.sort(recoveredACK);

         boolean first = true;

         for (PagePosition pos : recoveredACK)
         {
            lastAckedPosition = pos;
            PageCursorInfo positions = getPageInfo(pos);
            if (first)
            {
               first = false;
               if (pos.getMessageNr() > 0)
               {
                  positions.confirmed.addAndGet(pos.getMessageNr());
               }
            }

            positions.addACK(pos);
         }

         recoveredACK.clear();
         recoveredACK = null;
      }
   }

   public void flushExecutors()
   {
      Future future = new Future();
      executor.execute(future);
      while (!future.await(1000))
      {
         PageSubscriptionImpl.log.warn("Waiting page cursor to finish executors - " + this);
      }
   }

   public void stop()
   {
      flushExecutors();
   }

   public void printDebug()
   {
      printDebug(toString());
   }

   public void printDebug(final String msg)
   {
      System.out.println("Debug information on PageCurorImpl- " + msg);
      for (PageCursorInfo info : consumedPages.values())
      {
         System.out.println(info);
      }
   }

   private synchronized PageCursorInfo getPageInfo(final PagePosition pos)
   {
      return getPageInfo(pos, true);
   }

   /**
    * @param page
    * @return
    */
   private synchronized PageCursorInfo getPageInfo(final PagePosition pos, boolean create)
   {
      PageCursorInfo pageInfo = consumedPages.get(pos.getPageNr());

      if (create && pageInfo == null)
      {
         PageCache cache = cursorProvider.getPageCache(pos);
         pageInfo = new PageCursorInfo(pos.getPageNr(), cache.getNumberOfMessages(), cache);
         consumedPages.put(pos.getPageNr(), pageInfo);
      }

      return pageInfo;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected boolean match(final ServerMessage message)
   {
      if (filter == null)
      {
         return true;
      }
      else
      {
         return filter.match(message);
      }
   }

   // Private -------------------------------------------------------

   // To be called only after the ACK has been processed and guaranteed to be on storae
   // The only exception is on non storage events such as not matching messages
   private void processACK(final PagePosition pos)
   {
      if (lastAckedPosition == null || pos.compareTo(lastAckedPosition) > 0)
      {
         if (lastAckedPosition != null && lastAckedPosition.getPageNr() != pos.getPageNr())
         {
            // there's a different page being acked, we will do the check right away
            if (autoCleanup)
            {
               scheduleCleanupCheck();
            }
         }
         lastAckedPosition = pos;
      }
      PageCursorInfo info = getPageInfo(pos);

      info.addACK(pos);
   }

   /**
    * @param tx
    * @param position
    */
   private void installTXCallback(final Transaction tx, final PagePosition position)
   {
      if (position.getRecordID() > 0)
      {
         // It needs to persist, otherwise the cursor will return to the fist page position
         tx.setContainsPersistent();
      }

      PageCursorTX cursorTX = (PageCursorTX)tx.getProperty(TransactionPropertyIndexes.PAGE_CURSOR_POSITIONS);

      if (cursorTX == null)
      {
         cursorTX = new PageCursorTX();
         tx.putProperty(TransactionPropertyIndexes.PAGE_CURSOR_POSITIONS, cursorTX);
         tx.addOperation(cursorTX);
      }

      cursorTX.addPositionConfirmation(this, position);

   }

   private PageTransactionInfo getPageTransaction(final PagedReference reference)
   {
      if (reference.getPagedMessage().getTransactionID() != 0)
      {
         return pageStore.getPagingManager().getTransaction(reference.getPagedMessage().getTransactionID());
      }
      else
      {
         return null;
      }
   }

   /**
    *  A callback from the PageCursorInfo. It will be called when all the messages on a page have been acked
    * @param info
    */
   private void onPageDone(final PageCursorInfo info)
   {
      if (autoCleanup)
      {
         scheduleCleanupCheck();
      }
   }

   // Inner classes -------------------------------------------------

   /** 
    * This will hold information about the pending ACKs towards a page.
    * This instance will be released as soon as the entire page is consumed, releasing the memory at that point
    * The ref counts are increased also when a message is ignored for any reason.
    * */
   private class PageCursorInfo
   {
      // Number of messages existent on this page
      private final int numberOfMessages;

      private final long pageId;

      // Confirmed ACKs on this page
      private final List<PagePosition> acks = Collections.synchronizedList(new LinkedList<PagePosition>());

      private WeakReference<PageCache> cache;

      private Set<PagePosition> removedReferences = new ConcurrentHashSet<PagePosition>();

      // The page was live at the time of the creation
      private final boolean wasLive;

      // There's a pending delete on the async IO pipe
      // We're holding this object to avoid delete the pages before the IO is complete,
      // however we can't delete these records again
      private boolean pendingDelete;

      // We need a separate counter as the cursor may be ignoring certain values because of incomplete transactions or
      // expressions
      private final AtomicInteger confirmed = new AtomicInteger(0);

      @Override
      public String toString()
      {
         return "PageCursorInfo::PageID=" + pageId +
                " numberOfMessage = " +
                numberOfMessages +
                ", confirmed = " +
                confirmed;
      }

      public PageCursorInfo(final long pageId, final int numberOfMessages, final PageCache cache)
      {
         this.pageId = pageId;
         this.numberOfMessages = numberOfMessages;
         wasLive = cache.isLive();
         if (wasLive)
         {
            this.cache = new WeakReference<PageCache>(cache);
         }
      }

      public boolean isDone()
      {
         return getNumberOfMessages() == confirmed.get();
      }

      public boolean isPendingDelete()
      {
         return pendingDelete;
      }

      public void setPendingDelete()
      {
         pendingDelete = true;
      }

      /**
       * @return the pageId
       */
      public long getPageId()
      {
         return pageId;
      }

      public boolean isRemoved(final PagePosition pos)
      {
         return removedReferences.contains(pos);
      }

      public void remove(final PagePosition position)
      {
         removedReferences.add(position);
      }

      public void addACK(final PagePosition posACK)
      {
         removedReferences.add(posACK);
         acks.add(posACK);

         if (isTrace)
         {
            PageSubscriptionImpl.trace("numberOfMessages =  " + getNumberOfMessages() +
                                       " confirmed =  " +
                                       (confirmed.get() + 1) +
                                       ", page = " +
                                       pageId);
         }

         // Negative could mean a bookmark on the first element for the page (example -1)
         if (posACK.getMessageNr() >= 0)
         {
            if (getNumberOfMessages() == confirmed.incrementAndGet())
            {
               onPageDone(this);
            }
         }
      }

      private int getNumberOfMessages()
      {
         if (wasLive)
         {
            PageCache cache = this.cache.get();
            if (cache != null)
            {
               return cache.getNumberOfMessages();
            }
            else
            {
               cache = cursorProvider.getPageCache(new PagePositionImpl(pageId, 0));
               this.cache = new WeakReference<PageCache>(cache);
               return cache.getNumberOfMessages();
            }
         }
         else
         {
            return numberOfMessages;
         }
      }

   }

   static class PageCursorTX extends TransactionOperationAbstract
   {
      HashMap<PageSubscriptionImpl, List<PagePosition>> pendingPositions = new HashMap<PageSubscriptionImpl, List<PagePosition>>();

      public void addPositionConfirmation(final PageSubscriptionImpl cursor, final PagePosition position)
      {
         List<PagePosition> list = pendingPositions.get(cursor);

         if (list == null)
         {
            list = new LinkedList<PagePosition>();
            pendingPositions.put(cursor, list);
         }

         list.add(position);
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.transaction.TransactionOperation#afterCommit(org.hornetq.core.transaction.Transaction)
       */
      @Override
      public void afterCommit(final Transaction tx)
      {
         for (Entry<PageSubscriptionImpl, List<PagePosition>> entry : pendingPositions.entrySet())
         {
            PageSubscriptionImpl cursor = entry.getKey();

            List<PagePosition> positions = entry.getValue();

            for (PagePosition confirmed : positions)
            {
               cursor.processACK(confirmed);
            }

         }
      }
      
      /* (non-Javadoc)
       * @see org.hornetq.core.transaction.TransactionOperation#getRelatedMessageReferences()
       */
      public List<MessageReference> getRelatedMessageReferences()
      {
         return Collections.emptyList();
      }
      

   }

   class CursorIterator implements LinkedListIterator<PagedReference>
   {
      private PagePosition position = null;

      private PagePosition lastOperation = null;

      private volatile boolean isredelivery = false;

      private volatile PagedReference lastRedelivery = null;

      /** next element taken on hasNext test.
       *  it has to be delivered on next next operation */
      private volatile PagedReference cachedNext;

      public CursorIterator()
      {
      }

      public void repeat()
      {
         if (isredelivery)
         {
            synchronized (redeliveries)
            {
               cachedNext = lastRedelivery;
            }
         }
         else
         {
            if (lastOperation == null)
            {
               position = null;
            }
            else
            {
               position = lastOperation;
            }
         }
      }

      /* (non-Javadoc)
       * @see java.util.Iterator#next()
       */
      public synchronized PagedReference next()
      {

         if (cachedNext != null)
         {
            PagedReference retPos = cachedNext;
            cachedNext = null;
            return retPos;
         }

         try
         {
            if (position == null)
            {
               position = getStartPosition();
            }

            return moveNext();
         }
         catch (Exception e)
         {
            throw new RuntimeException(e.getMessage(), e);
         }
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.paging.cursor.PageCursor#moveNext()
       */
      public PagedReference moveNext() throws Exception
      {
         synchronized (PageSubscriptionImpl.this)
         {
            boolean match = false;

            PagedReference message = null;

            PagePosition lastPosition = position;
            PagePosition tmpPosition = position;

            do
            {
               synchronized (redeliveries)
               {
                  PagePosition redelivery = redeliveries.poll();

                  if (redelivery != null)
                  {
                     // There's a redelivery pending, we will get it out of that pool instead
                     isredelivery = true;
                     PagedReference redeliveredMsg = getReference(redelivery);
                     lastRedelivery = redeliveredMsg;

                     return redeliveredMsg;
                  }
                  else
                  {
                     lastRedelivery = null;
                     isredelivery = false;
                  }

                  message = internalGetNext(tmpPosition);
               }

               if (message == null)
               {
                  break;
               }

               tmpPosition = message.getPosition();

               boolean valid = true;
               boolean ignored = false;

               // Validate the scenarios where the message should be considered not valid even to be considered

               // 1st... is it routed?

               valid = routed(message.getPagedMessage());
               if (!valid)
               {
                  ignored = true;
               }

               // 2nd ... if TX, is it committed?
               if (valid && message.getPagedMessage().getTransactionID() != 0)
               {
                  PageTransactionInfo tx = pageStore.getPagingManager().getTransaction(message.getPagedMessage()
                                                                                              .getTransactionID());
                  if (tx == null)
                  {
                     log.warn("Couldn't locate page transaction " + message.getPagedMessage().getTransactionID() +
                              ", ignoring message on position " +
                              message.getPosition());
                     valid = false;
                     ignored = true;
                  }
                  else
                  {
                     if (tx.deliverAfterCommit(PageSubscriptionImpl.this, message.getPosition()))
                     {
                        valid = false;
                        ignored = false;
                     }
                  }
               }

               // 3rd... was it previously removed?
               if (valid)
               {
                  // We don't create a PageCursorInfo unless we are doing a write operation (ack or removing)
                  // Say you have a Browser that will only read the files... there's no need to control PageCursors is
                  // nothing
                  // is being changed. That's why the false is passed as a parameter here
                  PageCursorInfo info = getPageInfo(message.getPosition(), false);
                  if (info != null && info.isRemoved(message.getPosition()))
                  {
                     valid = false;
                  }
               }

               if (!ignored)
               {
                  position = message.getPosition();
               }

               if (valid)
               {
                  match = match(message.getMessage());

                  if (!match)
                  {
                     processACK(message.getPosition());
                  }
               }
               else if (ignored)
               {
                  positionIgnored(message.getPosition());
               }
            }
            while (message != null && !match);

            if (message != null)
            {
               lastOperation = lastPosition;
            }

            return message;
         }
      }

      /** QueueImpl::deliver could be calling hasNext while QueueImpl.depage could be using next and hasNext as well. 
       *  It would be a rare race condition but I would prefer avoiding that scenario */
      public synchronized boolean hasNext()
      {
         // if an unbehaved program called hasNext twice before next, we only cache it once.
         if (cachedNext != null)
         {
            return true;
         }

         if (!pageStore.isPaging())
         {
            return false;
         }

         cachedNext = next();

         return cachedNext != null;
      }

      /* (non-Javadoc)
       * @see java.util.Iterator#remove()
       */
      public void remove()
      {
         if (!isredelivery)
         {
            PageSubscriptionImpl.this.getPageInfo(position).remove(position);
         }
      }

      /* (non-Javadoc)
       * @see org.hornetq.utils.LinkedListIterator#close()
       */
      public void close()
      {
      }
   }

}
