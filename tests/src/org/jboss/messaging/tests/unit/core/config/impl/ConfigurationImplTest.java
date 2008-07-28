/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.messaging.tests.unit.core.config.impl;

import static org.jboss.messaging.tests.util.RandomUtil.randomBoolean;
import static org.jboss.messaging.tests.util.RandomUtil.randomInt;
import static org.jboss.messaging.tests.util.RandomUtil.randomLong;
import static org.jboss.messaging.tests.util.RandomUtil.randomString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import junit.framework.TestCase;

import org.jboss.messaging.core.client.ConnectionParams;
import org.jboss.messaging.core.config.Configuration;
import org.jboss.messaging.core.config.impl.ConfigurationImpl;
import org.jboss.messaging.core.remoting.TransportType;
import org.jboss.messaging.core.server.JournalType;

/**
 * 
 * A ConfigurationImplTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class ConfigurationImplTest extends TestCase
{
   protected Configuration conf;
         
   public void testDefaults()
   {      
      assertEquals(ConfigurationImpl.DEFAULT_CLUSTERED, conf.isClustered());
      assertEquals(ConfigurationImpl.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, conf.getScheduledThreadPoolMaxSize());
      assertEquals(ConfigurationImpl.DEFAULT_HOST, conf.getHost());
      assertEquals(ConfigurationImpl.DEFAULT_TRANSPORT, conf.getTransport());
      assertEquals(ConfigurationImpl.DEFAULT_PORT, conf.getPort());
      assertEquals(ConfigurationImpl.DEFAULT_SECURITY_INVALIDATION_INTERVAL, conf.getSecurityInvalidationInterval());
      assertEquals(ConfigurationImpl.DEFAULT_REQUIRE_DESTINATIONS, conf.isRequireDestinations());
      assertEquals(ConfigurationImpl.DEFAULT_SECURITY_ENABLED, conf.isSecurityEnabled());
      assertEquals(ConfigurationImpl.DEFAULT_SSL_ENABLED, conf.isSSLEnabled());
      assertEquals(ConfigurationImpl.DEFAULT_KEYSTORE_PATH, conf.getKeyStorePath());
      assertEquals(ConfigurationImpl.DEFAULT_KEYSTORE_PASSWORD, conf.getKeyStorePassword());
      assertEquals(ConfigurationImpl.DEFAULT_TRUSTSTORE_PATH, conf.getTrustStorePath());
      assertEquals(ConfigurationImpl.DEFAULT_TRUSTSTORE_PASSWORD, conf.getTrustStorePassword());
      assertEquals(ConfigurationImpl.DEFAULT_BINDINGS_DIRECTORY, conf.getBindingsDirectory());
      assertEquals(ConfigurationImpl.DEFAULT_CREATE_BINDINGS_DIR, conf.isCreateBindingsDir());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_DIR, conf.getJournalDirectory());
      assertEquals(ConfigurationImpl.DEFAULT_CREATE_JOURNAL_DIR, conf.isCreateJournalDir());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_TYPE, conf.getJournalType());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_SYNC_TRANSACTIONAL, conf.isJournalSyncTransactional());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_SYNC_NON_TRANSACTIONAL, conf.isJournalSyncNonTransactional());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_FILE_SIZE, conf.getJournalFileSize());
      assertEquals(ConfigurationImpl.DEFAULT_JOURNAL_MIN_FILES, conf.getJournalMinFiles());      
      assertEquals(ConfigurationImpl.DEFAULT_MAX_AIO, conf.getJournalMaxAIO());
   }
   
   public void testSetGetAttributes()
   {
      for (int j = 0; j < 100; j++)
      {         
         boolean b = randomBoolean();
         conf.setClustered(b);
         assertEquals(b, conf.isClustered());
         
         int i = randomInt();
         conf.setScheduledThreadPoolMaxSize(i);
         assertEquals(i, conf.getScheduledThreadPoolMaxSize());
         
         String s = randomString();
         conf.setHost(s);
         assertEquals(s, conf.getHost());
         
         i = randomInt() % 3;
         TransportType transport = i == 0 ? TransportType.TCP : i == 1 ? TransportType.HTTP : i == 2 ? TransportType.INVM : TransportType.INVM;
         conf.setTransport(transport);
         assertEquals(transport, conf.getTransport());
         
         i = randomInt();
         conf.setPort(i);
         assertEquals(i, conf.getPort());
         
         long l = randomLong();
         conf.setSecurityInvalidationInterval(l);
         assertEquals(l, conf.getSecurityInvalidationInterval());
         
         b = randomBoolean();
         conf.setRequireDestinations(b);
         assertEquals(b, conf.isRequireDestinations());
         
         b = randomBoolean();
         conf.setSecurityEnabled(b);
         assertEquals(b, conf.isSecurityEnabled());
         
         b = randomBoolean();
         conf.setSSLEnabled(b);
         assertEquals(b, conf.isSSLEnabled());
         
         s = randomString();
         conf.setKeyStorePath(s);
         assertEquals(s, conf.getKeyStorePath());
         
         s = randomString();
         conf.setKeyStorePassword(s);
         assertEquals(s, conf.getKeyStorePassword());
         
         s = randomString();
         conf.setTrustStorePath(s);
         assertEquals(s, conf.getTrustStorePath());
         
         s = randomString();
         conf.setTrustStorePassword(s);
         assertEquals(s, conf.getTrustStorePassword());
         
         s = randomString();
         conf.setBindingsDirectory(s);
         assertEquals(s, conf.getBindingsDirectory());
         
         b = randomBoolean();
         conf.setCreateBindingsDir(b);
         assertEquals(b, conf.isCreateBindingsDir());
         
         s = randomString();
         conf.setJournalDirectory(s);
         assertEquals(s, conf.getJournalDirectory());
         
         b = randomBoolean();
         conf.setCreateJournalDir(b);
         assertEquals(b, conf.isCreateJournalDir());
         
         i = randomInt() % 3;
         JournalType journal = i == 0 ? JournalType.ASYNCIO : i == 1 ? JournalType.JDBC : i == 2 ? JournalType.NIO : JournalType.NIO;
         conf.setJournalType(journal);
         assertEquals(journal, conf.getJournalType());
         
         b = randomBoolean();
         conf.setJournalSyncTransactional(b);
         assertEquals(b, conf.isJournalSyncTransactional());
         
         b = randomBoolean();
         conf.setJournalSyncNonTransactional(b);
         assertEquals(b, conf.isJournalSyncNonTransactional());
         
         i = randomInt();
         conf.setJournalFileSize(i);
         assertEquals(i, conf.getJournalFileSize());
         
         i = randomInt();
         conf.setJournalMinFiles(i);
         assertEquals(i, conf.getJournalMinFiles());
         
         i = randomInt();
         conf.setJournalMaxAIO(i);
         assertEquals(i, conf.getJournalMaxAIO());
         
         ConnectionParams params = conf.getConnectionParams();         
         assertNotNull(params);         
      }
   }
   
   public void testGetLocation()
   {
      conf.setTransport(TransportType.TCP);
      conf.setHost("blahhost");
      conf.setPort(1234);
      assertEquals("tcp://blahhost:1234", conf.getLocation().getLocation());
   }
   
   public void testGetSetInterceptors()
   {
      List<String> interceptors = conf.getInterceptorClassNames();
      
      final String name1 = "uqwyuqywuy";
      final String name2 = "yugyugyguyg";
      
      conf.getInterceptorClassNames().add(name1);
      conf.getInterceptorClassNames().add(name2);
      
      assertTrue(conf.getInterceptorClassNames().contains(name1));
      assertTrue(conf.getInterceptorClassNames().contains(name2));
      assertFalse(conf.getInterceptorClassNames().contains("iijij"));
   }
   
   public void testOverrideWithSystemProperties()
   {
      testOverrideWithSystemProperties(false);
      testOverrideWithSystemProperties(true);
   }
   
   public void testSerialize() throws Exception
   {
      boolean b = randomBoolean();
      conf.setClustered(b);
      
      int i = randomInt();
      conf.setScheduledThreadPoolMaxSize(i);
   
      String s = randomString();
      conf.setHost(s);

      i = randomInt() % 2;
      TransportType transport = i == 0 ? TransportType.TCP : TransportType.HTTP;
      conf.setTransport(transport);

      i = randomInt();
      conf.setPort(i);
 
      long l = randomLong();
      conf.setSecurityInvalidationInterval(l);

      b = randomBoolean();
      conf.setRequireDestinations(b);
    
      b = randomBoolean();
      conf.setSecurityEnabled(b);

      b = randomBoolean();
      conf.setSSLEnabled(b);
 
      s = randomString();
      conf.setKeyStorePath(s);

      s = randomString();
      conf.setKeyStorePassword(s);
 
      s = randomString();
      conf.setTrustStorePath(s);
 
      s = randomString();
      conf.setTrustStorePassword(s);
   
      s = randomString();
      conf.setBindingsDirectory(s);

      b = randomBoolean();
      conf.setCreateBindingsDir(b);

      s = randomString();
      conf.setJournalDirectory(s);
   
      b = randomBoolean();
      conf.setCreateJournalDir(b);
   
      i = randomInt() % 3;
      JournalType journal = i == 0 ? JournalType.ASYNCIO : i == 1 ? JournalType.JDBC : i == 2 ? JournalType.NIO : JournalType.NIO;
      conf.setJournalType(journal);
   
      b = randomBoolean();
      conf.setJournalSyncTransactional(b);
    
      b = randomBoolean();
      conf.setJournalSyncNonTransactional(b);

      i = randomInt();
      conf.setJournalFileSize(i);
 
      i = randomInt();
      conf.setJournalMinFiles(i);
 
      i = randomInt();
      conf.setJournalMaxAIO(i);
  
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(conf);
      oos.flush();
      
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      Configuration conf2 = (Configuration)ois.readObject();
      
      assertTrue(conf.equals(conf2));      
   }
   
   // Protected ----------------------------------------------------------------------------------------
   
   protected void setUp() throws Exception
   {
      super.setUp();
      
      conf = createConfiguration();
   }
   
   protected Configuration createConfiguration() throws Exception
   {
      return new ConfigurationImpl();
   }
   
   // Private --------------------------------------------------------------------------------------------
   
   private void testOverrideWithSystemProperties(final boolean b)
   {
      try
      {
         System.setProperty(ConfigurationImpl.ENABLE_SSL_PROPERTY_NAME, String.valueOf(b));
         
         assertEquals(b, conf.isSSLEnabled()); 
      }
      finally
      {
         System.clearProperty(ConfigurationImpl.ENABLE_SSL_PROPERTY_NAME);
      }
   }
     
}
