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

package org.hornetq.core.settings.impl;

import java.util.regex.Pattern;

/**
    a Match is the holder for the match string and the object to hold against it.
 */
public class Match<T>
{	
   public static String WORD_WILDCARD = "*";
   private static String WORD_WILDCARD_REPLACEMENT = "[^.]+";
   public static String WILDCARD = "#";
   private static String WILDCARD_REPLACEMENT = ".+";
   private static final String DOT = ".";
   private static final String DOT_REPLACEMENT = "\\.";
   
   private String match;
   private Pattern pattern;
   private T value;



   public Match(String match)
   {
      this.match = match;
      String actMatch = match;
      //replace any regex characters
      if(WILDCARD.equals(match))
      {
         actMatch = WILDCARD_REPLACEMENT;
      }
      else
      {
         actMatch = actMatch.replace(DOT, DOT_REPLACEMENT);
         actMatch = actMatch.replace(WILDCARD,  WILDCARD_REPLACEMENT);
         actMatch = actMatch.replace(WORD_WILDCARD, WORD_WILDCARD_REPLACEMENT);
      }
      pattern = Pattern.compile(actMatch);

   }


   public String getMatch()
   {
      return match;
   }

   public void setMatch(String match)
   {
      this.match = match;
   }

   public Pattern getPattern()
   {
      return pattern;
   }


   public T getValue()
   {
      return value;
   }

   public void setValue(T value)
   {
      this.value = value;
   }

   public boolean equals(Object o)
   {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Match that = (Match) o;

      return !(match != null ? !match.equals(that.match) : that.match != null);

   }

   public int hashCode()
   {
      return (match != null ? match.hashCode() : 0);
   }

   /**
    * utility method to verify consistency of match
    * @param match the match to validate
    * @throws IllegalArgumentException if a match isnt valid
    */
   public static void verify(String match) throws IllegalArgumentException
   {
      if(match == null)
      {
         throw new IllegalArgumentException("match can not be null");
      }
      if(match.contains("#") && match.indexOf("#") < match.length() - 1)
      {
         throw new IllegalArgumentException("* can only be at end of match");
      }
   }

}
