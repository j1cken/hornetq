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
package org.jboss.jms.example;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.naming.InitialContext;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * a baee class for examples. This takes care of starting and stopping the server as well as deploying any queue needed.
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public abstract class JMSExample
{
   protected static Logger log = Logger.getLogger(JMSExample.class.getName());

   private Process[] servers;

   private Connection conn;

   private boolean failure = false;

   public abstract void runExample() throws Exception;

   protected void run(String[] args)
   {
      String runServerProp = System.getProperty("jbm.example.runServer");
      String logServerOutputProp = System.getProperty("jbm.example.logserveroutput");
      boolean runServer = runServerProp == null ? true : Boolean.valueOf(runServerProp);
      boolean logServerOutput = logServerOutputProp == null?false:Boolean.valueOf(logServerOutputProp);
      log.info("jbm.example.runServer is " + runServer);
      try
      {
         if (runServer)
         {
            startServer(getServerNames(args), logServerOutput);
         }
         runExample();
      }
      catch (Throwable e)
      {
         failure = true;
         e.printStackTrace();
      }
      finally
      {
         if (conn != null)
         {
            try
            {
               conn.close();
            }
            catch (JMSException e)
            {
               //ignore
            }
         }
         if (runServer)
         {
            try
            {
               stopServer();
            }
            catch (Throwable throwable)
            {
               throwable.printStackTrace();
            }
         }
      }
      reportResultAndExit();
   }

   protected InitialContext getContext() throws Exception
   {
      URL url = Thread.currentThread().getContextClassLoader().getResource("client-jndi.properties");
      Properties props = new Properties();
      props.load(url.openStream());
      return new InitialContext(props);
   }

   private void startServer(String[][] args, boolean logServerOutput) throws Throwable
   {
      servers = new Process[args.length];
      for (int i = 0; i < args.length; i++)
      {
         String[] arg = args[i];
         log.info("starting server with config '" + arg[0] + "' " + "logServerOutput " + logServerOutput);
         String logProps = System.getProperty("java.util.logging.config.file");
         servers[i] = SpawnedVMSupport.spawnVM(SpawnedJMSServer.class.getName(), new String[]{"-Djava.util.logging.config.file=" + logProps}, logServerOutput, "STARTED::", "FAILED::", args[i]);
      }
   }

   private void stopServer() throws Throwable
   {
      for (Process server : servers)
      {
         server.getInputStream().close();
         server.getErrorStream().close();
         server.destroy();
      }
   }

   private String[][] getServerNames(String[] args)
   {
      String[][] actArgs = new String[args.length][1];
      for (int i = 0; i < args.length; i++)
      {
         actArgs[i][0] = args[i].trim();
      }
      return actArgs;
   }
   
   private void reportResultAndExit()
   {
      if (failure)
      {
         System.err.println();
         System.err.println("#####################");
         System.err.println("###    FAILURE!   ###");
         System.err.println("#####################");
         System.exit(1);
      }
      else
      {
         System.out.println();
         System.out.println("#####################");
         System.out.println("###    SUCCESS!   ###");
         System.out.println("#####################");
         System.exit(0);
      }
   }
}