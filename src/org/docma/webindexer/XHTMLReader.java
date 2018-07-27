/*
 * XHTMLReader.java
 */
package org.docma.webindexer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 *
 * @author MP
 */
public class XHTMLReader 
{
    public static String readFile(File file) throws Exception
    {
        return readFile(file, null);
    }
    
    public static String readFile(File file, String encoding) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
        try (FileInputStream fin = new FileInputStream(file)) {
            byte[] buf = new byte[64*1024];
            int cnt;
            while ((cnt = fin.read(buf)) >= 0) {
                if (cnt > 0) out.write(buf, 0, cnt);
            }
        }
        out.close();
        byte[] content = out.toByteArray();

        if ((encoding == null) || encoding.trim().equals("")) {
            encoding = guessEncoding(content);
        }
        
        String res = "";
        try {
            res = new String(content, encoding);
        } catch (UnsupportedEncodingException ex) {
            if (! encoding.equalsIgnoreCase("UTF-8")) {
                res = new String(content, "UTF-8");
                System.out.println("Unsupported encoding '" + encoding + "' for file '" + 
                                   file.getName() + ". Falling back to UTF-8.");
            } else {
                System.out.println("Cannot read file '" + file.getName() + "'. Unsupported encoding: " + encoding);
            }
        }
        return res;
    }

    private static String guessEncoding(byte[] content) throws Exception
    {
        String head = getHead(content);
        String encoding = extractXMLEncoding(head);
        if (encoding == null) {
            encoding = extractContentTypeEncoding(head);
        }
        return (encoding == null) ? "UTF-8" : encoding;
    }
    
    private static String extractXMLEncoding(String head)
    {
        // Try to extract encoding from XML declaration. Example:
        // <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        String encoding = null;
        int xStart = head.indexOf("<?xml");
        if (xStart >= 0) {
            int xEnd = head.indexOf("?>", xStart);
            if (xEnd > 0) {
                String s = head.substring(xStart, xEnd)
                               .replace('\'', '"')
                               .replaceAll("\\s*=\\s*", "=").toLowerCase();

                final String ENC_PATTERN = "encoding=\"";
                int p1 = s.indexOf(ENC_PATTERN);
                if (p1 > 0) {
                    p1 += ENC_PATTERN.length();
                    int p2 = s.indexOf('"', p1);
                    if (p2 > 0) {
                        encoding = s.substring(p1, p2);
                    }
                }
            }
        }
        return encoding;
    }
    
    private static String extractContentTypeEncoding(String head)
    {
        // Try to extract encoding from content-type. Example:
        // <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
        
        String encoding = null;
        final String CHARSET_PATTERN = "charset=";
        head = head.toLowerCase();
        int pos = 0;
        while (pos < head.length()) {
            int p1 = head.indexOf("<meta", pos);
            if (p1 < 0) {
                break;
            }
            pos = p1 + 1;  // continue after this meta tag
            int p2 = head.indexOf('>', p1);
            if (p2 > 0) {
                String meta = head.substring(p1, p2 + 1)
                                  .replace('\'', '"')
                                  .replaceAll("\\s*=\\s*", "=");
                if (meta.contains("http-equiv=\"content-type\"")) {

                    int p3 = meta.lastIndexOf(CHARSET_PATTERN);
                    if (p3 > 0) {
                        p3 += CHARSET_PATTERN.length();
                        int p4 = meta.indexOf('"', p3);
                        if (p4 > 0) {
                            encoding = meta.substring(p3, p4).trim().toUpperCase();
                            if (encoding.endsWith(";")) {
                                encoding = encoding.substring(0, encoding.length() - 1).trim();
                            }
                        }
                    }
                }
            }
        }
        return encoding;
    }
    
    private static String getHead(byte[] content) throws Exception
    {
        // Read content up to the body tag
        
        final String BODY = "body";
        int len = content.length; // Math.min(content.length, 1000);
        int i = 0;
        while (i < len) {
            if (((char) content[i]) == '<') {
                if (i + BODY.length() < len) {
                    boolean isBody = true;
                    for (int k=0; k < BODY.length(); k++) {
                        if (Character.toLowerCase(content[i + k + 1]) != BODY.charAt(k)) {
                            isBody = false;
                            break;
                        }
                    }
                    if (isBody ) {
                        break;
                    }
                }
            }
            i++;
        }
        byte[] head = (i < len) ? Arrays.copyOf(content, i) : content;
        return new String(head, "UTF-8");
    }
}
