package be.hyperverse.dowab;

import java.io.File;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;

//https://www.youtube.com/watch?v=XPWa_yg6o4U 
@Component(immediate = true)
public class WarDoWab {
	private static final Log log = LogFactoryUtil.getLog(WarDoWab.class);

	private static final String LIFERAY_HOME = PropsUtil.get(PropsKeys.LIFERAY_HOME)+File.separator;
	private static final String PATH = "dowab";

	private AutoDeployScanner autoDeployScanner;

	@Activate
	public void activate(final BundleContext bc) {
		log.info("activate");

		Thread currentThread = Thread.currentThread();
		final File deployDir = new File(LIFERAY_HOME + File.separator + PATH);
		autoDeployScanner = new AutoDeployScanner(bc, currentThread.getThreadGroup(), AutoDeployScanner.class.getName(), deployDir);
		autoDeployScanner.start();

		log.info("activated");
	}

	@Deactivate
	public void deactivate() {
		log.info("deactivate");

		autoDeployScanner.pause();

		log.info("deactivated");
	}
}