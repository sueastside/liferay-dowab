package be.hyperverse.dowab.war;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.SetUtil;

import be.hyperverse.dowab.BundleUtils;
import be.hyperverse.dowab.ZipUtils;

public class WarHandler {
	private static final Log log = LogFactoryUtil.getLog(WarHandler.class);
	
	private static final String LIFERAY_HOME = PropsUtil.get(PropsKeys.LIFERAY_HOME);
	private static final String DEPLOY = PropsUtil.get(PropsKeys.AUTO_DEPLOY_DEPLOY_DIR);
	
	private static final String TLDS_PATH = LIFERAY_HOME + "/tomcat-8.0.32/webapps/ROOT/WEB-INF/tld/";
	private static final List<String> TLDS = Arrays.asList("liferay-ui.tld", "liferay-aui.tld", "liferay-portlet-ext.tld",
										"liferay-util.tld", "liferay-security.tld", "liferay-portlet.tld", "liferay-theme.tld");
	
	private static final String LIBS_PATH = LIFERAY_HOME+"/tomcat-8.0.32/webapps/ROOT/WEB-INF/lib/";
	private static final List<String> LIBS = Arrays.asList("util-bridges.jar", "util-java.jar");

	private final Set<String> ignoredResourcePaths = SetUtil.fromArray(
			PropsUtil.getArray(PropsKeys.MODULE_FRAMEWORK_WEB_GENERATOR_EXCLUDED_PATHS));
	
	private final BundleContext bc;
	
	public WarHandler(final BundleContext bc) {
		this.bc = bc;
	}
	
	public void processFile(File file) {
		String symbolicName = BundleUtils.getSymbolicName(file);
		Optional<Bundle> bundle = Arrays.asList(bc.getBundles()).stream()
				.filter(b -> b.getSymbolicName().equals(symbolicName)).findFirst();
		try {
			System.out.println("bloop: "+Paths.get(DEPLOY, file.getName()));
			if (bundle.isPresent()) {
				log.info("Updating bundle...");
				cleanCache(file, bundle.get());
				updateBundle(bundle.get(), file);
			} else {
				log.info("Bundle not yet present, doing a classic deploy...");
				Files.move(Paths.get(file.getAbsolutePath()), Paths.get(DEPLOY, file.getName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			log.info("Processed: "+file.getName());
		} catch (IOException | BundleException e) {
			log.warn(e);
		} finally {
			if (bundle.isPresent()) {
				file.delete();
			}
		}
	}
	
	private void cleanCache(File file, Bundle bundle) {
		// TODO: remove css cache only if css/js changed
		Enumeration<URL> js = bundle.findEntries("/js/", "*.js", true);
		Collections.list(js).stream().forEach(x -> {
			System.out.println(x.getPath());
		});
		Enumeration<URL> css = bundle.findEntries("/css/", "*.css", true);
		Collections.list(css).stream().forEach(x -> {
			System.out.println(x.getPath());
		});
		cleanCache(bundle.getSymbolicName());
	}
	
	private void cleanCache(String symbolicName) {
		 File cachePath = new File(LIFERAY_HOME + "/tomcat-8.0.32/work/Catalina/localhost/ROOT/aggregate");
		 Arrays.asList(cachePath.listFiles()).stream().filter(x -> x.getName().toLowerCase().contains(symbolicName)).forEach(x -> {
			System.out.println("May want to delete cache file: "+x.getAbsolutePath());
		});
	}
	
	private void updateBundle(Bundle bundle, File file) throws IOException, BundleException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(bytes);

		copyEntry(bundle, "/META-INF/MANIFEST.MF", zip);
		copyEntry(bundle, "/WEB-INF/liferay-plugin-package.properties", zip);
		copyEntry(bc.getBundle().getResource("_servlet_context_include.jsp").openStream(),
				"/WEB-INF/jsp/_servlet_context_include.jsp", zip);

		copyEntry(bc.getBundle().getResource("log4j.properties").openStream(),
				"/WEB-INF/classes/log4j.properties", zip);
		copyEntry(bc.getBundle().getResource("logging.properties").openStream(),
				"/WEB-INF/classes/logging.properties", zip);

		Enumeration<URL> xmls = bundle.findEntries("/WEB-INF/", "*.xml", false);
		Collections.list(xmls).stream().forEach(x -> {
			copyEntry(bundle, x.getPath(), zip);
		});
		// TODO: missing liferay-plugin-package.xml in bundle so can't copy it over, 
		//but this does not seem to affect it.

		TLDS.forEach(t -> {
			try {
				copyEntry(new FileInputStream(TLDS_PATH + t), "/WEB-INF/tld/" + t, zip);
			} catch (FileNotFoundException e) {
				log.warn(e);
			}
		});

		LIBS.forEach(t -> {
			try {
				copyEntry(new FileInputStream(LIBS_PATH + t), "/WEB-INF/lib/" + t, zip);
			} catch (FileNotFoundException e) {
				log.warn(e);
			}
		});

		ZipUtils.copyZip(file, zip, ignoredResourcePaths);

		zip.close();

		bundle.update(new ByteArrayInputStream(bytes.toByteArray()));
	}
	
	private void copyEntry(Bundle bundle, String fileName, ZipOutputStream append) {
		URL entry = bundle.getEntry(fileName);
		if (entry != null) {
	        try {
	        	ZipEntry e = new ZipEntry(fileName.substring(1));
				append.putNextEntry(e);
				
				ZipUtils.copy(entry.openStream(), append);
		        append.closeEntry();
			} catch (IOException e1) {
				log.warn(e1);
			}
		}
	}
	
	private void copyEntry(InputStream from, String fileName, ZipOutputStream append) {
        try {
        	ZipEntry entry = new ZipEntry(fileName.substring(1));
			append.putNextEntry(entry);
			
			ZipUtils.copy(from, append);
	        append.closeEntry();
		} catch (IOException e) {
			log.warn(e);
		}
	}
}
