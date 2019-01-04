# Liferay-dowab
Speedup liferay 7 DXP deploys.

This bundle creates a 'dowab' folder under your Liferay home (next to the deploy directory). With the following 2 usecases:
 * Decrease deployment time of war files
 * Allow to directly deploy wab files

## Motivation
Although war files are not the recommended way to create portlets for Liferay. You'll still see them in migration projects and complex portlets using the Spring framework.
Liferay uses some 'magic' to convert these wars to osgi ready wab bundles at deploy time using their internal WabGenerator.
This is however done at 'every' deploy and it scans all class files, jars, xmls and jsps for imports using the [BND tool](https://bnd.bndtools.org/). A very expensive and memory intensive operation. With large portlets it is not uncommon to see deploy times of several minutes!

## War files
When deploying war files to the 'dowab' folder it checks the symbolic name if the portlet is already deployed; if it is not, it is deployed normally(no speedup). If it is already deployed, it gets the Manifest file and the transformed liferay xml files from the osgi bundle, copies them into the war together with other specific libraries and then deploys this altered war directly as an Osgi bundle, thus skipping the expensive transformation.

**Limitations**: Adding new imports to java and jsp files or new class references in xml files require a new scan by bnd and thus will not work with this method.

## Wab files
You can deploy wab files from a different system or backup, thus skipping the expensive transformation.
Maybe later a standalone war2wab tool will be made available.
Being able to run this process offline would severaly decrease the memory requirements of the Liferay instance.


[![do wab a do wab](http://img.youtube.com/vi/XPWa_yg6o4U/0.jpg)](http://www.youtube.com/watch?v=XPWa_yg6o4U "do wab a do wab")
