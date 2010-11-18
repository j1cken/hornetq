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

package org.hornetq.tests.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;

import junit.framework.Assert;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.jms.client.HornetQBytesMessage;
import org.hornetq.jms.client.HornetQTextMessage;
import org.hornetq.spi.core.security.HornetQSecurityManager;

/**
 * 
 * Base class with basic utilities on starting up a basic server
 * 
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public abstract class ServiceTestBase extends UnitTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   protected static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();

   protected static final String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();

   protected static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();

   protected static final String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();

   // Static --------------------------------------------------------
   private final Logger log = Logger.getLogger(this.getClass());

   // Constructors --------------------------------------------------

   public ServiceTestBase()
   {
      super();
   }

   public ServiceTestBase(final String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final int pageSize,
                                        final int maxAddressSize,
                                        final Map<String, AddressSettings> settings)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration, false);
      }

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(pageSize);
      defaultSetting.setMaxSizeBytes(maxAddressSize);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final MBeanServer mbeanServer,
                                        final Map<String, AddressSettings> settings)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer, false);
      }

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles)
   {
      return createServer(realFiles, false);
   }

   protected HornetQServer createServer(final boolean realFiles, final boolean netty)
   {
      return createServer(realFiles, createDefaultConfig(netty), -1, -1, new HashMap<String, AddressSettings>());
   }

   protected HornetQServer createServer(final boolean realFiles, final Configuration configuration)
   {
      return createServer(realFiles, configuration, -1, -1, new HashMap<String, AddressSettings>());
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final HornetQSecurityManager securityManager)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration, ManagementFactory.getPlatformMBeanServer(), securityManager);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration,
                                           ManagementFactory.getPlatformMBeanServer(),
                                           securityManager,
                                           false);
      }

      Map<String, AddressSettings> settings = new HashMap<String, AddressSettings>();

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createClusteredServerWithParams(final boolean isNetty,
                                                           final int index,
                                                           final boolean realFiles,
                                                           final Map<String, Object> params)
   {
      if (isNetty)
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, ServiceTestBase.NETTY_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
      else
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, ServiceTestBase.INVM_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
   }

   protected HornetQServer createClusteredServerWithParams(final boolean isNetty,
                                                           final int index,
                                                           final boolean realFiles,
                                                           final int pageSize,
                                                           final int maxAddressSize,
                                                           final Map<String, Object> params)
   {
      if (isNetty)
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, ServiceTestBase.NETTY_ACCEPTOR_FACTORY),
                             pageSize,
                             maxAddressSize,
                             new HashMap<String, AddressSettings>());
      }
      else
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, ServiceTestBase.INVM_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
   }

   protected Configuration createDefaultConfig()
   {
      return createDefaultConfig(false);
   }

   protected Configuration createDefaultConfig(final boolean netty)
   {
      if (netty)
      {
         return createDefaultConfig(new HashMap<String, Object>(),
                                    ServiceTestBase.INVM_ACCEPTOR_FACTORY,
                                    ServiceTestBase.NETTY_ACCEPTOR_FACTORY);
      }
      else
      {
         return createDefaultConfig(new HashMap<String, Object>(), ServiceTestBase.INVM_ACCEPTOR_FACTORY);
      }
   }

   protected Configuration createClusteredDefaultConfig(final int index,
                                                        final Map<String, Object> params,
                                                        final String... acceptors)
   {
      Configuration config = createDefaultConfig(index, params, acceptors);

      config.setClustered(true);

      return config;
   }

   protected Configuration createDefaultConfig(final int index,
                                               final Map<String, Object> params,
                                               final String... acceptors)
   {
      Configuration configuration = new ConfigurationImpl();
      configuration.setSecurityEnabled(false);
      configuration.setBindingsDirectory(getBindingsDir(index, false));
      configuration.setJournalMinFiles(2);
      configuration.setJournalDirectory(getJournalDir(index, false));
      configuration.setJournalFileSize(100 * 1024);
      configuration.setJournalType(getDefaultJournalType());
      configuration.setPagingDirectory(getPageDir(index, false));
      configuration.setLargeMessagesDirectory(getLargeMessagesDir(index, false));
      configuration.setJournalCompactMinFiles(0);
      configuration.setJournalCompactPercentage(0);

      configuration.getAcceptorConfigurations().clear();

      for (String acceptor : acceptors)
      {
         TransportConfiguration transportConfig = new TransportConfiguration(acceptor, params);
         configuration.getAcceptorConfigurations().add(transportConfig);
      }

      return configuration;
   }

   protected Configuration createDefaultConfig(final Map<String, Object> params, final String... acceptors)
   {
      Configuration configuration = new ConfigurationImpl();
      configuration.setSecurityEnabled(false);
      configuration.setJMXManagementEnabled(false);
      configuration.setBindingsDirectory(getBindingsDir());
      configuration.setJournalMinFiles(2);
      configuration.setJournalDirectory(getJournalDir());
      configuration.setJournalFileSize(100 * 1024);
      configuration.setPagingDirectory(getPageDir());
      configuration.setLargeMessagesDirectory(getLargeMessagesDir());
      configuration.setJournalCompactMinFiles(0);
      configuration.setJournalCompactPercentage(0);

      configuration.setFileDeploymentEnabled(false);

      configuration.setJournalType(getDefaultJournalType());

      configuration.getAcceptorConfigurations().clear();

      for (String acceptor : acceptors)
      {
         TransportConfiguration transportConfig = new TransportConfiguration(acceptor, params);
         configuration.getAcceptorConfigurations().add(transportConfig);
      }

      return configuration;
   }

   protected ClientSessionFactoryImpl createFactory(final boolean isNetty)
   {
      if (isNetty)
      {
         return createNettyFactory();
      }
      else
      {
         return createInVMFactory();
      }
   }
   
   protected void createQueue(String address, String queue) throws Exception
   {
      ClientSessionFactory sf = createInVMFactory();
      ClientSession session = sf.createSession();
      session.createQueue(address, queue);
      session.close();
      sf.close();
   }

   protected ClientSessionFactoryImpl createInVMFactory()
   {
      return createFactory(ServiceTestBase.INVM_CONNECTOR_FACTORY);
   }

   protected ClientSessionFactoryImpl createNettyFactory()
   {
      return createFactory(ServiceTestBase.NETTY_CONNECTOR_FACTORY);
   }

   protected ClientSessionFactoryImpl createFactory(final String connectorClass)
   {
      return (ClientSessionFactoryImpl) HornetQClient.createClientSessionFactory(new TransportConfiguration(connectorClass), null);

   }

   protected ClientMessage createTextMessage(final ClientSession session, final String s)
   {
      return createTextMessage(session, s, true);
   }

   public String getTextMessage(final ClientMessage m)
   {
      m.getBodyBuffer().resetReaderIndex();
      return m.getBodyBuffer().readString();
   }

   protected ClientMessage createTextMessage(final ClientSession session, final String s, final boolean durable)
   {
      ClientMessage message = session.createMessage(HornetQTextMessage.TYPE,
                                                          durable,
                                                          0,
                                                          System.currentTimeMillis(),
                                                          (byte)1);
      message.getBodyBuffer().writeString(s);
      return message;
   }

   protected ClientMessage createBytesMessage(final ClientSession session, final byte[] b, final boolean durable)
   {
      ClientMessage message = session.createMessage(HornetQBytesMessage.TYPE,
                                                          durable,
                                                          0,
                                                          System.currentTimeMillis(),
                                                          (byte)1);
      message.getBodyBuffer().writeBytes(b);
      return message;
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir(final int expect) throws Exception
   {
      File largeMessagesFileDir = new File(getLargeMessagesDir());

      // Deleting the file is async... we keep looking for a period of the time until the file is really gone
      for (int i = 0; i < 100; i++)
      {
         if (largeMessagesFileDir.listFiles().length != expect)
         {
            Thread.sleep(10);
         }
         else
         {
            break;
         }
      }

      Assert.assertEquals(expect, largeMessagesFileDir.listFiles().length);
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir() throws Exception
   {
      validateNoFilesOnLargeDir(0);
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
