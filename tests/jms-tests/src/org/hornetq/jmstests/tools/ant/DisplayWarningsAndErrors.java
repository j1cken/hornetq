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

package org.hornetq.jmstests.tools.ant;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Greps fror WARN and ERROR entries in the specified file.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * $Id$
 */
public class DisplayWarningsAndErrors
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   public static void main(String[] args) throws Exception
   {
      new DisplayWarningsAndErrors(args).run();
   }

   // Attributes ----------------------------------------------------

   private File file;
   private List ignoreList;

   // Constructors --------------------------------------------------

   private DisplayWarningsAndErrors(String[] args) throws Exception
   {
      if (args.length == 0)
      {
         throw new Exception("Specify the file to grep!");
      }

      file = new File(args[0]);

      if (!file.canRead())
      {
         throw new Exception("The file " + file + " does not exist or cannot be read");
      }

      for(int i = 1; i < args.length; i++)
      {
         if (ignoreList == null)
         {
            ignoreList = new ArrayList();
         }

         ignoreList.add(args[i]);
      }
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private void run() throws Exception
   {
      FileReader fr = new FileReader(file);
      BufferedReader br = new BufferedReader(fr);
      boolean error = false;

      try
      {
         String line;
         boolean first = true;
         outer: while((line = br.readLine()) != null)
         {
            if (line.indexOf("ERROR") != -1 || line.indexOf("WARN") != -1)
            {
               //System.out.println(">"+line+"<");
               if (ignoreList != null)
               {
                  for(Iterator i = ignoreList.iterator(); i.hasNext(); )
                  {
                     if (line.endsWith((String)i.next()))
                     {
                        continue outer;
                     }
                  }
               }

               if (first)
               {
                  printBanner();
                  first = false;
               }

               if (line.indexOf("ERROR") != -1)
               {
                  error = true;
               }

               System.out.println(line);
            }
         }

      }
      finally
      {
         fr.close();
         br.close();
      }

      if (error)
      {
         System.exit(1);
      }
   }


   private void printBanner()
   {
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX    WARNING! JBoss server instance generated WARN/ERROR log entries:      XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXX                                                                          XXXXXXXX");
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

      System.out.println();

   }

   // Inner classes -------------------------------------------------

}
