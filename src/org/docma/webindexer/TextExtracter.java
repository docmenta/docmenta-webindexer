/*
 * TextExtracter.java
 */
package org.docma.webindexer;

import com.nexwave.nsidita.BlankRemover;
import com.nexwave.nsidita.DocFileInfo;

import java.util.HashMap;
import java.util.Map;
import org.docma.util.XMLParser;

/**
 *
 * @author MP
 */
public class TextExtracter 
{
    public static StringBuilder extract(String xml, DocFileInfo fileDesc) throws Exception
    {
        StringBuilder buf = new StringBuilder(Math.max(xml.length(), 32));
        
        int txtStart = 0;
        int titleStart = -1;
        XMLParser xmlParser = new XMLParser(xml, true, true, true);
        Map<String, String> atts = new HashMap<String, String>();
        boolean isFileTitle = false;
        boolean isScript = false;
        boolean isContent = false;
        int divLevel = 0;
        
        int nextType;
        do {
            nextType = xmlParser.next();
            if (nextType == XMLParser.START_ELEMENT) {
                
                // Add text between previous tag and this tag
                int tagStart = xmlParser.getStartOffset();
                if (tagStart > txtStart) {
                    buf.append(xml, txtStart, tagStart);
                }
                txtStart = xmlParser.getEndOffset();
                
                // Get tag information
                String eName = xmlParser.getElementName().toLowerCase();
                xmlParser.getAttributesLower(atts);
                boolean nonEmpty = !xmlParser.isEmptyElement();
                
                // Handle meta tag
                if (eName.equals("meta")) {
                    String metaName = atts.get("name");
                    if((metaName != null) && (metaName.equals("keywords") || metaName.equals("description"))) {
                        String metaContent = atts.get("content");
                        if ((metaContent != null) && !metaContent.equals("")) {
                            buf.append(" ").append(metaContent).append(" ");
                        }
                        
                        // dwc: adding this to make the docbook <abstract> element
                        // (which becomes <meta name="description".../> in html)
                        // into the brief description that shows up in search
                        // results.
                        if (metaName.equals("description") && (metaContent != null)) {
                            String descLine = BlankRemover.rmWhiteSpace(metaContent.replace('\n', ' '));
                            fileDesc.setShortdesc(descLine);
                        }
                    }
                }
                
                // Handle title tag
                isFileTitle = nonEmpty && eName.equals("title"); // || eName.equals("shortdesc");
                if (isFileTitle) {
                    titleStart = xmlParser.getEndOffset();
                }
                
                // Handle script tag
                isScript = nonEmpty && eName.equals("script");

                // Skip navigation bars and elements that are marked with
                // class "no_search_indexing".
                boolean skipped = false;
                String clsVal = atts.get("class");
                if (clsVal != null) {
                    clsVal = " " + clsVal + " ";
                    if (clsVal.contains(" navfooter ") || 
                        clsVal.contains(" navheader ") || 
                        clsVal.contains(" no_search_indexing ")) {
                        // Skip this element 
                        xmlParser.readUntilCorrespondingClosingTag(); 
                        txtStart = xmlParser.getEndOffset();  // skip text inside skipped element
                        skipped = true;
                    }
                }

                if (! skipped) {
                    String idVal = atts.get("id");
                    if("content".equals(idVal)) {
                        isContent = true;
                    }

                    if (isContent) {
                        if(eName.equals("div")){
                            divLevel++;
                        }
                        // if (eName.equals("div") || eName.equals("p") || eName.equals("span")) {
                        //     buf.append(" ");
                        // }
                    }
                }
                buf.append(" ");
                
            } else if (nextType == XMLParser.END_ELEMENT) {
                
                // Add text between previous tag and this closing tag
                int tagStart = xmlParser.getStartOffset();
                if ((isContent || isFileTitle) && !isScript) {
                    if (tagStart > txtStart) {
                        buf.append(xml, txtStart, tagStart);
                    }
                }
                txtStart = xmlParser.getEndOffset();
                
                // Get tag name
                String eName = xmlParser.getElementName().toLowerCase();
                
                // Handle title tag
                if (eName.equals("title")) {
                    int titleEnd = xmlParser.getStartOffset();
                    if ((titleStart >= 0) && (titleStart < titleEnd)) {
                        String title = xml.substring(titleStart, titleEnd);
                        title = removeXMLTags(title);
                        fileDesc.setTitle(BlankRemover.rmWhiteSpace(title));
                        titleStart = -1;
                    }
                }
                
                if (eName.equals("div") && isContent){
                    divLevel--;
                    if (divLevel == 0) {
                        isContent = false;
                    }
                }
            }
            // else if (nextType == XMLParser.CDATA) {
            // }
        } while (nextType != XMLParser.FINISHED);
        
        // Copy remaining text after the last tag
        if (txtStart < xml.length()) {  // copy remaining content
            buf.append(xml, txtStart, xml.length());
        }
        
        return buf;
    }

    private static String removeXMLTags(String xml) 
    {
        int tstart = xml.indexOf("<");
        if (tstart < 0) {  // no tags found
            return xml;
        }
        StringBuilder buf = new StringBuilder(xml);
        do {
            // find end of tag
            int tend = buf.indexOf(">", tstart + 1);
            if (tend < 0) {
                return buf.toString();
            }
            buf.delete(tstart, tend + 1);  // delete tag
            tstart = buf.indexOf("<", tstart);  // find start of next tag
        } while (tstart >= 0);
        return buf.toString();
    }

}
