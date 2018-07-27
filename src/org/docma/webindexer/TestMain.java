/*
 * TestMain.java
 */
package org.docma.webindexer;

/**
 *
 * @author MP
 */
public class TestMain 
{
    public static void main(String[] args) throws Exception
    {
        DocmaWebIndexer indexer = new DocmaWebIndexer();
        indexer.setHtmldir("C:\\TEMP\\webindexer_test_input");
        indexer.setIndexerLanguage("en");
        indexer.execute();
    }
}
