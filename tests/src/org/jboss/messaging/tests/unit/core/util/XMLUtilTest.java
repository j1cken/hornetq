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

package org.jboss.messaging.tests.unit.core.util;

import junit.framework.TestCase;

import org.jboss.messaging.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class XMLUtilTest extends TestCase
{
   // Constructors --------------------------------------------------

   public XMLUtilTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void setUp() throws Exception
   {
      super.setUp();
   }

   public void tearDown() throws Exception
   {
      super.tearDown();
   }

   public void testGetTextContext_1() throws Exception
   {
      String document = "<blah>foo</blah>";

      Element e = XMLUtil.stringToElement(document);

      assertEquals("foo", XMLUtil.getTextContent(e));
   }

   public void testGetTextContext_2() throws Exception
   {
      String document = "<blah someattribute=\"somevalue\">foo</blah>";

      Element e = XMLUtil.stringToElement(document);

      assertEquals("foo", XMLUtil.getTextContent(e));
   }

   public void testGetTextContext_3() throws Exception
   {
      String document = "<blah someattribute=\"somevalue\"><a/></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
   }

   public void testGetTextContext_4() throws Exception
   {
      String document = "<blah someattribute=\"somevalue\"><a></a></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
   }

   public void testGetTextContext_5() throws Exception
   {
      String document = "<blah someattribute=\"somevalue\"><a><b/></a></blah>";

      Element e = XMLUtil.stringToElement(document);

      String s = XMLUtil.getTextContent(e);

      Element subelement = XMLUtil.stringToElement(s);

      assertEquals("a", subelement.getNodeName());
      NodeList nl = subelement.getChildNodes();

      // try to find <b>
      boolean found = false;
      for(int i = 0; i < nl.getLength(); i++)
      {
         Node n = nl.item(i);
         if ("b".equals(n.getNodeName()))
         {
            found = true;
         }
      }
      assertTrue(found);
   }


   public void testEquivalent_1() throws Exception
   {
      String s = "<a/>";
      String s2 = "<a/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testEquivalent_2() throws Exception
   {
      String s = "<a></a>";
      String s2 = "<a/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testEquivalent_3() throws Exception
   {
      String s = "<a attr1=\"val1\" attr2=\"val2\"/>";
      String s2 = "<a attr2=\"val2\"/>";

      try
      {
         XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
         fail("this should throw exception");
      }
      catch(IllegalArgumentException e)
      {
         // OK
         e.printStackTrace();
      }
   }

   public void testEquivalent_4() throws Exception
   {
      String s = "<a attr1=\"val1\" attr2=\"val2\"/>";
      String s2 = "<a attr2=\"val2\" attr1=\"val1\"/>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testEquivalent_5() throws Exception
   {
      String s = "<a><b/></a>";
      String s2 = "<a><b/></a>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testEquivalent_6() throws Exception
   {
      String s = "<enclosing><a attr1=\"val1\" attr2=\"val2\"/></enclosing>";
      String s2 = "<enclosing><a attr2=\"val2\" attr1=\"val1\"/></enclosing>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testEquivalent_7() throws Exception
   {
      String s = "<a><b/><c/></a>";
      String s2 = "<a><c/><b/></a>";

      try
      {
         XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
         fail("this should throw exception");
      }
      catch(IllegalArgumentException e)
      {
         // OK
         e.printStackTrace();
      }
   }

   public void testEquivalent_8() throws Exception
   {
      String s = "<a><!-- some comment --><b/><!--some other comment --><c/><!-- blah --></a>";
      String s2 = "<a><b/><!--blah blah--><c/></a>";

      XMLUtil.assertEquivalent(XMLUtil.stringToElement(s), XMLUtil.stringToElement(s2));
   }

   public void testElementToString_1() throws Exception
   {
      String s = "<a b=\"something\">somethingelse</a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   public void testElementToString_2() throws Exception
   {
      String s = "<a b=\"something\"></a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   public void testElementToString_3() throws Exception
   {
      String s = "<a b=\"something\"/>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   public void testElementToString_4() throws Exception
   {
      String s = "<a><![CDATA[somedata]]></a>";
      Element e = XMLUtil.stringToElement(s);
      String tostring = XMLUtil.elementToString(e);
      Element convertedAgain = XMLUtil.stringToElement(tostring);
      XMLUtil.assertEquivalent(e, convertedAgain);
   }

   public void testReplaceSystemProperties()
   {
      String before = "<deployment>\n" +
           "   <test name=\"${sysprop1}\">content1</test>\n" +
           "   <test name=\"test2\">content2</test>\n" +
           "   <test name=\"test3\">content3</test>\n" +
           "   <test name=\"test4\">${sysprop2}</test>\n" +
           "   <test name=\"test5\">content5</test>\n" +
           "   <test name=\"test6\">content6</test>\n" +
           "</deployment>";
      String after = "<deployment>\n" +
           "   <test name=\"test1\">content1</test>\n" +
           "   <test name=\"test2\">content2</test>\n" +
           "   <test name=\"test3\">content3</test>\n" +
           "   <test name=\"test4\">content4</test>\n" +
           "   <test name=\"test5\">content5</test>\n" +
           "   <test name=\"test6\">content6</test>\n" +
           "</deployment>";
      System.setProperty("sysprop1", "test1");
      System.setProperty("sysprop2", "content4");
      String replaced = XMLUtil.replaceSystemProps(before);
      assertEquals(after, replaced);
   }
   
   public void testStripCDATA() throws Exception
   {
      String xml = "<![CDATA[somedata]]>";
      String stripped = XMLUtil.stripCDATA(xml);

      assertEquals("somedata", stripped);
   }


}
