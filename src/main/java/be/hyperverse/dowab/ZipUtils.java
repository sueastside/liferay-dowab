package be.hyperverse.dowab;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

public class ZipUtils {
	private static final Log log = LogFactoryUtil.getLog(ZipUtils.class);
	
	private static final byte[] BUFFER = new byte[4096 * 1024];
	
	private ZipUtils() {}

    public static void copy(InputStream input, OutputStream output) throws IOException {
        int bytesRead;
        while ((bytesRead = input.read(BUFFER))!= -1) {
            output.write(BUFFER, 0, bytesRead);
        }
    }
    
    public static void copyZip(File from, ZipOutputStream append, Set<String> excludes) throws IOException {
    	try(ZipFile war = new ZipFile(from)) {
	        Enumeration<? extends ZipEntry> entries = war.entries();
	        while (entries.hasMoreElements()) {
	            ZipEntry e = entries.nextElement();
	            if (!excludes.contains(e.getName())) {
		            try {
			            append.putNextEntry(e);
			            if (!e.isDirectory()) {
			                copy(war.getInputStream(e), append);
			            }
			            append.closeEntry();
		            } catch (ZipException ze) {
		            	log.debug(ze.getMessage());
		            }
	            }
	        }
    	}
    }
}
