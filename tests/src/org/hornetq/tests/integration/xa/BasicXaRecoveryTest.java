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
package org.hornetq.tests.integration.xa;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.jms.client.HornetQBytesMessage;
import org.hornetq.jms.client.HornetQTextMessage;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.utils.SimpleString;
import org.hornetq.utils.UUIDGenerator;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 */
public class BasicXaRecoveryTest extends ServiceTestBase
{
   private static Logger log = Logger.getLogger(BasicXaRecoveryTest.class);

   private final Map<String, AddressSettings> addressSettings = new HashMap<String, AddressSettings>();

   private HornetQServer server;

   private ClientSession clientSession;

   private ClientProducer clientProducer;

   private ClientConsumer clientConsumer;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private final SimpleString atestq = new SimpleString("atestq");

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      clearData();
      addressSettings.clear();
      configuration = createDefaultConfig();
      configuration.setSecurityEnabled(false);
      configuration.setJournalMinFiles(2);
      configuration.setPagingDirectory(getPageDir());

      server = createServer(true, configuration, -1, -1, addressSettings);

      // start the server
      server.start();

      // then we create a client as normal
      createClients(true, false);
   }

   @Override
   protected void tearDown() throws Exception
   {
      if (clientSession != null)
      {
         try
         {
            clientSession.close();
         }
         catch (HornetQException e1)
         {
            //
         }
      }
      if (server != null && server.isStarted())
      {
         try
         {
            server.stop();
         }
         catch (Exception e1)
         {
            //
         }
      }
      server = null;

      clientSession = null;

      server = null;

      clientProducer = null;

      clientConsumer = null;

      sessionFactory = null;

      configuration = null;
      

      super.tearDown();
   }

   public void testBasicSendWithCommit() throws Exception
   {

      testBasicSendWithCommit(false);
   }

   public void testBasicSendWithCommitWithServerStopped() throws Exception
   {
      testBasicSendWithCommit(true);
   }

   public void testBasicSendWithRollback() throws Exception
   {
      testBasicSendWithRollback(false);
   }

   public void testBasicSendWithRollbackWithServerStopped() throws Exception
   {
      testBasicSendWithRollback(true);
   }

   public void testMultipleBeforeSendWithCommit() throws Exception
   {
      testMultipleBeforeSendWithCommit(false);
   }

   public void testMultipleBeforeSendWithCommitWithServerStopped() throws Exception
   {
      testMultipleBeforeSendWithCommit(true);
   }

   public void testMultipleTxSendWithCommit() throws Exception
   {
      testMultipleTxSendWithCommit(false);
   }

   public void testMultipleTxSendWithCommitWithServerStopped() throws Exception
   {
      testMultipleTxSendWithCommit(true);
   }

   public void testMultipleTxSendWithRollback() throws Exception
   {
      testMultipleTxSendWithRollback(false);
   }

   public void testMultipleTxSendWithRollbackWithServerStopped() throws Exception
   {
      testMultipleTxSendWithRollback(true);
   }

   public void testMultipleTxSendWithCommitAndRollback() throws Exception
   {
      testMultipleTxSendWithCommitAndRollback(false);
   }

   public void testMultipleTxSendWithCommitAndRollbackWithServerStopped() throws Exception
   {
      testMultipleTxSendWithCommitAndRollback(true);
   }

   public void testMultipleTxSameXidSendWithCommit() throws Exception
   {
      testMultipleTxSameXidSendWithCommit(false);
   }

   public void testMultipleTxSameXidSendWithCommitWithServerStopped() throws Exception
   {
      testMultipleTxSameXidSendWithCommit(true);
   }

   public void testBasicReceiveWithCommit() throws Exception
   {
      testBasicReceiveWithCommit(false);
   }

   public void testBasicReceiveWithCommitWithServerStopped() throws Exception
   {
      testBasicReceiveWithCommit(true);
   }

   public void testBasicReceiveWithRollback() throws Exception
   {
      testBasicReceiveWithRollback(false);
   }

   public void testBasicReceiveWithRollbackWithServerStopped() throws Exception
   {
      testBasicReceiveWithRollback(true);
   }

   public void testMultipleTxReceiveWithCommit() throws Exception
   {
      testMultipleTxReceiveWithCommit(false);
   }

   public void testMultipleTxReceiveWithCommitWithServerStopped() throws Exception
   {
      testMultipleTxReceiveWithCommit(true);
   }

   public void testMultipleTxReceiveWithRollback() throws Exception
   {
      testMultipleTxReceiveWithRollback(false);
   }

   public void testMultipleTxReceiveWithRollbackWithServerStopped() throws Exception
   {
      testMultipleTxReceiveWithRollback(true);
   }

   public void testPagingServerRestarted() throws Exception
   {
      testPaging(true);
   }

   public void testPaging() throws Exception
   {
      testPaging(false);
   }

   public void testPaging(final boolean restartServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      SimpleString pageQueue = new SimpleString("pagequeue");

      AddressSettings pageAddressSettings = new AddressSettings();
      pageAddressSettings.setMaxSizeBytes(100 * 1024);
      pageAddressSettings.setPageSizeBytes(10 * 1024);

      addressSettings.put(pageQueue.toString(), pageAddressSettings);

      addSettings();

      clientSession.createQueue(pageQueue, pageQueue, null, true);

      clientSession.start(xid, XAResource.TMNOFLAGS);

      ClientProducer pageProducer = clientSession.createProducer(pageQueue);

      for (int i = 0; i < 1000; i++)
      {
         ClientMessage m = createBytesMessage(new byte[512], true);
         pageProducer.send(m);
      }

      pageProducer.close();

      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      log.info("*** stopping and restarting");
      
      if (restartServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }
      
      

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      clientSession.commit(xid, false);

      clientSession.close();

      clientSession = sessionFactory.createSession(false, false, false);

      clientSession.start();

      ClientConsumer pageConsumer = clientSession.createConsumer(pageQueue);

      for (int i = 0; i < 1000; i++)
      {
         ClientMessage m = pageConsumer.receive(10000);
         
         assertNotNull(m);
         m.acknowledge();
         clientSession.commit();
      }
      
      assertNull(pageConsumer.receiveImmediate());

   }

   public void testRollbackPaging() throws Exception
   {
      testRollbackPaging(false);
   }

   public void testRollbackPagingServerRestarted() throws Exception
   {
      testRollbackPaging(true);
   }

   public void testRollbackPaging(final boolean restartServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      SimpleString pageQueue = new SimpleString("pagequeue");

      AddressSettings pageAddressSettings = new AddressSettings();
      pageAddressSettings.setMaxSizeBytes(100 * 1024);
      pageAddressSettings.setPageSizeBytes(10 * 1024);

      addressSettings.put(pageQueue.toString(), pageAddressSettings);

      addSettings();

      clientSession.createQueue(pageQueue, pageQueue, null, true);
      
      long initialPageSize = this.server.getPostOffice().getPagingManager().getTotalMemory();

      clientSession.start(xid, XAResource.TMNOFLAGS);

      ClientProducer pageProducer = clientSession.createProducer(pageQueue);

      for (int i = 0; i < 1000; i++)
      {
         ClientMessage m = createBytesMessage(new byte[512], true);
         pageProducer.send(m);
      }

      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (restartServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      clientSession.rollback(xid);

      clientSession.start();

      ClientConsumer pageConsumer = clientSession.createConsumer(pageQueue);

      assertNull(pageConsumer.receiveImmediate());
      
      long globalSize = this.server.getPostOffice().getPagingManager().getTotalMemory();
      // Management message (from createQueue) will not be taken into account again as it is nonPersistent
      
      log.info("global size is " + globalSize +  " initial page size is " + initialPageSize);
      
      assertTrue(globalSize == initialPageSize || globalSize == 0l);

   }

   public void testNonPersistent() throws Exception
   {
      testNonPersistent(true);
      testNonPersistent(false);
   }

   public void testNonPersistent(final boolean commit) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1", false);
      ClientMessage m2 = createTextMessage("m2", false);
      ClientMessage m3 = createTextMessage("m3", false);
      ClientMessage m4 = createTextMessage("m4", false);

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      stopAndRestartServer();

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      if (commit)
      {
         clientSession.commit(xid, false);
      }
      else
      {
         clientSession.rollback(xid);
      }
   }

   public void testNonPersistentMultipleIDs() throws Exception
   {
      for (int i = 0; i < 10; i++)
      {
         Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

         ClientMessage m1 = createTextMessage("m1", false);
         ClientMessage m2 = createTextMessage("m2", false);
         ClientMessage m3 = createTextMessage("m3", false);
         ClientMessage m4 = createTextMessage("m4", false);

         clientSession.start(xid, XAResource.TMNOFLAGS);
         clientProducer.send(m1);
         clientProducer.send(m2);
         clientProducer.send(m3);
         clientProducer.send(m4);
         clientSession.end(xid, XAResource.TMSUCCESS);
         clientSession.prepare(xid);

         if (i == 2)
         {
            clientSession.commit(xid, false);
         }

         recreateClients();

      }

      stopAndRestartServer();

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(9, xids.length);
   }

   public void testBasicSendWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);

      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
   }

   public void testBasicSendWithRollback(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      log.info("shutting down server");
      
      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }
      
      log.info("restarted");

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.rollback(xid);
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleBeforeSendWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m5");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m6");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m7");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m8");
   }

   public void testMultipleTxSendWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 2);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.commit(xid, false);
      clientSession.commit(xid2, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m5");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m6");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m7");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m8");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
   }

   public void testMultipleTxSendWithRollback(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 2);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.rollback(xid);
      clientSession.rollback(xid2);
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxSendWithCommitAndRollback(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 2);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.rollback(xid);
      clientSession.commit(xid2, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxSameXidSendWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid, XAResource.TMSUCCESS);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMJOIN);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m5");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m6");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m7");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m8");
   }

   public void testBasicReceiveWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      clientSession.end(xid, XAResource.TMSUCCESS);
      assertEquals("Expected XA_OK", XAResource.XA_OK, clientSession.prepare(xid));

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(xids.length, 1);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.commit(xid, false);
      clientSession.start();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testBasicReceiveWithRollback(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      log.info("stopping and restarting");
      
      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }
      
      log.info("Restarted");

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.rollback(xid);
      clientSession.start();
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
   }

   public void testMultipleTxReceiveWithCommit(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      SimpleString anewtestq = new SimpleString("anewtestq");
      clientSession.createQueue(anewtestq, anewtestq, null, true);
      ClientProducer clientProducer3 = clientSession2.createProducer(anewtestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientProducer3.send(m5);
      clientProducer3.send(m6);
      clientProducer3.send(m7);
      clientProducer3.send(m8);
      clientSession2.close();
      clientSession2 = sessionFactory.createSession(true, false, false);
      ClientConsumer clientConsumer2 = clientSession2.createConsumer(anewtestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientSession2.start();
      ClientMessage m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m5");
      m = clientConsumer2.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m6");
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m7");
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m8");
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession2 = null;
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.commit(xid, false);
      clientSession.start();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxReceiveWithRollback(final boolean stopServer) throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      SimpleString anewtestq = new SimpleString("anewtestq");
      clientSession.createQueue(anewtestq, anewtestq, null, true);
      ClientProducer clientProducer3 = clientSession2.createProducer(anewtestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientProducer3.send(m5);
      clientProducer3.send(m6);
      clientProducer3.send(m7);
      clientProducer3.send(m8);
      clientSession2.close();
      clientSession2 = sessionFactory.createSession(true, false, false);
      ClientConsumer clientConsumer2 = clientSession2.createConsumer(anewtestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientSession2.start();
      ClientMessage m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m5");
      m = clientConsumer2.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m6");
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m7");
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m8");
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession2 = null;
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer)
      {
         stopAndRestartServer();
      }
      else
      {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(xids.length, 0);
      clientSession.rollback(xid);
      clientSession.start();
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m1");
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals(m.getBody().readString(), "m2");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m3");
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals(m.getBody().readString(), "m4");
   }

   protected void stopAndRestartServer() throws Exception
   {
      // now stop and start the server
      clientSession.close();
      clientSession = null;
      server.stop();
      server = null;
      server = createServer(true, configuration, -1, -1, addressSettings);

      server.start();
      createClients();
   }

   private void addSettings()
   {
      for (Map.Entry<String, AddressSettings> setting : addressSettings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }
   }

   protected void recreateClients() throws Exception
   {
      clientSession.close();
      clientSession = null;
      createClients();
   }

   private ClientMessage createTextMessage(final String s)
   {
      return createTextMessage(s, true);
   }

   private ClientMessage createTextMessage(final String s, final boolean durable)
   {
      ClientMessage message = clientSession.createClientMessage(HornetQTextMessage.TYPE,
                                                                durable,
                                                                0,
                                                                System.currentTimeMillis(),
                                                                (byte)1);
      message.getBody().writeString(s);
      return message;
   }

   private ClientMessage createBytesMessage(final byte[] b, final boolean durable)
   {
      ClientMessage message = clientSession.createClientMessage(HornetQBytesMessage.TYPE,
                                                                durable,
                                                                0,
                                                                System.currentTimeMillis(),
                                                                (byte)1);
      message.getBody().writeBytes(b);
      return message;
   }

   private void createClients() throws HornetQException
   {
      createClients(false, true);
   }

   private void createClients(final boolean createQueue, final boolean commitACKs) throws HornetQException
   {

      sessionFactory = createInVMFactory();
      clientSession = sessionFactory.createSession(true, false, commitACKs);
      if (createQueue)
      {
         clientSession.createQueue(atestq, atestq, null, true);
      }
      clientProducer = clientSession.createProducer(atestq);
      clientConsumer = clientSession.createConsumer(atestq);
   }

   private void assertEqualXids(final Xid[] xids, final Xid... origXids)
   {
      assertEquals(xids.length, origXids.length);
      for (Xid xid : xids)
      {
         boolean found = false;
         for (Xid origXid : origXids)
         {
            found = Arrays.equals(origXid.getBranchQualifier(), xid.getBranchQualifier());
            if (found)
            {
               assertEquals(xid.getFormatId(), origXid.getFormatId());
               assertEqualsByteArrays(xid.getBranchQualifier(), origXid.getBranchQualifier());
               assertEqualsByteArrays(xid.getGlobalTransactionId(), origXid.getGlobalTransactionId());
               break;
            }
         }
         if (!found)
         {
            fail("correct xid not found: " + xid);
         }
      }
   }
}
