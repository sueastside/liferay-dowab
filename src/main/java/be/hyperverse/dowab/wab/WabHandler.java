package be.hyperverse.dowab.wab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.startlevel.BundleStartLevel;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import be.hyperverse.dowab.BundleUtils;
import be.hyperverse.dowab.war.WarHandler;

public class WabHandler {
	private static final Log log = LogFactoryUtil.getLog(WarHandler.class);

	private final BundleContext bc;

	public WabHandler(final BundleContext bc) {
		this.bc = bc;
	}

	public void processFile(final File file) {
		try {
			String symbolicName = BundleUtils.getSymbolicName(file);
			URL artifactPath = createBundleLocation(file.getAbsolutePath(), symbolicName);

			Optional<Bundle> bundle = Arrays.stream(bc.getBundles()).filter(b -> b.getSymbolicName().equals(symbolicName)).findFirst();
			try (FileInputStream fileStream = new FileInputStream(file)) {
				if (bundle.isPresent()) {
					log.info("Updating: " + bundle);
					bundle.get().update(fileStream);
				} else {
					log.info("Installing: " + bundle);
					Bundle b = bc.installBundle(artifactPath.toString(), new FileInputStream(file));
					BundleStartLevel bundleStartLevel = b.adapt(BundleStartLevel.class);
					bundleStartLevel.setStartLevel(1);
					b.start();
				}
				log.info("Processed: " + file.getName());
			} catch (IOException e) {
				log.warn(e);
			}
		} catch (MalformedURLException | BundleException e) {
			log.warn(e);
		} finally {
			file.delete();
		}
	}

	private URL createBundleLocation(final String path, final String symbolicName) throws MalformedURLException {
		String contextName = symbolicName;

		StringBuilder sb = new StringBuilder();

		sb.append("file:" + path.replaceAll("\\\\", "/"));
		sb.append("?");
		sb.append(Constants.BUNDLE_SYMBOLICNAME);
		sb.append("=");
		sb.append(symbolicName);
		sb.append("&Web-ContextPath=/");
		sb.append(contextName);
		sb.append("&protocol=file");
		
		return new URL("webbundle", null, sb.toString());
	}
}
