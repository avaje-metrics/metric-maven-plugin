package io.avaje.metrics.enhance.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import io.avaje.metrics.agent.AgentManifest;
import io.avaje.metrics.agent.Transformer;
import io.avaje.metrics.agent.offline.OfflineFileTransform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A Maven Plugin that can enhance beans adding timed metric collection.
 * <p>
 * You can use this plugin as part of your build process to enhance beans
 * etc.
 * <p>
 * The parameters are:
 * <ul>
 * <li><b>classDestination</b> This is the root directory where the .class files
 * are written to. If this is left out then this defaults to the
 * <b>classSource</b>.</li>
 * <li><b>packages</b> A comma delimited list of packages that is searched for
 * classes that need to be enhanced. If the package ends with ** or * then all
 * subpackages are also searched.</li>
 * <li><b>transformArgs</b> Arguments passed to the transformer. Typically a
 * debug level in the form of debug=1 etc.</li>
 * </ul>
 */
@Mojo(name = "enhance", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class MavenEnhanceTask extends AbstractMojo {

  /**
   * The class path used to read related classes.
   */
  @Parameter(property = "project.compileClasspathElements", required = true, readonly = true)
  private List<String> compileClasspathElements;

  /**
   * The directory holding the class files we want to transform.
   */
  @Parameter(property = "project.build.outputDirectory", required = true, readonly = true)
  private String classSource;

  /**
   * Set the destination directory where we will put the transformed classes.
   * <p/>
   * This is commonly the same as the classSource directory.
   */
  @Parameter
  private String classDestination;

  public void execute() throws MojoExecutionException {

    final Log log = getLog();
    if (classSource == null) {
      classSource = "target/classes";
    }

    if (classDestination == null) {
      classDestination = classSource;
    }

    ClassLoader cl = buildClassLoader();

    AgentManifest agentManifest = AgentManifest.read(cl);

    Transformer t = new Transformer(agentManifest);
    t.setLogger(metricName -> {
      getLog().info("Add timed metric " + metricName);
    });

    Set<String> packageSet = agentManifest.getPackages();

    log.info("classSource=" + classSource + "  classDestination="
            + classDestination + "  manifestPackages=" + packageSet +" classPathSize:"+ compileClasspathElements.size());

    OfflineFileTransform ft = new OfflineFileTransform(t, cl, classSource, classDestination);
    try {
      ft.process(null);

    } catch (FileNotFoundException e) {
      log.warn("Unable to transform classes: "+e.getMessage());

    } catch (IOException e) {
      throw new MojoExecutionException("Error trying to transform classes", e);
    }
  }

  /**
   * Return the ClassLoader used during the enhancement.
   */
  private ClassLoader buildClassLoader() {
    URL[] urls = buildClassPath();
    return URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Return the class path using project compileClasspathElements.
   */
  private URL[] buildClassPath() {
    try {
      List<URL> urls = new ArrayList<>(compileClasspathElements.size());

      Log log = getLog();

      for (String element : compileClasspathElements) {
        if (log.isDebugEnabled()) {
          log.debug("ClasspathElement: " + element);
        }
        urls.add(new File(element).toURI().toURL());
      }

      return urls.toArray(new URL[0]);

    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
