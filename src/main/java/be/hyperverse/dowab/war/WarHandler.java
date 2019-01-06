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
import java.util.*;
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
	private static final List<String> TLDS = Arrays.asList(
			"liferay-ui.tld", "liferay-aui.tld", "liferay-portlet-ext.tld",
			"liferay-util.tld", "liferay-security.tld", "liferay-portlet.tld", "liferay-theme.tld");

	private static final String LIBS_PATH = LIFERAY_HOME + "/tomcat-8.0.32/webapps/ROOT/WEB-INF/lib/";
	private static final List<String> LIBS = Arrays.asList("util-bridges.jar", "util-java.jar");

	private static final String CACHE_PATH = LIFERAY_HOME + "/tomcat-8.0.32/work/Catalina/localhost/ROOT/aggregate";

	private static final String LIFERAY_PROPERTIES_PATH = "/WEB-INF/liferay-plugin-package.properties";
	private static final String MANIFEST_PATH = "/META-INF/MANIFEST.MF";

	private static final Map<String, String> SPECIAL_FILES = new HashMap<>();
	static {
		SPECIAL_FILES.put("_servlet_context_include.jsp", "/WEB-INF/jsp/_servlet_context_include.jsp");
		SPECIAL_FILES.put("log4j.properties", "/WEB-INF/classes/log4j.properties");
		SPECIAL_FILES.put("logging.properties", "/WEB-INF/classes/logging.properties");
	}

	private final Set<String> ignoredResourcePaths = SetUtil.fromArray(
			PropsUtil.getArray(PropsKeys.MODULE_FRAMEWORK_WEB_GENERATOR_EXCLUDED_PATHS));

	private final BundleContext bc;

	public WarHandler(final BundleContext bc) {
		this.bc = bc;
	}
	
	public void processFile(final File file) {
		final String symbolicName = BundleUtils.getSymbolicName(file);
		final Optional<Bundle> bundle = Arrays.stream(bc.getBundles())
				.filter(b -> b.getSymbolicName().equals(symbolicName))
				.findFirst();

		try {
			System.out.println("bloop: " + Paths.get(DEPLOY, file.getName()));
			if (bundle.isPresent()) {
				log.info("Updating bundle...");
				cleanCache(bundle.get());
				updateBundle(bundle.get(), file);
			} else {
				log.info("Bundle not yet present, doing a classic deploy...");
				Files.move(Paths.get(file.getAbsolutePath()), Paths.get(DEPLOY, file.getName()),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			log.info("Processed: " + file.getName());
		} catch (IOException | BundleException e) {
			log.warn(e);
		} finally {
			if (bundle.isPresent()) {
				log.info("Bundle is present, deleting file");
				file.delete();
			}
		}
	}
	
	private void cleanCache(final Bundle bundle) {
		// TODO: remove css cache only if css/js changed
		final Enumeration<URL> js = bundle.findEntries("/js/", "*.js", true);
		Collections.list(js).forEach(x -> {
			System.out.println(x.getPath());
		});

		final Enumeration<URL> css = bundle.findEntries("/css/", "*.css", true);
		Collections.list(css).forEach(x -> {
			System.out.println(x.getPath());
		});

		cleanCache(bundle.getSymbolicName());
	}

	private void cleanCache(final String symbolicName) {
		final File cachePath = new File(CACHE_PATH);
		final File[] listFiles = cachePath.listFiles();
		if (listFiles != null) {
			Arrays.stream(listFiles).filter(x -> x.getName().toLowerCase().contains(symbolicName)).forEach(x -> {
				log.info("May want to delete cache file: " + x.getAbsolutePath());
			});
		} else {
			log.info("No files found in aggregate folder");
		}
	}
	
	private void updateBundle(final Bundle bundle, final File file) throws IOException, BundleException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(bytes);

		copyManifestFile(bundle, zip);
		copyLiferayProperties(bundle, zip);
		copySpecialFiles(zip);

		Enumeration<URL> xmls = bundle.findEntries("/WEB-INF/", "*.xml", false);
		Collections.list(xmls).forEach(x -> {
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

	private void copyManifestFile(final Bundle bundle, final ZipOutputStream zip) {
		copyEntry(bundle, MANIFEST_PATH, zip);
	}

	private void copyLiferayProperties(final Bundle bundle, final ZipOutputStream zip) {
		copyEntry(bundle, LIFERAY_PROPERTIES_PATH, zip);
	}

	private void copySpecialFiles(final ZipOutputStream zip) throws IOException {
		for (Map.Entry<String, String> entry : SPECIAL_FILES.entrySet()) {
			String initialFile = entry.getKey();
			String defaultFile = entry.getValue();
			copyEntry(bc.getBundle().getResource(initialFile).openStream(), defaultFile, zip);
		}
	}

	private void copyEntry(final Bundle bundle, final String fileName, final ZipOutputStream append) {
		URL entry = bundle.getEntry(fileName);
		if (entry != null) {
			try {
				ZipEntry zipEntry = new ZipEntry(sanitizeFilename(fileName));
				append.putNextEntry(zipEntry);

				ZipUtils.copy(entry.openStream(), append);
				append.closeEntry();
			} catch (IOException e) {
				log.warn(e);
			}
		}
	}

	private void copyEntry(final InputStream from, final String fileName, final ZipOutputStream append) {
		try {
			ZipEntry entry = new ZipEntry(sanitizeFilename(fileName));
			append.putNextEntry(entry);

			ZipUtils.copy(from, append);
			append.closeEntry();
		} catch (IOException e) {
			log.warn(e);
		}
	}

	/**
	 * Remove trailing character if forward slash.
	 * @param fileName
	 * @return
	 */
	private String sanitizeFilename(final String fileName) {
		if (fileName.indexOf(1) == '/') {
			return fileName.substring(1);
		}

		return fileName;
	}
}
