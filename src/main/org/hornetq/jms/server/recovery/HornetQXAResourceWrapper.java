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

package org.hornetq.jms.server.recovery;

import java.util.Map;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.client.SessionFailureListener;
import org.hornetq.core.logging.Logger;

/**
 * XAResourceWrapper.
 * 
 * Mainly from org.jboss.server.XAResourceWrapper from the JBoss AS server module
 * 
 * The reason why we don't use that class directly is that it assumes on failure of connection
 * the RM_FAIL or RM_ERR is thrown, but in HornetQ we throw XA_RETRY since we want the recovery manager to be able
 * to retry on failure without having to manually retry
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="tim.fox@jboss.com">Tim Fox/a>
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version $Revision: 45341 $
 */
public class HornetQXAResourceWrapper implements XAResource, SessionFailureListener
{
   /** The log */
   private static final Logger log = Logger.getLogger(HornetQXAResourceWrapper.class);

   /** The state lock */
   private static final Object lock = new Object();

   /** The JNDI lookup for the XA connection factory */
   private final String connectorFactoryClassName;

   private final Map<String, Object> connectorConfig;

   private final String username;

   private final String password;

   private ServerLocator serverLocator;
   
   private ClientSessionFactory csf;

   private XAResource delegate;

   public HornetQXAResourceWrapper(final String connectorFactoryClassName,
                                   final Map<String, Object> connectorConfig,
                                   final String username,
                                   final String password)
   {
      this.connectorFactoryClassName = connectorFactoryClassName;
      this.connectorConfig = connectorConfig;
      this.username = username;
      this.password = password;
   }

   public Xid[] recover(final int flag) throws XAException
   {
      HornetQXAResourceWrapper.log.debug("Recover " + connectorFactoryClassName);
      XAResource xaResource = getDelegate();
      try
      {
         return xaResource.recover(flag);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void commit(final Xid xid, final boolean onePhase) throws XAException
   {
      HornetQXAResourceWrapper.log.debug("Commit " + connectorFactoryClassName + " xid " + " onePhase=" + onePhase);
      XAResource xaResource = getDelegate();
      try
      {
         xaResource.commit(xid, onePhase);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void rollback(final Xid xid) throws XAException
   {
      HornetQXAResourceWrapper.log.debug("Rollback " + connectorFactoryClassName + " xid ");
      XAResource xaResource = getDelegate();
      try
      {
         xaResource.rollback(xid);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void forget(final Xid xid) throws XAException
   {
      HornetQXAResourceWrapper.log.debug("Forget " + connectorFactoryClassName + " xid ");
      XAResource xaResource = getDelegate();
      try
      {
         xaResource.forget(xid);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public boolean isSameRM(XAResource xaRes) throws XAException
   {
      if (xaRes instanceof HornetQXAResourceWrapper)
      {
         xaRes = ((HornetQXAResourceWrapper)xaRes).getDelegate();
      }

      XAResource xaResource = getDelegate();
      try
      {
         return xaResource.isSameRM(xaRes);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public int prepare(final Xid xid) throws XAException
   {
      XAResource xaResource = getDelegate();
      try
      {
         return xaResource.prepare(xid);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void start(final Xid xid, final int flags) throws XAException
   {
      XAResource xaResource = getDelegate();
      try
      {
         xaResource.start(xid, flags);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void end(final Xid xid, final int flags) throws XAException
   {
      XAResource xaResource = getDelegate();
      try
      {
         xaResource.end(xid, flags);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public int getTransactionTimeout() throws XAException
   {
      XAResource xaResource = getDelegate();
      try
      {
         return xaResource.getTransactionTimeout();
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public boolean setTransactionTimeout(final int seconds) throws XAException
   {
      XAResource xaResource = getDelegate();
      try
      {
         return xaResource.setTransactionTimeout(seconds);
      }
      catch (XAException e)
      {
         throw check(e);
      }
   }

   public void connectionFailed(final HornetQException me, boolean failedOver)
   {
      HornetQXAResourceWrapper.log.warn("Notified of connection failure in recovery connectionFactory for provider " + connectorFactoryClassName,
                                        me);
      close();
   }

   public void beforeReconnect(final HornetQException me)
   {
   }

   /**
    * Get the connectionFactory XAResource
    * 
    * @return the connectionFactory
    * @throws XAException for any problem
    */
   public XAResource getDelegate() throws XAException
   {
      XAResource result = null;
      Exception error = null;
      try
      {
         result = connect();
      }
      catch (Exception e)
      {
         HornetQXAResourceWrapper.log.error("********************************Failed to connect to server", e);
         error = e;
      }

      if (result == null)
      {
         XAException xae = new XAException("Error trying to connect to provider " + connectorFactoryClassName);
         xae.errorCode = XAException.XAER_RMERR;
         if (error != null)
         {
            xae.initCause(error);
         }
         HornetQXAResourceWrapper.log.debug("Cannot get connectionFactory XAResource", xae);
         throw xae;
      }

      return result;
   }

   /**
    * Connect to the server if not already done so
    * 
    * @return the connectionFactory XAResource
    * @throws Exception for any problem
    */
   protected XAResource connect() throws Exception
   {
      // Do we already have a valid connectionFactory?
      synchronized (HornetQXAResourceWrapper.lock)
      {
         if (delegate != null)
         {
            return delegate;
         }
      }

      TransportConfiguration config = new TransportConfiguration(connectorFactoryClassName, connectorConfig);
      serverLocator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration[]{config});
      csf = serverLocator.createSessionFactory();
      ClientSession cs = null;

      if (username == null)
      {
         cs = csf.createSession(true, false, false);
      }
      else
      {
         cs = csf.createSession(username, password, true, false, false, false, 1);
      }
      cs.addFailureListener(this);

      synchronized (HornetQXAResourceWrapper.lock)
      {
         delegate = cs;
      }

      return delegate;
   }

   /**
    * Close the connection
    */
   public void close()
   {
      try
      {
         ServerLocator oldServerLocator = null;
         ClientSessionFactory oldCSF = null;
         synchronized (HornetQXAResourceWrapper.lock)
         {
            oldCSF = csf;
            csf = null;
            delegate = null;
            oldServerLocator = serverLocator;
            serverLocator = null;
         }
         if (oldCSF != null)
         {
            oldCSF.close();
            oldServerLocator.close();
         }
      }
      catch (Exception ignored)
      {
         HornetQXAResourceWrapper.log.trace("Ignored error during close", ignored);
      }
   }

   /**
    * Check whether an XAException is fatal. If it is an RM problem
    * we close the connection so the next call will reconnect.
    * 
    * @param e the xa exception
    * @return never
    * @throws XAException always
    */
   protected XAException check(final XAException e) throws XAException
   {
      if (e.errorCode == XAException.XA_RETRY)
      {
         HornetQXAResourceWrapper.log.debug("Fatal error in provider " + connectorFactoryClassName, e);
         close();
      }
      throw new XAException(XAException.XAER_RMFAIL);
   }

   @Override
   protected void finalize() throws Throwable
   {
      close();
   }
}
