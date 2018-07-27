/*
 * FilesIndexer.java
 */
package org.docma.webindexer;

import com.nexwave.nquindexer.IndexerConstants;
import com.nexwave.nsidita.DocFileInfo;
import com.nexwave.stemmer.snowball.SnowballStemmer;
import com.nexwave.stemmer.snowball.ext.EnglishStemmer;
import com.nexwave.stemmer.snowball.ext.FrenchStemmer;
import com.nexwave.stemmer.snowball.ext.GermanStemmer;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 *
 * @author MP
 */
public class FilesIndexer 
{
    // tempDico stores all the keywords and a pointer to the files containing 
    // the index in a Map
    // Example: ("keyword1", "0,2,4"), ("docbook", "1,2,5") 
    private Map<String,String> tempDico;
    private DocFileInfo fileDesc = null;
    private int fileIdx = 0;

    // Words to ignore
    private ArrayList <String> cleanUpList = null;
    
    // Special characters to be ignored (transformed to space)
    private ArrayList <String> cleanUpPunctuation = null;

    // Encoding properties (character set, symbolic entities)
    private Properties encodingProps = null;

    
    /**
     * Constructor
     */
    public FilesIndexer () 
    {
    }
    
    /**
     * Constructor
     */
    public FilesIndexer (ArrayList <String> cleanUpStrings) 
    {
        this.cleanUpList = cleanUpStrings;
    }
    
    /**
     * Constructor
     */
    public FilesIndexer (ArrayList <String> cleanUpStrings, 
                         ArrayList <String> cleanUpChars, 
                         Properties encodingProps) 
    {
        this.cleanUpList = cleanUpStrings;
        this.cleanUpPunctuation = cleanUpChars;
        this.encodingProps = encodingProps;
    }

    /**
     * Initializer
     */
    public int init(Map<String,String> tempMap)
    {
        tempDico = tempMap;
        return 0;
    }

    /**
     * Parses the file to extract all the words for indexing and
     * some data characterizing the file.
     * @param file contains the fullpath of the document to parse
     * @param indexerLanguage this will be used to tell the program which stemmer to be used.
     * @return a DitaFileInfo object filled with data describing the file
     */
    public DocFileInfo runExtractData(File file, String indexerLanguage) 
    {
        //initialization
        fileDesc = new DocFileInfo(file);
        
        // Fill txtBuf by parsing the file
        StringBuilder txtBuf = null; 
        try {
            String fileEncoding = null;
            if (encodingProps != null) {
                fileEncoding = encodingProps.getProperty("file_encoding");
            }
            String fileContent = XHTMLReader.readFile(file, fileEncoding);
            txtBuf = TextExtracter.extract(fileContent, fileDesc);
            decodeCharEntities(txtBuf);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        if (txtBuf == null) {
            txtBuf = new StringBuilder("");
        }
        
        String str = cleanBuffer(txtBuf);
        str = str.replaceAll("\\s+", " ");  //there's still redundant spaces in the middle
        // System.out.println(file.toString()+" "+ str +"\n");
        String[] items = str.split("\\s");      //contains all the words in the array

        // Get items one-by-one, tunnel through the stemmer, and get the stem.
        // Then, add them to tempSet
        // Do Stemming for words in items
        // TODO currently, stemming support is for english and german only. 
        // Add support for other languages as well.

        String[] tokenizedItems;
        if (indexerLanguage.equalsIgnoreCase("ja") || indexerLanguage.equalsIgnoreCase("zh")
            || indexerLanguage.equalsIgnoreCase("ko")) {

            LinkedList<String> tokens = new LinkedList<String>();
            try {
                CJKAnalyzer analyzer = new CJKAnalyzer(org.apache.lucene.util.Version.LUCENE_30);
                Reader reader = new StringReader(str);
                TokenStream stream = analyzer.tokenStream("", reader);
                TermAttribute termAtt = (TermAttribute) stream.addAttribute(TermAttribute.class);
                OffsetAttribute offAtt = (OffsetAttribute) stream.addAttribute(OffsetAttribute.class);

                while (stream.incrementToken()) {
                    String term = termAtt.term();
                    tokens.add(term);
                    // System.out.println(term + " " + offAtt.startOffset() + " " + offAtt.endOffset());
                }

                tokenizedItems = tokens.toArray(new String[tokens.size()]);

            } catch (IOException ex) {
                tokenizedItems = items;
                System.out.println("Error tokenizing content using CJK Analyzer. IOException");
                ex.printStackTrace();
            }
        } else {
            SnowballStemmer stemmer;
            if(indexerLanguage.equalsIgnoreCase("en")){
                 stemmer = new EnglishStemmer();
            } else if (indexerLanguage.equalsIgnoreCase("de")){
                stemmer= new GermanStemmer();
            } else if (indexerLanguage.equalsIgnoreCase("fr")){
                stemmer= new FrenchStemmer();
            } else {
                stemmer = null; //Languages which stemming is not yet supproted.So, No stemmers will be used.
            }
            if(stemmer != null)             //If a stemmer available
                tokenizedItems = stemmer.doStem(items);
            else                            //if no stemmer available for the particular language
                tokenizedItems = items;

        }

        /* 
        for (String stemmedItem: tokenizedItems) {
            System.out.print(stemmedItem+"| ");
        }
        */

        //items: remove the duplicated strings first
        HashSet <String> tempSet = new HashSet<String>();
        tempSet.addAll(Arrays.asList(tokenizedItems));
        Iterator it = tempSet.iterator();
        String s;
        while (it.hasNext()) {
            s = (String) it.next();
            if (tempDico.containsKey(s)) {
                String temp = tempDico.get(s);
                temp = temp.concat(",").concat(Integer.toString(fileIdx));
                //System.out.println("temp="+s+"="+temp);
                tempDico.put(s, temp);
            } else {
                tempDico.put(s, Integer.toString(fileIdx));
            }
        }

        fileIdx++;
        return fileDesc;
    }

    /**
     * Cleans the string buffer containing all the text retrieved from
     * the html file:  remove punctuation, clean white spaces, remove the words
     * which you do not want to index.
     * NOTE: You may customize this function:
     * This version takes into account english and japanese. Depending on your
     * needs, you may have to add/remove some characters/words through props 
     * files or by modifying the default code,
     * you may want to separate the language processing (doc only in japanese,
     * doc only in english, check the language metadata ...).
     */
    private String cleanBuffer (StringBuilder strbf) 
    {
        String str = strbf.toString().toLowerCase();
        StringBuilder tempStrBuf = new StringBuilder("");
        StringBuilder tempCharBuf = new StringBuilder("");
        if ((cleanUpList == null) || (cleanUpList.isEmpty())) {
            // Default clean-up

            // Should perhaps eliminate the words at the end of the table?
            tempStrBuf.append("(?i)\\bthe\\b|\\ba\\b|\\ban\\b|\\bto\\b|\\band\\b|\\bor\\b");//(?i) ignores the case
            tempStrBuf.append("|\\bis\\b|\\bare\\b|\\bin\\b|\\bwith\\b|\\bbe\\b|\\bcan\\b");
            tempStrBuf.append("|\\beach\\b|\\bhas\\b|\\bhave\\b|\\bof\\b|\\b\\xA9\\b|\\bnot\\b");
            tempStrBuf.append("|\\bfor\\b|\\bthis\\b|\\bas\\b|\\bit\\b|\\bhe\\b|\\bshe\\b");
            tempStrBuf.append("|\\byou\\b|\\bby\\b|\\bso\\b|\\bon\\b|\\byour\\b|\\bat\\b");
            tempStrBuf.append("|\\b-or-\\b|\\bso\\b|\\bon\\b|\\byour\\b|\\bat\\b");
            tempStrBuf.append("|\\bI\\b|\\bme\\b|\\bmy\\b");

            // str = str.replaceFirst("Copyright � 1998-2007 NexWave Solutions.", " ");

            //nqu 25.01.2008 str = str.replaceAll("\\b.\\b|\\\\", " ");
            // remove contiguous white charaters
            //nqu 25.01.2008 str = str.replaceAll("\\s+", " ");
        } else {
            // Clean-up using the props files
            tempStrBuf.append("\\ba\\b");
            Iterator it = cleanUpList.iterator();
            while (it.hasNext()) {
                tempStrBuf.append("|\\b" + it.next() + "\\b");
            }
        }
        
        if ((cleanUpPunctuation != null) && (!cleanUpPunctuation.isEmpty())) {
            tempCharBuf.append("\\u3002");
            Iterator it = cleanUpPunctuation.iterator();
            while (it.hasNext()) {
                tempCharBuf.append("|" + it.next());
            }
        }

        str = minimalClean(str, tempStrBuf.toString(), tempCharBuf.toString());
        return str;
    }

    private String minimalClean(String str, String ignoreWords, String punctuation) 
    {
        str = str.replaceAll("\\s+", " ");
        str = str.replaceAll("->", " ");
        str = str.replaceAll(IndexerConstants.EUPUNCTUATION1, " ");
        str = str.replaceAll(IndexerConstants.EUPUNCTUATION2, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION1, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION2, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION3, " ");
        if (punctuation.length() > 0) {
            str = str.replaceAll(punctuation, " ");
        }

        //remove useless words
        str = str.replaceAll(ignoreWords, " ");

        // Redo punctuation after removing some words: (TODO: useful?)
        str = str.replaceAll(IndexerConstants.EUPUNCTUATION1, " ");
        str = str.replaceAll(IndexerConstants.EUPUNCTUATION2, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION1, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION2, " ");
        str = str.replaceAll(IndexerConstants.JPPUNCTUATION3, " ");
        if (punctuation.length() > 0) {
            str = str.replaceAll(punctuation, " ");
        }
        return str;
    }
    
    private void decodeCharEntities(StringBuilder buf)
    {
        int start_pos = 0;
        while (start_pos < buf.length()) {
            int pos = buf.indexOf("&", start_pos);
            if (pos < 0) break;  // no more entities exist
            start_pos = pos + 1;

            int pos2 = buf.indexOf(";", pos);
            if (pos2 < 0) break;  // no more entities exist
            
            String ent_name = buf.substring(pos + 1, pos2);
            if (ent_name.contains(" ")) {
                continue;   // no valid entity; search next entity
            }
            
            // Convert entity to character code 
            int code = -1;
            try {
                if (ent_name.startsWith("#")) {  // numeric entity
                    if (ent_name.startsWith("#x")) {  // hexadecimal
                        code = Integer.parseInt(ent_name.substring(2), 16);
                    } else {  // decimal
                        code = Integer.parseInt(ent_name.substring(1));
                    }
                } else {  // symbolic entity
                    if (encodingProps != null) {
                        String code_str = encodingProps.getProperty("symbol." + ent_name);
                        if (code_str != null) {
                            code = Integer.parseInt(code_str);
                        }
                    }
                }
            } catch (Exception ex) {   // Invalid entity; code remains -1
            }
            
            // Replace entity by character 
            if (code > 0) {
                String replaceStr = "" + (char) code;
                buf.replace(pos, pos2 + 1, replaceStr);
            }
            
            // Continue search at start_pos
        }
        
    }
}
